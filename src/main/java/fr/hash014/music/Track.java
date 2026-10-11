/* Music Reforged — made by hash014. Rewritten 2026-10-11. GPL-2.0-or-later.
 * Based on the functionality of Music, Copyright (C) 2016 Zombie_Striker. */
package fr.hash014.music;

import java.util.Locale;

public final class Track {
    public final String id, sound;
    public final double seconds;
    public final boolean custom;
    public final int segments;
    public Track(String id, String sound, double seconds) {
        this(id, sound, seconds, 0);
    }
    public Track(String id, String sound, double seconds, int segments) {
        if (id == null || !id.matches("[A-Za-z0-9 _-]{1,80}")) throw new IllegalArgumentException("Nom de morceau invalide");
        if (sound == null || !sound.matches("(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+") || sound.contains("..")) throw new IllegalArgumentException("Cle de son invalide");
        if (!Double.isFinite(seconds) || seconds < .05 || seconds > 86400) throw new IllegalArgumentException("Duree: 0.05 a 86400 secondes");
        this.id = id; this.sound = sound.indexOf(':') < 0 ? "minecraft:" + sound : sound; this.seconds = seconds;
        this.custom = !this.sound.startsWith("minecraft:music_disc.") && !this.sound.startsWith("minecraft:records.");
        if (segments < 0 || segments > 14400) throw new IllegalArgumentException("Nombre de segments invalide");
        this.segments = segments;
    }
    public long ticks() { return (long) Math.ceil(seconds * 20); }
    public String key() { return id.toLowerCase(Locale.ROOT); }
    public Track segment(int index) { return new Track(id, sound + "_segments/" + String.format(Locale.ROOT, "%05d", index), .25); }
}
