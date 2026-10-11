# CurseForge publication kit

The description, visual assets and metadata in this folder are prepared for
publication. They do not create or publish a CurseForge project automatically.

## Project fields

| Field | Prepared value |
|---|---|
| Name | Music Reforged |
| Summary | Custom music, playlists, zones, event soundtracks and discs, with MP3/OGG imports and automatic resource packs. |
| Game | Minecraft |
| Project type | Bukkit Plugins / server plugin |
| Author | hash014 |
| License | GNU GPL version 2 or later, matching the repository's GPL-2.0-or-later license |
| Source | https://github.com/hashk014/Music-Reforged/tree/codex/music-reforged |
| Issues | https://github.com/hashk014/Music-Reforged/issues |
| Description | Paste CURSEFORGE-PAGE.md into the Markdown description editor |
| Main file | Music-Reforged-1.0.0.jar from the verified dist/ folder |

Keep the source URL pointed at the Reforged branch while its pull request is
open. Update it to the default branch after merging if desired. Choose actual
game-version tags supported by your uploaded file; the precise validation scope
is documented in the description and repository reports.

## Media files

| File | Usage | Suggested caption |
|---|---|---|
| media/logo.png | Square project avatar; original transparent PNG | Music Reforged logo |
| media/banner.png | Description header and promotional media | Music Reforged — made by hash014 |
| media/admin-library.jpg | Gallery: real interface screenshot | Import MP3/OGG audio, preview tracks and generate the resource pack. |
| media/admin-playlists.jpg | Gallery: real interface screenshot | Build playlists, reorder tracks and control the radio. |

The logo is an original generated graphic, not a copied Minecraft or CurseForge
logo. The banner is promotional artwork; the two administration images are real
screenshots. These assets do not claim to be in-game gameplay screenshots.

Upload the logo in the project's Logo field and the other images through the
project's Images area. The Markdown description uses publicly accessible image
URLs from this repository. You may replace them with CurseForge-hosted image
URLs after uploading to keep the published page independent of GitHub.

The square PNG meets CurseForge's documented minimum of 400 × 400 pixels;
larger square images are accepted and downscaled. The media manifest records
the exact saved dimensions and SHA-256 checksums.

## Installation text and dependencies

The description includes installation, resource pack distribution, FFmpeg for
imports, permissions, zones, discs, events and migration. There is no mandatory
dependency on another Minecraft plugin. FFmpeg is an external executable on the
server, rather than a plugin dependency.

The private MP3 imported on the local test server is not included in this media
kit, the plugin JAR or the sources. Local world files, credentials and temporary
administration links are excluded as well.

## Asset provenance

Logo and banner: generated with the built-in image_gen tool. Exact prompts are
saved in media/PROMPTS.md. Administration screenshots: captured from the running
Music Reforged administration interface during local tests. Existing credit
and licensing information for the plugin is preserved.

## Official publishing references

- [Project submission guide and tips](https://support.curseforge.com/support/solutions/articles/9000199552-project-submission-guide-and-tips): project fields, square PNG logo requirements, Markdown, screenshots and licensing.
- [Supported description HTML and CSS](https://support.curseforge.com/support/solutions/articles/9000284625-supported-html-tags-css-for-project-descriptions): supported images, headings, tables and code blocks.
