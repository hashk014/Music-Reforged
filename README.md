# Music Reforged — made by hash014

Réécriture du plugin Music de Zombie_Striker : radios, playlists, jukeboxes,
audio personnalisé et génération autonome du pack. Plugin serveur Bukkit/Spigot/Paper.
Le JAR ne nécessite aucune dépendance de plugin, aucun compte et aucun service payant.

## Administration, zones et événements

`/music admin` ouvre l'administration dans le navigateur via un lien valable
30 minutes. Un joueur doit rester connecté avec la permission administrateur.
Un lien émis par la console fonctionne aussi. L'interface écoute par défaut sur
`127.0.0.1:8125` ; elle est réservée à la machine du serveur. Pour un accès distant,
configurer `admin.bind` et une `admin.public-url` derrière un proxy HTTPS.

L'interface permet d'importer des **OGG ou MP3**, de les préécouter, de réordonner
les playlists, de créer des zones, des déclencheurs et des disques. FFmpeg doit
être disponible dans le PATH, ou son chemin doit être indiqué dans `admin.ffmpeg`.
Il est déjà disponible sur la machine utilisée pour développer cette version.
Les fichiers importés sont convertis en OGG Vorbis mono ; la durée est détectée
automatiquement. Le pack est reconstruit après chaque import réussi. Un bouton
permet de le proposer aux joueurs connectés, qui doivent l'accepter.
Les imports se font en arrière-plan, avec progression, une limite de 32 Mo par
fichier et de 600 secondes par morceau par défaut. Un fichier trop long est
refusé, pas tronqué. Une conversion qui échoue n'ajoute pas de morceau incomplet.

```text
/music playlist create Foret
/music playlist add Foret Chill
/music playlist create Combat
/music playlist add Combat Arcade
/music radio Foret
/music radio off
/music zone wand
/music zone create foret Foret
/music trigger set bataille COMBAT * Combat 12 80
/music trigger set boss BOSS WITHER Combat 15 100
/music trigger set bois BIOME FOREST Foret 5 40
/music disc create theme Reforged
/music disc give theme MonJoueur
/music disc eject
```

Pour créer une zone en jeu, prendre la baguette avec `/music zone wand`, faire
un clic gauche sur le premier coin puis un clic droit sur le second, et lancer
`zone create`. Une zone est un volume rectangulaire, avec un monde et une priorité.
Ses données sont conservées après un redémarrage.

Les événements disponibles sont COMBAT, BOSS, BIOME, JOIN, DEATH et RESPAWN.
COMBAT réagit aux dégâts entre entités, y compris les projectiles de joueurs.
BOSS réagit à un Wither ou un Ender Dragon à proximité, et aux attaques contre
ces boss. BIOME maintient l'ambiance tant que le biome correspond au filtre.
La priorité la plus haute prend le relais de la zone ou de la radio ; le joueur
retrouve l'ambiance normale lorsque le déclencheur expire. `*` accepte tous les
cas ; un filtre peut être un nom de biome ou d'entité fourni par le serveur.

Les disques sont des objets d'inventaire Minecraft avec un nom et un morceau
associé. Un clic droit avec le disque sur un jukebox l'insère ; `disc eject`, en
regardant le jukebox, le rend au joueur. Le propriétaire du jukebox est respecté.
Les playlists, les zones, les règles et les définitions de disques sont éditables
dans `state.yml` et `ambiences.yml`, puis rechargeables avec `/music reload`.

![Administration : import MP3 terminé](docs/admin-import.jpg)

## Installer

1. Arrêter le serveur et placer `Music-Reforged-1.0.0.jar` dans `plugins/`.
2. Retirer l'ancien `Music.jar` pour libérer la commande `/music`. Garder son dossier `plugins/Music/` si une migration est nécessaire.
3. Démarrer le serveur avec la version Java requise par ce serveur.
4. Le plugin crée `plugins/MusicReforged/`, son catalogue et `pack.zip`.
5. Pour entendre les morceaux personnalisés, installer ce pack côté client ou configurer sa distribution automatique ci-dessous.

Les disques Cat, Blocks et Stal fonctionnent avec les sons Minecraft inclus.
Les trois boucles originales Reforged, Chill et Arcade et les quinze exemples
historiques de Music nécessitent le pack audio.

## Jouer

```text
/music play Reforged 0
/music addToQueue Chill 0
/music addToQueue Arcade 0
```

