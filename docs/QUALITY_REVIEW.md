# Review against recent session guidance

Reviewed November 15, 2026 against the published v1.0.0 game and the relevant
local Codex sessions from the preceding hours: original Emberwood and its Kotlin
port, Turbo Tides and its terminal port, and Phippy: Memory Rescue. Read the actual
user instructions and relevant review findings. This document records applicable
requirements; private conversations and unrelated personal details are not
included in the repository. Another project's test result is not evidence here.

## Requirements and evidence

| Recent guidance | Emberwood Terminal evidence and follow-up |
| --- | --- |
| Watch every command's actual effect; especially screen-left and screen-right | [169-case control inventory](CONTROLS.md), 468 inspected captures, alternate bindings and state-specific disabled commands. The review rehashed all captures and the installed JAR. Terminal key-release limitations are explicit. |
| Test physics, collision geometry, progression, failure and retry | 39 regression tests; all twenty trails, six guardians, sixty acorns and twenty fireflies completed through real ANSI input. The physical campaign includes deliberate crumble death, checkpoint recovery, moving-platform carry, mushroom bounce and guardian phases. [Verification](VERIFICATION.md). |
| Repeated independent adversarial review | Separate gameplay/physics, control/onboarding and distribution reviews. The follow-up traced simulation, projectiles, guardians, equipment, input states, camera and rendered evidence; it found no additional gameplay defect. This is a bounded review, not a claim that no possible bug remains. |
| Preserve the originals, assets and deliberate visual design | The source games remain untouched; all 2,107 imported assets and authoritative level geometry retain their hashes and licenses. Original fox/forest art and icon remain recognizable. [Source audit](SOURCE_AUDIT.md), [credits](../THIRD_PARTY.md). Removed chests, shields and companion stay absent. |
| Make first installation work without manual dependency management | The baseline installed Java automatically but merely reported missing OS tools. This was a concrete gap in the requested onboarding; the distribution follow-up addresses missing utilities, certificates, native libraries, fonts and desktop-terminal availability. [Installation](PACKAGING.md). |
| Provide an offline command and a useful application icon | Actual installed command and GIO desktop launch, 120×40 terminal request, valid desktop entry and original fox icon. The follow-up inspected the icon at 512 and 32 pixels and rechecked installed help/version. macOS bundle generation and installation pass CI; native Finder/Terminal interaction remains a separate limit. |
| Test an entry point's consumer, not just its configuration file | Zed actually loaded all three tasks and launched the game. Its default dock was only 130×14, correctly producing the resize prompt; the Play task now requests the center pane to remove that extra first-use step. |
| Check exit, interrupted input, updates and persistence | Peer-inspired checks reproduced two baseline defects: Ctrl+Z stopped rendering without a clean suspend, and JLine changed native terminal control/baud bits on exit. The follow-up filters suspend into a safe pause and preserves/restores the full native terminal state. Additional physical cases verify the fix. Live installed-game replacement, repair and preference checks supplement earlier rollback tests. |
| Observe low-resource behavior and large windows | Existing measured 100×36 gameplay, idle and pause CPU/RSS/paint rates are documented. The follow-up adds a one-CPU, 96 MiB heap probe and a maximum-size terminal measurement; a configured heap or tick rate alone is not performance evidence. |
| Keep instructions short, accurate and reproducible | README begins with installation and play, explains latched steering and in-memory campaign progress, and links detailed evidence and limits. Source, installed command, practice levels, editor tasks and application menu share the same game. |
| Track and publish the actual reviewed result | Public source, installer, portable release, checksums and raw evidence are attached to GitHub. Changes to onboarding must be packaged and tested through the installed entry point as well. |

## Dependency freshness

Rechecked the official sources on November 15:
[Kotlin 2.4.21](https://kotlinlang.org/docs/releases.html),
[Gradle 9.9.1](https://services.gradle.org/versions/current),
[tui4j 0.3.5](https://repo.maven.apache.org/maven2/com/williamcallahan/tui4j/maven-metadata.xml),
[ktlint plugin 14.2.0](https://plugins.gradle.org/m2/org/jlleitschuh/gradle/ktlint/org.jlleitschuh.gradle.ktlint.gradle.plugin/maven-metadata.xml),
and [Temurin 25.0.5+8](https://github.com/adoptium/temurin25-binaries/releases/tag/jdk-25.0.5%2B8).
The project retains the requested Java major version 25.

Kotlin's [compatibility table](https://kotlinlang.org/docs/gradle-configure-project.html)
lists Gradle 9.7.0 as the upper fully supported version for Kotlin 2.4.21 and
allows newer releases with caveats. The requested current Gradle 9.9.1 combination
passes the project's actual checks; that does not extend the vendor's support
statement. The release metadata is retained in `build/quality-review/dependencies.json`.

## Evidence boundaries

Before the terminal-lifecycle fix, ten-second samples of the v1.0.0 game at
100×36 with a 96 MiB heap and one actual CPU affinity produced 28.70 active
redraws/second, 38% of one CPU and 105.7 MiB sampled RSS. At 180×65 with the normal
256 MiB heap, active output measured 28.50 redraws/second, 62.9% CPU and 182.9 MiB
RSS. Title and pause produced no unchanged-screen redraws. Both runs performed
real steering, stopping, jumping, pause and Q; their complete ANSI frames were
inspected. These short loaded-host samples are neither a full campaign under
memory pressure nor a performance guarantee. Raw measurements are in
`build/quality-review/performance-constrained` and `performance-large`.

Model tests for every weapon/species combination supplement physical firing and
combat evidence; they are not presented as a physical test of every possible
combination. Full collection of all 378 optional gems, every timing combination,
physical speaker listening and native macOS GUI interaction remain unclaimed.
Unsupported controller, touch and mouse play are not silently counted as tested.

The [verification record](VERIFICATION.md) identifies the executable for each
completed audit. The [game checklist](GAME_CHECKLIST.md) remains the maintained
checklist for mechanics and presentation.
