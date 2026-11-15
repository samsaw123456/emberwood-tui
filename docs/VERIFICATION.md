# Verification record

Verified on Linux x86-64 on November 15, 2026. Model tests, actual terminal
input/output, native rendering, audio output and installation are distinct forms
of evidence; none substitutes for another.

## Completed checks

- `./emberwood-tui --build` and `./gradlew check fatJar` passed with Java 25,
  Kotlin 2.4.21, Gradle 9.9.1 and tui4j 0.3.5. The 39 tests include two complete
  twenty-trail model campaigns, content counts, six guardians, gate rules,
  bounce/collision regressions, every weapon and armor capacity, settings,
  terminal input and camera/palette checks. `check` includes ktlint; compiler
  warnings are errors.
- Both route tests earned every required acorn, every firefly and all equipment.
  The terminal-adapter model route took 462.95 simulation seconds, collected
  196 of 378 optional gems and needed no retries. This is **model evidence**.
- ShellCheck, `bash -n`, Ruff checks and Ruff formatting passed. Fourteen installer checks
  cover fresh installation, unusual paths, invalid options, corrupted runtime
  rejection, simultaneous source launch ordering, offline play, update
  preservation, exact license-file copying and rollback when Java preparation fails.
- An installed public game ran in a PTY. M changed its rendered sound state and
  saved preference; a reinstall retained it and a new process loaded it.
- The actual Linux desktop entry launched through GIO into GNOME Terminal.
  Its PTY measured exactly 120×40 cells after the desktop resize request.
  Native XTerm 407 screenshots separately establish real terminal rendering:
  original art, screen-left/right steering, stopping, jumping, dash, retry,
  pause/resume, equipment navigation, locked-item feedback and clean quit.
- In the installed public game's Lantern Hollow practice run, a physical J
  press fired a lance at a real hopping slime. Native frames show its approach,
  impact, the slime disappearing and the piercing lance continuing onward. The
  fox stayed stationary with unchanged health and armor; no contact or stomp
  supplied the hit. A missed shot and a normal R retry are retained too.
- `tools/audio_audit.py` captured actual Java playback through PipeWire.
  Baseline and muted buffers had peak/RMS zero; enabled and restored buffers
  contained nonzero PCM samples. Exact measurements are retained in the audio
  results JSON. The host speaker's muted state was
  preserved. This proves audio output, not physical-speaker listening.

Production JAR SHA-256 for these terminal checks:
`856921d9a634cae6bb996717a1a1ff2417a66bfe60f90548a35e549e8cb96abf`.

The public entry points passed sixteen help/version/offline/desktop and invalid
argument cases. Actual PTY launches started the default adventure and practice
trails 1, 12 and 20, showed the correct equipment, paused and quit cleanly. A
separate public-JAR launch loaded a saved best of 125.9 seconds and visibly showed
`Best: 2:05`; M/G refreshed the cached title, and quitting a partial run preserved
that record. All 2,107 asset hashes match the imported snapshot and `levels.tsv`
remains byte-identical to the source Kotlin game.

## Performance

`tools/performance_audit.py` measured the actual public JAR at 100×36 cells after
warmup, with fifteen seconds in each state. CPU refers to one logical core; RSS
includes JVM/native memory, beyond the 256 MiB Java heap limit.

| State | CPU | Peak sampled RSS | Emitted terminal paints/second |
| --- | --- | --- | --- |
| Title | 7.13% | 118.5 MiB | 0 after initial paint |
| Playing | 45.4% | 168.1 MiB | 27.0 |
| Paused | 4.93% | 168.2 MiB | 0 after initial paint |

Other audits were active on the shared host. These are observed results, not a
performance guarantee. Reusing static scenes reduced measured title CPU from
21.4% to 7.13% and paused CPU from 10% to 4.93%; input and resize changes still
refresh immediately. The simulation uses 60 Hz fixed steps independently of the
approximately 30 Hz terminal paint cadence.

## Control and campaign audit