Poser un jukebox en restant accroupi. Clic droit : choisir la station 0.
Le menu permet aussi de régler le volume, d'éteindre le jukebox et de passer
en diffusion globale au monde, avec la permission correspondante.
`/music` ouvre le catalogue ; un clic sur un morceau lance une écoute personnelle.

```text
/music listSongs [page]
/music listStations [page]
/music listStationSongs <station> [page]
/music removeFromQueue <index depuis 0> <station>
/music removeFromStation <morceau> <station>
/music clearQueue <station>
/music removeStation <station>
/music toggleRandomizeSongs <station>
/music setRadius <station> <blocs>
/music pause <station>
/music resume <station>
/music skip <station>
/music listen <morceau>
/music playOnce <morceau>
/music volume <0 à 1>
/music stop
/music getPack
/music box station <station>
/music box volume <0 à 10>
/music box global|local|on|off
/music createSong <nom> <namespace:son> <durée en secondes>
/music reload
/music status
/music author
```

`playOnce` est réservé aux administrateurs et joue pour les joueurs du monde,
comme dans Music. `listen` joue pour soi. `setUpStation` reste un alias de `play`.
Les noms contenant des espaces s'écrivent entre guillemets : `"Mon morceau"`.
`clearQueue` conserve la station et son propriétaire. Une playlist vide reste silencieuse.

## Distribuer le pack

Pour un essai sur la même machine, dans `plugins/MusicReforged/config.yml` :

```yaml
resource-pack:
  serve: true
  bind: 127.0.0.1
  port: 8124
  public-url: ''
  send-on-join: true
```

Redémarrer, puis `/music getPack`. Le serveur HTTP intégré ne sert que `pack.zip`.
Pour un serveur accessible à d'autres machines, héberger `pack.zip` sur une URL
HTTPS téléchargeable directement et définir `public-url`, ou utiliser le serveur
HTTP intégré avec `bind: 0.0.0.0`, un port accessible et une `public-url` publique.
Une URL localhost fonctionne uniquement sur la machine du serveur.
Le pack est proposé au joueur, qui doit l'accepter. Le plugin suit le statut de
chargement reçu du client et envoie le SHA-1 lorsque l'API du serveur le permet.
Il ne remplace pas automatiquement un pack de serveur existant : fusionner les
fichiers `assets/` et les entrées `sounds.json` si d'autres plugins ont un pack.

Le format est lu depuis le `version.json` du serveur. Les anciennes versions
utilisent une table de repli. Les formats modernes, dès 1.21.9, utilisent
`min_format` et `max_format` ; le format de 26.3 est 97.1.
Après un changement de version serveur, le pack est régénéré au prochain démarrage.
Avec ViaVersion, ce pack cible la version réelle du serveur ; une validation
supplémentaire est nécessaire pour des clients d'une autre version.

## Ajouter sa musique

Encoder un OGG **Vorbis mono** pour obtenir un son positionnel.
Exemple avec FFmpeg :

```text
ffmpeg -i chanson.mp3 -ac 1 -c:a libvorbis -q:a 4 chanson.ogg
```

Placer le fichier dans `plugins/MusicReforged/audio/monsite/chanson.ogg` puis :

```text
/music createSong MaChanson monsite:chanson 123.45
/music reload
```

Le nom `monsite:chanson` correspond à l'entrée du pack générée depuis le chemin
du fichier. L'import depuis le navigateur effectue cette conversion automatiquement
avec FFmpeg ; l'ajout manuel ci-dessus utilise un fichier OGG déjà préparé.
On peut aussi créer `plugins/MusicReforged/Music/MaChanson.txt` :

```text
Time:123.45
Soundname:monsite:chanson
```

L'ancien champ `Songname:` est reconnu. La durée est en secondes avec décimales,
planifiée à la précision d'un tick, soit 50 ms à 20 TPS. Le pack est limité
à 64 Mo au total ; la limite individuelle configurable est de 32 Mo par défaut.
Les erreurs de catalogue interrompent le rechargement, sans remplacer l'état actif.

## Permissions

| Permission | Défaut | Utilité |
|---|---|---|
| `musicreforged.use` | tous | Catalogue, écoute, réglages personnels |
| `musicreforged.create` | OP | Créer une station |
| `musicreforged.admin` | OP | Administrer toutes les stations et jukeboxes, créer des morceaux, recharger |
| `musicreforged.global` | OP | Diffusion au monde entier |
| `music.placejukebox` / `music.usejukebox` | tous | Poser et ouvrir son radio-jukebox |
| `music.changesong` / `music.changevolume` | tous | Choisir une station et régler son jukebox |

