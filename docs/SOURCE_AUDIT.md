# Source and parity audit

Inspected on 2026-11-14 before implementation. This is a requirements and provenance
record, **not a claim that the terminal port has passed verification**. Final
terminal evidence belongs in the control audit and verification notes.

## Sources

- Original Emberwood: `~/git/aie`, commit
  `7146ac838c0db21afe9406ea4546cb094aa9721f`, Apache-2.0 code.
- Kotlin adaptation: `~/git/emberwood-kt`, then an active, uncommitted sibling
  checkout. Read `AGENTS.md`, `README.md`, `THIRD_PARTY.md`, requirements and control
  notes, `Model.kt`, `Simulation.kt`, `Equipment.kt`, `Levels.kt`, `Main.kt`,
  renderer/art/audio code and simulation tests. Do not modify either source game.
- Godot history and lessons: `docs/RESTORED_GAMEPLAY.md`, `BOSSES.md`,
  `TWENTY_LEVELS.md`, `AUDIT.md`, `MONSTERS.md`, asset catalog and licenses.
- Workshop origin: [Phippy Gaming Workshop](https://github.com/Tingsters/Phippy-Gaming-Workshop).

The reusable Kotlin simulation has no desktop dependencies. Its model, equipment,
level parser and lossless `levels.tsv` can provide the authoritative gameplay.
Separate terminal input, screen rendering, audio/persistence and package concerns.
The source renderer assumes a 1152×648 world view; camera and world scaling must
remain internally consistent when mapping it into terminal cells.

## Campaign and rules to preserve

Twenty routes, 60 required acorns, 378 optional gems, 20 hidden fireflies,
four regular monster kinds, six guardians, five weapons and eight armor sets.
Each route needs all three acorns to unlock its exit. Guardian routes additionally
need victory. Continue advances chapters; the last gate reaches an ending and
replay starts a fresh adventure. Closing the original game resets the adventure;
settings and best time persist, not campaign progress.

| Level | Route | Theme / guardian |
| --- | --- | --- |
| 1 | Emberwood | Forest |
| 2 | Flooded Mill | Mill |
| 3 | High Canopy | Canopy; Old Croak (frog) |
| 4 | Moss Caverns | Cavern |
| 5 | Sunlit Ridge | Ridge |
| 6 | Reedbank Trail | River; Reed Rumbler (opossum) |
| 7 | Willow Narrows | River |
| 8 | Broken Aqueduct | River |
| 9 | Heron Marsh | River; Marsh Tusk (boar) |
| 10 | Riverwatch Falls | River |
| 11 | Frostpine Pass | Frost |
| 12 | Snowmelt Gorge | Frost; Frost Fang (wolf) |
| 13 | Slate Heights | Frost |
| 14 | Whitewater Steps | Frost |
| 15 | Cloudline Crossing | Frost; Cloudwing (eagle) |
| 16 | Copperbark Woods | Ancient |
| 17 | Rootbound Ruins | Ancient |
| 18 | Lantern Hollow | Ancient; Lantern Owl (owl) |
| 19 | Sunleaf Crown | Ancient |
| 20 | Heart of Emberwood | Ancient; final ending |

Campfires advance the checkpoint, award and equip earned gear, and recharge armor.
Retry preserves collectibles, unlocked gear and defeated guardians, while resetting
ordinary enemies, platforms, active shots and unfinished fights. Restart resets
the whole adventure. A fall or unprotected ordinary hazard returns to camp;
guardian encounters use three hearts after armor protection is exhausted.

**Explicit exclusion:** do not restore treasure chests, bonus leaf shields, or the
following firefly companion. Those additions were reverted in the source edition. Optional hidden
fireflies remain collectibles, with no companion or enlarged gem pickup range.

## Physics and interactions

World +x is right; +y is down; fox origin is its feet. The source simulation slices
elapsed time into at most 1/60-second steps. Preserve the simulation scale rather
than replacing it with per-cell collision approximations.

- Ground acceleration 2200 units/s², air acceleration 1500; top walking speed 250.
  Gravity 1500, downward cap 900. Body is 22×36 world units.
- First jump velocity −540, double jump −510; 0.12-second coyote time and jump
  buffer. Release cuts a player jump to 45% upward velocity once. No third jump.
- Dash is 620 units/s for 0.16 seconds, 0.65-second cooldown; one air dash until
  landing. Direction locks at dash initiation. Jump takes priority over a
  simultaneous new dash. Walls interrupt dash.
- Moving platforms carry grounded players using their actual displacement.
  Crumble platforms stop supporting after 0.55 seconds and recover after 3.5.
- Mushroom spring velocity −770, critter bounce −440, guardian bounce −560.
  All external bounces cancel dash and stale floor contact, leave one air jump,
  and must not be shortened by release of an earlier player jump.
- Critters: opossum patrol; hopping slime; faster beetle; elevated flapping bat.
  Descending stomps defeat them, including while player invulnerability is active.
- Projectile contacts use segment sweeps; nearer terrain blocks farther targets.
  Seed blasts respect terrain occlusion. Spears and lances pierce their stated
  number of critters. Fan pellets cannot multiply damage within one boss opening.
- Guardians warn with a landing mark, leap, emit ground ripples, then expose a
  gold cap during rest. Three hits, one per rest opening. Descending cap crossings
  and weapon hits work; side/underside contact is not a stomp. Body and drawn cap
  both intercept projectiles; guarded hits block shots without damaging the boss.
- Arena entry waits for all acorns, preserving backtracking until then. Starting
  the fight activates the arena camp. Completed fights stay completed after R
  or falling; an unfinished fight restarts at camp.

## Gear

| Reward | Earned at | Behavior |
| --- | --- | --- |
| Bow | Level 1, camp 2 | Fast straight arrows |
| Leaf armor | Level 1, camp 3 | 1 blocked hit |
| Throwing spear | Level 2, camp 2 | Pierce 2 critters |
| Bark armor | Level 3, camp 2 | 2 blocked hits |
| Seed bombs | Level 4, camp 2 | Arcing projectile, local blast |
| Ironwood armor | Level 5, camp 2 | 3 blocked hits |
| Reed mail | Level 6, camp 2 | 4 blocked hits |
| Thorn fan | Level 7, camp 2 | Three spreading short-range shots |
| Frost armor | Level 11, camp 2 | 5 blocked hits |
| Stoneplate | Level 13, camp 2 | 6 blocked hits |
| Copperbark armor | Level 16, camp 2 | 7 blocked hits |
| Sunwood lance | Level 17, camp 2 | Pierce 4 critters, long reach |
| Sunleaf armor | Level 19, camp 2 | 8 blocked hits |

Equipment changes happen in pause. Toggling armor does not refill protection:
all armor shares the spent-hit budget until a new camp or retry recharges it.
Every gear choice must remain reachable and legible in the terminal menu.

## Original controls and terminal adaptations to resolve

Original desktop controls are A/Left and D/Right for movement; Space/W/Up for
jump; either Shift for dash; J weapon; R retry; Esc/P pause; M mute. Menus also
support Tab/Shift+Tab, Up/Down, Enter/keypad Enter/Space, mouse buttons and scrolling.
The terminal port needs its own accurate inventory derived from its actual input
handlers and displayed instructions; desktop mouse support need not be invented.

Ordinary ANSI terminals do **not** transmit standalone Shift or key release.
Choose an ordinary dash key and clearly document the movement/jump policy.
If supporting extended keyboard protocols, test negotiation and fallback; never
claim ordinary ANSI key-up support based only on simulated `InputFrame` releases.
The imported level signs include “Shift dashes” and “Hold jump to go higher”;
translate those hints to the terminal bindings and actual behavior.

Audit lowercase/uppercase letters, alternate arrows, menu navigation, pause,
retry, state-dependent disabled controls, repeated/held input and combinations.
Check release/focus loss behavior supported by the terminal. Resize must retain
readable controls and pause safely when the terminal becomes too small.

## Assets and visual direction

The sibling asset tree is about 24 MiB and already includes unchanged Emberwood
artwork, catalogs, manifests, licenses and the app icon. Prefer it over generating
replacement images. `assets/app/emberwood.png` is the existing launcher identity;
SunnyLand supplies the fox, terrain, collectibles, forest and several animals.
Later creatures/guardians have additional source-code-drawn artwork that can also
be reused when practical. Preserve supplied licenses and attribution unchanged.

Use the game's forest scene, original warm fox and collectible colors, plain
controls and clear game states. Avoid marketing landing-page composition. Avoid faux field-guide/book styling, slash-separated eyebrow
labels and oversized serif headlines. Small slime/beetle/bat art needs recognizable
silhouettes; check species and hazards in actual terminal cells,
not merely a large preview. Reduce decorative detail where it hides gameplay.

## Review requirements and known regression risks

- Independently audit rendered left/right for every physical binding. Internal
  vector signs, code review, successful builds and scripted wins are supplementary
  evidence, never substitutes for watching the actual direction of movement.
- Test every implemented binding and alternate separately in every state whose
  meaning differs. Keep passed/failed/unverified rows, rendered before/during/after
  evidence, reproducible scripts and explicit blockers. A successful full campaign
  does not cover every menu, gear action, retry edge or disabled command.
- Prior Emberwood defects included revived defeated bosses after retry, arrows
  missing drawn cap edges, delayed stomp detection, invulnerability disabling
  offensive stomps, bounce granting excess jumps, dash cancelling bounce, and
  releasing an earlier jump cutting a spring launch. Retain regression coverage.
- Settings saves must preserve a faster existing record and unrelated preferences;
  unreadable files should not be silently overwritten. Use isolated test data.
- Installer UX must support a fresh user without global Java changes or an account,
  offline play after install, paths with spaces, upgrade/reinstall, and a distinct
  launcher/icon. Native macOS execution remains unverified without a Mac.
- Low-resource behavior needs observation, not assumptions: bounded heap, frame
  throughput, CPU/RSS, idle/pause behavior and cleanup of test processes.
- Use JVM 25 and independently verify current stable Kotlin/Gradle/dependency
  versions. Prior sibling version pins are a starting point, not current proof.

## Acceptance evidence

The maintained [game checklist](GAME_CHECKLIST.md) tracks campaign, physics,
interaction, navigation, state, accessibility, polish and robustness. The
[physical control inventory](CONTROLS.md) tracks each binding and game state.
[Verification results](VERIFICATION.md) separately record simulation, ANSI,
native-terminal, audio and distribution evidence, including remaining limits.
