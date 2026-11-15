# Control evidence

The final Linux PTY inventory passed **177 of 177 control cases** on November 15,
2026, with **506 before, during and after frames** on 34 contact sheets. Two
reviewers inspected every frame and found no unresolved presentation defect.
A separate public-launcher audit passed **20 of 20 terminal lifecycle cases**;
all 100 of those frames were inspected too.

Audited production JAR SHA-256:
`10220ad3607d6f947b4cf8ae017589f532a5b53ccd409e5d225b7488f859d5a2`.

The [control inventory](../CONTROLS.md) records each binding, applicable state,
expected effect and observed result. The [verification record](../VERIFICATION.md)
covers the separate full campaign, native terminal, audio and installer checks.

The full inventory run took 720.57 seconds and initially passed 176 cases. Its
camera test captured a complete frame before visible camera easing caught up
with telemetry. The test now first observes a moving reduced-motion reference,
then requires a visible rightward shift under easing and a leftward recenter
when reduced motion returns. That physical recheck passed in 6.24 seconds on the
same JAR. The original failed trace is retained under
`build/quality-review/camera-before-observable-wait`.

Two additional focused cases passed: that stronger camera test and literal
Ctrl+Z configured on the child terminal before Java starts. Their eleven frames
were inspected. The seven ordinary Ctrl+Z rows exercise actual terminal suspend
signals; the literal row separately requires a key-parser acknowledgement.

To reproduce only those two cases, use
`python3 tools/ansi_audit.py --case playing 'Ctrl+Z literal' --case motion 'G camera recentering'`.
Selected cases write to a separate `selected/` evidence directory and leave the
complete inventory intact.

A later macOS Intel CI check caught the menu-wrap assertion one completed paint
early: the retained raw output highlights Sunleaf armor at character 128270 and
Bow at 129301 after the final Tab. The test now retains thirteen rapid presses,
requires a visible intermediate selection change, and waits for the visible
wrap. A separate physical recheck passed in 6.71 seconds; all seven frames were
inspected and replayed from their exact raw offsets. This [supplemental menu
review](menu-wrap-review.json) leaves the original 177-row, 506-frame inventory
intact. Reproduce it with
`python3 tools/ansi_audit.py --case paused 'Up/Down/Tab boundaries'`.

The harness sends physical ANSI bytes to running tui4j, with the shipped 256 MiB
heap limit. A test-only host supplies isolated starting states; it never calls
an input handler for a tested command. Game classes and assets load from the
production JAR. Telemetry supplements visible direction, jump, projectile,
selection and recovery assertions.

Each row below shows a separately exercised direction binding before input,
during movement and after stopping. A/Left move the fox screen-left; D/Right move
it screen-right. These are actual decoded terminal frames, scaled uniformly.

![A, Left, D and Right before movement, during movement and after stopping](directions.png)

## Capture provenance

Each snapshot JSON records its character offset in the retained `terminal.ansi`
stream. Decode the original UTF-8 bytes without normalizing carriage returns
when replaying those offsets. All 506 offsets were checked: 492 gameplay images
end at tui4j's completed-paint marker (`ESC[?7h`); fourteen Q/Ctrl+C after-images
show the restored empty primary PTY buffer. Process exit, cursor visibility,
focus-reporting cleanup and bracketed-paste cleanup are independently asserted.
Blank exit images are expected. The final traces contain no UTF-8 replacements.

All equipment selections, equipped states and removals were captured directly
in this run. The camera recheck produced new physical input and new output;
no final inventory image was synthesized from game state or replaced with an
image from an older JAR.

The local records are `build/quality-review/ansi-audit-final/results.json`,
`run.json`, `visual-review.json` and `contact-sheets/manifest.json`. Raw ANSI,
text and full-size PNGs remain beside each case. The committed
[control review manifest](control-review.json) retains every inspected page and
capture hash. The earlier v1.0.0 audit remains under `build/ansi-audit`; its
169-case results are historical evidence, not the current verification count.

## Public launchers and terminal restoration

`tools/terminal_lifecycle_audit.py` launches the actual source and installed
commands through isolated interactive Bash sessions. Twenty cases passed in
249.9 seconds, including repeated default and customized flow-control settings.
Every case starts Ctrl+Z from title, playing or pause; repeats it without
resuming; recovers with Enter or P; and exits from recovered gameplay using Q or
Ctrl+C. The full inventory separately tests exit in every game state.

All twenty cases returned exit status zero, restored the primary screen and
exact original `stty -g`/native termios values, and executed a subsequent shell
command. This includes speed fields, control characters, canonical input and
echo. The sixty game captures end at completed paints; forty shell captures
show primary-screen text. See the [native review manifest](native-terminal-review.json)
and `build/quality-review/terminal-final-clean/results.json`.

These checks exposed and fixed native suspend handling, incomplete terminal
attribute restoration and a signal-triggered write-flush hang. The shared
session wrapper preserves the original native termios structure, uses Ctrl+Z
as a recoverable pause and enables `NOFLSH` during play. Failed diagnostic traces
remain under `build/quality-review/terminal`. One interrupted audit also recorded
a concurrent packaging build holding the source-launch lock; the clean twenty
cases above ran after that build finished.

## Limits

Controlled fixtures establish command behavior; the separate physical-input
campaign establishes progression through the original levels. Decoded PNGs
establish emitted ANSI content. Native XTerm captures check actual font and cell
rendering separately. Linux captures do not establish native macOS Terminal.app,
Dock behavior or physical-speaker listening. CI results and actual PCM
output/silence have separate evidence in the verification record.

Ordinary ANSI sends no key-release events. Steering intentionally remains
latched until S/Down or a state boundary. The inventory checks that behavior,
focus recovery, repeated/opposing input, unsupported input and disabled commands.
Every listed local binding/state row passed; finite tests cannot prove every
possible timing combination.
