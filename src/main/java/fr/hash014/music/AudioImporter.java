/* Music Reforged — made by hash014. Rewritten 2026-10-11. GPL-2.0-or-later. */
package fr.hash014.music;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import javax.sound.sampled.*;
import org.bukkit.Bukkit;

public final class AudioImporter implements AutoCloseable {
    public static final class Job {
        public final String id = UUID.randomUUID().toString();
        public final String name; public volatile String status = "queued", message = "Queued"; public volatile int progress;
        Job(String name) { this.name = name; }
    }
    private final MusicReforged plugin;
    public final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor worker;
    public AudioImporter(MusicReforged p) {
        plugin = p; worker = new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(4),r -> { Thread t = new Thread(r,"MusicReforged-Import");t.setDaemon(true);return t; });
    }
    public Job submit(String name, String extension, byte[] input, Runnable authorizeCommit) {
        if (!name.matches("[A-Za-z0-9 _-]{1,80}")) throw new IllegalArgumentException("Invalid track name");
        if (!extension.equals("ogg") && !extension.equals("mp3")) throw new IllegalArgumentException("Choose an OGG or MP3 file");
        int max=plugin.getConfig().getInt("admin.max-upload-mb",32);
        if (input.length == 0 || input.length > max*1024L*1024) throw new IllegalArgumentException("File is empty or too large");
        Job job = new Job(name); jobs.put(job.id,job);
        try { worker.execute(() -> process(job,extension,input,authorizeCommit)); }
        catch (RejectedExecutionException e) { jobs.remove(job.id); throw new IllegalArgumentException("The import queue is full; wait for current imports to finish"); }
        if (jobs.size()>100) jobs.entrySet().removeIf(e -> e.getValue()!=job && (e.getValue().status.equals("done")||e.getValue().status.equals("failed")));
        return job;
    }
    private void process(Job job,String extension,byte[] input,Runnable authorizeCommit) {
        Path work=plugin.getDataFolder().toPath().resolve("import-staging").resolve(job.id);
        try {
            job.status="processing";job.message="Reading and converting audio";job.progress=5;Files.createDirectories(work);
            Path source=work.resolve("source."+extension),pcm=work.resolve("decoded.wav"),ogg=work.resolve("song.ogg"),segments=work.resolve("segments");
            Files.write(source,input);Files.createDirectories(segments);
            String ffmpeg=plugin.getConfig().getString("admin.ffmpeg","ffmpeg");
            int limit=Math.max(1,Math.min(3600,plugin.getConfig().getInt("admin.max-duration-seconds",600)));
            // Explicit demuxer and file-only protocols prevent a disguised playlist from fetching URLs.
            run(work,ffmpeg,"-hide_banner","-loglevel","error","-nostdin","-y","-protocol_whitelist","file,pipe","-f",extension,"-i",source.toAbsolutePath().toString(),"-t",String.valueOf(limit+1),"-ac","1","-ar","22050","-c:a","pcm_s16le",pcm.toAbsolutePath().toString());
            byte[] raw;AudioFormat format;
            try (AudioInputStream stream=AudioSystem.getAudioInputStream(pcm.toFile());ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                format=stream.getFormat();byte[] buffer=new byte[8192];int n;
                while((n=stream.read(buffer))>=0) { out.write(buffer,0,n); if(out.size()>(limit+1)*44100L)throw new IOException("Audio is too long"); }raw=out.toByteArray();
            }
            double seconds=raw.length/(double)(format.getFrameSize()*format.getFrameRate());
            if (seconds<.05 || seconds>limit) throw new IllegalArgumentException("Maximum duration: "+limit+" seconds; the track was not truncated");
            run(work,ffmpeg,"-hide_banner","-loglevel","error","-nostdin","-y","-i",pcm.toAbsolutePath().toString(),"-c:a","libvorbis","-q:a","3",ogg.toAbsolutePath().toString());
            int frames=(int)Math.ceil(seconds*4);int bytesPerSecond=(int)(format.getFrameSize()*format.getFrameRate());
            for(int i=0;i<frames;i++) {
                if(Thread.currentThread().isInterrupted())throw new InterruptedException();
                int start=(int)Math.round(i*.25*bytesPerSecond),end=Math.min(raw.length,(int)Math.round((i+1)*.25*bytesPerSecond));
                start-=start%format.getFrameSize();end-=end%format.getFrameSize();
                Path frame=work.resolve("segment.wav");
                try(AudioInputStream stream=new AudioInputStream(new ByteArrayInputStream(raw,start,end-start),format,(end-start)/format.getFrameSize())) { AudioSystem.write(stream,AudioFileFormat.Type.WAVE,frame.toFile()); }
                run(work,ffmpeg,"-hide_banner","-loglevel","error","-nostdin","-y","-i",frame.toAbsolutePath().toString(),"-c:a","libvorbis","-q:a","3",segments.resolve(String.format(Locale.ROOT,"%05d.ogg",i)).toAbsolutePath().toString());
                job.progress=15+(int)(65L*(i+1)/frames);job.message="Preparing transitions: "+(i+1)+"/"+frames;
            }
            String resource="import_"+job.id.replace("-","");
            Track track=new Track(job.name,"musicreforged:"+resource,seconds,frames);
            job.progress=85;job.message="Creating track and pack";
            Bukkit.getScheduler().callSyncMethod(plugin,() -> {
                authorizeCommit.run();
                if(plugin.tracks().containsKey(track.key()))throw new IllegalArgumentException("This track name is already in use");
                Path destination=plugin.getDataFolder().toPath().resolve("audio/musicreforged");Files.createDirectories(destination);
                Path sound=destination.resolve(resource+".ogg"),chunks=destination.resolve(resource+"_segments");
                try {
                    Files.move(ogg,sound);Files.move(segments,chunks);plugin.library().create(track);plugin.refreshTracks();plugin.pack().build();
                } catch(Exception e) {
                    Files.deleteIfExists(plugin.getDataFolder().toPath().resolve("Music").resolve(track.id+".txt"));
                    Files.deleteIfExists(sound);removeTree(chunks);plugin.refreshTracks();throw e;
                }
                return null;
            }).get(30,TimeUnit.SECONDS);
            job.status="done";job.progress=100;job.message="Track imported; pack generated. Send the new pack to players.";
        } catch(Exception e) {
            Throwable cause=e instanceof ExecutionException&&e.getCause()!=null?e.getCause():e;
            job.status="failed";job.message=cause instanceof IOException&&cause.getMessage()!=null&&cause.getMessage().contains("Cannot run program")?"FFmpeg was not found. Install it or set admin.ffmpeg in config.yml.":(cause.getMessage()==null?cause.getClass().getSimpleName():cause.getMessage());
            plugin.getLogger().warning("Import "+job.name+" failed: "+job.message);
        } finally { try{removeTree(work);}catch(IOException e){plugin.getLogger().warning("Could not clean up import: "+work);} }
    }
    private static void run(Path work,String command,String... args)throws Exception {
        List<String> line=new ArrayList<>();line.add(command);line.addAll(Arrays.asList(args));Path log=work.resolve("ffmpeg.log");
        Process process=new ProcessBuilder(line).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            if(!process.waitFor(60,TimeUnit.SECONDS))throw new IOException("Audio conversion timed out");
            if(process.exitValue()!=0) {String text=new String(Files.readAllBytes(log),StandardCharsets.UTF_8);throw new IOException("Invalid audio file: "+text.substring(0,Math.min(800,text.length())).replace('\n',' '));}
        } finally { if(process.isAlive())process.destroyForcibly(); }
    }
    private static void removeTree(Path folder)throws IOException {
        if(!Files.exists(folder))return;
        List<Path> files=new ArrayList<>();try(java.util.stream.Stream<Path> stream=Files.walk(folder)) { stream.sorted(Comparator.reverseOrder()).forEach(files::add); }
        for(Path p:files)Files.deleteIfExists(p);
    }
    @Override public void close() {worker.shutdownNow();}
}
