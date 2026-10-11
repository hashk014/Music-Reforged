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
        if (quote) throw new IllegalArgumentException("Guillemets non fermes");
        if (token.length() > 0) result.add(token.toString()); return result.toArray(new String[0]);
    }
    private static void need(String[] a, int count, String usage) { if (a.length < count) throw new IllegalArgumentException("Usage : /music " + usage); }
    private static int integer(String s, int min, int max) {
        try { int n = Integer.parseInt(s); if (n < min || n > max) throw new NumberFormatException(); return n; }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Nombre attendu entre " + min + " et " + max); }
    }
    private static float volume(String s, float max) {
        try { float f = Float.parseFloat(s); if (!Float.isFinite(f) || f < 0 || f > max) throw new NumberFormatException(); return f; }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Volume entre 0 et " + max); }
    }
    private Player player(CommandSender sender) { if (!(sender instanceof Player)) throw new IllegalArgumentException("Cette commande necessite un joueur"); return (Player) sender; }
    private void admin(CommandSender s) { if (!p.admin(s)) throw new IllegalArgumentException("Permission administrateur requise"); }
    private Station station(CommandSender sender, String id, boolean edit) {
        int n = integer(id, 0, 999999); Station station = p.state().stations.get(n);
        if (station == null) throw new IllegalArgumentException("Station inconnue: " + n);
        if (edit && !p.owns(sender, station.owner)) throw new IllegalArgumentException("Cette station ne t'appartient pas");
        return station;
    }
    private RadioBox box(Player player) {
        Block block = player.getTargetBlock((Set<Material>) null, 10);
        RadioBox box = block == null ? null : p.state().boxes.get(RadioBox.key(block.getLocation()));
        if (box == null) throw new IllegalArgumentException("Regarde un radio-jukebox (pose-le en etant accroupi)");
        p.menus().requireBox(player, box); return box;
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] raw) {
        try {
            String[] a = quoted(raw);
            String action = a.length == 0 ? "menu" : a[0].toLowerCase(Locale.ROOT);
            if (action.equals("selftest") || action.equals("playercheck") || action.equals("persistcheck") || action.equals("protocolinfo") || action.equals("migrationcheck")) {
                admin(sender); if (!Boolean.getBoolean("musicreforged.test")) throw new IllegalArgumentException("Commande reservee au serveur de test");
                ServerChecks checks = new ServerChecks(p);
                if (action.equals("selftest")) checks.run();
                if (action.equals("playercheck")) { need(a, 2, "playercheck <joueur>"); checks.player(a[1]); }
                if (action.equals("persistcheck")) checks.persist();
                if (action.equals("protocolinfo")) checks.protocol();
                if (action.equals("migrationcheck")) checks.migration();
                return true;
            }
            if (!p.use(sender)) throw new IllegalArgumentException("Permission insuffisante");
            switch (action) {
                case "admin": admin(sender); p.tell(sender, "Administration audio (connexion valable 30 minutes) : " + p.administration().login(sender)); break;
                case "radio": need(a, 2, "radio <playlist ou station|off>"); p.ambiences().radio(player(sender), a[1]); p.tell(sender, "Radio personnelle : " + a[1]); break;
                case "playlist": {
                    need(a, 2, "playlist create|add|remove|list <nom> [morceau ou index]"); String op=a[1].toLowerCase(Locale.ROOT);
                    if (op.equals("list")) { for (Station s:p.state().stations.values()) p.tell(sender, s.name + " (#" + s.id + ") : " + s.queue.size() + " morceaux"); break; }
                    need(a, 3, "playlist " + op + " <nom> [morceau ou index]");
                    if (op.equals("create")) {
                        if (!p.admin(sender) && !sender.hasPermission("musicreforged.create") && !sender.hasPermission("music.commands")) throw new IllegalArgumentException("Permission de creation requise");
                        Station s=p.ambiences().createPlaylist(a[2], sender instanceof Player?((Player)sender).getUniqueId():AmbientManager.CONSOLE); p.tell(sender,"Playlist " + s.name + " creee (#" + s.id + ").");
                    } else {
                        Station s=p.ambiences().playlist(a[2]); if (!p.owns(sender,s.owner)) throw new IllegalArgumentException("Cette playlist ne t'appartient pas");
                        need(a, 4, "playlist " + op + " <nom> <morceau ou index>");
                        if (op.equals("add")) { if (s.queue.size()>=p.getConfig().getInt("max-queue-size",500)) throw new IllegalArgumentException("Playlist pleine"); s.queue.add(p.track(a[3]).id); }
                        else if(op.equals("remove")){if(s.queue.isEmpty())throw new IllegalArgumentException("Playlist vide");s.queue.remove(integer(a[3],0,s.queue.size()-1));s.reset();}
                        else throw new IllegalArgumentException("playlist : create, add, remove, list");p.persist();p.tell(sender,"Playlist mise a jour.");
                    }
                    break;
                }
                case "zone": {
                    admin(sender);need(a,2,"zone wand|create|remove|list");
                    switch(a[1].toLowerCase(Locale.ROOT)) {
                        case "wand":p.ambiences().wand(player(sender));break;
                        case "create":need(a,4,"zone create <nom> <playlist>");p.ambiences().selectedZone(player(sender),a[2],a[3]);p.tell(sender,"Zone musicale creee.");break;
                        case "remove":need(a,3,"zone remove <nom>");if(p.ambiences().zones.remove(AmbientManager.key(a[2]))==null)throw new IllegalArgumentException("Zone inconnue");p.ambiences().save();p.tell(sender,"Zone supprimee.");break;
                        case "list":for(AmbientManager.Zone z:p.ambiences().zones.values())p.tell(sender,z.name+" : "+z.world+", station "+z.station);break;
                        default:throw new IllegalArgumentException("zone : wand, create, remove, list");
                    }break;
                }
                case "trigger": {
                    admin(sender);need(a,2,"trigger set|remove|list");
                    if(a[1].equalsIgnoreCase("set")){need(a,6,"trigger set <id> <COMBAT|BOSS|BIOME|JOIN|DEATH|RESPAWN> <filtre ou *> <playlist> [secondes] [priorite]");p.ambiences().rule(a[2],a[3],a[4],p.ambiences().playlist(a[5]).id,a.length>6?Double.parseDouble(a[6]):12,a.length>7?integer(a[7],0,1000):70);p.tell(sender,"Declencheur enregistre.");}
                    else if(a[1].equalsIgnoreCase("remove")){need(a,3,"trigger remove <id>");p.ambiences().rules.remove(AmbientManager.key(a[2]));p.ambiences().save();p.tell(sender,"Declencheur supprime.");}
                    else if(a[1].equalsIgnoreCase("list"))for(AmbientManager.Rule r:p.ambiences().rules.values())p.tell(sender,r.id+" : "+r.event+" / "+r.selector+" -> station "+r.station);
                    else throw new IllegalArgumentException("trigger : set, remove, list");break;
                }
                case "disc": {
                    need(a,2,"disc create|give|eject|list");String op=a[1].toLowerCase(Locale.ROOT);
                    if(op.equals("eject")){Player pl=player(sender);RadioBox b=box(pl);if(b.disc==null)throw new IllegalArgumentException("Ce jukebox n'a pas de disque personnalise");AmbientManager.give(pl,p.ambiences().discItem(b.disc));b.disc=null;b.active=false;p.persist();p.tell(sender,"Disque retire.");break;}
                    admin(sender);
                    if(op.equals("create")){need(a,4,"disc create <id> <morceau>");p.ambiences().disc(a[2],a[3]);p.tell(sender,"Disque personnalise cree.");}
                    else if(op.equals("give")){need(a,3,"disc give <id> [joueur]");Player target=a.length>3?Bukkit.getPlayerExact(a[3]):player(sender);if(target==null)throw new IllegalArgumentException("Joueur deconnecte");AmbientManager.give(target,p.ambiences().discItem(a[2]));p.tell(sender,"Disque donne.");}
                    else if(op.equals("list"))for(AmbientManager.Disc d:p.ambiences().discs.values())p.tell(sender,d.id+" : "+d.track);
                    else throw new IllegalArgumentException("disc : create, give, eject, list");break;
                }
                case "menu": if (sender instanceof Player) p.menus().open((Player) sender, null, 0); else help(sender); break;
                case "help": case "?": help(sender); break;
                case "author": case "author1": p.tell(sender, "Music Reforged 1.0.0 — made by hash014. Base fonctionnelle: Music par Zombie_Striker. GPL-2.0-or-later; sans garantie. Sources fournies dans la distribution."); break;
                case "status":
                    p.tell(sender, "1.0.0 — made by hash014 | Serveur " + Bukkit.getBukkitVersion());
                    p.tell(sender, p.tracks().size() + " morceaux, " + p.state().stations.size() + " stations, " + p.state().boxes.size() + " jukeboxes, " + p.engine().listeners() + " auditeurs");
                    p.tell(sender, "Pack format " + p.pack().format() + " | SHA-1 " + p.pack().sha1()); break;
                case "listsongs": case "songs": {
                    int page = a.length > 1 ? integer(a[1], 1, 10000) : 1; List<Track> songs = new ArrayList<>(p.tracks().values());
                    paginated(sender, page, songs.size(), "Morceaux");
                    for (int n = (page - 1) * 20; n < Math.min(songs.size(), page * 20); n++) p.tell(sender, songs.get(n).id + " | " + songs.get(n).seconds + " s"); break;
                }
                case "liststations": case "list": {
                    int page = a.length > 1 ? integer(a[1], 1, 10000) : 1; List<Station> stations = new ArrayList<>(p.state().stations.values());
                    paginated(sender, page, stations.size(), "Stations");
                    for (int n = (page - 1) * 20; n < Math.min(stations.size(), page * 20); n++) { Station s = stations.get(n); p.tell(sender, "#" + s.id + " | " + s.queue.size() + " morceaux | " + (s.paused ? "pause" : s.shuffle ? "aleatoire" : "ordre")); } break;
                }
                case "play": case "setupstation": {
                    need(a, 3, "play <morceau> <station>"); Track t = p.track(a[1]); int id = integer(a[2], 0, p.getConfig().getInt("max-stations", 250) - 1);
                    Station s = p.state().stations.get(id);
                    if (s == null) {
                        if (!p.admin(sender) && !sender.hasPermission("musicreforged.create") && !sender.hasPermission("music.commands")) throw new IllegalArgumentException("Permission de creation de station requise");
                        UUID owner = sender instanceof Player ? ((Player) sender).getUniqueId() : UUID.nameUUIDFromBytes("MusicReforged:console".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        s = new Station(id, owner); s.radius = p.getConfig().getInt("default-radius", 64); p.state().stations.put(id, s);
                    } else if (!p.owns(sender, s.owner)) throw new IllegalArgumentException("Cette station ne t'appartient pas");
                    s.queue.clear(); s.queue.add(t.id); s.paused = false; s.reset(); p.persist();
                    p.tell(sender, "Station " + id + " : " + t.id + ". Pose un jukebox accroupi puis choisis cette station."); break;
                }
                case "addtoqueue": case "add": {
                    need(a, 3, "addToQueue <morceau> <station>"); Track t = p.track(a[1]); Station s = station(sender, a[2], true);
                    if (s.queue.size() >= p.getConfig().getInt("max-queue-size", 500)) throw new IllegalArgumentException("Playlist pleine");
                    s.queue.add(t.id); p.persist(); p.tell(sender, t.id + " ajoute a la station " + s.id); break;
                }
                case "removefromqueue": {
                    need(a, 3, "removeFromQueue <index depuis 0> <station>"); Station s = station(sender, a[2], true);
                    if (s.queue.isEmpty()) throw new IllegalArgumentException("Playlist vide");
                    int index = integer(a[1], 0, s.queue.size() - 1); s.queue.remove(index); s.reset(); p.persist(); p.tell(sender, "Morceau retire."); break;
                }
                case "removefromstation": {
                    need(a, 3, "removeFromStation <morceau> <station>"); Station s = station(sender, a[2], true);
                    if (!s.queue.removeIf(t -> t.equalsIgnoreCase(a[1]))) throw new IllegalArgumentException("Morceau absent de la playlist");
                    s.reset(); p.persist(); p.tell(sender, "Morceau retire."); break;
                }
                case "clearqueue": {
                    need(a, 2, "clearQueue <station>"); Station s = station(sender, a[1], true); s.queue.clear(); s.reset(); p.persist(); p.tell(sender, "Playlist videe; station conservee."); break;
                }
                case "removestation": {
                    need(a, 2, "removeStation <station>"); Station s = station(sender, a[1], true); p.state().stations.remove(s.id);
                    for (RadioBox b : p.state().boxes.values()) if (b.station == s.id) { b.station = -1; b.active = false; }
                    p.persist(); p.tell(sender, "Station supprimee."); break;
                }
                case "liststationsongs": case "queue": {
                    need(a, 2, "listStationSongs <station> [page]"); Station s = station(sender, a[1], false);
                    int page = a.length > 2 ? integer(a[2], 1, 10000) : 1; paginated(sender, page, s.queue.size(), "Station " + s.id);
                    for (int n = (page - 1) * 20; n < Math.min(s.queue.size(), page * 20); n++) p.tell(sender, "[" + n + "] " + s.queue.get(n) + (n == s.index() ? " < en cours" : "")); break;
                }
                case "togglerandomizesongs": case "shuffle": {
                    need(a, 2, "toggleRandomizeSongs <station>"); Station s = station(sender, a[1], true); s.shuffle = !s.shuffle; p.persist(); p.tell(sender, "Lecture aleatoire : " + s.shuffle); break;
                }
                case "setradius": case "radius": {
                    need(a, 3, "setRadius <station> <blocs>"); Station s = station(sender, a[1], true);
                    s.radius = integer(a[2], 1, p.getConfig().getInt("max-radius", 512)); p.persist(); p.tell(sender, "Rayon : " + s.radius + " blocs"); break;
                }
                case "pause": case "resume": case "skip": {
                    need(a, 2, action + " <station>"); Station s = station(sender, a[1], true);
                    if (action.equals("skip")) s.skip(); else { s.paused = action.equals("pause"); s.reset(); }
                    p.persist(); p.tell(sender, "Station " + s.id + " : " + action); break;
                }
                case "listen": case "playonce": {
                    need(a, 2, action + " <morceau>"); Player player = player(sender); Track t = p.track(a[1]);
                    if (action.equals("playonce")) { admin(sender); for (Player other : player.getWorld().getPlayers()) p.engine().once(other, t); }
                    else p.engine().once(player, t);
                    p.tell(sender, "Lecture : " + t.id); break;
                }
                case "stop": case "mute": { Player player = player(sender); p.state().preferences.put(player.getUniqueId(), 0f); p.engine().stop(player); p.persist(); p.tell(sender, "Audio personnel coupe. /music volume 1 pour le reactiver."); break; }
                case "volume": {
                    need(a, 2, "volume <0 a 1>"); Player player = player(sender); p.state().preferences.put(player.getUniqueId(), volume(a[1], 1)); p.engine().stop(player); p.persist(); p.tell(sender, "Volume personnel : " + a[1]); break;
                }
                case "box": { Player player = player(sender); RadioBox b = box(player);
                    if (a.length == 1) { p.menus().open(player, b, 0); break; }
                    switch (a[1].toLowerCase(Locale.ROOT)) {
                        case "station": need(a, 3, "box station <station>"); if (!player.hasPermission("music.changesong")) throw new IllegalArgumentException("Permission insuffisante"); if(b.disc!=null)throw new IllegalArgumentException("Retire le disque avec /music disc eject"); b.station = station(sender, a[2], false).id; b.active = true; break;
                        case "volume": need(a, 3, "box volume <0 a 10>"); if (!player.hasPermission("music.changevolume")) throw new IllegalArgumentException("Permission insuffisante"); b.volume = volume(a[2], 10); break;
                        case "global": if (!p.global(sender)) throw new IllegalArgumentException("Permission globale requise"); b.global = true; break;
                        case "local": b.global = false; break;
                        case "off": b.active = false; break;
                        case "on": if (!p.state().stations.containsKey(b.station)) throw new IllegalArgumentException("Choisis d'abord une station"); b.active = true; break;
                        default: throw new IllegalArgumentException("box : station, volume, global, local, on, off");
                    }
                    p.persist(); p.tell(sender, "Jukebox mis a jour."); break;
                }
                case "get": case "getpack": case "pack": p.pack().send(player(sender)); break;
                case "createsong": {
                    admin(sender); need(a, 4, "createSong <nom> <namespace:son> <duree en secondes>");
                    if (p.tracks().containsKey(a[1].toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Ce morceau existe deja");
                    Track t = new Track(a[1], a[2], Double.parseDouble(a[3])); p.library().create(t); p.refreshTracks(); p.tell(sender, "Morceau cree. Ajoute son OGG dans audio/<namespace>/<son>.ogg puis /music reload."); break;
                }
                case "reload": admin(sender); p.reloadMusic(); p.tell(sender, "Morceaux, stations et pack recharges. Redemarre pour changer les parametres du serveur HTTP."); break;
                default: throw new IllegalArgumentException("Commande inconnue. /music help");
            }
        } catch (IllegalArgumentException e) { p.tell(sender, e.getMessage() == null ? "Arguments invalides" : e.getMessage()); }
        catch (Exception e) { p.tell(sender, "Erreur : " + e.getMessage()); p.getLogger().log(java.util.logging.Level.SEVERE, "Commande Music echouee", e); }
        return true;
    }
    private void paginated(CommandSender s, int page, int count, String title) {
        int pages = Math.max(1, (count + 19) / 20); if (page > pages) throw new IllegalArgumentException("Page maximum : " + pages);
        p.tell(s, title + " | page " + page + "/" + pages);
    }
    private void help(CommandSender s) {
        p.tell(s, "Music Reforged — made by hash014");
        p.tell(s, "/music admin | playlist create|add|remove|list | radio <playlist|off>");
        p.tell(s, "/music zone wand puis zone create <nom> <playlist> | trigger set <id> <evenement> <filtre> <playlist>");
        p.tell(s, "/music disc create <id> <morceau> | disc give <id> [joueur] | disc eject");
        for (String line : Arrays.asList("/music : ouvrir le catalogue; clic pour ecouter", "/music play <morceau> <station> | addToQueue <morceau> <station>", "/music listSongs | listStations | listStationSongs <station>", "/music removeFromQueue <index depuis 0> <station> | clearQueue <station>", "/music setRadius <station> <blocs> | toggleRandomizeSongs <station>", "/music pause|resume|skip|removeStation <station>", "/music listen <morceau> | playOnce <morceau> (admin, monde entier)", "/music volume <0 a 1> | stop | getPack", "Pose un jukebox accroupi, puis clic droit : stations, volume et mode global.", "/music box station <id> | box volume <0 a 10> | box global|local|on|off", "Admin : createSong <nom> <namespace:son> <duree> | reload | status", "Noms avec espaces : entre guillemets, ex. \"Mon morceau\"")) p.tell(s, line);
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
