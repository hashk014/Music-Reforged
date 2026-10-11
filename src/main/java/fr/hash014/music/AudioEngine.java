/* Music Reforged — made by hash014. Rewritten 2026-10-11. GPL-2.0-or-later.
 * Based on the functionality of Music, Copyright (C) 2016 Zombie_Striker. */
package fr.hash014.music;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;

public final class AudioEngine {
    private final MusicReforged plugin;
    public final SoundBridge bridge = new SoundBridge();
    private final Random random = new Random();
    private final Map<UUID, Voice> voices = new HashMap<>();
    private final Map<UUID, Voice> once = new HashMap<>();
    private long clock;
    static final class Voice {
        final Track track; final String box; final int station; final long generation; final float volume; final long until;
        Voice(Track t, String b, int s, long g, float v, long until) { track = t; box = b; station = s; generation = g; volume = v; this.until = until; }
    }
    public AudioEngine(MusicReforged plugin) { this.plugin = plugin; }
    public void tick() {
        clock++;
        for (Station s : plugin.state().stations.values()) s.tick(plugin.tracks(), random);
        for (Player p : Bukkit.getOnlinePlayers()) update(p);
    }
    private void update(Player p) {
        UUID id = p.getUniqueId();
        Voice single = once.get(id);
        if (single != null) { if (clock < single.until) return; bridge.stop(p, single.track); once.remove(id); }
        RadioBox best = null; Station station = null; Location source = null; double nearest = Double.MAX_VALUE;
        float personal = plugin.state().preferences.getOrDefault(id, 1f);
        if (personal > 0) for (RadioBox b : plugin.state().boxes.values()) {
            if (!b.active || b.volume <= 0 || !b.world.equals(p.getWorld().getName())) continue;
            Station s = plugin.state().stations.get(b.station);
            if (s == null || s.paused || s.current() == null) continue;
            Location l = b.location();
            if (l == null || !l.getWorld().isChunkLoaded(b.x >> 4, b.z >> 4)) continue;
            if (l.getBlock().getType() != Material.JUKEBOX) continue;
            double distance = b.global ? Double.MAX_VALUE / 2 : p.getLocation().distanceSquared(l);
            if (!b.global && distance > (double) s.radius * s.radius) continue;
            if (distance < nearest) { nearest = distance; best = b; station = s; source = l; }
        }
        Voice old = voices.get(id);
        if (best == null) { if (old != null) { bridge.stop(p, old.track); voices.remove(id); } return; }
        float volume = Math.min(10, best.volume * personal);
        if (old != null && old.box.equals(best.key()) && old.generation == station.generation() && old.station == station.id && old.volume == volume) return;
        if (old != null) bridge.stop(p, old.track);
        Track track = station.current();
        float effective = best.global ? volume : volume * Math.max(1, station.radius / 16f);
        bridge.play(p, best.global ? p.getLocation() : source, track, effective);
        voices.put(id, new Voice(track, best.key(), station.id, station.generation(), volume, 0));
    }
    public void once(Player p, Track t) {
        stop(p); float v = plugin.state().preferences.getOrDefault(p.getUniqueId(), 1f);
        if (v <= 0) return;
        bridge.play(p, p.getLocation(), t, v);
        once.put(p.getUniqueId(), new Voice(t, "", -1, 0, v, clock + t.ticks()));
    }
    public void stop(Player p) {
        Voice v = voices.remove(p.getUniqueId()); if (v != null) bridge.stop(p, v.track);
        v = once.remove(p.getUniqueId()); if (v != null) bridge.stop(p, v.track);
    }
    public void stopAll() { for (Player p : Bukkit.getOnlinePlayers()) stop(p); voices.clear(); once.clear(); }
    public int listeners() { return voices.size() + once.size(); }
}
