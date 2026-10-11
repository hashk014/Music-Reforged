/* Music Reforged — made by hash014. Rewritten 2026-10-11. GPL-2.0-or-later.
 * Based on the functionality of Music, Copyright (C) 2016 Zombie_Striker. */
package fr.hash014.music;

import java.util.*;

public final class Station {
    public final int id;
    public UUID owner;
    public String name;
    public final List<String> queue = new ArrayList<>();
    public boolean shuffle, paused;
    public int radius = 64;
    private int index = -1;
    private long remaining, generation;
    private Track current;
    public Station(int id, UUID owner) {
        if (id < 0 || owner == null) throw new IllegalArgumentException("Station invalide");
        this.id = id; this.owner = owner;
        this.name = "Station " + id;
    }
    public Track current() { return current; }
    public int index() { return index; }
    public long generation() { return generation; }
    public long remaining() { return remaining; }
    public void reset() { current = null; index = -1; remaining = 0; generation++; }
    public void skip() { remaining = 0; }
    public boolean tick(Map<String, Track> library, Random random) {
        if (paused || queue.isEmpty()) {
            if (current != null) { current = null; generation++; }
            return false;
        }
        if (remaining > 0) { remaining--; if (remaining > 0) return false; }
        int next = shuffle ? random.nextInt(queue.size()) : (index + 1) % queue.size();
        for (int attempts = 0; attempts < queue.size(); attempts++) {
            Track candidate = library.get(queue.get(next).toLowerCase(Locale.ROOT));
            if (candidate != null) {
                index = next; current = candidate; remaining = candidate.ticks(); generation++; return true;
            }
            next = (next + 1) % queue.size();
        }
        if (current != null) generation++;
        current = null; remaining = 20; return false;
    }
}
