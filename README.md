# Music Reforged — made by hash014

Réécriture du plugin Music de Zombie_Striker : radios, playlists, jukeboxes,
audio personnalisé et génération autonome du pack. Plugin serveur Bukkit/Spigot/Paper.
Le JAR ne nécessite aucune dépendance de plugin, aucun compte et aucun service payant.

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
du fichier. Le plugin ne convertit pas les MP3 lui-même.
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

Chaque joueur reçoit une seule radio : la radio locale admissible la plus proche,
puis une radio globale de son monde. Cela évite les doublons et l'arrêt mutuel
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
pour les régénérer avec `python tools/make_assets.py`.

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

Licence GPL-2.0-or-later ; crédits et dépendances dans `NOTICE` et `licenses/`.
