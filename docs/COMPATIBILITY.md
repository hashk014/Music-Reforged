# Compatibilité vérifiée — Music Reforged 1.0.0

Le 11 octobre 2026, le JAR ci-dessous a passé les essais sur les **54 versions
Paper disponibles dans le service officiel de téléchargements**, de 1.8.8 à 26.3.
Chaque ligne correspond à deux démarrages réels : installation et redémarrage
avec les données conservées. Un joueur de protocole rejoint chaque serveur.
Les serveurs de test sont isolés, en mode hors ligne et accessibles seulement
sur la boucle locale. Ils ne sont pas destinés à être utilisés en production.

```text
SHA-256 du JAR : 3b73736bec507f2968861acd621211fe3a62cf33c81a9ef6252d63c02211b846
```

## Preuves et portée

- **3 024 assertions** dans la matrice : 26 contrôles serveur, 24 contrôles joueur
  et 6 contrôles après redémarrage, sur chacune des 54 versions.
- Import effectif de MP3 et d'OGG avec FFmpeg, préécoute OGG, pack généré contenant
  les fichiers importés, authentification HTTP et rejet d'une requête sans accès.
- Playlists avec ordre conservé, zones, règles d'événements et disques personnalisés.
- Interaction en jeu avec la baguette et les jukeboxes, droits de propriété,
  transitions et réception des paquets audio et de proposition du pack.
- 9 tests unitaires Maven réussis ; migration historique vérifiée au démarrage
  puis après redémarrage, sans modification du dossier Music original, y compris
  les références à un monde déchargé.
- Essai supplémentaire de l'interface dans le navigateur : création d'une
  playlist, ajout d'un morceau, choix d'un MP3 et import terminé. Aucune erreur
  JavaScript observée ; captures dans `admin-import.jpg` et `admin-playlists.jpg`.

Les détails bruts sont dans [matrix-results.json](matrix-results.json),
[migration-results.json](migration-results.json) et [server-builds.json](server-builds.json).
Le dernier fichier identifie chaque binaire Paper par son SHA-256 et son URL
officielle. Le rapport `base-matrix-results.json` est un essai antérieur de la
réécriture avant l'ajout de l'administration étendue ; il ne remplace pas le
rapport final ci-dessus.

## Limites précises

Ce sont les versions listées ci-dessous qui ont été exécutées. Certaines versions
Minecraft intermédiaires n'ont aucun binaire Paper proposé par ce service ; leur
compatibilité n'est donc pas démontrée séparément. Le build **26.3-174, canal BETA**,
a été testé ; ce résultat ne certifie pas tout futur build 26.3.

Le plugin cible l'API Bukkit/Spigot Java 8. La validation d'exécution porte sur
Paper ; des distributions Spigot ou CraftBukkit distinctes n'ont pas été démarrées.
Ce JAR est un plugin serveur, et ne s'installe pas comme mod Fabric ou Forge.
Folia n'est pas pris en charge.

Les paquets, les gains et les fichiers audio ont été contrôlés, mais aucun vrai
client Minecraft n'a été utilisé pour écouter les fondus. Leur continuité et
leur rendu perceptif restent à vérifier à l'oreille. Les musiques importées et
les trois boucles originales utilisent des segments de 250 ms pour faire varier
le volume progressivement ; les sons vanilla et anciens fichiers sans segments
gardent une lecture classique. Sur 1.8.8 et 1.9.4, l'API n'expose pas d'arrêt :
un son déjà envoyé se termine naturellement. Les autres versions testées ont
transmis les paquets d'arrêt attendus.

Le pack cible la version du serveur (97.1 pour 26.3). Des clients d'une autre
version via ViaVersion, l'intégration à d'autres packs et les protections d'autres
plugins nécessitent des essais propres au serveur concerné. Les tests de droit
de propriété locaux ne constituent pas un test de toutes les intégrations tierces.

FFmpeg doit être installé sur la machine du serveur pour les imports MP3/OGG.
Le JAR inclut les morceaux par défaut et génère leur pack sans FFmpeg.

## Matrice exécutée

