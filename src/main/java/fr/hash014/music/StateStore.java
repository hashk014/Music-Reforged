/* Music Reforged — made by hash014. Rewritten 2026-10-11. GPL-2.0-or-later.
 * Based on the functionality of Music, Copyright (C) 2016 Zombie_Striker. */
package fr.hash014.music;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class StateStore {
    public final Map<Integer, Station> stations = new TreeMap<>();
    public final Map<String, RadioBox> boxes = new LinkedHashMap<>();
    public final Map<UUID, Float> preferences = new HashMap<>();
    private final Path file;
    public StateStore(Path file) { this.file = file; }
    public void load() throws Exception {
        if (!Files.exists(file)) return;
        YamlConfiguration y = new YamlConfiguration(); y.load(file.toFile());
        if (y.getInt("schema") != 1) throw new IOException("Unknown state.yml version");
        ConfigurationSection ss = y.getConfigurationSection("stations");
        if (ss != null) for (String key : ss.getKeys(false)) {
            ConfigurationSection s = ss.getConfigurationSection(key);
            if (s == null) throw new IOException("Could not read station: " + key);
            Station station = new Station(Integer.parseInt(key), UUID.fromString(s.getString("owner")));
            station.name = s.getString("name", "Station " + station.id);
            for (String id : s.getStringList("queue")) {
                if (!id.matches("[A-Za-z0-9 _-]{1,80}")) throw new IOException("Invalid track: " + id);
                station.queue.add(id);
            }
            station.radius = s.getInt("radius", 64);
            if (station.radius < 1 || station.radius > 4096 || station.queue.size() > 10000) throw new IOException("Invalid station limits");
            station.shuffle = s.getBoolean("shuffle"); station.paused = s.getBoolean("paused");
            stations.put(station.id, station);
        }
        ConfigurationSection bs = y.getConfigurationSection("jukeboxes");
        if (bs != null) for (String key : bs.getKeys(false)) {
            ConfigurationSection b = bs.getConfigurationSection(key);
            if (b == null) throw new IOException("Could not read jukebox: " + key);
            RadioBox box = new RadioBox(b.getString("world"), b.getInt("x"), b.getInt("y"), b.getInt("z"), UUID.fromString(b.getString("owner")));
            box.station = b.getInt("station", -1); box.volume = (float) b.getDouble("volume", 1);
            if (!Float.isFinite(box.volume) || box.volume < 0 || box.volume > 10) throw new IOException("Invalid jukebox volume");
            box.global = b.getBoolean("global"); box.active = b.getBoolean("active");
            box.disc = b.getString("disc");
            if (boxes.put(box.key(), box) != null) throw new IOException("Duplicate jukebox: " + box.key());
        }
        ConfigurationSection ps = y.getConfigurationSection("players");
        if (ps != null) for (String key : ps.getKeys(false)) {
            float volume = (float) ps.getDouble(key);
            if (!Float.isFinite(volume) || volume < 0 || volume > 1) throw new IOException("Invalid personal volume");
            preferences.put(UUID.fromString(key), volume);
        }
    }
    public void save() throws IOException {
        YamlConfiguration y = new YamlConfiguration(); y.set("schema", 1);
        for (Station s : stations.values()) {
            String p = "stations." + s.id + ".";
            y.set(p + "owner", s.owner.toString()); y.set(p + "queue", new ArrayList<>(s.queue));
            y.set(p + "name", s.name);
            y.set(p + "radius", s.radius); y.set(p + "shuffle", s.shuffle); y.set(p + "paused", s.paused);
        }
        int n = 0;
        for (RadioBox b : boxes.values()) {
            String p = "jukeboxes." + (n++) + ".";
            y.set(p + "world", b.world); y.set(p + "x", b.x); y.set(p + "y", b.y); y.set(p + "z", b.z);
            y.set(p + "owner", b.owner.toString()); y.set(p + "station", b.station);
            y.set(p + "volume", b.volume); y.set(p + "global", b.global); y.set(p + "active", b.active);
            y.set(p + "disc", b.disc);
        }
        for (Map.Entry<UUID, Float> p : preferences.entrySet()) y.set("players." + p.getKey(), p.getValue());
        AtomicFiles.write(file, y.saveToString());
    }
    public Path file() { return file; }
}