The final live campaign completed all twenty trails and six guardians, collecting
all 60 required acorns, all 20 hidden fireflies and 180 of 378 optional gems. It
sent 1,613 acknowledged physical-key commands and reached the actual ending in
517.417 simulation seconds / 741.039 wall-clock seconds on the busy shared host.
Its one death was a deliberate crumble-platform fall; no accidental retry was
needed. The run visibly proved moving-platform carry, crumble disappearance and
recovery, mushroom launch, checkpoints, armor feedback, guardian phases and
health changes, nineteen chapter-clear screens and the final ending. A previous
complete ANSI run, before the static-screen cache change, finished with zero
deaths and 179 gems.

The final local control inventory passed **169 of 169 cases**. Two reviewers
inspected all **468 captures** across 32 contact sheets; eight targeted recovery
rechecks also passed on the same JAR. The [control inventory](CONTROLS.md) lists
every physical binding and applicable state. [Evidence notes](evidence/README.md)
record frame hashes, exact offsets and the original-stream replays used to
correct exit, equipment-removal and selection captures.

The control harness launches tui4j in real pseudoterminals and sends ANSI bytes.
Its test-only host supplies initial states and read-only telemetry. Before,
during and after captures come from actual ANSI output decoded with pyte, not
images drawn independently by the game renderer. Captures are committed at
tui4j's completed-paint marker, with exact raw-stream character offsets. The host is excluded from the
production JAR. Native XTerm captures additionally check real font/cell rendering.

The campaign harness navigates original levels with physical ANSI commands and
read-only telemetry. It does not teleport, grant collectibles, force boss phases,
inject simulation input or accelerate the clock. Failures and normal checkpoint
recovery are retained.

## Reproduce

```sh
export JAVA_HOME="$(tools/bootstrap.sh)"
./emberwood-tui --build
./gradlew check
python3 -m venv .venv
.venv/bin/pip install -r tools/audit-requirements.txt -r tools/lint-requirements.txt
.venv/bin/shellcheck install.sh emberwood-tui tools/bootstrap.sh tools/package.sh
.venv/bin/ruff check tools
.venv/bin/ruff format --check tools
.venv/bin/python tools/ansi_audit.py
.venv/bin/python tools/ansi_campaign.py
./tools/package.sh
python3 tools/test_install.py
```

On Linux, `.venv/bin/python tools/performance_audit.py` measures the public JAR.
With PipeWire utilities available, `.venv/bin/python tools/audio_audit.py`
captures only the audit game's playback stream and compares sound/mute/recovery.
It preserves the host's volume, mute setting and other applications' audio links.

Compile before auditing: Gradle replaces the fixture class directory when
rebuilding tests. The campaign decoder uses a separate process so decoding does
not delay navigation. Prefer an otherwise lightly loaded host.

Final evidence remains in `build/verification/`, `build/ansi-audit/`,
`build/native-final/`, `build/public-combat/`, `build/cli-audit/`,
`build/best-time-audit/`, `build/performance-final/`, `build/audio-audit/` and
`build/installer-evidence.json`. A verification archive accompanies the
[v1.0.0 release](https://github.com/samsaw123456/emberwood-tui/releases/tag/v1.0.0).
CI uploads its own terminal evidence for Linux x86-64 and both macOS architectures.

## Limits

- Native macOS Terminal.app, Dock launching and physical macOS audio have not
  run on this Linux host. [Cross-platform CI](https://github.com/samsaw123456/emberwood-tui/actions/runs/43695872122)
  passed build, lint, tests and installation on Linux/macOS x86-64 and ARM64.
  Linux x86-64 and both macOS jobs also passed all 169 ANSI control rows; Linux
  ARM64 exercised installed PTY play through the installer audit.
- GIO proved the GNOME launcher executed the game, but the available X11 tool
  could not capture GNOME's Wayland window. Its isolated X11 capture was stale.
  Visual proof therefore uses XTerm.
- Standard ANSI has no key-release events. Latched steering and a fixed
  0.35-second jump pulse are explicit adaptations. Focus pausing depends on
  terminal support for focus events.
- Optional gem collection was sampled across full model routes, not every
  possible path. No finite playtest proves every timing combination.
- Controller, touch, mouse play, network play and campaign save slots are not
  implemented. Current-run progress is intentionally in memory.