Les permissions historiques `music.admin`, `music.commands`, `music.default`,
`music.*` et `music.changevolumetoglobal` sont conservées. Chaque station et
jukebox a un propriétaire UUID. Un autre joueur ne peut pas modifier ou casser
un radio-jukebox appartenant à quelqu'un d'autre. Les événements annulés par
des plugins de protection sont respectés.

## Migration

Au premier démarrage uniquement, le plugin détecte `plugins/Music/config.yml`
et importe `Loop`, `Jukeboxes` et les fichiers `Music/*.txt` dans son propre dossier.
Une copie des fichiers importés est conservée dans `MusicReforged/backups/`.
Le dossier original n'est pas modifié. Le marqueur `migration-complete` empêche
un nouvel import. Les positions sont enregistrées en primitives, même lorsque
le monde n'est pas chargé au démarrage. Les playlists redémarrent depuis le début.

Les UUID historiques sont conservées. Pour un ancien propriétaire enregistré
par nom, le cache joueur est utilisé ; si son identité est inconnue sur un
serveur en mode authentifié, l'import est refusé explicitement pour préserver
les droits de propriété. Corriger une copie de la configuration avec son UUID.
L'ancien `r` devient un rayon en blocs, multiplié par 16. Un ancien volume
`-1` devient une diffusion globale. La suppression de pistes ne détruit plus
la configuration entière de la station.

## Comportement audio

Les trois boucles originales et les morceaux importés sont préparés en segments
de 250 ms. Le moteur fait monter la nouvelle ambiance et descendre la précédente
pendant la durée configurée dans `transitions.seconds` (2 secondes par défaut).
Cette méthode change le gain entre segments sans redémarrer la chanson entière.
Elle augmente la taille du pack et le nombre de paquets audio envoyés. Les sons
vanilla et les anciens morceaux sans segments gardent une lecture classique ;
le navigateur les distingue. Le fondu est validé par les paquets et leurs gains,
mais sa restitution perceptive reste à écouter dans un vrai client Minecraft.

Les ambiances de zones, d'événements et la radio personnelle sont départagées
par priorité. Sans ambiance active, le joueur reçoit la radio locale admissible
la plus proche, puis une radio globale de son monde. Cela évite les doublons et l'arrêt mutuel
de plusieurs copies du même son. Le rayon exclut les auditeurs hors de portée ;
le volume envoyé tient compte de l'atténuation Minecraft et du réglage personnel.
Les jukeboxes de chunks déchargés ne forcent pas le chargement du monde.

Les clients ne proposent pas de recherche temporelle dans un son déjà commencé.
Un joueur qui arrive, change de radio ou modifie son volume commence le morceau
courant au début ; il rejoint les transitions de la station au morceau suivant.
Sur les API qui n'exposent aucun arrêt du son, un morceau déjà envoyé doit se
terminer naturellement. Une mise en sourdine empêche les prochaines lectures.

## Construire et vérifier

Avec un JDK récent et Maven : `mvn clean verify`.
Sur Windows : `./build.ps1 -JavaHome C:\chemin\jdk -Maven C:\chemin\mvn.cmd`.
Le bytecode cible Java 8 ; le serveur impose sa propre version Java.
`python tools/bootstrap.py` télécharge Maven et les JDK de test si nécessaire.
Les OGG sont déjà inclus dans les sources ; FFmpeg est nécessaire seulement
pour les régénérer avec `python tools/make_assets.py` ou importer de nouveaux morceaux.
Les tests de protocole demandent aussi Node.js : après `bootstrap.py`, exécuter
`npm install --prefix .tools/protocol`.

```text
python tools/prepare_servers.py --all
python tools/run_matrix.py --all --workers 4
python tools/test_migration.py
python tools/package_release.py
```

Les scripts créent des mondes jetables dans `test-servers/`, lient les serveurs
à `127.0.0.1`, acceptent l'EULA pour ces essais autorisés, vérifient les téléchargements
officiels par SHA-256 et arrêtent les serveurs après les tests.
Le client de protocole vérifie les paquets, pas une restitution sonore humaine.
Voir `docs/COMPATIBILITY.md` et `docs/matrix-results.json` pour les preuves exactes.
Le packaging crée le JAR, une archive d'installation et une archive des sources
complètes dans `dist/`, avec leurs SHA-256. Il vérifie que le JAR correspond bien
au binaire de la matrice réussie. Les dépendances de test, mondes et données
locales d'administration sont exclus des archives.

Licence GPL-2.0-or-later ; crédits et dépendances dans `NOTICE` et `licenses/`.
