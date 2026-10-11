/* Music Reforged — made by hash014. Rewritten 2026-10-11. GPL-2.0-or-later.
 * Based on the functionality of Music, Copyright (C) 2016 Zombie_Striker. */
package fr.hash014.music;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class RadioBox {
    public final String world;
    public final int x, y, z;
    public UUID owner;
    public int station = -1;
    public float volume = 1;
    public boolean active, global;
    public String disc;
    public RadioBox(String world, int x, int y, int z, UUID owner) {
        if (world == null || world.isEmpty() || owner == null) throw new IllegalArgumentException("Invalid jukebox");
        this.world = world; this.x = x; this.y = y; this.z = z; this.owner = owner;
    }
    public static String key(Location l) { return key(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ()); }
    public static String key(String w, int x, int y, int z) { return w + ":" + x + ":" + y + ":" + z; }
    public String key() { return key(world, x, y, z); }
    public Location location() { World w = Bukkit.getWorld(world); return w == null ? null : new Location(w, x + .5, y + .5, z + .5); }
}
