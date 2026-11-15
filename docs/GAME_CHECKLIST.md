# Game checklist

This record distinguishes model tests, actual ANSI input/output, native terminal
inspection and device checks. Passing a model test is not physical control proof.
Per-binding results belong in [CONTROLS.md](CONTROLS.md); build, installer, audio
and platform evidence belongs in [VERIFICATION.md](VERIFICATION.md).

## Completed campaign on the final build

The actual tui4j game completed all **20 trails, 60 required acorns, 20 hidden
fireflies and six guardians** using ordinary ANSI key presses in a live PTY.
It collected **180 of 378 optional gems** and reached the ending after **517.417
simulation seconds / 741.039 wall-clock seconds**, with 1,613 acknowledged inputs
before quitting. One deliberate crumble-platform fall tested natural camp
recovery; there were **no accidental retries**.

Artifact SHA-256:
`856921d9a634cae6bb996717a1a1ff2417a66bfe60f90548a35e549e8cb96abf`.

The driver reads telemetry for navigation but never teleports, grants pickups,
forces guardian phases, injects simulation input or accelerates time. Its
separate terminal decoder commits only complete tui4j redraws. All **72 captured
screens** were visually inspected, individually or in contact sheets. Their
saved character offsets were independently checked against complete-frame
markers in the actual ANSI archive. That archive contains 19 trail-clear
messages, six guardian-defeat messages and the final twenty-trail ending.

Evidence is under `build/ansi-audit/campaign/`: `result.json`,
`complete-terminal.ansi.gz`, `commands.jsonl`, `events.jsonl`,
`visual-review.json`, screenshots, text captures and state records. The full run
log is `build/verification/ansi-campaign-final.log`.

| Area | Acceptance and risks | Evidence and scope |
| --- | --- | --- |
| Core loop | Understandable goals, progression, scoring, win and restart | Final ANSI campaign passed all 20 trails and the ending. Every clear screen and the victory totals were inspected. Restart and replay bindings have separate control-inventory rows. |
| Rules | Three acorns per exit; guardian victory also required; no removed chests, shields or companion | Original content inventory and gate/backtracking regressions passed. The live campaign opened every required exit and defeated all six guardians. |
| Physics | Fixed-step movement, braking, double-jump budget, dash cooldown, swept collisions, coyote time, buffering and safe respawn | Model cases passed at 30/60/120 Hz. Live ANSI routes exercised the original platforms and jumps; the control audit checks visible movement, stop, jump and dash commands separately. |
| Moving and crumble platforms | Carry a stationary player; collapse after use; recover after a fall | In the final live run, the fox stopped and rode a moving platform for 0.5 seconds. Its displacement matched the platform motion. The first crumble tile visibly disappeared, the fox fell, returned to the lit camp with pickups retained, and crossed the restored tile. Before/after captures were inspected. |
| Bounces | Mushroom, critter and guardian impulses cancel stale dash/grounding; correct remaining air jump | Model bounce/stomp regressions passed. Final ANSI captures show mushroom launch and upper-shelf landing; all six live guardians were defeated by jumping onto their exposed caps. |
| Interaction | Weapon triggers, cooldowns, hit priority, four critter species, piercing, guardian cap and guarded phases | Model projectile/contact cases cover hit rules. The separate control audit fires all five weapons in both directions. Campaign frames additionally show actual armor-block feedback and guardian damage; this is not a claim that every weapon/target pairing was manually fought. |
| Equipment | Five weapons, eight armor choices, earned rewards, shared spent-hit budget and camp recharge | Both complete model routes earned all equipment. The live campaign reached the final lance and armor; armor charges, hit protection and camp recharge were visible. Every equipment-menu item is covered by the control inventory. |
| Navigation and camera | Reachable routes, readable exits, bounded tracking, backtracking and menu reachability | Both model route drivers and the final physical-input campaign completed all original routes. Gate/backtracking regressions passed. The control inventory records screen-direction and camera-follow checks. |
| State | Title, play, pause, return to camp, chapter complete, victory and restart | Model transition/reset tests passed. The live campaign exercised title through ending plus natural failure/recovery. State-specific enabled/disabled bindings, pause and restart have individual control-inventory rows. |
| Input | Latched ANSI steering; S/Down stop; fixed jump pulse; alternates, repeats, chords and state boundaries | Terminal-input tests and full adapter routes passed. Physical binding results are recorded individually in CONTROLS.md; the campaign is additional evidence, not a replacement for that inventory. |
| Focus and resize | Clear steering at boundaries, pause on supported focus loss, small-window recovery and terminal restoration | Dedicated ANSI inventory rows cover focus reports, resizing, pause/recovery and clean quit. Standard ANSI has no key-up events; focus reporting depends on the terminal. |
| Feel and accessibility | Recognizable original art, readable text, contrast, useful feedback, mute and reduced motion | All final campaign screens were inspected at 100×36 cells. Native XTerm and palette/control checks are recorded separately. Audio output/mute/recovery was captured through PipeWire; physical-speaker listening was not claimed. |
| Polish and discovery | Accurate counts, guardian warnings/caps, gear feedback, camp messages, secrets and ending | Inspected all 20 clear screens, all six guardian sequences, armor feedback, return-to-camp messages and ending. All 20 fireflies were found through the live routes. |
| Robustness and performance | Invalid timing, bounded input, visible settings errors, sustained play, resource cleanup | Model invalid-time/state tests passed. The final live campaign sustained more than 12 minutes of wall-clock play under host contention and exited cleanly. Settings, resize and performance probes are recorded in VERIFICATION.md. |
| Distribution | Private JVM 25, offline launch, reinstall/rollback, unusual paths and desktop icon/launcher | Installer and native Linux launcher evidence is recorded in PACKAGING.md and VERIFICATION.md. Native macOS Terminal.app/Dock/audio hardware remains a separate platform limitation. |

## Regression found during the port

The initial 0.18-second terminal jump pulse passed a flat-ground height test but
could not reach the first trail's shelf at `(1540, 436)`. The full terminal-adapter
route test exposed it. A **0.35-second pulse** restores the inherited reach and
passes all twenty model routes with every acorn, every firefly and six guardian
victories. That model run took 462.95 simulation seconds, collected 196 optional
gems and required no retries. The final live ANSI run above independently
establishes that the terminal parser, rendering and real-time scheduling preserve
the playable routes.

## Boundaries of the evidence

Optional gem collection was sampled across full campaigns; every possible gem
route and every timing combination was not exhaustively played. Model tests and
physical tests are deliberately identified separately above. Native macOS GUI
and physical audio-device checks are not inferred from Linux results or CI.

Slopes, network play, touch/controller/mouse play, camera-mode switching, control
remapping and campaign save slots are outside this port's inherited gameplay and
are not implemented. Standard ANSI's latched steering and fixed jump pulse are
explicit terminal adaptations.
