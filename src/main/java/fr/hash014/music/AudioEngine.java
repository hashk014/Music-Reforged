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
    private final Map<UUID, Mix> mixes = new HashMap<>();
    private final Map<UUID, OneShot> once = new HashMap<>();
    private long clock;
    static final class OneShot { Track track; long until; }
    static final class Channel { AmbientManager.Selection selection; long started, nextSegment, fadeOut = -1; int index; Track playing; }
    static final class Mix { Channel current, outgoing; }
    public AudioEngine(MusicReforged plugin) { this.plugin = plugin; }
    public void tick() {
        clock++; plugin.ambiences().tick();
        for (Station s : plugin.state().stations.values()) s.tick(plugin.tracks(), random);
        for (Player p : Bukkit.getOnlinePlayers()) update(p);
    }
    private AmbientManager.Selection selected(Player p) {
        UUID id = p.getUniqueId();
        float personal = plugin.state().preferences.getOrDefault(id, 1f);
        if (personal <= 0) return null;
        OneShot single = once.get(id);
        if (single != null) {
            if (clock < single.until) return new AmbientManager.Selection(single.track, "listen", -1, single.until, personal, p.getLocation(), false);
            once.remove(id);
        }
        AmbientManager.Selection ambient = plugin.ambiences().resolve(p, personal);
        if (ambient != null) return ambient;
        RadioBox best = null; Station station = null; Location source = null; double nearest = Double.MAX_VALUE;
        if (personal > 0) for (RadioBox b : plugin.state().boxes.values()) {
            if (!b.active || b.volume <= 0 || !b.world.equals(p.getWorld().getName())) continue;
            Station s = b.disc == null ? plugin.state().stations.get(b.station) : plugin.ambiences().discStation(b);
            if (s == null || s.paused || s.current() == null) continue;
            Location l = b.location();
            if (l == null || !l.getWorld().isChunkLoaded(b.x >> 4, b.z >> 4)) continue;
            if (l.getBlock().getType() != Material.JUKEBOX) continue;
            double distance = b.global ? Double.MAX_VALUE / 2 : p.getLocation().distanceSquared(l);
            if (!b.global && distance > (double) s.radius * s.radius) continue;
            if (distance < nearest) { nearest = distance; best = b; station = s; source = l; }
        }
        if (best == null) return null;
        float volume = best.volume * personal * (best.global ? 1 : Math.max(1, station.radius / 16f));
        return new AmbientManager.Selection(station.current(), best.key(), station.id, station.generation(), volume, best.global ? p.getLocation() : source, !best.global);
    }
    private int fadeTicks() { return Math.max(1, Math.min(200, (int) Math.round(plugin.getConfig().getDouble("transitions.seconds", 2) * 20))); }
    private void update(Player player) {
        UUID id = player.getUniqueId(); AmbientManager.Selection desired = selected(player); Mix mix = mixes.get(id);
        if (mix == null && desired == null) return;
        if (mix == null) { mix = new Mix(); mixes.put(id, mix); }
        Channel current = mix.current;
        boolean same = current != null && desired != null && current.selection.identity().equals(desired.identity());
        if (!same) {
            if (mix.outgoing != null) stopChannel(player, mix.outgoing);
            mix.outgoing = current;
            if (current != null) { current.fadeOut = clock; if (current.selection.track.segments == 0) { stopChannel(player, current); mix.outgoing = null; } }
            mix.current = null;
            if (desired != null) { Channel c = new Channel(); c.selection = desired; c.started = clock; c.nextSegment = clock; mix.current = c; }
        } else {
            if (desired.track.segments == 0 && (current.selection.volume != desired.volume || current.selection.local != desired.local)) { stopChannel(player, current); current.playing = null; }
            current.selection = desired;
        }
        if (mix.outgoing != null && !render(player, mix.outgoing)) mix.outgoing = null;
        if (mix.current != null) render(player, mix.current);
        if (mix.current == null && mix.outgoing == null) mixes.remove(id);
    }
    private boolean render(Player player, Channel channel) {
        Track track = channel.selection.track; int fade = fadeTicks();
        if (channel.fadeOut >= 0 && clock - channel.fadeOut >= fade) { stopChannel(player, channel); return false; }
        if (track.segments == 0) {
            if (channel.playing == null) { bridge.play(player, channel.selection.local ? channel.selection.origin : player.getLocation(), track, channel.selection.volume); channel.playing = track; }
            return true;
        }
        if (channel.index >= track.segments) return channel.fadeOut < 0;
        if (clock < channel.nextSegment) return true;
        float gain = Math.min(1, (clock - channel.started + 1) / (float) fade);
        if (channel.fadeOut >= 0) gain *= Math.max(0, 1 - (clock - channel.fadeOut) / (float) fade);
        gain *= Math.min(1, (track.segments - channel.index) * 5f / fade);
        Track segment = track.segment(channel.index++);
        if (channel.playing != null) bridge.stop(player, channel.playing);
        bridge.play(player, channel.selection.local ? channel.selection.origin : player.getLocation(), segment, channel.selection.volume * gain);
        channel.playing = segment; channel.nextSegment = clock + 5;
        return true;
    }
    private void stopChannel(Player player, Channel channel) { if (channel.playing != null) bridge.stop(player, channel.playing); }
    public void once(Player player, Track track) { stop(player); OneShot shot = new OneShot(); shot.track = track; shot.until = clock + track.ticks(); once.put(player.getUniqueId(), shot); update(player); }
    public void stop(Player player) {
        Mix mix = mixes.remove(player.getUniqueId()); if (mix != null) { if (mix.current != null) stopChannel(player, mix.current); if (mix.outgoing != null) stopChannel(player, mix.outgoing); } once.remove(player.getUniqueId());
    }
    public void stopAll() { for (Player p : Bukkit.getOnlinePlayers()) stop(p); mixes.clear(); once.clear(); }
    public int listeners() { return mixes.size(); }
}
