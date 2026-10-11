# Verified compatibility — Music Reforged 1.0.0

On October 11, 2026, the feature-complete build passed tests on the **54 Paper
versions available from the official download service**, from 1.8.8 to 26.3.
Each row represents two real boots: installation and restart with persisted data.
A protocol player connects to each server. Test servers are isolated, run in
offline mode and are accessible only through loopback; they are not production servers.

```text
Feature-complete build SHA-256: 3b73736bec507f2968861acd621211fe3a62cf33c81a9ef6252d63c02211b846
```

## English translation regression checks

The English translation changes displayed text and documentation, without
changing command identifiers, permission nodes, saved configuration keys or
user-defined names. The translated JAR is tested again on **1.8.8, 1.12.2,
1.21.11 and 26.3**, using the same server, player, import, pack and restart checks.
Its exact SHA-256 and individual results are recorded in
[english-matrix-results.json](english-matrix-results.json). The 54-version report
below refers to the earlier feature-complete JAR, not to the translated binary.

## Evidence and scope

The subsequent clickable administration link fix is covered by
[admin-link-matrix-results.json](admin-link-matrix-results.json). All four
regression versions passed the same 224 assertions and additionally delivered
an actual OPEN_URL click event to the protocol client. The server's component
API translates the event into the appropriate legacy JSON or modern component
format. Console output remains a plain, copyable URL. This report identifies
the latest distributed JAR; the English translation report identifies the prior build.

- **3,024 assertions** in the full matrix: 26 server checks, 24 player checks
  and 6 restart checks on each of 54 versions.
- Actual MP3 and OGG import using FFmpeg, OGG previews, generated packs containing
  imported files, HTTP authentication and rejection of unauthenticated requests.
- Playlists with persistent order, zones, event rules and custom discs.
- In-game wand and jukebox interactions, ownership permissions, transitions,
  sound packets and resource pack offer packets.
- 9 passing Maven unit tests; legacy migration checked on first boot and restart,
  without modifying the original Music folder, including references to unloaded worlds.
- Additional browser administration test: playlist creation, track addition and
  completed MP3 import. Screenshots are in `admin-import.jpg` and `admin-playlists.jpg`.

Raw reports are available in [matrix-results.json](matrix-results.json),
[migration-results.json](migration-results.json) and [server-builds.json](server-builds.json).
The last file identifies each Paper binary by SHA-256 and official URL.
`base-matrix-results.json` records an earlier rewrite test before the extended
administration features; it does not replace the full feature report.

## Specific limitations

Only the versions listed below were executed. Some intermediate Minecraft
versions have no Paper binary offered by this service, so compatibility with
them has not been demonstrated separately. **26.3-174, BETA channel**, was tested;
this does not certify every future 26.3 build.

The plugin targets the Bukkit/Spigot Java 8 API. Runtime validation uses Paper;
separate Spigot or CraftBukkit distributions were not booted. This JAR is a server
plugin and cannot be installed as a Fabric or Forge mod. Folia is not supported.

Packets, gains and audio files were checked, but no real Minecraft client was
used to listen to the fades. Continuity and perceptual quality still need a
listening test. Imported tracks and the three original loops use 250 ms segments
for progressive gain changes; vanilla sounds and legacy tracks without segments
use classic playback. On 1.8.8 and 1.9.4, the API provides no sound stopping:
already sent sounds finish naturally. Other tested versions transmitted the
expected stop packets.

The pack targets the server version (97.1 for 26.3). ViaVersion clients using a
different version, integration with other packs and third-party protection
plugins require tests for the specific server. Local ownership checks do not
constitute validation of all third-party integrations.

FFmpeg must be installed on the server machine for MP3/OGG imports. The JAR
includes default tracks and generates their pack without FFmpeg.

## Full feature matrix

