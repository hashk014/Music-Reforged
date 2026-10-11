/* Music Reforged — made by hash014. Rewritten 2026-10-11. GPL-2.0-or-later.
 * Based on the functionality of Music, Copyright (C) 2016 Zombie_Striker. */
package fr.hash014.music;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class LegacyImporter {
    static UUID owner(String text) {
        if (text == null || text.trim().isEmpty()) throw new IllegalArgumentException("Ancien proprietaire manquant");
        try { return UUID.fromString(text); }
        catch (IllegalArgumentException e) {
            // Only an existing cached identity is trusted on an online-mode server.
            for (OfflinePlayer p : Bukkit.getOfflinePlayers()) if (text.equalsIgnoreCase(p.getName())) return p.getUniqueId();
            if (Bukkit.getOnlineMode()) throw new IllegalArgumentException("Ancien proprietaire inconnu: " + text + "; utiliser une UUID dans la copie de config");
            return UUID.nameUUIDFromBytes(("OfflinePlayer:" + text).getBytes(StandardCharsets.UTF_8));
        }
    }
    static boolean importFirstInstall(MusicReforged plugin, Path target) throws Exception {
        Path legacy = plugin.getDataFolder().toPath().getParent().resolve("Music");
        if (Files.exists(target.resolve("state.yml")) || Files.exists(target.resolve("migration-complete")) || !Files.exists(legacy.resolve("config.yml"))) return false;
        // Build and validate all data before the new state is committed. The original folder is read-only.
        // Read old Location sections as primitives; Bukkit's automatic deserializer
        // rejects a world that is currently unloaded and would lose its name.
        String text = new String(Files.readAllBytes(legacy.resolve("config.yml")), StandardCharsets.UTF_8);
        text = text.replaceAll("(?m)^\\s*==:\\s*['\"]?(?:org\\.bukkit\\.)?Location['\"]?\\s*$", "");
        YamlConfiguration y = new YamlConfiguration(); y.loadFromString(text);
        StateStore store = new StateStore(target.resolve("state.yml"));
        ConfigurationSection loops = y.getConfigurationSection("Loop");
        if (loops != null) for (String key : loops.getKeys(false)) {
            if (!key.matches("[0-9]+")) continue;
            ConfigurationSection c = loops.getConfigurationSection(key); if (c == null) throw new IllegalArgumentException("Ancienne station illisible: " + key);
            Station s = new Station(Integer.parseInt(key), owner(c.getString("p", "")));
            Object songs = c.get("s");
            if (songs instanceof String) s.queue.add((String) songs); else s.queue.addAll(c.getStringList("s"));
            s.radius = Math.min(4096, Math.max(1, c.getInt("r", 4) * 16)); s.shuffle = c.getBoolean("rand");
            store.stations.put(s.id, s);
        }
        ConfigurationSection boxes = y.getConfigurationSection("Jukeboxes");
        if (boxes != null) for (String key : boxes.getKeys(false)) {
            ConfigurationSection c = boxes.getConfigurationSection(key);
            Object loc = c.get("location");
            String world; int x, yy, z;
            if (loc instanceof Location) {
                Location l = (Location) loc; if (l.getWorld() == null) throw new IllegalArgumentException("Monde manquant: " + key);
                world = l.getWorld().getName(); x = l.getBlockX(); yy = l.getBlockY(); z = l.getBlockZ();
            } else if (c.isConfigurationSection("location")) {
                ConfigurationSection l = c.getConfigurationSection("location"); world = l.getString("world");
                x = l.getInt("x"); yy = l.getInt("y"); z = l.getInt("z");
            } else throw new IllegalArgumentException("Ancien jukebox illisible: " + key);
            RadioBox b = new RadioBox(world, x, yy, z, owner(c.getString("owner", "")));
            int v = c.getInt("volume", 5); b.volume = Math.min(10, Math.max(0, v)); b.global = v == -1;
            if (b.global) b.volume = 1;
            b.station = c.getInt("station", -1); b.active = c.getBoolean("active"); store.boxes.put(b.key(), b);
        }
        Path backup = target.resolve("backups").resolve("legacy-" + System.currentTimeMillis()); Files.createDirectories(backup);
        Files.copy(legacy.resolve("config.yml"), backup.resolve("config.yml"));
        Path music = legacy.resolve("Music"); Path destination = target.resolve("Music"); Files.createDirectories(destination);
        if (Files.isDirectory(music)) try (DirectoryStream<Path> files = Files.newDirectoryStream(music, "*.txt")) {
            for (Path f : files) {
                Files.copy(f, backup.resolve(f.getFileName()));
                if (f.getFileName().toString().startsWith("[TEMPLATE")) continue;
                Library.parse(f.getFileName().toString().replaceFirst("(?i)\\.txt$", ""), Files.readAllLines(f, StandardCharsets.UTF_8));
                Path dest = destination.resolve(f.getFileName()); if (!Files.exists(dest)) Files.copy(f, dest);
            }
        }
        store.save();
        AtomicFiles.write(target.resolve("migration-complete"), "Imported from Music; original files unchanged.\nBackup: " + backup + "\n");
        plugin.getLogger().info("Ancien Music importe: " + store.stations.size() + " stations, " + store.boxes.size() + " jukeboxes. Sauvegarde: " + backup);
        return true;
    }
}
