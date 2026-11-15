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
| Watch every command's actual effect; especially screen-left and screen-right | [177-case control inventory](CONTROLS.md), 506 inspected captures, alternate bindings and state-specific disabled commands. The review rehashed all captures and the installed JAR. Terminal key-release limitations are explicit. |
| Test physics, collision geometry, progression, failure and retry | 40 regression tests; all twenty trails, six guardians, sixty acorns and twenty fireflies completed through real ANSI input. The physical campaign includes deliberate crumble death, checkpoint recovery, moving-platform carry, mushroom bounce and guardian phases. [Verification](VERIFICATION.md). |
| Repeated independent adversarial review | Separate gameplay/physics, control/onboarding and distribution reviews. The follow-up traced simulation, projectiles, guardians, equipment, input states, camera and rendered evidence; it found no additional gameplay defect. This is a bounded review, not a claim that no possible bug remains. |
| Preserve the originals, assets and deliberate visual design | The source games remain untouched; all 2,107 imported assets and authoritative level geometry retain their hashes and licenses. Original fox/forest art and icon remain recognizable. [Source audit](SOURCE_AUDIT.md), [credits](../THIRD_PARTY.md). Removed chests, shields and companion stay absent. |
| Make first installation work without manual dependency management | The baseline installed Java automatically but merely reported missing OS tools. This was a concrete gap in the requested onboarding; the distribution follow-up addresses missing utilities, certificates, native libraries, fonts and desktop-terminal availability. [Installation](PACKAGING.md). |
| Provide an offline command and a useful application icon | Actual installed command and GIO desktop launch, 120×40 terminal request, valid desktop entry and original fox icon. The follow-up inspected the icon at 512 and 32 pixels and rechecked installed help/version. macOS bundle generation and installation pass CI; native Finder/Terminal interaction remains a separate limit. |
| Test an entry point's consumer, not just its configuration file | Zed actually loaded all three tasks and launched the game. Its default dock was only 130×14, correctly producing the resize prompt; the Play task now requests the center pane to remove that extra first-use step. |
| Check exit, interrupted input, updates and persistence | Peer-inspired checks reproduced two baseline defects: Ctrl+Z stopped rendering without a clean suspend, and JLine changed native terminal control/baud bits on exit. The follow-up filters suspend into a safe pause, prevents interrupt-time output flushing while the game runs, and preserves/restores the full native terminal state. Additional physical cases verify the fix. Live installed-game replacement, repair and preference checks supplement earlier rollback tests. |
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

## Installation, editor and upgrade results

The final v1.0.1 executable is
`10220ad3607d6f947b4cf8ae017589f532a5b53ccd409e5d225b7488f859d5a2`.
All 40 tests and Kotlin lint checks passed. Byte comparison with v1.0.0 confirms
unchanged physics, equipment, artwork, level and terminal-input classes. The
changed application classes implement terminal lifecycle, the safe Ctrl+Z pause
and version reporting; `Tick` has unchanged disassembled instructions.

- Twenty-one installer checks pass, including missing-tool package-manager paths,
  noninteractive apt input, offline checks, SIGTERM staging cleanup, departed-PID
  lock recovery, active-lock rejection, version validation, all seventeen notices,
  saved preferences and invalid-runtime rollback. The installed public game also
  exercises Ctrl+Z, visible mute recovery and exact terminal restoration.
- Twenty installed CLI/PTY cases pass on the final executable: help, version,
  offline/desktop options, invalid arguments, and actual default/practice 1, 12
  and 20 launches. All twelve captures were inspected and all four game sessions
  exited normally with terminal modes restored.
- A real minimal Ubuntu 24.04.5 image initially lacked downloaders, HTTPS roots,
  fontconfig, fonts, ALSA and Java. The local release installed in 90.236 seconds
  using real authenticated apt transactions and a fresh private Java download.
  Installed play then passed with networking disabled: title, start, steering,
  stop, Ctrl+Z pause, resume, Q and terminal cleanup. All captured frames were
  inspected. The launcher entry and original icon matched. No host packages
  changed. The disposable user namespace maps its optional `staff`/`adm` groups
  to its only available GID and runs apt's download sandbox as its mapped root;
  these are documented test-environment adaptations, not installer behavior.
- An actual installed v1.0.0 process kept playing while the v1.0.1 installer
  replaced its application directory and JAR inode. Jump, projectile, pause,
  resume and quit still worked. A corrupt installed JAR was repaired; a corrupt
  update candidate was rejected without replacing the healthy installation.
  A new offline process reported v1.0.1 and loaded both saved preferences.
- Fresh Zed 1.28.1 loaded all three tasks. Play opened a 130×41 center terminal,
  downloaded Java into an empty private cache, and accepted actual start,
  movement, stop, pause/resume and Q commands. Twenty native captures were
  inspected; the user's editor preferences were not changed.
- Packaged installations include the Zed tasks and project instructions. The
  installed help accurately lists Up/Down/Tab for equipment selection; W/S are
  gameplay controls and do not navigate equipment. The fixture checks the actual
  installed task objects and help output.
- Isolated GIO launches established that several installed terminal applications
  alone are not discovered for a `Terminal=true` desktop entry. The prerequisite
  check now requires a recognized desktop terminal or installs XTerm as a
  fallback. Existing terminal preferences were not changed.

Reproduce the optional minimal Linux check after packaging:

