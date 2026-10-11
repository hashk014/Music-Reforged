/* Music Reforged — made by hash014. Rewritten 2026-10-11. GPL-2.0-or-later.
 * Based on the functionality of Music, Copyright (C) 2016 Zombie_Striker. */
package fr.hash014.music;

import java.nio.file.*;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class MusicReforged extends JavaPlugin {
    private StateStore state;
    private Library library;
    private Map<String, Track> tracks = new TreeMap<>();
    private AudioEngine engine;
    private PackService pack;
    private Menus menus;
    private AmbientManager ambiences;
    private AudioImporter importer;
    private AdminServer adminServer;
    private boolean initialized;
    @Override public void onEnable() {
        try {
            saveDefaultConfig(); validateConfig();
            Path folder = getDataFolder().toPath(); Files.createDirectories(folder);
            LegacyImporter.importFirstInstall(this, folder);
            library = new Library(folder.resolve("Music"), getLogger()); library.defaults(); tracks = library.load();
            state = new StateStore(folder.resolve("state.yml")); state.load();
            ambiences = new AmbientManager(this); ambiences.load();
            if (Bukkit.getPluginManager().getPlugin("Music") != null) getLogger().warning("The legacy Music plugin is still installed: remove Music.jar to avoid a /music command conflict.");
            engine = new AudioEngine(this); pack = new PackService(this); pack.initialize();
            importer = new AudioImporter(this); adminServer = new AdminServer(this);
            try { adminServer.start(); } catch (java.io.IOException e) { getLogger().warning("Administration unavailable: " + e.getMessage() + ". Change admin.port, then restart."); }
            getServer().getPluginManager().registerEvents(ambiences, this);
            menus = new Menus(this); getServer().getPluginManager().registerEvents(menus, this);
            Commands commands = new Commands(this); getCommand("music").setExecutor(commands); getCommand("music").setTabCompleter(commands);
            Bukkit.getScheduler().runTaskTimer(this, () -> engine.tick(), 1, 1);
            initialized = true;
            getLogger().info("Music Reforged 1.0.0 — made by hash014 | " + tracks.size() + " tracks | " + state.stations.size() + " stations | " + Bukkit.getBukkitVersion());
            if (!engine.bridge.canStop()) getLogger().info("This legacy API cannot stop a sound already sent to a client: it will finish naturally.");
        } catch (Exception e) {
            getLogger().log(java.util.logging.Level.SEVERE, "Initialization failed. Existing files are preserved.", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    private void validateConfig() {
        int stations = getConfig().getInt("max-stations", 250), queue = getConfig().getInt("max-queue-size", 500);
        int radius = getConfig().getInt("default-radius", 64), max = getConfig().getInt("max-radius", 512);
        double volume = getConfig().getDouble("default-volume", 1);
        if (stations < 1 || stations > 10000 || queue < 1 || queue > 10000 || radius < 1 || radius > max || max > 4096 || !Double.isFinite(volume) || volume < 0 || volume > 10) throw new IllegalArgumentException("Invalid limits in config.yml");
        if (getConfig().getLong("resource-pack.join-delay-ticks", 40) < 0) throw new IllegalArgumentException("Pack delay cannot be negative");
        double fade = getConfig().getDouble("transitions.seconds", 2);
        int upload = getConfig().getInt("admin.max-upload-mb", 32), duration = getConfig().getInt("admin.max-duration-seconds", 600), port = getConfig().getInt("admin.port", 8125);
        if (!Double.isFinite(fade) || fade < .25 || fade > 10 || upload < 1 || upload > 64 || duration < 1 || duration > 3600 || port < 1 || port > 65535) throw new IllegalArgumentException("Invalid administration or transition configuration");
    }
    @Override public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        if (engine != null) engine.stopAll(); if (pack != null) pack.close();
        if (importer != null) importer.close(); if (adminServer != null) adminServer.close();
        if (initialized && state != null) persist();
    }
    public void reloadMusic() throws Exception {
        Map<String, Track> nextTracks = library.load();
        StateStore next = new StateStore(state.file()); next.load();
        AmbientManager nextAmbiences = new AmbientManager(this); nextAmbiences.load();
        // Invalid data is rejected before the running state is replaced.
        String previousConfig = getConfig().saveToString();
        try {
            reloadConfig(); validateConfig();
            pack.build(); engine.stopAll(); tracks = nextTracks; state = next; ambiences.replace(nextAmbiences);
        } catch (Exception e) { getConfig().loadFromString(previousConfig); throw e; }
    }
    public void persist() {
        try { state.save(); }
        catch (java.io.IOException e) { getLogger().log(java.util.logging.Level.SEVERE, "Could not save: " + state.file(), e); throw new IllegalStateException("Could not save; check the console", e); }
    }
    public void tell(CommandSender s, String message) { s.sendMessage(ChatColor.DARK_AQUA + "[Music Reforged] " + ChatColor.GRAY + message); }
    public boolean admin(CommandSender s) { return s.hasPermission("musicreforged.admin") || s.hasPermission("music.admin"); }
    public boolean owns(CommandSender s, UUID owner) { return admin(s) || s instanceof Player && ((Player) s).getUniqueId().equals(owner); }
    public boolean use(CommandSender s) { return admin(s) || s.hasPermission("musicreforged.use") || s.hasPermission("music.usejukebox"); }
    public boolean global(CommandSender s) { return admin(s) || s.hasPermission("musicreforged.global") || s.hasPermission("music.changevolumetoglobal"); }
    public StateStore state() { return state; }
    public Map<String, Track> tracks() { return Collections.unmodifiableMap(tracks); }
    public Library library() { return library; }
    public void refreshTracks() throws java.io.IOException { tracks = library.load(); }
    public Track track(String name) { Track t = tracks.get(name.toLowerCase(Locale.ROOT)); if (t == null) throw new IllegalArgumentException("Unknown track: " + name); return t; }
    public AudioEngine engine() { return engine; }
    public PackService pack() { return pack; }
    public Menus menus() { return menus; }
    public AmbientManager ambiences() { return ambiences; }
    public AudioImporter importer() { return importer; }
    public AdminServer administration() { return adminServer; }
}
