/* Music Reforged — made by hash014. Rewritten 2026-10-11. GPL-2.0-or-later. */
package fr.hash014.music;

import java.lang.reflect.Method;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class SoundBridge {
    private final Method play, stop;
    private final Object records;
    public SoundBridge() {
        Method p = null, s = null; Object r = null;
        try {
            Class<?> category = Class.forName("org.bukkit.SoundCategory");
            r = category.getField("RECORDS").get(null);
            p = Player.class.getMethod("playSound", Location.class, String.class, category, float.class, float.class);
            s = Player.class.getMethod("stopSound", String.class, category);
        } catch (ReflectiveOperationException ignored) { }
        if (s == null) try { s = Player.class.getMethod("stopSound", String.class); } catch (NoSuchMethodException ignored) { }
        play = p; stop = s; records = r;
    }
    public boolean canStop() { return stop != null; }
    public String sound(Track t) {
        String s = t.sound;
        boolean legacyNames = org.bukkit.Bukkit.getBukkitVersion().matches("1\\.(8|9|10|11|12)(\\..*)?-.*");
        if (legacyNames && s.startsWith("minecraft:music_disc.")) s = s.replace("minecraft:music_disc.", "minecraft:records.");
        if (play == null && s.startsWith("minecraft:")) s = s.substring(10);
        return s;
    }
    public void play(Player p, Location at, Track t, float volume) {
        try {
            if (play != null) play.invoke(p, at, sound(t), records, volume, 1f);
            else p.playSound(at, sound(t), volume, 1f);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Envoi du son impossible", e); }
    }
    public void stop(Player p, Track t) {
        if (stop == null) return;
        try { if (stop.getParameterTypes().length == 1) stop.invoke(p, sound(t)); else stop.invoke(p, sound(t), records); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Arret du son impossible", e); }
    }
}