```sh
python3 tools/minimal_install_audit.py --archive dist/emberwood-tui-1.0.1.tar.gz
```

This requires bubblewrap and the normal Python audit dependencies on the Linux
x86-64 audit host. The checked official Ubuntu Base archive is cached under
`build/quality-review`; the disposable system is removed after the run. Raw logs,
complete-paint captures and checksums remain alongside the result. Native macOS
and non-apt package-manager execution are not established by this Linux run;
the other package-manager branches use isolated fixtures.

## Terminal lifecycle regression

The public-shell follow-up reproduced failures that the earlier escape-sequence
cleanup checks did not establish: native terminal control/baud bits changed on
exit, and Ctrl+Z could stop rendering without handing control back to the shell.
The first correction restored native state and mapped suspend to pause. A
repeated Ctrl+Z → resume → Ctrl+C probe then exposed interrupted repaint writes:
the renderer was stuck in native output while exit waited for its lock. Direct
PTY draining and output restart did not recover it. Disabling signal-time queue
flushing resolved the reproduced cases.

Production and the fixture host now use the same `TerminalSession`. It captures
the complete native termios structure, enables `NOFLSH` only during the session,
maps suspend to a non-toggling pause, and restores the original structure after
tui4j closes. Platform values are documented by the
[Linux header](https://github.com/torvalds/linux/blob/master/include/uapi/asm-generic/termbits.h)
and [Darwin header](https://github.com/apple-oss-distributions/xnu/blob/main/bsd/sys/termios.h).

On the final executable, **20 of 20 public-shell cases passed**: source and
installed commands, Ctrl+Z starting from title/playing/paused states, recovery
into gameplay, Q and Ctrl+C, and eight additional repeated interrupt-during-repaint
cases with default and custom IXOFF/erase settings. The full control inventory
separately tests exit in every game state. Each requires exit status zero, exact original
terminal attributes, intact UTF-8, restored primary screen and execution of a
subsequent shell command. All 100 before/during/after frames are retained.

```sh
python3 tools/terminal_lifecycle_audit.py \
  --installed-launcher "$HOME/.local/bin/emberwood-tui"
```

The default runs ten source-entry cases; CI runs these on all four platforms.
An earlier probe overlapped the source package build and timed out waiting for
the launcher lock before Java started. That interference is retained separately;
the clean twenty-case run is `build/quality-review/terminal-final-clean`.

## Cross-platform audit corrections

The CI rechecks also exposed measurement errors in the audit helpers. The Intel
macOS menu test sampled the preceding completed paint after its final Tab key:
the original ANSI stream shows Sunleaf armor at offset 128270 and the correct
wrapped Bow selection at offset 129301. The corrected test retains thirteen
rapid presses, requires a visible intermediate selection, and waits for the
required completed wrap paint. All seven supplemental local captures were
inspected and replayed exactly from raw ANSI; the original 177-case, 506-frame
local inventory remains intact.

On macOS ARM, the installed game exited successfully, but the test's subsequent
`tcgetattr` failed because the controlling session had ended. The installation
probe now keeps its Bash session owner alive until after exact native terminal
comparison. A deliberate negative probe ran the real game, then disabled echo
before exiting successfully; the test correctly rejected the changed attributes.
The shell therefore does not conceal a restoration failure. The separate ten-case
native lifecycle audit had already passed on that runner.

Earlier literal Ctrl+Z setup also differed between Linux and Darwin. The fixture
now disables the suspend control character on its own child terminal before Java
starts; its parser-acknowledgement requirement remains distinct from the seven
ordinary Ctrl+Z signal cases. Failed CI logs, original frames, exact replay and
successful targeted rechecks are retained under `build/quality-review/ci-final`
and `menu-wrap-final`. These audit corrections do not change the tested game JAR.

## Performance and evidence boundaries

Before the terminal-lifecycle fix, ten-second samples of the v1.0.0 game at
100×36 with a 96 MiB heap and one actual CPU affinity produced 28.70 active
redraws/second, 38% of one CPU and 105.7 MiB sampled RSS. At 180×65 with the normal
256 MiB heap, active output measured 28.50 redraws/second, 62.9% CPU and 182.9 MiB
RSS. Title and pause produced no unchanged-screen redraws. Both runs performed
real steering, stopping, jumping, pause and Q; their complete ANSI frames were
inspected. These short loaded-host samples are neither a full campaign under
memory pressure nor a performance guarantee. Raw measurements are in
`build/quality-review/performance-constrained` and `performance-large`.

A fresh eight-second-per-state check on the final executable used the same
100×36, one-CPU, 96 MiB heap limits while other projects were building on this
shared host. It produced 23.62 active redraws/second, 67.1% of one CPU and
101.8 MiB sampled RSS, with no unchanged-screen redraws in title or pause. It
also completed steering, stopping, jumping, pause and clean quit. These results
are retained in `build/quality-review/performance-final`; the lower frame rate
under changing host load is not attributed to a code change.

Model tests for every weapon/species combination supplement physical firing and
combat evidence; they are not presented as a physical test of every possible
combination. Full collection of all 378 optional gems, every timing combination,
physical speaker listening and native macOS GUI interaction remain unclaimed.
Unsupported controller, touch and mouse play are not silently counted as tested.

The [verification record](VERIFICATION.md) identifies the executable for each
completed audit. The [game checklist](GAME_CHECKLIST.md) remains the maintained
checklist for mechanics and presentation.
