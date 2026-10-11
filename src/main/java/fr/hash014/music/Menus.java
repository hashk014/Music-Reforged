/* Music Reforged — made by hash014. Rewritten 2026-10-11. GPL-2.0-or-later.
 * Based on the functionality of Music, Copyright (C) 2016 Zombie_Striker. */
package fr.hash014.music;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

public final class Menus implements Listener {
    private final MusicReforged plugin;
    static final class Menu implements InventoryHolder {
        final String box; final int page; final List<String> items = new ArrayList<>(); Inventory inventory;
        Menu(String box, int page) { this.box = box; this.page = page; }
        public Inventory getInventory() { return inventory; }
    }
    public Menus(MusicReforged p) { plugin = p; }
    private ItemStack item(String material, String title, String... lines) {
        Material m = Material.matchMaterial(material); if (m == null) m = Material.JUKEBOX;
        ItemStack i = new ItemStack(m); ItemMeta meta = i.getItemMeta(); meta.setDisplayName(ChatColor.AQUA + title);
        meta.setLore(Arrays.asList(lines)); i.setItemMeta(meta); return i;
    }
    public void open(Player p, RadioBox box, int page) {
        if (box != null) { requireBox(p, box); }
        List<?> values = new ArrayList<>(box == null ? plugin.tracks().values() : plugin.state().stations.values());
        int last = Math.max(0, (values.size() - 1) / 45); page = Math.max(0, Math.min(page, last));
        Menu menu = new Menu(box == null ? null : box.key(), page);
        menu.inventory = Bukkit.createInventory(menu, 54, "Music Reforged | " + (page + 1) + "/" + (last + 1));
        for (int n = page * 45; n < Math.min(values.size(), (page + 1) * 45); n++) {
            Object v = values.get(n); int slot = n % 45;
            if (v instanceof Track) {
                Track t = (Track) v; menu.items.add(t.id);
                menu.inventory.setItem(slot, item("MUSIC_DISC_CAT", t.id, ChatColor.GRAY + "Duree : " + t.seconds + " s", "Clic : ecouter", t.custom ? "Necessite le pack audio" : "Son Minecraft inclus"));
            } else {
                Station s = (Station) v; menu.items.add(String.valueOf(s.id));
                menu.inventory.setItem(slot, item("JUKEBOX", "Station " + s.id, s.queue.size() + " morceaux", "Rayon : " + s.radius + " blocs", s.paused ? "En pause" : "Clic : choisir cette station"));
            }
        }
        menu.inventory.setItem(45, item("ARROW", "Page precedente"));
        menu.inventory.setItem(46, item("NOTE_BLOCK", box == null ? "Volume personnel" : "Volume : " + box.volume, "Ajuster avec - et +"));
        menu.inventory.setItem(47, item("REDSTONE", "Volume -"));
        menu.inventory.setItem(48, item("GLOWSTONE_DUST", "Volume +"));
        menu.inventory.setItem(49, item("BARRIER", box == null ? "Couper mon audio" : "Eteindre le jukebox"));
        menu.inventory.setItem(50, item("COMPASS", box == null ? "Reactiver mon audio" : (box.global ? "Mode global actif" : "Mode local actif"), "Clic : changer de mode"));
        menu.inventory.setItem(51, item("BOOK", "Obtenir le pack audio"));
        menu.inventory.setItem(52, item("ARROW", "Page suivante"));
        menu.inventory.setItem(53, item("OAK_DOOR", "Fermer", "made by hash014"));
        p.openInventory(menu.inventory);
    }
    public void requireBox(Player p, RadioBox b) {
        if (plugin.state().boxes.get(b.key()) != b) throw new IllegalArgumentException("Ce jukebox n'existe plus");
        if (!plugin.owns(p, b.owner)) throw new IllegalArgumentException("Ce jukebox ne t'appartient pas");
        Location loc = b.location();
        if (loc == null || !loc.getWorld().equals(p.getWorld()) || loc.distanceSquared(p.getLocation()) > 144 || loc.getBlock().getType() != Material.JUKEBOX) throw new IllegalArgumentException("Approche-toi du jukebox");
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void place(BlockPlaceEvent e) {
        if (e.getBlockPlaced().getType() != Material.JUKEBOX || !e.getPlayer().isSneaking()) return;
        if (!plugin.use(e.getPlayer()) || !e.getPlayer().hasPermission("music.placejukebox")) return;
        Location l = e.getBlockPlaced().getLocation();
        RadioBox b = new RadioBox(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ(), e.getPlayer().getUniqueId());
        b.volume = (float) plugin.getConfig().getDouble("default-volume", 1);
        plugin.state().boxes.put(b.key(), b); plugin.persist();
        plugin.tell(e.getPlayer(), "Radio-jukebox pose. Clic droit pour choisir une station.");
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void breakProtection(BlockBreakEvent e) {
        RadioBox b = plugin.state().boxes.get(RadioBox.key(e.getBlock().getLocation()));
        if (b != null && !plugin.owns(e.getPlayer(), b.owner)) { e.setCancelled(true); plugin.tell(e.getPlayer(), "Ce jukebox ne t'appartient pas."); }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void broken(BlockBreakEvent e) {
        RadioBox box = plugin.state().boxes.remove(RadioBox.key(e.getBlock().getLocation()));
        if (box != null) { if (box.disc != null && plugin.ambiences().discs.containsKey(box.disc) && e.getPlayer().getGameMode() != GameMode.CREATIVE) AmbientManager.give(e.getPlayer(), plugin.ambiences().discItem(box.disc)); plugin.persist(); }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void explode(EntityExplodeEvent e) {
        boolean changed = false; for (Block b : e.blockList()) changed |= plugin.state().boxes.remove(RadioBox.key(b.getLocation())) != null;
        if (changed) plugin.persist();
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interact(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        try { Object hand = e.getClass().getMethod("getHand").invoke(e); if (hand != null && hand.toString().equals("OFF_HAND")) return; }
        catch (ReflectiveOperationException ignored) { }
        RadioBox b = plugin.state().boxes.get(RadioBox.key(e.getClickedBlock().getLocation())); if (b == null) return;
        e.setCancelled(true);
        try { if (!plugin.use(e.getPlayer()) || !e.getPlayer().hasPermission("music.usejukebox")) throw new IllegalArgumentException("Permission insuffisante"); open(e.getPlayer(), b, 0); }
        catch (IllegalArgumentException ex) { plugin.tell(e.getPlayer(), ex.getMessage()); }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked(); Menu m = (Menu) e.getView().getTopInventory().getHolder(); int slot = e.getRawSlot();
        if (slot < 0 || slot >= 54) return;
        try {
            if (!plugin.use(p)) throw new IllegalArgumentException("Permission insuffisante");
            RadioBox b = m.box == null ? null : plugin.state().boxes.get(m.box);
            if (m.box != null) { if (b == null) throw new IllegalArgumentException("Jukebox supprime"); requireBox(p, b); }
            if (slot < m.items.size()) {
                if (b == null) plugin.engine().once(p, plugin.track(m.items.get(slot)));
                else {
                    if (!p.hasPermission("music.changesong")) throw new IllegalArgumentException("Permission insuffisante");
                    int id = Integer.parseInt(m.items.get(slot));
                    if (b.disc != null) throw new IllegalArgumentException("Retire le disque avec /music disc eject avant de choisir une station");
                    if (!plugin.state().stations.containsKey(id)) throw new IllegalArgumentException("Station supprimee");
                    b.station = id; b.active = true; plugin.persist(); plugin.tell(p, "Station " + id + " selectionnee.");
                }
            } else if (slot == 45) { open(p, b, m.page - 1); return; }
            else if (slot == 52) { open(p, b, m.page + 1); return; }
            else if (slot == 53) { p.closeInventory(); return; }
            else if (slot == 51) { plugin.pack().send(p); return; }
            else if (slot == 49) {
                if (b != null) b.active = false; else { plugin.state().preferences.put(p.getUniqueId(), 0f); plugin.engine().stop(p); }
                plugin.persist();
            } else if (slot == 50) {
                if (b != null) { if (!plugin.global(p)) throw new IllegalArgumentException("Permission de diffusion globale requise"); b.global = !b.global; }
                else plugin.state().preferences.put(p.getUniqueId(), 1f);
                plugin.persist();
            } else if (slot == 47 || slot == 48) {
                if (b != null) {
                    if (!p.hasPermission("music.changevolume")) throw new IllegalArgumentException("Permission insuffisante");
                    b.volume = Math.max(0, Math.min(10, Math.round((b.volume + (slot == 47 ? -.1f : .1f)) * 10) / 10f));
                } else {
                    float v = plugin.state().preferences.getOrDefault(p.getUniqueId(), 1f);
                    plugin.state().preferences.put(p.getUniqueId(), Math.max(0, Math.min(1, Math.round((v + (slot == 47 ? -.1f : .1f)) * 10) / 10f)));
                    plugin.engine().stop(p);
                }
                plugin.persist();
            }
            open(p, b, m.page);
        } catch (IllegalArgumentException ex) { plugin.tell(p, ex.getMessage()); p.closeInventory(); }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }
    @EventHandler public void quit(PlayerQuitEvent e) { plugin.engine().stop(e.getPlayer()); }
    @EventHandler public void join(PlayerJoinEvent e) {
        if (!plugin.getConfig().getBoolean("resource-pack.send-on-join")) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (e.getPlayer().isOnline()) plugin.pack().send(e.getPlayer()); }, plugin.getConfig().getLong("resource-pack.join-delay-ticks", 40));
    }
    @EventHandler public void packStatus(PlayerResourcePackStatusEvent e) {
        String status = e.getStatus().name();
        if (status.equals("SUCCESSFULLY_LOADED")) { plugin.engine().stop(e.getPlayer()); plugin.tell(e.getPlayer(), "Pack audio charge."); }
        else if (status.equals("DECLINED") || status.startsWith("FAILED")) plugin.tell(e.getPlayer(), "Pack non charge. Les morceaux personnalises resteront silencieux; les disques Minecraft fonctionnent.");
    }
}
