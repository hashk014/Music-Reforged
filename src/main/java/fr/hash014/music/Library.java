/* Music Reforged — made by hash014. Rewritten 2026-10-11. GPL-2.0-or-later.
 * Based on the functionality of Music, Copyright (C) 2016 Zombie_Striker. */
package fr.hash014.music;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

public final class Library {
    private final Path directory;
    private final Logger log;
    public Library(Path directory, Logger log) { this.directory = directory; this.log = log; }
    public void defaults() throws IOException {
        Files.createDirectories(directory);
        if (Files.exists(directory.resolve(".initialized"))) return;
        createIfAbsent(new Track("Reforged", "musicreforged:reforged", 16, 64));
        createIfAbsent(new Track("Chill", "musicreforged:chill", 16, 64));
        createIfAbsent(new Track("Arcade", "musicreforged:arcade", 16, 64));
        createIfAbsent(new Track("Cat", "minecraft:music_disc.cat", 185));
        createIfAbsent(new Track("Blocks", "minecraft:music_disc.blocks", 345));
        createIfAbsent(new Track("Stal", "minecraft:music_disc.stal", 150));
        String[] names = {"Closer", "ScienceCrusher", "Tavern", "TetrisTrance", "USSR", "WaterDrops", "AfterSchoolSpecial", "Forest", "WindowsXP", "Piano", "HisTheme", "Smashlovania", "ThomasTheDootEngine", "WaterFall", "wind"};
        String[] sounds = {"closer", "sciencecrusher", "tavern", "tetristrance", "ussr", "waterdrop", "afterschoolspecial", "forest", "winxpsong", "piano", "histheme", "smashlovania", "thomasthedootengine", "waterfall", "wind"};
        double[] duration = {21.1, 60.7, 33.4, 13.5, 67.4, 4, 77, 9, 61.544, 5.142, 79, 60, 37, 9, 11};
        for (int i = 0; i < names.length; i++) createIfAbsent(new Track(names[i], sounds[i], duration[i]));
        AtomicFiles.write(directory.resolve(".initialized"), "Music Reforged made by hash014\n");
    }
    private void createIfAbsent(Track t) throws IOException { if (!Files.exists(directory.resolve(t.id + ".txt"))) create(t); }
    public void create(Track track) throws IOException {
        Path file = directory.resolve(track.id + ".txt").normalize();
        if (!file.getParent().equals(directory.normalize())) throw new IllegalArgumentException("Invalid path");
        if (Files.exists(file)) throw new IllegalArgumentException("This track already exists");
        AtomicFiles.write(file, "# Music Reforged — made by hash014\nTime:" + track.seconds + "\nSoundname:" + track.sound + "\nSegments:" + track.segments + "\n");
    }
    public Map<String, Track> load() throws IOException {
        Map<String, Track> result = new TreeMap<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.txt")) {
            for (Path f : files) {
                String id = f.getFileName().toString().replaceFirst("(?i)\\.txt$", "");
                if (id.startsWith("[TEMPLATE")) continue;
                try {
                    Track t = parse(id, Files.readAllLines(f, StandardCharsets.UTF_8));
                    if (result.containsKey(t.key())) throw new IllegalArgumentException("Duplicate name (case-insensitive)");
                    result.put(t.key(), t);
                } catch (IllegalArgumentException e) { throw new IOException("Invalid track " + f.getFileName() + ": " + e.getMessage(), e); }
            }
        }
        return result;
    }
    public static Track parse(String id, List<String> lines) {
        String sound = id.toLowerCase(Locale.ROOT); double seconds = Double.NaN; int segments = 0;
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("#") || line.isEmpty() || line.equals("[&&END&&]")) continue;
            int colon = line.indexOf(':'); if (colon < 0) continue;
            String field = line.substring(0, colon).trim().toLowerCase(Locale.ROOT), value = line.substring(colon + 1).trim();
            if (field.equals("time")) seconds = Double.parseDouble(value);
            if (field.equals("songname") || field.equals("soundname")) sound = value;
            if (field.equals("segments")) segments = Integer.parseInt(value);
        }
        return new Track(id, sound, seconds, segments);
    }
}
