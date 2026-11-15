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

The release includes full runtime-library licenses and notices in
[`packaging/licenses/`](packaging/licenses/README.md). These files are installed
alongside the game; they do not rely on duplicate `META-INF` filenames surviving
the combined JAR. The resolved runtime classpath contains:

| Library | Version | License and retained notices |
| --- | --- | --- |
| TUI4J | 0.3.5 | [MIT, including Latte and Bubble Tea attribution](packaging/licenses/tui4j-0.3.5/LICENSE.md) |
| JLine terminal-jni, terminal, native | 3.26.1 | [BSD-3-Clause](packaging/licenses/jline-3.26.1/LICENSE.txt) |
| ICU4J | 76.1 | [Unicode-3.0 and included third-party notices](packaging/licenses/icu4j-76.1/LICENSE.txt) |
| Apache Commons Text | 1.15.0 | [Apache-2.0](packaging/licenses/commons-text-1.15.0/LICENSE.txt), [NOTICE](packaging/licenses/commons-text-1.15.0/NOTICE.txt) |
| Apache Commons Lang | 3.20.0 | [Apache-2.0](packaging/licenses/commons-lang3-3.20.0/LICENSE.txt), [NOTICE](packaging/licenses/commons-lang3-3.20.0/NOTICE.txt) |
| Kotlin standard library | 2.4.21 | [Apache-2.0](packaging/licenses/kotlin-stdlib-2.4.21/LICENSE.txt), [copyright](packaging/licenses/kotlin-stdlib-2.4.21/COPYRIGHT.txt), [third-party attributions](packaging/licenses/kotlin-stdlib-2.4.21/THIRD-PARTY-COPYRIGHTS.txt) and accompanying GWT, Guava, ThreeTen, and Boost licenses |
| JetBrains annotations | 13.0 | [Apache-2.0](packaging/licenses/annotations-13.0/LICENSE.txt), [original source copyright headers](packaging/licenses/annotations-13.0/COPYRIGHT-HEADERS.txt) |

JNA is not part of this game's runtime classpath. The installer provisions
Eclipse Temurin/OpenJDK 25; its legal notices remain in the private JDK’s `legal/`
directory. JDK downloads are pinned by version and verified SHA-256. Build/test
dependencies (Gradle, ktlint, JUnit) are not required to play downloaded app bundles.

`docs/assets-sha256.json` records every migrated file hash.
`src/main/resources/levels.tsv` records exact original geometry, objects and
routes, exported by the desktop edition’s `tools/export_levels.gd`; Godot is not a runtime dependency.