| Minecraft / Paper | Build | Canal | Java utilisé | Assertions | Résultat |
|---|---:|---|---:|---:|---|
| 1.8.8 | 445 | STABLE | 8 | 56 | Réussi |
| 1.9.4 | 775 | STABLE | 8 | 56 | Réussi |
| 1.10.2 | 918 | STABLE | 8 | 56 | Réussi |
| 1.11.2 | 1106 | STABLE | 8 | 56 | Réussi |
| 1.12 | 1169 | STABLE | 8 | 56 | Réussi |
| 1.12.1 | 1204 | STABLE | 8 | 56 | Réussi |
| 1.12.2 | 1620 | STABLE | 8 | 56 | Réussi |
| 1.13 | 173 | STABLE | 8 | 56 | Réussi |
| 1.13.1 | 386 | STABLE | 8 | 56 | Réussi |
| 1.13.2 | 657 | STABLE | 8 | 56 | Réussi |
| 1.14 | 17 | STABLE | 8 | 56 | Réussi |
| 1.14.1 | 50 | STABLE | 8 | 56 | Réussi |
| 1.14.2 | 107 | STABLE | 8 | 56 | Réussi |
| 1.14.3 | 134 | STABLE | 8 | 56 | Réussi |
| 1.14.4 | 245 | STABLE | 8 | 56 | Réussi |
| 1.15 | 21 | STABLE | 8 | 56 | Réussi |
| 1.15.1 | 62 | STABLE | 8 | 56 | Réussi |
| 1.15.2 | 393 | STABLE | 8 | 56 | Réussi |
| 1.16.1 | 138 | STABLE | 8 | 56 | Réussi |
| 1.16.2 | 189 | STABLE | 8 | 56 | Réussi |
| 1.16.3 | 253 | STABLE | 8 | 56 | Réussi |
| 1.16.4 | 416 | STABLE | 8 | 56 | Réussi |
| 1.16.5 | 794 | STABLE | 8 | 56 | Réussi |
| 1.17 | 79 | STABLE | 16 | 56 | Réussi |
| 1.17.1 | 411 | STABLE | 17 | 56 | Réussi |
| 1.18 | 66 | ALPHA | 17 | 56 | Réussi |
| 1.18.1 | 216 | STABLE | 17 | 56 | Réussi |
| 1.18.2 | 388 | STABLE | 17 | 56 | Réussi |
| 1.19 | 81 | STABLE | 17 | 56 | Réussi |
| 1.19.1 | 111 | STABLE | 17 | 56 | Réussi |
| 1.19.2 | 307 | STABLE | 17 | 56 | Réussi |
| 1.19.3 | 448 | STABLE | 17 | 56 | Réussi |
| 1.19.4 | 550 | STABLE | 17 | 56 | Réussi |
| 1.20 | 17 | STABLE | 17 | 56 | Réussi |
| 1.20.1 | 196 | STABLE | 17 | 56 | Réussi |
| 1.20.2 | 318 | STABLE | 17 | 56 | Réussi |
| 1.20.4 | 499 | STABLE | 17 | 56 | Réussi |
| 1.20.5 | 22 | ALPHA | 21 | 56 | Réussi |
| 1.20.6 | 151 | STABLE | 21 | 56 | Réussi |
| 1.21 | 130 | STABLE | 21 | 56 | Réussi |
| 1.21.1 | 133 | STABLE | 21 | 56 | Réussi |
| 1.21.3 | 83 | STABLE | 21 | 56 | Réussi |
| 1.21.4 | 232 | STABLE | 21 | 56 | Réussi |
| 1.21.5 | 114 | ALPHA | 21 | 56 | Réussi |
| 1.21.6 | 48 | STABLE | 21 | 56 | Réussi |
| 1.21.7 | 32 | STABLE | 21 | 56 | Réussi |
| 1.21.8 | 60 | STABLE | 21 | 56 | Réussi |
| 1.21.9 | 59 | ALPHA | 21 | 56 | Réussi |
| 1.21.10 | 130 | STABLE | 21 | 56 | Réussi |
| 1.21.11 | 132 | STABLE | 21 | 56 | Réussi |
| 26.1.1 | 29 | ALPHA | 25 | 56 | Réussi |
| 26.1.2 | 74 | STABLE | 25 | 56 | Réussi |
| 26.2 | 133 | STABLE | 25 | 56 | Réussi |
| 26.3 | 174 | BETA | 25 | 56 | Réussi |

## Reproduire

Depuis Windows, avec Python 3.11+, Node.js, FFmpeg et les outils installés :

```text
python tools/bootstrap.py
npm install --prefix .tools/protocol
./build.ps1
python tools/prepare_servers.py --all
python tools/run_matrix.py --all --workers 4
python tools/test_migration.py
python tools/package_release.py
```

Les versions historiques peuvent nécessiter leurs propres JDK, récupérés par
`bootstrap.py`. Le test accepte l'EULA dans ses dossiers jetables `test-servers/`.
Il télécharge des binaires officiels vérifiés et ferme ses serveurs en fin d'essai.
La matrice exige des téléchargements réseau et plusieurs gigaoctets de disque.
Les dépendances Maven proviennent des dépôts officiels déclarés dans `pom.xml`.
Le script de packaging refuse un JAR différent de celui identifié par la matrice.
