# Bundled runtime licenses

These notices accompany the classes included in `build/libs/emberwood-tui.jar`.
The release archive and installer copy this entire directory. The dependency
inventory comes from `./gradlew dependencies --configuration runtimeClasspath`:

| Maven coordinates | License directory |
| --- | --- |
| `com.williamcallahan:tui4j:0.3.5` | [tui4j-0.3.5](tui4j-0.3.5/) |
| `org.jline:jline-terminal-jni:3.26.1` | [jline-3.26.1](jline-3.26.1/) |
| `org.jline:jline-terminal:3.26.1` | [jline-3.26.1](jline-3.26.1/) |
| `org.jline:jline-native:3.26.1` | [jline-3.26.1](jline-3.26.1/) |
| `com.ibm.icu:icu4j:76.1` | [icu4j-76.1](icu4j-76.1/) |
| `org.apache.commons:commons-text:1.15.0` | [commons-text-1.15.0](commons-text-1.15.0/) |
| `org.apache.commons:commons-lang3:3.20.0` | [commons-lang3-3.20.0](commons-lang3-3.20.0/) |
| `org.jetbrains.kotlin:kotlin-stdlib:2.4.21` | [kotlin-stdlib-2.4.21](kotlin-stdlib-2.4.21/) |
| `org.jetbrains:annotations:13.0` | [annotations-13.0](annotations-13.0/) |

[`sources.json`](sources.json) records the authoritative source and SHA-256 for
every retained file. Apache Commons license and notice files were extracted
unchanged from the resolved Maven JARs. TUI4J, JLine, ICU, and Kotlin files were
downloaded unchanged from the matching upstream release tags. ICU's complete
license file includes its third-party notices. Kotlin's standard-library
directory also includes the GWT, Guava, ThreeTen, and Boost notices identified by
its upstream license inventory. Its upstream `NOTICE.txt` names the compiler
distribution; the game only bundles the standard-library artifact.

The annotations 13.0 JAR and source JAR contain no separate license file. Its
published POM specifies Apache-2.0, reproduced from the Apache Software
Foundation. Copyright/license headers from the matching source JAR are retained
verbatim in `COPYRIGHT-HEADERS.txt`, with source filenames identifying each block.

Original game artwork and fonts retain their separate credits under
`src/main/resources/assets/`. The separately downloaded Temurin JDK retains its
own `legal/` directory.