| Minecraft / Paper | Build | Channel | Java used | Assertions | Result |
|---|---:|---|---:|---:|---|
| 1.8.8 | 445 | STABLE | 8 | 56 | Passed |
| 1.9.4 | 775 | STABLE | 8 | 56 | Passed |
| 1.10.2 | 918 | STABLE | 8 | 56 | Passed |
| 1.11.2 | 1106 | STABLE | 8 | 56 | Passed |
| 1.12 | 1169 | STABLE | 8 | 56 | Passed |
| 1.12.1 | 1204 | STABLE | 8 | 56 | Passed |
| 1.12.2 | 1620 | STABLE | 8 | 56 | Passed |
| 1.13 | 173 | STABLE | 8 | 56 | Passed |
| 1.13.1 | 386 | STABLE | 8 | 56 | Passed |
| 1.13.2 | 657 | STABLE | 8 | 56 | Passed |
| 1.14 | 17 | STABLE | 8 | 56 | Passed |
| 1.14.1 | 50 | STABLE | 8 | 56 | Passed |
| 1.14.2 | 107 | STABLE | 8 | 56 | Passed |
| 1.14.3 | 134 | STABLE | 8 | 56 | Passed |
| 1.14.4 | 245 | STABLE | 8 | 56 | Passed |
| 1.15 | 21 | STABLE | 8 | 56 | Passed |
| 1.15.1 | 62 | STABLE | 8 | 56 | Passed |
| 1.15.2 | 393 | STABLE | 8 | 56 | Passed |
| 1.16.1 | 138 | STABLE | 8 | 56 | Passed |
| 1.16.2 | 189 | STABLE | 8 | 56 | Passed |
| 1.16.3 | 253 | STABLE | 8 | 56 | Passed |
| 1.16.4 | 416 | STABLE | 8 | 56 | Passed |
| 1.16.5 | 794 | STABLE | 8 | 56 | Passed |
| 1.17 | 79 | STABLE | 16 | 56 | Passed |
| 1.17.1 | 411 | STABLE | 17 | 56 | Passed |
| 1.18 | 66 | ALPHA | 17 | 56 | Passed |
| 1.18.1 | 216 | STABLE | 17 | 56 | Passed |
| 1.18.2 | 388 | STABLE | 17 | 56 | Passed |
| 1.19 | 81 | STABLE | 17 | 56 | Passed |
| 1.19.1 | 111 | STABLE | 17 | 56 | Passed |
| 1.19.2 | 307 | STABLE | 17 | 56 | Passed |
| 1.19.3 | 448 | STABLE | 17 | 56 | Passed |
| 1.19.4 | 550 | STABLE | 17 | 56 | Passed |
| 1.20 | 17 | STABLE | 17 | 56 | Passed |
| 1.20.1 | 196 | STABLE | 17 | 56 | Passed |
| 1.20.2 | 318 | STABLE | 17 | 56 | Passed |
| 1.20.4 | 499 | STABLE | 17 | 56 | Passed |
| 1.20.5 | 22 | ALPHA | 21 | 56 | Passed |
| 1.20.6 | 151 | STABLE | 21 | 56 | Passed |
| 1.21 | 130 | STABLE | 21 | 56 | Passed |
| 1.21.1 | 133 | STABLE | 21 | 56 | Passed |
| 1.21.3 | 83 | STABLE | 21 | 56 | Passed |
| 1.21.4 | 232 | STABLE | 21 | 56 | Passed |
| 1.21.5 | 114 | ALPHA | 21 | 56 | Passed |
| 1.21.6 | 48 | STABLE | 21 | 56 | Passed |
| 1.21.7 | 32 | STABLE | 21 | 56 | Passed |
| 1.21.8 | 60 | STABLE | 21 | 56 | Passed |
| 1.21.9 | 59 | ALPHA | 21 | 56 | Passed |
| 1.21.10 | 130 | STABLE | 21 | 56 | Passed |
| 1.21.11 | 132 | STABLE | 21 | 56 | Passed |
| 26.1.1 | 29 | ALPHA | 25 | 56 | Passed |
| 26.1.2 | 74 | STABLE | 25 | 56 | Passed |
| 26.2 | 133 | STABLE | 25 | 56 | Passed |
| 26.3 | 174 | BETA | 25 | 56 | Passed |

## Reproducing the tests

On Windows with Python 3.11+, Node.js, FFmpeg and the required tools:

```text
python tools/bootstrap.py
npm install --prefix .tools/protocol
./build.ps1
python tools/prepare_servers.py --all
python tools/run_matrix.py --all --workers 4
python tools/test_migration.py
python tools/package_release.py
```

To repeat the translation regression checks without overwriting the full matrix:

```text
python tools/run_matrix.py 1.8.8 1.12.2 1.21.11 26.3 --workers 4 --report docs/english-matrix-results.json
python tools/package_release.py --report docs/english-matrix-results.json
```

For the latest clickable-link build, use `--report docs/admin-link-matrix-results.json`
for both `run_matrix.py` and `package_release.py` instead.

Historical versions may require different JDKs, downloaded by `bootstrap.py`.
The test accepts the EULA in its disposable `test-servers/` directories, downloads
verified official binaries and closes its servers afterwards. The full matrix
requires network downloads and several gigabytes of disk space. Maven dependencies
come from the official repositories declared in `pom.xml`. Packaging rejects a
JAR whose SHA-256 differs from the selected successful runtime report.
