/* Music Reforged — made by hash014. Rewritten 2026-10-11. GPL-2.0-or-later. */
package fr.hash014.music;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class AmbientManager implements Listener {
    public static final UUID CONSOLE = UUID.nameUUIDFromBytes("MusicReforged:console".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    public static final Set<String> EVENTS = new LinkedHashSet<>(Arrays.asList("COMBAT", "BOSS", "BIOME", "JOIN", "DEATH", "RESPAWN"));
    public static final class Zone {
        public String name, world; public UUID owner; public int station, priority = 30;
        public int minX, minY, minZ, maxX, maxY, maxZ;
        public boolean contains(Location l) { return l.getWorld().getName().equals(world) && l.getBlockX() >= minX && l.getBlockX() <= maxX && l.getBlockY() >= minY && l.getBlockY() <= maxY && l.getBlockZ() >= minZ && l.getBlockZ() <= maxZ; }
    }
    public static final class Rule {
        public String id, event, selector = "*"; public int station, priority = 70; public double seconds = 12;
    }
    public static final class Disc {
        public String id, track;
    }
    static final class Lease { int station, priority; long until; String source; }
    public static final class Selection {
        final Track track; final String source; final int station; final long generation; final float volume; final Location origin; final boolean local;
        Selection(Track track, String source, int station, long generation, float volume, Location origin, boolean local) { this.track = track; this.source = source; this.station = station; this.generation = generation; this.volume = volume; this.origin = origin; this.local = local; }
        String identity() { return source + ":" + station + ":" + generation + ":" + track.key(); }
    }
    private final MusicReforged p;
    public final Map<String, Zone> zones = new TreeMap<>();
    public final Map<String, Rule> rules = new TreeMap<>();
    public final Map<String, Disc> discs = new TreeMap<>();
    private final Map<UUID, Location[]> selections = new HashMap<>();
    private final Map<UUID, Map<String, Lease>> leases = new HashMap<>();
    private final Map<UUID, Integer> radios = new HashMap<>();
    private final Map<String, Station> discStations = new HashMap<>();
    private long clock;
    private final Random random = new Random();
    public AmbientManager(MusicReforged p) { this.p = p; }
    public static String key(String s) { if (s == null || !s.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("Identifier: letters, digits, - and _ (maximum 64 characters)"); return s.toLowerCase(Locale.ROOT); }
    public Station playlist(String name) {
        for (Station s : p.state().stations.values()) if (String.valueOf(s.id).equals(name) || s.name.equalsIgnoreCase(name)) return s;
        throw new IllegalArgumentException("Unknown playlist: " + name);
    }
    public Station createPlaylist(String name, UUID owner) {
        if (!name.matches("[A-Za-z0-9 _-]{1,80}")) throw new IllegalArgumentException("Invalid playlist name");
        for (Station s : p.state().stations.values()) if (s.name.equalsIgnoreCase(name)) throw new IllegalArgumentException("This playlist already exists");
        int max = p.getConfig().getInt("max-stations", 250), id = 0;
        while (p.state().stations.containsKey(id) && id < max) id++;
        if (id == max) throw new IllegalArgumentException("All station slots are occupied");
        Station s = new Station(id, owner); s.name = name; s.radius = p.getConfig().getInt("default-radius", 64);
        p.state().stations.put(id, s); p.persist(); return s;
    }
    public void radio(Player player, String name) {
        if (name.equalsIgnoreCase("off")) radios.remove(player.getUniqueId());
        else radios.put(player.getUniqueId(), playlist(name).id);
        p.engine().stop(player);
    }
    public Zone zone(String name, int station, UUID owner, Location a, Location b, int priority) {
        String id = key(name);
        if (!a.getWorld().equals(b.getWorld())) throw new IllegalArgumentException("Both points must be in the same world");
        return zone(id, station, owner, a.getWorld().getName(), a.getBlockX(), a.getBlockY(), a.getBlockZ(), b.getBlockX(), b.getBlockY(), b.getBlockZ(), priority);
    }
    public Zone zone(String name, int station, UUID owner, String world, int x1, int y1, int z1, int x2, int y2, int z2, int priority) {
        String id = key(name); if (zones.containsKey(id)) throw new IllegalArgumentException("This zone already exists");
        if (!p.state().stations.containsKey(station)) throw new IllegalArgumentException("Unknown station");
        if (world == null || world.isEmpty() || world.length() > 128 || priority < 0 || priority > 1000) throw new IllegalArgumentException("Invalid zone");
        for (int n : new int[] {x1,z1,x2,z2}) if (n < -30000000 || n > 30000000) throw new IllegalArgumentException("Coordinates are out of bounds");
        Zone z = new Zone(); z.name = id; z.station = station; z.owner = owner; z.world = world; z.priority = priority;
        z.minX = Math.min(x1,x2); z.minY = Math.min(y1,y2); z.minZ = Math.min(z1,z2); z.maxX = Math.max(x1,x2); z.maxY = Math.max(y1,y2); z.maxZ = Math.max(z1,z2);
        if (zones.size() >= 1000) throw new IllegalArgumentException("Limit of 1000 zones");
        zones.put(id,z); save(); return z;
    }
    public void selectedZone(Player player, String name, String station) {
        Location[] points = selections.get(player.getUniqueId());
        if (points == null || points[0] == null || points[1] == null) throw new IllegalArgumentException("Select both corners with /music zone wand (left-click, then right-click)");
        zone(name, playlist(station).id, player.getUniqueId(), points[0], points[1], 30);
    }
    public Rule rule(String id, String event, String selector, int station, double seconds, int priority) {
        id = key(id); event = event.toUpperCase(Locale.ROOT);
        if (!EVENTS.contains(event) || !p.state().stations.containsKey(station)) throw new IllegalArgumentException("Unknown event or station");
        if (!Double.isFinite(seconds) || seconds < .25 || seconds > 3600 || priority < 0 || priority > 1000) throw new IllegalArgumentException("Invalid duration or priority");
        if (selector == null || !selector.matches("[A-Za-z0-9_:*.-]{1,80}")) throw new IllegalArgumentException("Invalid filter");
        Rule r = new Rule(); r.id = id; r.event = event; r.selector = selector.toUpperCase(Locale.ROOT); r.station = station; r.seconds = seconds; r.priority = priority;
        if (!rules.containsKey(id) && rules.size() >= 1000) throw new IllegalArgumentException("Limit of 1000 rules");
        rules.put(id,r); save(); return r;
    }
    public Disc disc(String id, String track) {
        id = key(id); Track t = p.track(track); Disc d = new Disc(); d.id = id; d.track = t.id;
        if (!discs.containsKey(id) && discs.size() >= 1000) throw new IllegalArgumentException("Limit of 1000 discs");
        discs.put(id,d); discStations.clear(); save(); return d;
    }
    public ItemStack discItem(String id) {
        Disc d = discs.get(key(id)); if (d == null) throw new IllegalArgumentException("Unknown disc");
        Material m = Material.matchMaterial("MUSIC_DISC_CAT"); if (m == null) m = Material.matchMaterial("GREEN_RECORD");
        if (m == null) m = Material.JUKEBOX;
        ItemStack item = new ItemStack(m); ItemMeta meta = item.getItemMeta(); meta.setDisplayName(ChatColor.AQUA + d.id + ChatColor.GRAY + " — Reforged");
        meta.setLore(Arrays.asList(ChatColor.GRAY + p.track(d.track).id, ChatColor.DARK_GRAY + "made by hash014", ChatColor.BLACK + "musicreforged:disc:" + d.id)); item.setItemMeta(meta); return item;
    }
    private String discId(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) return null;
        for (String s : item.getItemMeta().getLore()) { String line = ChatColor.stripColor(s); if (line.startsWith("musicreforged:disc:")) { String id = line.substring(19); return discs.containsKey(id) ? id : null; } }
        return null;
    }
    public Station discStation(RadioBox box) {
        Disc d = discs.get(box.disc); if (d == null || !p.tracks().containsKey(d.track.toLowerCase(Locale.ROOT))) return null;
        return discStations.computeIfAbsent(box.key(), k -> { Station s = new Station(1000000, box.owner); s.queue.add(d.track); return s; });
    }
    public void tick() {
        clock++;
        for (RadioBox box : p.state().boxes.values()) if (box.disc != null) { Station s = discStation(box); if (s != null) s.tick(p.tracks(), random); }
        discStations.keySet().retainAll(p.state().boxes.keySet());
        if (clock % 20 != 0) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            String biome = player.getLocation().getBlock().getBiome().name(); trigger(player, "BIOME", biome);
            if (rules.values().stream().anyMatch(r -> r.event.equals("BOSS"))) for (Entity e : player.getNearbyEntities(64,64,64)) {
                String type = e.getType().name(); if (type.equals("WITHER") || type.equals("ENDER_DRAGON")) trigger(player,"BOSS",type);
            }
        }
    }
    public void trigger(Player player, String event, String selector) {
        for (Rule r : rules.values()) if (r.event.equals(event) && (r.selector.equals("*") || r.selector.equalsIgnoreCase(selector))) {
            Lease lease = new Lease(); lease.station = r.station; lease.priority = r.priority; lease.until = clock + (long) Math.ceil(r.seconds * 20); lease.source = "event:" + r.id;
            leases.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).put(r.id,lease);
        }
    }
    public Selection resolve(Player player, float volume) {
        int priority = -1, station = -1; String source = null;
        Integer radio = radios.get(player.getUniqueId()); if (radio != null) { priority = 5; station = radio; source = "radio:" + radio; }
        for (Zone z : zones.values()) if (z.priority > priority && z.contains(player.getLocation())) { priority = z.priority; station = z.station; source = "zone:" + z.name; }
        Map<String, Lease> active = leases.get(player.getUniqueId());
        if (active != null) { active.values().removeIf(l -> l.until <= clock || !p.state().stations.containsKey(l.station)); for (Lease l : active.values()) if (l.priority > priority) { priority = l.priority; station = l.station; source = l.source; } }
        Station s = p.state().stations.get(station);
        return s == null || s.paused || s.current() == null || volume <= 0 ? null : new Selection(s.current(),source,s.id,s.generation(),volume,player.getLocation(),false);
    }
    public void clear(Player player) { leases.remove(player.getUniqueId()); selections.remove(player.getUniqueId()); radios.remove(player.getUniqueId()); }
    public void wand(Player player) {
        ItemStack wand = new ItemStack(Material.STICK); ItemMeta meta = wand.getItemMeta(); meta.setDisplayName(ChatColor.AQUA + "Music Reforged selection wand"); meta.setLore(Collections.singletonList(ChatColor.BLACK + "musicreforged:zone-wand")); wand.setItemMeta(meta);
        give(player,wand); p.tell(player,"Left-click: first corner. Right-click: second corner. /music zone create <name> <playlist>");
    }
    public static void give(Player player, ItemStack item) { for (ItemStack extra : player.getInventory().addItem(item).values()) player.getWorld().dropItemNaturally(player.getLocation(),extra); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interaction(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        try { Object hand = e.getClass().getMethod("getHand").invoke(e); if (hand != null && hand.toString().equals("OFF_HAND")) return; } catch (ReflectiveOperationException ignored) { }
        ItemStack item = e.getItem();
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore() && item.getItemMeta().getLore().contains(ChatColor.BLACK + "musicreforged:zone-wand")) {
            if (!p.admin(e.getPlayer())) return;
            int index = e.getAction() == Action.LEFT_CLICK_BLOCK ? 0 : e.getAction() == Action.RIGHT_CLICK_BLOCK ? 1 : -1; if (index < 0) return;
            selections.computeIfAbsent(e.getPlayer().getUniqueId(), k -> new Location[2])[index] = e.getClickedBlock().getLocation(); e.setCancelled(true); p.tell(e.getPlayer(),"Corner " + (index+1) + " selected."); return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock().getType() != Material.JUKEBOX) return;
        String id = discId(item); if (id == null) return;
        e.setCancelled(true);
        try {
            if (!p.use(e.getPlayer())) throw new IllegalArgumentException("Insufficient permission");
            RadioBox box = p.state().boxes.get(RadioBox.key(e.getClickedBlock().getLocation()));
            if (box != null && !p.owns(e.getPlayer(),box.owner)) throw new IllegalArgumentException("You do not own this jukebox");
            if (box != null && box.disc != null) throw new IllegalArgumentException("Eject the disc first with /music disc eject while looking at this jukebox");
            Location l = e.getClickedBlock().getLocation(); if (box == null) { box = new RadioBox(l.getWorld().getName(),l.getBlockX(),l.getBlockY(),l.getBlockZ(),e.getPlayer().getUniqueId()); p.state().boxes.put(box.key(),box); }
            box.disc = id; box.active = true; box.global = false; box.volume = 1; box.station = -1;
            if (e.getPlayer().getGameMode() != GameMode.CREATIVE) { ItemStack held = e.getPlayer().getItemInHand(); held.setAmount(held.getAmount()-1); e.getPlayer().setItemInHand(held.getAmount() <= 0 ? null : held); }
            p.persist(); p.tell(e.getPlayer(),"Disc " + id + " inserted.");
        } catch (IllegalArgumentException ex) { p.tell(e.getPlayer(),ex.getMessage()); }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void combat(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player) trigger((Player)e.getEntity(),"COMBAT",e.getDamager().getType().name());
        Entity attacker = e.getDamager(); if (attacker instanceof Projectile && ((Projectile)attacker).getShooter() instanceof Player) attacker = (Player)((Projectile)attacker).getShooter();
        if (attacker instanceof Player) { trigger((Player)attacker,"COMBAT",e.getEntity().getType().name()); String type=e.getEntity().getType().name(); if (type.equals("WITHER")||type.equals("ENDER_DRAGON")) trigger((Player)attacker,"BOSS",type); }
    }
    @EventHandler public void join(PlayerJoinEvent e) { trigger(e.getPlayer(),"JOIN","*"); }
    @EventHandler public void death(PlayerDeathEvent e) { trigger(e.getEntity(),"DEATH","*"); }
    @EventHandler public void respawn(PlayerRespawnEvent e) { trigger(e.getPlayer(),"RESPAWN","*"); }
    @EventHandler public void quit(PlayerQuitEvent e) { clear(e.getPlayer()); }
    public void save() {
        YamlConfiguration y=new YamlConfiguration();y.set("schema",1);
        for (Zone z:zones.values()) { String q="zones."+z.name+"."; y.set(q+"world",z.world);y.set(q+"owner",z.owner.toString());y.set(q+"station",z.station);y.set(q+"priority",z.priority); y.set(q+"bounds",Arrays.asList(z.minX,z.minY,z.minZ,z.maxX,z.maxY,z.maxZ)); }
        for (Rule r:rules.values()) { String q="rules."+r.id+".";y.set(q+"event",r.event);y.set(q+"selector",r.selector);y.set(q+"station",r.station);y.set(q+"seconds",r.seconds);y.set(q+"priority",r.priority); }
        for (Disc d:discs.values()) y.set("discs."+d.id,d.track);
        try { AtomicFiles.write(p.getDataFolder().toPath().resolve("ambiences.yml"),y.saveToString()); } catch (IOException e) { throw new IllegalStateException("Could not save ambiences",e); }
    }
    public void load() throws Exception {
        Path file=p.getDataFolder().toPath().resolve("ambiences.yml"); if (!Files.exists(file)) return;
        YamlConfiguration y=new YamlConfiguration();y.load(file.toFile()); if (y.getInt("schema")!=1) throw new IOException("Unknown ambiences.yml schema");
        Map<String,Zone> nextZones=new TreeMap<>();Map<String,Rule> nextRules=new TreeMap<>();Map<String,Disc> nextDiscs=new TreeMap<>();
        ConfigurationSection zs=y.getConfigurationSection("zones");if(zs!=null) for(String id:zs.getKeys(false)) {
            ConfigurationSection c=zs.getConfigurationSection(id);List<Integer>b=c.getIntegerList("bounds");if(b.size()!=6)throw new IOException("Invalid zone: "+id);
            Zone z=new Zone();z.name=key(id);z.world=c.getString("world");z.owner=UUID.fromString(c.getString("owner"));z.station=c.getInt("station");z.priority=c.getInt("priority",30);z.minX=b.get(0);z.minY=b.get(1);z.minZ=b.get(2);z.maxX=b.get(3);z.maxY=b.get(4);z.maxZ=b.get(5); if(z.world==null||z.minX>z.maxX||z.minY>z.maxY||z.minZ>z.maxZ)throw new IOException("Invalid zone: "+id);nextZones.put(z.name,z);
        }
        ConfigurationSection rs=y.getConfigurationSection("rules");if(rs!=null) for(String id:rs.getKeys(false)) { ConfigurationSection c=rs.getConfigurationSection(id);Rule r=new Rule();r.id=key(id);r.event=c.getString("event");r.selector=c.getString("selector","*");r.station=c.getInt("station");r.seconds=c.getDouble("seconds",12);r.priority=c.getInt("priority",70);if(!EVENTS.contains(r.event)||!Double.isFinite(r.seconds)||r.seconds<.25||r.seconds>3600)throw new IOException("Invalid rule: "+id);nextRules.put(r.id,r); }
        ConfigurationSection ds=y.getConfigurationSection("discs");if(ds!=null)for(String id:ds.getKeys(false)){Disc d=new Disc();d.id=key(id);d.track=ds.getString(id);nextDiscs.put(d.id,d);}
        zones.clear();zones.putAll(nextZones);rules.clear();rules.putAll(nextRules);discs.clear();discs.putAll(nextDiscs);discStations.clear();
    }
    public void replace(AmbientManager next) { zones.clear();zones.putAll(next.zones);rules.clear();rules.putAll(next.rules);discs.clear();discs.putAll(next.discs);discStations.clear();leases.clear(); }
}
