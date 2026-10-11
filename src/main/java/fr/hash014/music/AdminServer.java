/* Music Reforged — made by hash014. Rewritten 2026-10-11. GPL-2.0-or-later. */
package fr.hash014.music;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class AdminServer implements AutoCloseable {
    private final MusicReforged p;
    private final Gson gson=new Gson();
    private final Map<String,Session> sessions=new ConcurrentHashMap<>();
    private HttpServer server;private ExecutorService executor;
    private static final class Session { UUID owner;long expires; }
    public AdminServer(MusicReforged p){this.p=p;}
    public void start() throws IOException {
        if(!p.getConfig().getBoolean("admin.enabled",true))return;
        String bind=p.getConfig().getString("admin.bind","127.0.0.1");int port=p.getConfig().getInt("admin.port",8125);
        server=HttpServer.create(new InetSocketAddress(bind,port),16);
        executor=new ThreadPoolExecutor(4,4,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(16),r->{Thread t=new Thread(r,"MusicReforged-Admin");t.setDaemon(true);return t;});server.setExecutor(executor);
        server.createContext("/",this::handle);server.start();p.getLogger().info("Audio administration: "+bind+":"+port+" (sign in with /music admin)");
    }
    public String baseUrl() {
        if(server==null)return "";
        String publicUrl=p.getConfig().getString("admin.public-url","").trim();if(!publicUrl.isEmpty())return publicUrl.replaceAll("/$","");
        String bind=p.getConfig().getString("admin.bind","127.0.0.1");if(bind.equals("0.0.0.0")||bind.equals("::"))return "";
        return "http://"+bind+":"+p.getConfig().getInt("admin.port",8125);
    }
    public String login(CommandSender sender) {
        if(!p.admin(sender))throw new IllegalArgumentException("Administrator permission required");
        String base=baseUrl();if(base.isEmpty())throw new IllegalArgumentException("Enable admin.enabled and configure admin.public-url for remote administration");
        byte[] random=new byte[32];new SecureRandom().nextBytes(random);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        Session session=new Session();session.owner=sender instanceof Player?((Player)sender).getUniqueId():null;session.expires=System.currentTimeMillis()+30*60*1000;
        sessions.entrySet().removeIf(e->e.getValue().expires<System.currentTimeMillis());if(sessions.size()>100)sessions.clear();sessions.put(token,session);
        return base+"/#token="+token;
    }
    private Session auth(HttpExchange exchange) {
        String h=exchange.getRequestHeaders().getFirst("Authorization");if(h==null||!h.startsWith("Bearer "))throw new SecurityException("Sign-in required. Run /music admin in-game or in the console.");
        String token=h.substring(7);Session found=sessions.get(token);if(found==null||found.expires<System.currentTimeMillis())throw new SecurityException("Session expired. Run /music admin again.");return found;
    }
    private void authorize(Session session) {
        if(session.expires<System.currentTimeMillis())throw new SecurityException("Session expired");
        if(session.owner!=null) {Player owner=Bukkit.getPlayer(session.owner);if(owner==null||!owner.isOnline()||!p.admin(owner))throw new SecurityException("Administrator is offline or permission was revoked");}
    }
    private <T>T sync(Callable<T> work)throws Exception {return Bukkit.getScheduler().callSyncMethod(p,work).get(15,TimeUnit.SECONDS);}
    private Map<String,String> query(URI uri)throws UnsupportedEncodingException {
        Map<String,String> result=new HashMap<>();if(uri.getRawQuery()!=null)for(String pair:uri.getRawQuery().split("&")){String[] s=pair.split("=",2);result.put(URLDecoder.decode(s[0],"UTF-8"),s.length==2?URLDecoder.decode(s[1],"UTF-8"):"");}return result;
    }
    private byte[] read(HttpExchange e,int max)throws IOException {
        String length=e.getRequestHeaders().getFirst("Content-Length");if(length!=null&&Long.parseLong(length)>max)throw new IllegalArgumentException("File is too large");
        if(e.getRequestHeaders().getFirst("Content-Encoding")!=null)throw new IllegalArgumentException("Compressed uploads are not accepted");
        ByteArrayOutputStream data=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=e.getRequestBody().read(buffer))>=0){if(data.size()+n>max)throw new IllegalArgumentException("File is too large");data.write(buffer,0,n);}return data.toByteArray();
    }
    private void send(HttpExchange e,int status,String type,byte[] body)throws IOException {
        e.getResponseHeaders().set("Content-Type",type);e.getResponseHeaders().set("Cache-Control","no-store");e.getResponseHeaders().set("X-Content-Type-Options","nosniff");e.getResponseHeaders().set("Referrer-Policy","no-referrer");
        e.getResponseHeaders().set("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; media-src 'self' blob:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'");
        e.sendResponseHeaders(status,body.length);e.getResponseBody().write(body);
    }
    private void json(HttpExchange e,int status,Object value)throws IOException {send(e,status,"application/json; charset=utf-8",gson.toJson(value).getBytes(StandardCharsets.UTF_8));}
    private JsonObject body(HttpExchange e)throws IOException {return JsonParser.parseString(new String(read(e,128*1024),StandardCharsets.UTF_8)).getAsJsonObject();}
    private static String str(JsonObject j,String key){if(!j.has(key)||j.get(key).isJsonNull())throw new IllegalArgumentException("Missing field: "+key);return j.get(key).getAsString();}
    private static int num(JsonObject j,String key,int fallback){return j.has(key)?j.get(key).getAsInt():fallback;}
    private void handle(HttpExchange e) throws IOException {
        try {
            String path=e.getRequestURI().getPath(),method=e.getRequestMethod();
            if(path.equals("/pack.zip")&&method.equals("GET")){send(e,200,"application/zip",Files.readAllBytes(p.getDataFolder().toPath().resolve("pack.zip")));return;}
            if(!path.startsWith("/api/")) {
                if(!method.equals("GET")){json(e,405,Collections.singletonMap("error","Method not allowed"));return;}
                String resource=path.equals("/")?"index.html":path.equals("/app.js")?"app.js":path.equals("/style.css")?"style.css":null;
                if(resource==null){json(e,404,Collections.singletonMap("error","Page not found"));return;}
                try(InputStream in=p.getResource("admin/"+resource);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[]buffer=new byte[8192];int n;while((n=in.read(buffer))>=0)out.write(buffer,0,n);send(e,200,resource.endsWith("css")?"text/css; charset=utf-8":resource.endsWith("js")?"application/javascript; charset=utf-8":"text/html; charset=utf-8",out.toByteArray());}return;
            }
            Session session=auth(e);sync(()->{authorize(session);return null;});
            if(method.equals("GET")&&path.equals("/api/state")){json(e,200,sync(this::snapshot));return;}
            if(method.equals("GET")&&path.equals("/api/jobs")){json(e,200,new ArrayList<>(p.importer().jobs.values()));return;}
            if(method.equals("GET")&&path.equals("/api/audio")) {
                String id=query(e.getRequestURI()).get("id");Track track=sync(()->p.track(id));String[] sound=track.sound.split(":",2);
                Path base=p.getDataFolder().toPath().resolve("audio").toRealPath(),file=base.resolve(sound[0]).resolve(sound[1]+".ogg").toRealPath();
                if(!file.startsWith(base))throw new IllegalArgumentException("Audio unavailable");send(e,200,"audio/ogg",Files.readAllBytes(file));return;
            }
            if(!method.equals("POST")){json(e,405,Collections.singletonMap("error","Method not allowed"));return;}
            String origin=e.getRequestHeaders().getFirst("Origin"),host=e.getRequestHeaders().getFirst("Host");
            if(origin!=null&&!URI.create(origin).getAuthority().equalsIgnoreCase(host))throw new SecurityException("Origin rejected");
            if(path.equals("/api/import")) {
                Map<String,String> q=query(e.getRequestURI());String name=q.getOrDefault("name",""),extension=q.getOrDefault("type","");
                int max=p.getConfig().getInt("admin.max-upload-mb",32)*1024*1024;byte[] data=read(e,max);
                AudioImporter.Job job=p.importer().submit(name,extension,data,()->authorize(session));json(e,202,job);return;
            }
            JsonObject j=body(e);
            Object result=sync(()->{authorize(session);return action(path,j,session);});json(e,200,result);
        } catch(Exception ex) {
            Throwable cause=ex instanceof ExecutionException&&ex.getCause()!=null?ex.getCause():ex;
            int status=cause instanceof SecurityException?403:cause instanceof IllegalArgumentException||cause instanceof FileNotFoundException||cause instanceof NoSuchFileException?400:500;
            String message=cause.getMessage()==null?"Operation failed":cause.getMessage();json(e,status,Collections.singletonMap("error",message));
        } finally {e.close();}
    }
    private Object action(String path,JsonObject j,Session session)throws Exception {
        UUID owner=session.owner==null?AmbientManager.CONSOLE:session.owner;
        switch(path) {
            case "/api/pack":p.pack().build();if(j.has("send")&&j.get("send").getAsBoolean())for(Player player:Bukkit.getOnlinePlayers())p.pack().send(player);return snapshot();
            case "/api/playlists": {
                String op=str(j,"op");
                if(op.equals("create"))p.ambiences().createPlaylist(str(j,"name"),owner);
                else {
                    Station s=p.ambiences().playlist(str(j,"station"));
                    if(op.equals("add")){if(s.queue.size()>=p.getConfig().getInt("max-queue-size",500))throw new IllegalArgumentException("Playlist is full");s.queue.add(p.track(str(j,"track")).id);}
                    else if(op.equals("remove")){int i=num(j,"index",-1);if(i<0||i>=s.queue.size())throw new IllegalArgumentException("Invalid index");s.queue.remove(i);s.reset();}
                    else if(op.equals("move")){int i=num(j,"index",-1),to=num(j,"to",-1);if(i<0||i>=s.queue.size()||to<0||to>=s.queue.size())throw new IllegalArgumentException("Invalid index");String t=s.queue.remove(i);s.queue.add(to,t);s.reset();}
                    else if(op.equals("shuffle"))s.shuffle=!s.shuffle;
                    else if(op.equals("pause")){s.paused=!s.paused;s.reset();}
                    else if(op.equals("delete")){p.state().stations.remove(s.id);for(RadioBox b:p.state().boxes.values())if(b.station==s.id)b.active=false;}
                    else throw new IllegalArgumentException("Unknown action");p.persist();
                }
                return snapshot();
            }
            case "/api/zones":
                if(j.has("delete")){if(p.ambiences().zones.remove(AmbientManager.key(str(j,"delete")))==null)throw new IllegalArgumentException("Unknown zone");p.ambiences().save();}
                else p.ambiences().zone(str(j,"name"),num(j,"station",-1),owner,str(j,"world"),num(j,"x1",0),num(j,"y1",0),num(j,"z1",0),num(j,"x2",0),num(j,"y2",0),num(j,"z2",0),num(j,"priority",30));return snapshot();
            case "/api/rules":
                if(j.has("delete")){p.ambiences().rules.remove(AmbientManager.key(str(j,"delete")));p.ambiences().save();}
                else p.ambiences().rule(str(j,"id"),str(j,"event"),j.has("selector")?str(j,"selector"):"*",num(j,"station",-1),j.has("seconds")?j.get("seconds").getAsDouble():12,num(j,"priority",70));return snapshot();
            case "/api/discs":
                if(j.has("give")){Player player=Bukkit.getPlayerExact(str(j,"player"));if(player==null)throw new IllegalArgumentException("Player is offline");AmbientManager.give(player,p.ambiences().discItem(str(j,"give")));}
                else p.ambiences().disc(str(j,"id"),str(j,"track"));return snapshot();
            default:throw new IllegalArgumentException("Unknown operation");
        }
    }
    public Map<String,Object> snapshot() {
        Map<String,Object> data=new LinkedHashMap<>();data.put("version",p.getDescription().getVersion());data.put("server",Bukkit.getBukkitVersion());data.put("tracks",new ArrayList<>(p.tracks().values()));
        List<Map<String,Object>> playlists=new ArrayList<>();
        for(Station s:p.state().stations.values()){Map<String,Object> v=new LinkedHashMap<>();v.put("id",s.id);v.put("name",s.name);v.put("queue",new ArrayList<>(s.queue));v.put("shuffle",s.shuffle);v.put("paused",s.paused);playlists.add(v);}data.put("playlists",playlists);
        data.put("zones",new ArrayList<>(p.ambiences().zones.values()));data.put("rules",new ArrayList<>(p.ambiences().rules.values()));data.put("discs",new ArrayList<>(p.ambiences().discs.values()));
        data.put("players",Bukkit.getOnlinePlayers().stream().map(Player::getName).toArray());data.put("worlds",Bukkit.getWorlds().stream().map(org.bukkit.World::getName).toArray());
        data.put("pack",p.pack().sha1());data.put("listeners",p.engine().listeners());data.put("events",AmbientManager.EVENTS);return data;
    }
    @Override public void close(){if(server!=null)server.stop(0);if(executor!=null)executor.shutdownNow();sessions.clear();}
}
