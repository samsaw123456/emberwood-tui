# Verification record

Verified on Linux x86-64 on November 15, 2026. Model tests, actual terminal
input/output, native rendering, audio output and installation are distinct forms
of evidence; none substitutes for another.

## Completed checks

- `./emberwood-tui --build` and `./gradlew check fatJar` passed with Java 25,
  Kotlin 2.4.21, Gradle 9.9.1 and tui4j 0.3.5. The 37 tests include two complete
  twenty-trail model campaigns, content counts, six guardians, gate rules,
  bounce/collision regressions, every weapon and armor capacity, settings,
  terminal input and camera/palette checks. `check` includes ktlint; compiler
  warnings are errors.
- Both route tests earned every required acorn, every firefly and all equipment.
  The terminal-adapter model route took 462.95 simulation seconds, collected
  196 of 378 optional gems and needed no retries. This is **model evidence**.
- ShellCheck, `bash -n`, Ruff checks and Ruff formatting passed. Installer checks
  cover fresh installation, unusual paths, invalid options, corrupted runtime
  rejection, simultaneous source launch ordering, offline play, update
  preservation and rollback when Java preparation fails.
- An installed public game ran in a PTY. M changed its rendered sound state and
  saved preference; a reinstall retained it and a new process loaded it.
- The actual Linux desktop entry launched through GIO into GNOME Terminal.
  Native XTerm 407 screenshots separately establish real terminal rendering:
  original art, screen-left/right steering, stopping, jumping, dash, retry,
  pause/resume, equipment navigation, locked-item feedback and clean quit.
- `tools/audio_audit.py` captured actual Java playback through PipeWire.
  Baseline and muted buffers had peak/RMS zero; enabled and restored sound had
  peaks 2422/2426 and RMS 215.74/217.85. The host speaker's muted state was
  preserved. This proves audio output, not physical-speaker listening.

Production JAR SHA-256 for these terminal checks:
`a9e1a3428262859084def84bcc6c16cc7917e1fabb39b0565973ccbe5e0c5339`.

## Control and campaign audit

The exhaustive final control run and live twenty-trail campaign are in progress.
Their results will be recorded before release. The generated [control
inventory](CONTROLS.md) records each physical key and applicable state; a partial
table is not complete coverage.

The control harness launches tui4j in real pseudoterminals and sends ANSI bytes.
Its test-only host supplies initial states and read-only telemetry. Before,
during and after captures come from actual ANSI output decoded with pyte, not
images drawn independently by the game renderer. The host is excluded from the
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

Compile before auditing: Gradle replaces the fixture class directory when
rebuilding tests. The campaign decoder uses a separate process so decoding does
not delay navigation. Prefer an otherwise lightly loaded host.

Evidence remains in `build/verification/`, `build/ansi-audit/`,
`build/native-audit/`, `build/manual-final/`, `build/audio-audit/` and
`build/installer-evidence.json`. CI uploads terminal evidence for Linux x86-64
and both macOS architectures, including failed runs.

## Limits

- Native macOS Terminal.app, Dock launching and physical macOS audio have not
  run on this Linux host. Cross-platform CI is pending.
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
