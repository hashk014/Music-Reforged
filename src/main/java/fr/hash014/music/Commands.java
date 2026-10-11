/* Music Reforged — made by hash014. Rewritten 2026-10-11. GPL-2.0-or-later.
 * Based on the functionality of Music, Copyright (C) 2016 Zombie_Striker. */
package fr.hash014.music;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public final class Commands implements CommandExecutor, TabCompleter {
    private final MusicReforged p;
    private static final List<String> ACTIONS = Arrays.asList("help", "admin", "playlist", "radio", "zone", "trigger", "disc", "listSongs", "listStations", "play", "addToQueue", "removeFromQueue", "clearQueue", "removeStation", "listStationSongs", "toggleRandomizeSongs", "setRadius", "pause", "resume", "skip", "listen", "playOnce", "stop", "volume", "box", "getPack", "createSong", "reload", "status", "author");
    public Commands(MusicReforged p) { this.p = p; }
    public static String[] quoted(String[] args) {
        String text = String.join(" ", args); List<String> result = new ArrayList<>(); StringBuilder token = new StringBuilder(); boolean quote = false;
        for (char c : text.toCharArray()) {
            if (c == '"') quote = !quote;
            else if (Character.isWhitespace(c) && !quote) { if (token.length() > 0) { result.add(token.toString()); token.setLength(0); } }
            else token.append(c);
        }
        if (quote) throw new IllegalArgumentException("Unclosed quotation marks");
        if (token.length() > 0) result.add(token.toString()); return result.toArray(new String[0]);
    }
    private static void need(String[] a, int count, String usage) { if (a.length < count) throw new IllegalArgumentException("Usage: /music " + usage); }
    private static int integer(String s, int min, int max) {
        try { int n = Integer.parseInt(s); if (n < min || n > max) throw new NumberFormatException(); return n; }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Expected a number between " + min + " and " + max); }
    }
    private static float volume(String s, float max) {
        try { float f = Float.parseFloat(s); if (!Float.isFinite(f) || f < 0 || f > max) throw new NumberFormatException(); return f; }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Volume must be between 0 and " + max); }
    }
    private Player player(CommandSender sender) { if (!(sender instanceof Player)) throw new IllegalArgumentException("This command requires a player"); return (Player) sender; }
    private void admin(CommandSender s) { if (!p.admin(s)) throw new IllegalArgumentException("Administrator permission required"); }
    private Station station(CommandSender sender, String id, boolean edit) {
        int n = integer(id, 0, 999999); Station station = p.state().stations.get(n);
        if (station == null) throw new IllegalArgumentException("Unknown station: " + n);
        if (edit && !p.owns(sender, station.owner)) throw new IllegalArgumentException("You do not own this station");
        return station;
    }
    private RadioBox box(Player player) {
        Block block = player.getTargetBlock((Set<Material>) null, 10);
        RadioBox box = block == null ? null : p.state().boxes.get(RadioBox.key(block.getLocation()));
        if (box == null) throw new IllegalArgumentException("Look at a radio jukebox (place one while sneaking)");
        p.menus().requireBox(player, box); return box;
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] raw) {
        try {
            String[] a = quoted(raw);
            String action = a.length == 0 ? "menu" : a[0].toLowerCase(Locale.ROOT);
            if (action.equals("selftest") || action.equals("playercheck") || action.equals("persistcheck") || action.equals("protocolinfo") || action.equals("migrationcheck")) {
                admin(sender); if (!Boolean.getBoolean("musicreforged.test")) throw new IllegalArgumentException("This command is restricted to the test server");
                ServerChecks checks = new ServerChecks(p);
                if (action.equals("selftest")) checks.run();
                if (action.equals("playercheck")) { need(a, 2, "playercheck <player>"); checks.player(a[1]); }
                if (action.equals("persistcheck")) checks.persist();
                if (action.equals("protocolinfo")) checks.protocol();
                if (action.equals("migrationcheck")) checks.migration();
                return true;
            }
            if (!p.use(sender)) throw new IllegalArgumentException("Insufficient permission");
            switch (action) {
                case "admin": {
                    admin(sender); String url = p.administration().login(sender);
                    if (!(sender instanceof Player) || !ChatLinks.send((Player) sender, url))
                        p.tell(sender, "Audio administration (session valid for 30 minutes): " + url);
                    break;
                }
                case "radio": need(a, 2, "radio <playlist or station|off>"); p.ambiences().radio(player(sender), a[1]); p.tell(sender, "Personal radio: " + a[1]); break;
                case "playlist": {
                    need(a, 2, "playlist create|add|remove|list <name> [track or index]"); String op=a[1].toLowerCase(Locale.ROOT);
                    if (op.equals("list")) { for (Station s:p.state().stations.values()) p.tell(sender, s.name + " (#" + s.id + ") : " + s.queue.size() + " tracks"); break; }
                    need(a, 3, "playlist " + op + " <name> [track or index]");
                    if (op.equals("create")) {
                        if (!p.admin(sender) && !sender.hasPermission("musicreforged.create") && !sender.hasPermission("music.commands")) throw new IllegalArgumentException("Creation permission required");
                        Station s=p.ambiences().createPlaylist(a[2], sender instanceof Player?((Player)sender).getUniqueId():AmbientManager.CONSOLE); p.tell(sender,"Playlist " + s.name + " created (#" + s.id + ").");
                    } else {
                        Station s=p.ambiences().playlist(a[2]); if (!p.owns(sender,s.owner)) throw new IllegalArgumentException("You do not own this playlist");
                        need(a, 4, "playlist " + op + " <name> <track or index>");
                        if (op.equals("add")) { if (s.queue.size()>=p.getConfig().getInt("max-queue-size",500)) throw new IllegalArgumentException("Playlist is full"); s.queue.add(p.track(a[3]).id); }
                        else if(op.equals("remove")){if(s.queue.isEmpty())throw new IllegalArgumentException("Playlist is empty");s.queue.remove(integer(a[3],0,s.queue.size()-1));s.reset();}
                        else throw new IllegalArgumentException("playlist : create, add, remove, list");p.persist();p.tell(sender,"Playlist updated.");
                    }
                    break;
                }
                case "zone": {
                    admin(sender);need(a,2,"zone wand|create|remove|list");
                    switch(a[1].toLowerCase(Locale.ROOT)) {
                        case "wand":p.ambiences().wand(player(sender));break;
                        case "create":need(a,4,"zone create <name> <playlist>");p.ambiences().selectedZone(player(sender),a[2],a[3]);p.tell(sender,"Music zone created.");break;
                        case "remove":need(a,3,"zone remove <name>");if(p.ambiences().zones.remove(AmbientManager.key(a[2]))==null)throw new IllegalArgumentException("Unknown zone");p.ambiences().save();p.tell(sender,"Zone removed.");break;
                        case "list":for(AmbientManager.Zone z:p.ambiences().zones.values())p.tell(sender,z.name+" : "+z.world+", station "+z.station);break;
                        default:throw new IllegalArgumentException("zone : wand, create, remove, list");
                    }break;
                }
                case "trigger": {
                    admin(sender);need(a,2,"trigger set|remove|list");
                    if(a[1].equalsIgnoreCase("set")){need(a,6,"trigger set <id> <COMBAT|BOSS|BIOME|JOIN|DEATH|RESPAWN> <filter or *> <playlist> [seconds] [priority]");p.ambiences().rule(a[2],a[3],a[4],p.ambiences().playlist(a[5]).id,a.length>6?Double.parseDouble(a[6]):12,a.length>7?integer(a[7],0,1000):70);p.tell(sender,"Trigger saved.");}
                    else if(a[1].equalsIgnoreCase("remove")){need(a,3,"trigger remove <id>");p.ambiences().rules.remove(AmbientManager.key(a[2]));p.ambiences().save();p.tell(sender,"Trigger removed.");}
                    else if(a[1].equalsIgnoreCase("list"))for(AmbientManager.Rule r:p.ambiences().rules.values())p.tell(sender,r.id+" : "+r.event+" / "+r.selector+" -> station "+r.station);
                    else throw new IllegalArgumentException("trigger : set, remove, list");break;
                }
                case "disc": {
                    need(a,2,"disc create|give|eject|list");String op=a[1].toLowerCase(Locale.ROOT);
                    if(op.equals("eject")){Player pl=player(sender);RadioBox b=box(pl);if(b.disc==null)throw new IllegalArgumentException("This jukebox has no custom disc");AmbientManager.give(pl,p.ambiences().discItem(b.disc));b.disc=null;b.active=false;p.persist();p.tell(sender,"Disc ejected.");break;}
                    admin(sender);
                    if(op.equals("create")){need(a,4,"disc create <id> <track>");p.ambiences().disc(a[2],a[3]);p.tell(sender,"Custom disc created.");}
                    else if(op.equals("give")){need(a,3,"disc give <id> [player]");Player target=a.length>3?Bukkit.getPlayerExact(a[3]):player(sender);if(target==null)throw new IllegalArgumentException("Player is offline");AmbientManager.give(target,p.ambiences().discItem(a[2]));p.tell(sender,"Disc given.");}
                    else if(op.equals("list"))for(AmbientManager.Disc d:p.ambiences().discs.values())p.tell(sender,d.id+" : "+d.track);
                    else throw new IllegalArgumentException("disc : create, give, eject, list");break;
                }
                case "menu": if (sender instanceof Player) p.menus().open((Player) sender, null, 0); else help(sender); break;
                case "help": case "?": help(sender); break;
                case "author": case "author1": p.tell(sender, "Music Reforged 1.0.0 — made by hash014. Based on the functionality of Music by Zombie_Striker. GPL-2.0-or-later; no warranty. Sources included in the distribution."); break;
                case "status":
                    p.tell(sender, "1.0.0 — made by hash014 | Server " + Bukkit.getBukkitVersion());
                    p.tell(sender, p.tracks().size() + " tracks, " + p.state().stations.size() + " stations, " + p.state().boxes.size() + " jukeboxes, " + p.engine().listeners() + " listeners");
                    p.tell(sender, "Pack format " + p.pack().format() + " | SHA-1 " + p.pack().sha1()); break;
                case "listsongs": case "songs": {
                    int page = a.length > 1 ? integer(a[1], 1, 10000) : 1; List<Track> songs = new ArrayList<>(p.tracks().values());
                    paginated(sender, page, songs.size(), "Tracks");
                    for (int n = (page - 1) * 20; n < Math.min(songs.size(), page * 20); n++) p.tell(sender, songs.get(n).id + " | " + songs.get(n).seconds + " s"); break;
                }
                case "liststations": case "list": {
                    int page = a.length > 1 ? integer(a[1], 1, 10000) : 1; List<Station> stations = new ArrayList<>(p.state().stations.values());
                    paginated(sender, page, stations.size(), "Stations");
                    for (int n = (page - 1) * 20; n < Math.min(stations.size(), page * 20); n++) { Station s = stations.get(n); p.tell(sender, "#" + s.id + " | " + s.queue.size() + " tracks | " + (s.paused ? "paused" : s.shuffle ? "shuffle" : "ordered")); } break;
                }
                case "play": case "setupstation": {
                    need(a, 3, "play <track> <station>"); Track t = p.track(a[1]); int id = integer(a[2], 0, p.getConfig().getInt("max-stations", 250) - 1);
                    Station s = p.state().stations.get(id);
                    if (s == null) {
                        if (!p.admin(sender) && !sender.hasPermission("musicreforged.create") && !sender.hasPermission("music.commands")) throw new IllegalArgumentException("Station creation permission required");
                        UUID owner = sender instanceof Player ? ((Player) sender).getUniqueId() : UUID.nameUUIDFromBytes("MusicReforged:console".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        s = new Station(id, owner); s.radius = p.getConfig().getInt("default-radius", 64); p.state().stations.put(id, s);
                    } else if (!p.owns(sender, s.owner)) throw new IllegalArgumentException("You do not own this station");
                    s.queue.clear(); s.queue.add(t.id); s.paused = false; s.reset(); p.persist();
                    p.tell(sender, "Station " + id + " : " + t.id + ". Place a jukebox while sneaking, then select this station."); break;
                }
                case "addtoqueue": case "add": {
                    need(a, 3, "addToQueue <track> <station>"); Track t = p.track(a[1]); Station s = station(sender, a[2], true);
                    if (s.queue.size() >= p.getConfig().getInt("max-queue-size", 500)) throw new IllegalArgumentException("Playlist is full");
                    s.queue.add(t.id); p.persist(); p.tell(sender, t.id + " added to station " + s.id); break;
                }
                case "removefromqueue": {
                    need(a, 3, "removeFromQueue <zero-based index> <station>"); Station s = station(sender, a[2], true);
                    if (s.queue.isEmpty()) throw new IllegalArgumentException("Playlist is empty");
                    int index = integer(a[1], 0, s.queue.size() - 1); s.queue.remove(index); s.reset(); p.persist(); p.tell(sender, "Track removed."); break;
                }
                case "removefromstation": {
                    need(a, 3, "removeFromStation <track> <station>"); Station s = station(sender, a[2], true);
                    if (!s.queue.removeIf(t -> t.equalsIgnoreCase(a[1]))) throw new IllegalArgumentException("Track is not in the playlist");
                    s.reset(); p.persist(); p.tell(sender, "Track removed."); break;
                }
                case "clearqueue": {
                    need(a, 2, "clearQueue <station>"); Station s = station(sender, a[1], true); s.queue.clear(); s.reset(); p.persist(); p.tell(sender, "Playlist cleared; station retained."); break;
                }
                case "removestation": {
                    need(a, 2, "removeStation <station>"); Station s = station(sender, a[1], true); p.state().stations.remove(s.id);
                    for (RadioBox b : p.state().boxes.values()) if (b.station == s.id) { b.station = -1; b.active = false; }
                    p.persist(); p.tell(sender, "Station removed."); break;
                }
                case "liststationsongs": case "queue": {
                    need(a, 2, "listStationSongs <station> [page]"); Station s = station(sender, a[1], false);
                    int page = a.length > 2 ? integer(a[2], 1, 10000) : 1; paginated(sender, page, s.queue.size(), "Station " + s.id);
                    for (int n = (page - 1) * 20; n < Math.min(s.queue.size(), page * 20); n++) p.tell(sender, "[" + n + "] " + s.queue.get(n) + (n == s.index() ? " < now playing" : "")); break;
                }
                case "togglerandomizesongs": case "shuffle": {
                    need(a, 2, "toggleRandomizeSongs <station>"); Station s = station(sender, a[1], true); s.shuffle = !s.shuffle; p.persist(); p.tell(sender, "Shuffle: " + s.shuffle); break;
                }
                case "setradius": case "radius": {
                    need(a, 3, "setRadius <station> <blocks>"); Station s = station(sender, a[1], true);
                    s.radius = integer(a[2], 1, p.getConfig().getInt("max-radius", 512)); p.persist(); p.tell(sender, "Radius: " + s.radius + " blocks"); break;
                }
                case "pause": case "resume": case "skip": {
                    need(a, 2, action + " <station>"); Station s = station(sender, a[1], true);
                    if (action.equals("skip")) s.skip(); else { s.paused = action.equals("pause"); s.reset(); }
                    p.persist(); p.tell(sender, "Station " + s.id + " : " + action); break;
                }
                case "listen": case "playonce": {
                    need(a, 2, action + " <track>"); Player player = player(sender); Track t = p.track(a[1]);
                    if (action.equals("playonce")) { admin(sender); for (Player other : player.getWorld().getPlayers()) p.engine().once(other, t); }
                    else p.engine().once(player, t);
                    p.tell(sender, "Playing: " + t.id); break;
                }
                case "stop": case "mute": { Player player = player(sender); p.state().preferences.put(player.getUniqueId(), 0f); p.engine().stop(player); p.persist(); p.tell(sender, "Personal audio muted. Use /music volume 1 to unmute."); break; }
                case "volume": {
                    need(a, 2, "volume <0 to 1>"); Player player = player(sender); p.state().preferences.put(player.getUniqueId(), volume(a[1], 1)); p.engine().stop(player); p.persist(); p.tell(sender, "Personal volume: " + a[1]); break;
                }
                case "box": { Player player = player(sender); RadioBox b = box(player);
                    if (a.length == 1) { p.menus().open(player, b, 0); break; }
                    switch (a[1].toLowerCase(Locale.ROOT)) {
                        case "station": need(a, 3, "box station <station>"); if (!player.hasPermission("music.changesong")) throw new IllegalArgumentException("Insufficient permission"); if(b.disc!=null)throw new IllegalArgumentException("Eject the disc with /music disc eject"); b.station = station(sender, a[2], false).id; b.active = true; break;
                        case "volume": need(a, 3, "box volume <0 to 10>"); if (!player.hasPermission("music.changevolume")) throw new IllegalArgumentException("Insufficient permission"); b.volume = volume(a[2], 10); break;
                        case "global": if (!p.global(sender)) throw new IllegalArgumentException("Global broadcast permission required"); b.global = true; break;
                        case "local": b.global = false; break;
                        case "off": b.active = false; break;
                        case "on": if (!p.state().stations.containsKey(b.station)) throw new IllegalArgumentException("Select a station first"); b.active = true; break;
                        default: throw new IllegalArgumentException("box : station, volume, global, local, on, off");
                    }
                    p.persist(); p.tell(sender, "Jukebox updated."); break;
                }
                case "get": case "getpack": case "pack": p.pack().send(player(sender)); break;
                case "createsong": {
                    admin(sender); need(a, 4, "createSong <name> <namespace:sound> <duration in seconds>");
                    if (p.tracks().containsKey(a[1].toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("This track already exists");
                    Track t = new Track(a[1], a[2], Double.parseDouble(a[3])); p.library().create(t); p.refreshTracks(); p.tell(sender, "Track created. Add its OGG file to audio/<namespace>/<sound>.ogg, then run /music reload."); break;
                }
                case "reload": admin(sender); p.reloadMusic(); p.tell(sender, "Tracks, stations and pack reloaded. Restart to change the HTTP server settings."); break;
                default: throw new IllegalArgumentException("Unknown command. Use /music help");
            }
        } catch (IllegalArgumentException e) { p.tell(sender, e.getMessage() == null ? "Invalid arguments" : e.getMessage()); }
        catch (Exception e) { p.tell(sender, "Error: " + e.getMessage()); p.getLogger().log(java.util.logging.Level.SEVERE, "Music command failed", e); }
        return true;
    }
    private void paginated(CommandSender s, int page, int count, String title) {
        int pages = Math.max(1, (count + 19) / 20); if (page > pages) throw new IllegalArgumentException("Maximum page: " + pages);
        p.tell(s, title + " | page " + page + "/" + pages);
    }
    private void help(CommandSender s) {
        p.tell(s, "Music Reforged — made by hash014");
        p.tell(s, "/music admin | playlist create|add|remove|list | radio <playlist|off>");
        p.tell(s, "/music zone wand then zone create <name> <playlist> | trigger set <id> <event> <filter> <playlist>");
        p.tell(s, "/music disc create <id> <track> | disc give <id> [player] | disc eject");
        for (String line : Arrays.asList("/music: open the library; click to listen", "/music play <track> <station> | addToQueue <track> <station>", "/music listSongs | listStations | listStationSongs <station>", "/music removeFromQueue <zero-based index> <station> | clearQueue <station>", "/music setRadius <station> <blocks> | toggleRandomizeSongs <station>", "/music pause|resume|skip|removeStation <station>", "/music listen <track> | playOnce <track> (admin, entire world)", "/music volume <0 to 1> | stop | getPack", "Place a jukebox while sneaking, then right-click to select stations, volume and global mode.", "/music box station <id> | box volume <0 to 10> | box global|local|on|off", "Admin : createSong <name> <namespace:sound> <duration> | reload | status", "Names with spaces: use quotation marks, e.g. \"My track\"")) p.tell(s, line);
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!p.use(sender)) return Collections.emptyList(); List<String> values = new ArrayList<>();
        String action = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (args.length == 1) values.addAll(ACTIONS);
        else if (args.length == 2 && Arrays.asList("play", "setupstation", "addtoqueue", "add", "listen", "playonce", "removefromstation").contains(action)) for (Track t : p.tracks().values()) values.add(t.id.contains(" ") ? "\"" + t.id + "\"" : t.id);
        else if (args.length == 2 && action.equals("box")) values.addAll(Arrays.asList("station", "volume", "global", "local", "on", "off"));
        else if (args.length == 2 && action.equals("volume")) values.addAll(Arrays.asList("0", "0.5", "1"));
        else if (args.length == 2 || args.length == 3 && Arrays.asList("play", "setupstation", "addtoqueue", "removefromqueue", "removefromstation").contains(action) || args.length == 3 && action.equals("box") && args[1].equalsIgnoreCase("station")) {
            for (Station s : p.state().stations.values()) values.add(String.valueOf(s.id));
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        values.removeIf(v -> !v.toLowerCase(Locale.ROOT).startsWith(prefix)); return values;
    }
}
