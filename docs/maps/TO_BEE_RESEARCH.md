# To Bee Or Not To Bee / Gardens — research handoff

This file records evidence for importing the archived HiveMC DeathRun map into
Classic26. It deliberately separates confirmed evidence from candidate evidence
and unknown gameplay configuration. Do not invent missing coordinates, button
assignments, checkpoint regions, or trap effects.

## Confirmed public archive

Pinned original-world evidence currently comes from:

- Repository: `Mqzn/HiveMCMaps`
- Historical commit: `9a5b49593be61716ff3419aff24ce313567df14f`
- Path: `death-run/to-bee-or-not-to-bee/`
- Archived files at that commit: `level.dat`, four Anvil region files, and
  `screenshot.png`.
- Third-party binary world files are downloaded only into ephemeral GitHub
  Actions storage. They are not committed to or uploaded as artifacts by this
  project.

## Confirmed gameplay metadata

Public Hive community evidence identifies **Gardens / To Bee Or Not To Bee** as
one of the Hive DeathRun maps with **9 checkpoints**:

- https://forum.playhive.com/t/a-map-with-ten-checkpoints/37294
- https://www.speedrun.com/mcm_hivemc/levels

This confirms the checkpoint count and the Gardens/To Bee naming association.
It does **not** establish checkpoint coordinates, checkpoint point values,
Runner/Death spawns, finish region, or trap definitions.

## Archived Anvil evidence

Classic26 CI probes the complete four-region archive read-only.

The pinned archive currently produces:

- 202 parsed chunks
- 0 failed chunks
- 0 external chunks
- 855 original candidate blocks before sign-block expansion
- 0 command-block entities
- 409 buttons
- 80 pressure plates
- 366 Nether Portal blocks
- 154 sign TileEntities

The bounded local review pass (`radius=4`, `vertical-radius=3`) turns the
489 button/pressure-plate interaction candidates into:

- 110 local evidence groups
- 15 HIGH structural-review groups
- 79 MEDIUM structural-review groups
- 16 LOW structural-review groups
- largest group: 17 candidates

The priority is only a review queue. It is **not** a statement that a group is
a DeathRun trap.

## level.dat evidence

The pinned `level.dat` parses successfully as:

- DataVersion: **2580**
- Minecraft version: **1.16.3**
- LevelName: **HiveMC - DR2BORNOT2B**
- SpawnX/Y/Z: **0,0,0**
- initialized: **0**
- game type: **0**
- difficulty: **1**

Because the archive reports `initialized=0` with a zero world spawn,
Classic26 marks it `spawn_status=placeholder`. It must not be used as a
Runner spawn, Death spawn, waiting lobby, or orientation anchor.

## Physical topology evidence

`tools/anvil_topology.py` groups only physically adjacent pressure plates and
portal blocks. Real archive results:

- pressure-plate points: **80**
- pressure-plate physical components: **76**
- largest pressure-plate component: **2**
- pressure-plate materials:
  - stone: 56
  - light weighted: 22
  - heavy weighted: 2
- Nether Portal points: **366**
- portal physical components: **7**
- largest portal component: **70**

This is strong negative evidence against treating either surviving pressure
plates or portal components as a direct 9-checkpoint encoding.

## Surviving trap-action sign evidence

Run #242 / commit `8c6ea4016b66fbb46c571ba76a8698377cd0eeff`
passed the Java 25 build and complete Paper 26.2 smoke after probing this
archive.

The world contains:

- **23** directional action signs
- **12** unique cleaned action labels
- **20** Runner warning signs
- all **20/20** warnings have at least one shared-text action candidate
- all **20/20** warnings have an action sign within 30 blocks

Exact surviving action-label catalog:

| Action label | Count | Action-sign coordinates |
| --- | ---: | --- |
| Drop TNT | 1 | `29,44,9` |
| Explode the minefield | 1 | `11,29,40` |
| Fire Arrows | 3 | `-35,29,-19`; `-23,29,10`; `10,27,67` |
| Flood the floor | 2 | `-41,29,10`; `27,44,21` |
| Make the floor fall | 2 | `28,29,-17`; `52,25,56` |
| Melt the ice | 1 | `-45,29,7` |
| Release fire snake | 2 | `11,29,55`; `76,25,56` |
| Remove dark wood | 2 | `-45,29,-18`; `16,25,76` |
| Remove red blocks | 4 | `-21,29,-19`; `13,29,-19`; `32,25,76`; `89,25,65` |
| Remove Sea Lanterns | 1 | `72,25,76` |
| Set the coals on fire | 1 | `30,44,15` |
| Summon a random wall | 3 | `-45,29,2`; `-5,29,10`; `23,29,-19` |

These labels are preserved map text. They are much stronger evidence for the
original trap concepts than button density alone, but they still do not define
Classic26 target cuboids, durations, reset states, or exact button bindings.

