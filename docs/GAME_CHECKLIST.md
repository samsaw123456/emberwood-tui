# Game checklist

This checklist belongs to the terminal port. A source-game result does not count
as a terminal result. `SimulationTest` is deterministic model-level evidence;
ANSI control coverage and inspected terminal frames must establish the external
input → visible outcome chain separately.

| Area | Acceptance and risks | Evidence / current state |
| --- | --- | --- |
| Core loop | All 20 original routes, 60 acorns, 378 gems, 20 hidden fireflies; six guardians; ending and replay | Content inventory passed. Both `SimulationTest` and `TerminalRouteTest` completed all 20 trails, all 60 acorns, all 20 fireflies and six guardians with zero retries; live ANSI campaign pending |
| Rules | Three required acorns per gate; guardian victory also required; no reintroduced chests, shields or companion | Content inventory, guardian gate/backtracking tests; terminal inspection pending |
| Physics | Fixed-step motion, left/right, braking, double-jump budget, dash distance/cooldown, wall sweep, coyote/buffer, moving carry, crumble recovery | Focused model tests at 30/60/120 Hz; visible ANSI direction and command audit pending |
| Bounces | Mushroom/critter/boss bounces cancel dash and stale grounding; one air jump; old jump release cannot cut spring impulse | Model bounce and stomp regressions; terminal mechanics run pending |
| Interaction | All five weapon triggers in both directions, cooldowns, all four critter species, piercing, nearest wall priority, boss cap and guarded phases | Model projectile/trigger/contact tests; terminal weapon presentation pending |
| Equipment | Every reward earned, five weapons and eight armor choices; shared spent-hit budget; camp/retry recharge | Model reward/swap/hazard tests; terminal menu inventory pending |
| Navigation | Original routes reachable; missing acorns permit boss backtracking; camera preserves screen-left/right; all menu choices reachable | Original-input and terminal-adapter model campaigns passed; gate/backtracking regression passed; live ANSI camera/menu proof pending |
| State | Title/play/pause/retry/chapter complete/won; pause freezes simulation and retry timer; defeated boss survives retry; new run clears progression | Model state regressions; all terminal bindings in every relevant state pending |
| Input | Latched ANSI steering until S/Down; jump pulse and double jump; X dash; all alternates; repeat/chords; no claim of standard ANSI key-up | Terminal input unit tests and full adapter route passed after increasing the pulse from 0.18 to 0.35 seconds; physical ANSI audit pending |
| Focus and resize | Clear steering on boundaries, focus loss behavior, small-window recovery, clean terminal restore | ANSI tests pending; unsupported terminal focus reporting must be disclosed |
| Feel/accessibility | Original recognizable fox/art, readable hazards/menu text, contrast, mute and gentle motion; no decorative marketing screen | Actual-size terminal inspection and output/audio evidence pending |
| Polish | Accurate counters, clear boss marks and phase feedback, gear state, retry messages, credits, existing icon | Terminal inspection pending |
| Robustness | Invalid timing rejected, no unbounded input/action queue, settings errors visible, low-resource throughput, clean shutdown | Model invalid-time tests; runtime/settings/performance evidence pending |
| Distribution | Private JVM 25, clean install, launch offline, upgrade/reinstall/uninstall, spaces in paths, desktop launcher/icon | Installer agent evidence pending; native macOS execution requires a Mac |
| Playtest evidence | Title through success; actual failure/retry; pause/resume; entire physical control inventory | ANSI audit and manual inspected play pending; scripted model win alone insufficient |

Slopes, network play, touch and controller input, camera-mode switching and campaign
save slots are outside this port's inherited gameplay and are not implemented.
The fixed terminal jump pulse is a deliberate adaptation of desktop variable-height
jumping; its actual reach must be checked against the original routes.

## Regression found during this port

The initial 0.18-second terminal jump pulse could pass a flat-ground height test
but could not reach the first trail’s jump to `(1540, 436)`: it landed below the
shelf. The full terminal-adapter route test exposed this. A 0.35-second pulse
restores the inherited full-height jump and passes the complete 20-trail route
with all required acorns, all hidden fireflies and six guardian victories. The
model run took 462.95 simulation seconds and collected 196 of the 378 optional
gems. This is model evidence; the separate live PTY campaign still must establish
that the terminal parser, rendering and real-time scheduling preserve it.
