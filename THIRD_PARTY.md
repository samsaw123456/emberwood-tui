# Code and artwork credits

Emberwood Terminal is a terminal adaptation of the Kotlin/JVM port of [Emberwood](https://github.com/samsaw123456/emberwood),
originally built from [Phippy Gaming Workshop](https://github.com/Tingsters/Phippy-Gaming-Workshop).
The game code retains Apache-2.0 licensing; see LICENSE. This independent repository
uses the original assets byte for byte from Emberwood commit
`7146ac838c0db21afe9406ea4546cb094aa9721f`.

Artwork retains its own original licenses. The full source catalogs, author
credits and license texts are bundled under `src/main/resources/assets/`:
`CREDITS.md`, `sources.json`, `licenses/`, and `fonts/LICENSE.txt`. No artwork was
regenerated. In particular SunnyLand supplies the fox, terrain, forest sprites,
collectibles and several animals. DejaVu Sans remains bundled for the offscreen artwork renderer; terminal controls use the terminal’s font.

Kotlin standard library: JetBrains, Apache-2.0. The installer provisions
Eclipse Temurin/OpenJDK 25; its legal notices remain in the private JDK’s `legal/` directory.
JDK downloads are pinned by version and verified SHA-256. Build/test dependencies
(Gradle, ktlint, JUnit) are not required to play downloaded app bundles.

`docs/assets-sha256.json` records every migrated file hash.
`src/main/resources/levels.tsv` records exact original geometry, objects and
routes, exported by the desktop edition’s `tools/export_levels.gd`; Godot is not a runtime dependency.

TUI4J 0.3.5, William Callahan and contributors: MIT, based on Latte/Bubble Tea.
See https://github.com/WilliamAGH/tui4j/blob/main/LICENSE.md.
Transitive runtime libraries retain notices inside the distribution JARs, including
JLine (BSD), JNA (Apache-2.0/LGPL), ICU4J (Unicode), and Apache Commons (Apache-2.0).
