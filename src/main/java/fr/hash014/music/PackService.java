/* Music Reforged — made by hash014. Rewritten 2026-10-11. GPL-2.0-or-later. */
package fr.hash014.music;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class PackService implements AutoCloseable {
    private final MusicReforged plugin;
    private HttpServer http;
    private ExecutorService executor;
    private volatile byte[] pack;
    private byte[] hash;
    private int major, minor;
    public PackService(MusicReforged p) { plugin = p; }
    public int format() { return major; }
    public String sha1() { return hex(hash); }
    public static String hex(byte[] bytes) { StringBuilder s = new StringBuilder(); for (byte b : bytes) s.append(String.format(Locale.ROOT, "%02x", b & 255)); return s.toString(); }
    public void initialize() throws Exception {
        Path audio = plugin.getDataFolder().toPath().resolve("audio");
        try (InputStream in = plugin.getResource("bundled-sounds.json")) {
            JsonArray files = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
            for (JsonElement el : files) {
                String name = el.getAsString(); Path out = audio.resolve(name).normalize();
                if (!out.startsWith(audio)) throw new IOException("Chemin audio invalide");
                if (!Files.exists(out)) {
                    Files.createDirectories(out.getParent());
                    try (InputStream sound = plugin.getResource("audio/" + name)) { Files.copy(sound, out); }
                }
            }
        }
        build();
        if (plugin.getConfig().getBoolean("resource-pack.serve")) start();
    }
    private void detectFormat() throws IOException {
        try (InputStream in = Bukkit.getServer().getClass().getClassLoader().getResourceAsStream("version.json")) {
            if (in != null) {
                JsonElement value = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().get("pack_version");
                if (value != null && value.isJsonPrimitive()) { major = value.getAsInt(); minor = 0; return; }
                JsonObject v = value == null ? null : value.getAsJsonObject();
                if (v != null) {
                    major = v.has("resource_major") ? v.get("resource_major").getAsInt() : v.get("resource").getAsInt();
                    minor = v.has("resource_minor") ? v.get("resource_minor").getAsInt() : 0;
                    return;
                }
            }
        }
        String version = Bukkit.getBukkitVersion().split("-")[0];
        try (InputStream in = plugin.getResource("pack-formats.json")) {
            JsonObject formats = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!formats.has(version)) throw new IOException("Format de pack inconnu pour " + version);
            JsonArray f = formats.getAsJsonArray(version); major = f.get(0).getAsInt(); minor = f.get(1).getAsInt();
        }
    }
    public void build() throws Exception {
        detectFormat();
        Path audio = plugin.getDataFolder().toPath().resolve("audio"), out = plugin.getDataFolder().toPath().resolve("pack.zip");
        JsonObject meta = new JsonObject(), section = new JsonObject(); section.addProperty("description", "Music Reforged — made by hash014");
        if (major < 65) section.addProperty("pack_format", major);
        else { JsonArray f = new JsonArray(); f.add(major); f.add(minor); section.add("min_format", f); section.add("max_format", f.deepCopy()); }
        meta.add("pack", section);
        long maxFile = Math.max(1, Math.min(64, plugin.getConfig().getInt("resource-pack.max-audio-file-mb", 32))) * 1024L * 1024;
        Map<String, JsonObject> namespaces = new TreeMap<>(); List<Path> files = new ArrayList<>();
        try (java.util.stream.Stream<Path> stream = Files.walk(audio)) {
            stream.filter(p -> p.toString().endsWith(".ogg")).sorted().forEach(files::add);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            entry(zip, "pack.mcmeta", meta.toString().getBytes(StandardCharsets.UTF_8));
            entry(zip, "NOTICE.txt", "Music Reforged — made by hash014. GPL-2.0-or-later. Legacy samples from ZombieStriker/Music; see source NOTICE.\n".getBytes(StandardCharsets.UTF_8));
            long total = 0;
            for (Path file : files) {
                Path real = file.toRealPath(); if (!real.startsWith(audio.toRealPath())) throw new IOException("Audio hors du dossier: " + file);
                long size = Files.size(file); total += size;
                if (size > maxFile || total > 64L * 1024 * 1024) throw new IOException("Pack audio trop volumineux (maximum 64 Mo)");
                String name = audio.relativize(file).toString().replace('\\', '/');
                if (!name.matches("[a-z0-9_.-]+/[a-z0-9_./-]+\\.ogg") || name.contains("..")) throw new IOException("Nom OGG invalide: " + name);
                byte[] content = Files.readAllBytes(file);
                if (content.length < 4 || content[0] != 'O' || content[1] != 'g' || content[2] != 'g' || content[3] != 'S') throw new IOException("Ce fichier n'est pas OGG: " + name);
                int slash = name.indexOf('/'); String ns = name.substring(0, slash), path = name.substring(slash + 1, name.length() - 4);
                JsonObject sound = new JsonObject(), soundFile = new JsonObject(); JsonArray list = new JsonArray();
                soundFile.addProperty("name", ns + ":" + path); soundFile.addProperty("stream", true); list.add(soundFile);
                sound.add("sounds", list); namespaces.computeIfAbsent(ns, n -> new JsonObject()).add(path, sound);
                entry(zip, "assets/" + ns + "/sounds/" + path + ".ogg", content);
            }
            for (Map.Entry<String, JsonObject> n : namespaces.entrySet()) entry(zip, "assets/" + n.getKey() + "/sounds.json", n.getValue().toString().getBytes(StandardCharsets.UTF_8));
        }
        byte[] candidate = bytes.toByteArray(), sha = MessageDigest.getInstance("SHA-1").digest(candidate);
        Path temp = Files.createTempFile(out.getParent(), "pack-", ".tmp");
        try { Files.write(temp, candidate); AtomicFiles.move(temp, out); } finally { Files.deleteIfExists(temp); }
        hash = sha; pack = candidate;
        plugin.getLogger().info("Pack audio genere: format " + major + "." + minor + ", " + files.size() + " sons, SHA-1 " + sha1());
    }
    private static void entry(ZipOutputStream zip, String name, byte[] data) throws IOException {
        ZipEntry e = new ZipEntry(name); e.setTime(0); zip.putNextEntry(e); zip.write(data); zip.closeEntry();
    }
    private void start() throws IOException {
        String host = plugin.getConfig().getString("resource-pack.bind", "127.0.0.1"); int port = plugin.getConfig().getInt("resource-pack.port", 8124);
        if (port < 1 || port > 65535) throw new IOException("Port de pack invalide");
        http = HttpServer.create(new InetSocketAddress(host, port), 16);
        executor = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(16), r -> { Thread t = new Thread(r, "MusicReforged-Pack"); t.setDaemon(true); return t; });
        http.setExecutor(executor);
        http.createContext("/pack.zip", exchange -> {
            try {
                if (!exchange.getRequestURI().getPath().equals("/pack.zip")) { exchange.sendResponseHeaders(404, -1); return; }
                String method = exchange.getRequestMethod();
                if (!method.equals("GET") && !method.equals("HEAD")) { exchange.sendResponseHeaders(405, -1); return; }
                byte[] data = pack;
                exchange.getResponseHeaders().set("Content-Type", "application/zip");
                exchange.getResponseHeaders().set("Cache-Control", "no-cache");
                exchange.getResponseHeaders().set("Content-Length", String.valueOf(data.length));
                exchange.sendResponseHeaders(200, method.equals("HEAD") ? -1 : data.length);
                if (method.equals("GET")) exchange.getResponseBody().write(data);
            } finally { exchange.close(); }
        });
        http.start(); plugin.getLogger().info("Pack HTTP actif sur " + host + ":" + port + "/pack.zip");
    }
    public String url() {
        String u = plugin.getConfig().getString("resource-pack.public-url", "").trim();
        if (u.isEmpty() && http != null) {
            String bind = plugin.getConfig().getString("resource-pack.bind", "127.0.0.1");
            if (bind.equals("0.0.0.0") || bind.equals("::")) return "";
            u = "http://" + bind + ":" + plugin.getConfig().getInt("resource-pack.port", 8124) + "/pack.zip";
        }
        if (u.isEmpty()) return "";
        try { URI uri = new URI(u); if ((!uri.getScheme().equalsIgnoreCase("http") && !uri.getScheme().equalsIgnoreCase("https")) || uri.getHost() == null) throw new IllegalArgumentException("URL du pack invalide"); }
        catch (URISyntaxException | NullPointerException e) { throw new IllegalArgumentException("URL du pack invalide", e); }
        return u;
    }
    public void send(Player p) {
        String u = url();
        if (u.isEmpty()) { plugin.tell(p, "Pack pret dans plugins/MusicReforged/pack.zip. Configure resource-pack.public-url ou serve."); return; }
        String versioned = u + (u.contains("?") ? "&" : "?") + "v=" + sha1();
        try { Player.class.getMethod("setResourcePack", String.class, byte[].class).invoke(p, versioned, hash.clone()); }
        catch (NoSuchMethodException e) { p.setResourcePack(versioned); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Envoi du pack impossible", e); }
        plugin.tell(p, "Pack audio propose. Accepte-le pour entendre les morceaux personnalises.");
    }
    @Override public void close() { if (http != null) { http.stop(0); http = null; } if (executor != null) { executor.shutdownNow(); executor = null; } }
}
