/* Music Reforged — made by hash014. Rewritten 2026-10-11. GPL-2.0-or-later.
 * Disposable loopback test harness. Disabled unless -Dmusicreforged.test=true. */
package fr.hash014.music;

import com.google.gson.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;

final class ServerChecks {
    private final MusicReforged p;
    private int checks;
    ServerChecks(MusicReforged p) { this.p = p; }
    private void check(boolean ok, String message) { if (!ok) throw new IllegalStateException("CHECK FAIL " + message); checks++; }
    private void command(CommandSender sender, String... args) { p.getCommand("music").execute(sender, "music", args); }
    void run() throws Exception {
        CommandSender console = Bukkit.getConsoleSender();
        check(p.isEnabled(), "enabled"); check(p.tracks().size() >= 6, "library");
        check(p.track("rEfOrGeD").ticks() == 320, "fractional-time-and-case");
        command(console, "play", "Reforged", "0"); check(p.state().stations.get(0).queue.equals(Arrays.asList("Reforged")), "create");
        command(console, "addToQueue", "Chill", "0"); command(console, "addToQueue", "Arcade", "0");
        check(p.state().stations.get(0).queue.size() == 3, "queue-add");
        command(console, "removeFromQueue", "1", "0"); check(p.state().stations.get(0).queue.equals(Arrays.asList("Reforged", "Arcade")), "queue-remove");
        command(console, "removeFromStation", "Arcade", "0"); check(p.state().stations.get(0).queue.equals(Arrays.asList("Reforged")), "queue-remove-by-name");
        command(console, "clearQueue", "0"); check(p.state().stations.get(0).queue.isEmpty(), "clear-keeps-station");
        command(console, "play", "Reforged", "0"); command(console, "addToQueue", "Chill", "0");
        command(console, "toggleRandomizeSongs", "0"); check(p.state().stations.get(0).shuffle, "shuffle");
        command(console, "setRadius", "0", "96"); check(p.state().stations.get(0).radius == 96, "radius");
        command(console, "pause", "0"); check(p.state().stations.get(0).paused, "pause");
        command(console, "resume", "0"); check(!p.state().stations.get(0).paused, "resume");
        command(console, "removeFromQueue", "9999", "0"); check(p.state().stations.get(0).queue.size() == 2, "bad-index-no-mutation");
        command(console, "play", "../bad", "1"); check(!p.state().stations.containsKey(1), "unknown-track-no-mutation");
        command(console, "play", "Reforged"); command(console, "volume", "NaN"); command(console, "getPack");
        check(p.state().stations.size() == 1, "invalid-and-console-safe");
        Path pack = p.getDataFolder().toPath().resolve("pack.zip");
        check(Files.exists(pack), "pack-exists");
        check(PackService.hex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(pack))).equals(p.pack().sha1()), "pack-hash");
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            JsonObject metadata = JsonParser.parseReader(new InputStreamReader(zip.getInputStream(zip.getEntry("pack.mcmeta")), StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("pack");
            check(p.pack().format() < 65 ? metadata.get("pack_format").getAsInt() == p.pack().format() : metadata.has("min_format") && metadata.has("max_format") && !metadata.has("supported_formats"), "pack-format");
            check(zip.getEntry("assets/musicreforged/sounds/reforged.ogg") != null, "ogg-in-pack");
            JsonObject sounds = JsonParser.parseReader(new InputStreamReader(zip.getInputStream(zip.getEntry("assets/musicreforged/sounds.json")), StandardCharsets.UTF_8)).getAsJsonObject();
            check(sounds.has("reforged") && sounds.has("chill") && sounds.has("arcade"), "sound-events");
        }
        p.persist(); StateStore saved = new StateStore(p.state().file()); saved.load();
        check(saved.stations.get(0).queue.equals(Arrays.asList("Reforged", "Chill")), "persist-playlist");
        check(saved.stations.get(0).shuffle && saved.stations.get(0).radius == 96, "persist-settings");
        Station running = p.state().stations.get(0);
        Path configPath = p.getDataFolder().toPath().resolve("config.yml"); byte[] originalConfig = Files.readAllBytes(configPath);
        AtomicFiles.write(configPath, "max-stations: 0\n");
        try { p.reloadMusic(); throw new IllegalStateException("Invalid reload accepted"); }
        catch (IllegalArgumentException expected) { check(p.state().stations.get(0) == running && p.getConfig().getInt("max-stations") > 0, "invalid-config-reload-preserved"); }
        finally { Files.write(configPath, originalConfig); }
        Path broken = p.getDataFolder().toPath().resolve("Music/Broken.txt"); AtomicFiles.write(broken, "Time:NaN\n");
        try { p.reloadMusic(); throw new IllegalStateException("Invalid library accepted"); }
        catch (IOException expected) { check(p.state().stations.get(0) == running, "invalid-library-reload-preserved"); }
        finally { Files.delete(broken); }
        p.reloadMusic(); check(p.state().stations.get(0).queue.equals(Arrays.asList("Reforged", "Chill")), "valid-reload");
        p.getLogger().info("SELFTEST PASS checks=" + checks);
    }
    void player(String name) throws Exception {
        Player player = Bukkit.getPlayerExact(name); check(player != null, "real-player-online");
        Location base = new Location(player.getWorld(), 2.5, 81, 4.5); player.teleport(base); player.setGameMode(GameMode.CREATIVE);
        for (int x = 0; x < 6; x++) for (int z = 0; z < 6; z++) player.getWorld().getBlockAt(x, 80, z).setType(Material.STONE);
        player.getWorld().getBlockAt(2, 81, 2).setType(Material.JUKEBOX);
        RadioBox box = new RadioBox(player.getWorld().getName(), 2, 81, 2, player.getUniqueId()); box.active = true; box.station = 0;
        p.state().boxes.put(box.key(), box); p.state().preferences.put(player.getUniqueId(), 1f);
        p.engine().once(player, p.track("Reforged")); check(p.engine().listeners() == 1, "live-sound-call");
        command(player, "volume", "0"); check(p.state().preferences.get(player.getUniqueId()) == 0, "mute");
        command(player, "volume", "1"); check(p.state().preferences.get(player.getUniqueId()) == 1, "unmute");
        p.menus().open(player, box, 0); Inventory inv = player.getOpenInventory().getTopInventory();
        check(inv.getSize() == 54 && inv.getHolder() instanceof Menus.Menu, "live-box-menu");
        InventoryClickEvent event = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        Bukkit.getPluginManager().callEvent(event); check(event.isCancelled() && box.station == 0, "menu-station-click");
        event = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER, 48, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        Bukkit.getPluginManager().callEvent(event); check(event.isCancelled() && box.volume > 1, "menu-volume-click");
        InventoryDragEvent drag = new InventoryDragEvent(player.getOpenInventory(), new org.bukkit.inventory.ItemStack(Material.STONE), new org.bukkit.inventory.ItemStack(Material.STONE), false, Collections.singletonMap(1, new org.bukkit.inventory.ItemStack(Material.STONE)));
        Bukkit.getPluginManager().callEvent(drag); check(drag.isCancelled(), "menu-drag-blocked");
        p.menus().open(player, null, 0); check(player.getOpenInventory().getTopInventory().getSize() == 54, "live-catalogue");
        // Pagination is exercised with more entries than the entire inventory can hold.
        UUID owner = player.getUniqueId(); for (int n = 1; n <= 60; n++) { Station s = new Station(n, owner); s.queue.add("Reforged"); p.state().stations.put(n, s); }
        p.menus().open(player, box, 1); check(((Menus.Menu) player.getOpenInventory().getTopInventory().getHolder()).page == 1, "paginated-60-stations");
        for (int n = 1; n <= 60; n++) p.state().stations.remove(n);
        player.closeInventory();
        UUID stranger = UUID.randomUUID(); box.owner = stranger;
        try { p.menus().open(player, box, 0); throw new IllegalStateException("Ownership bypass"); }
        catch (IllegalArgumentException expected) { checks++; }
        box.owner = owner; box.global = true;
        p.engine().tick(); check(p.engine().listeners() == 1, "live-global-listener");
        p.pack().send(player); p.persist();
        Bukkit.getScheduler().runTaskLater(p, () -> {
            p.engine().stop(player); box.global = false; p.engine().tick();
            check(p.engine().listeners() == 1, "live-local-listener");
            p.persist(); p.getLogger().info("PLAYERTEST PASS checks=" + checks);
        }, 40);
    }
    void persist() {
        check(p.state().stations.containsKey(0), "restart-station");
        Station s = p.state().stations.get(0); check(s.queue.equals(Arrays.asList("Reforged", "Chill")), "restart-playlist");
        check(s.radius == 96 && s.shuffle && !s.paused, "restart-station-settings");
        check(p.state().boxes.size() == 1, "restart-jukebox"); RadioBox b = p.state().boxes.values().iterator().next();
        check(b.active && b.station == 0 && b.volume > 1 && !b.global, "restart-box-settings");
        check(p.state().preferences.size() == 1 && p.state().preferences.values().iterator().next() == 1f, "restart-player-volume");
        p.getLogger().info("PERSISTENCE PASS checks=" + checks);
    }
    void protocol() throws Exception {
        JsonObject result = new JsonObject();
        Class<?> provider = Class.forName("net.minecraft.network.ProtocolInfo$DetailsProvider"), detail = Class.forName("net.minecraft.network.ProtocolInfo$Details"), visitor = Class.forName("net.minecraft.network.ProtocolInfo$Details$PacketVisitor"), packet = Class.forName("net.minecraft.network.protocol.PacketType");
        for (String state : Arrays.asList("login", "configuration", "game")) {
            String prefix = state.equals("game") ? "Game" : state.equals("login") ? "Login" : "Configuration";
            Class<?> protocols = Class.forName("net.minecraft.network.protocol." + state + "." + prefix + "Protocols");
            for (String flow : Arrays.asList("CLIENTBOUND", "SERVERBOUND")) {
                JsonObject ids = new JsonObject(); Object template = protocols.getField(flow + "_TEMPLATE").get(null);
                Object descriptions = provider.getMethod("details").invoke(template);
                Object callback = Proxy.newProxyInstance(visitor.getClassLoader(), new Class<?>[] { visitor }, (proxy, method, args) -> { if (method.getName().equals("accept")) ids.addProperty(packet.getMethod("id").invoke(args[0]).toString(), (Integer) args[1]); return null; });
                detail.getMethod("listPackets", visitor).invoke(descriptions, callback); result.add(state + "_" + flow.toLowerCase(Locale.ROOT), ids);
            }
        }
        AtomicFiles.write(p.getDataFolder().toPath().resolve("protocol.json"), result.toString()); p.getLogger().info("PROTOCOL INFO READY");
    }
    void migration() throws Exception {
        Station s = p.state().stations.get(88); check(s != null && s.queue.equals(Collections.singletonList("LegacySong")), "legacy-station");
        check(s.owner.equals(UUID.fromString("19cd46b4-5227-3575-9cbf-64f1bc95421f")) && s.shuffle && s.radius == 32, "legacy-owner-radius");
        check(p.track("LegacySong").ticks() == 3, "legacy-track-fraction");
        RadioBox b = p.state().boxes.values().iterator().next(); check(b.active && b.global && b.station == 88 && b.volume == 1, "legacy-box");
        check(Files.exists(p.getDataFolder().toPath().resolve("migration-complete")), "migration-marker");
        try (java.util.stream.Stream<Path> stream = Files.walk(p.getDataFolder().toPath().resolve("backups"))) { check(stream.anyMatch(f -> f.getFileName().toString().equals("config.yml")), "legacy-backup"); }
        p.getLogger().info("MIGRATION PASS checks=" + checks);
    }
}