## Warning-to-action one-to-one candidate set

`tools/anvil_trap_evidence.py` builds a transparent review-only one-to-one set.
An edge requires at least one shared normalized word and a distance of at most
30 blocks. More shared words are preferred, then shorter distance.

Real archive result:

- **20** pair candidates
- **0** unmatched warning signs
- **3** unmatched action signs

The 20 candidates are:

| Warning coordinate | Candidate action | Action coordinate | Shared evidence | Distance |
| --- | --- | --- | --- | ---: |
| `-62,25,24` | Melt the ice | `-45,29,7` | ice, melt | 24.37 |
| `-57,25,-14` | Remove dark wood | `-45,29,-18` | dark, wood | 13.27 |
| `-55,25,0` | Summon a random wall | `-45,29,2` | random, wall | 10.95 |
| `-46,25,20` | Flood the floor | `-41,29,10` | flood | 11.87 |
| `-41,18,-34` | Fire Arrows | `-35,29,-19` | arrow, fire | 19.54 |
| `-25,18,-34` | Remove red blocks | `-21,29,-19` | block, red | 19.03 |
| `-19,25,20` | Fire Arrows | `-23,29,10` | arrow, fire | 11.49 |
| `-4,25,31` | Summon a random wall | `-5,29,10` | random, wall | 21.40 |
| `6,25,46` | Explode the minefield | `11,29,40` | minefield | 8.77 |
| `6,25,60` | Release fire snake | `11,29,55` | fire | 8.12 |
| `6,25,72` | Fire Arrows | `10,27,67` | fire | 6.71 |
| `7,25,-36` | Remove red blocks | `13,29,-19` | block | 18.47 |
| `22,25,-33` | Summon a random wall | `23,29,-19` | random, wall | 14.59 |
| `24,35,79` | Remove dark wood | `16,25,76` | dark, wood | 13.15 |
| `32,45,11` | Set the coals on fire | `30,44,15` | coal, fire | 4.58 |
| `33,25,-28` | Make the floor fall | `28,29,-17` | fall, floor | 12.73 |
| `36,45,21` | Flood the floor | `27,44,21` | flood | 9.06 |
| `37,34,85` | Remove red blocks | `32,25,76` | block, red | 13.67 |
| `50,25,53` | Make the floor fall | `52,25,56` | floor | 3.61 |
| `93,25,56` | Remove red blocks | `89,25,65` | block, red | 9.85 |

The three action signs left unmatched by that review heuristic are:

- `Drop TNT` at `29,44,9`
- `Remove Sea Lanterns` at `72,25,76`
- `Release fire snake` at `76,25,56`

These are **candidate pairings, not confirmed trap assignments**. In particular,
single generic words such as `fire`, `block`, or `floor` are weaker evidence
than two-word matches such as `random + wall`, `dark + wood`, or
`arrow + fire`.

## Sign-to-button evidence

The archive preserves modern BlockState for many signs and buttons.

Important result:

- 349/409 buttons retain wall-facing information
- all 23 directional action signs retain sign facing/rotation
- naive interpretation of `<<< / >>>` as pointing toward the control button
  failed; those arrows are therefore retained as target-direction evidence only
- distance + compatible sign/button facing reduces only one action to a single
  panel-button candidate

Current unique control-button candidate:

- action sign: `Release fire snake` at `76,25,56`
- candidate button: `76,25,47` (`minecraft:oak_button`, wall-facing south)

Even this remains a **candidate** until visual or behavioral evidence confirms
it. Three other signs reduce to small multi-button panel sets; most do not.

## Import procedure

1. Keep the pinned archive immutable and use it only as source evidence.
2. Ignore the placeholder `level.dat` spawn.
3. Use action/warning signs as the strongest surviving trap-concept evidence.
4. Treat the 20 one-to-one pairs as a review queue, not as authoritative trap
   definitions.
5. Use button-facing evidence only to reduce candidates; do not guess remaining
   button bindings.
6. Establish all **9 checkpoint** trigger regions from independent evidence.
7. Establish Runner/Death spawns, start barrier and finish region independently.
8. For every trap selected for implementation, confirm:
   - control button
   - target cuboid/geometry
   - activation behavior
   - active duration
   - restoration/reset behavior
9. Run `/dr map check`, `/dr map manifest`, backup/restore verification and
   the human acceptance trace before considering the imported map playable.

## Still unknown / blockers

- Exact Runner spawn(s)
- Exact Death spawn(s)
- Exact 9 checkpoint trigger regions and checkpoint spawn points
- Checkpoint point values
- Exact finish trigger
- Exact control button for 22/23 action-sign instances
- Exact target region/cuboid for each trap
- Trap active durations and reset state
- Whether any original server-side control logic was stripped before archive

The archive is now strong enough to recover the **trap concept catalog and a
20-instance warning/action review set**, but not yet strong enough to claim a
faithful formal Classic26 map configuration.
