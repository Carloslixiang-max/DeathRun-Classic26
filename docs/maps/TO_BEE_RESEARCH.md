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
- 855 original button/pressure-plate/portal candidates
- 1,138 enriched probe candidates after sign-block, mechanism and redstone expansion
- 0 command-block entities
- 409 buttons
- 80 pressure plates
- 366 Nether Portal blocks
- 154 sign TileEntities / sign blocks
- 80 mechanism blocks: 48 dispensers + 32 hoppers
- 49 redstone/control blocks: 32 levers + 17 redstone blocks

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

A separate portal-to-warning geometry pass uses a conservative 20-block 3D
radius around each of the 7 physical Nether Portal components. It is intended
only to identify route-structure candidates, not to relabel portals as
checkpoints. The current archive has **6/7** portal components near at least one
Runner warning sign and **1/7** portal component with no warning inside that
radius. The warning-free component therefore becomes a useful visual/video
review target for possible route-transition, start/finish, or decorative
geometry, but no gameplay role is assigned from proximity alone.

## Archived redstone/control-chain evidence

Run #255 / commit `19f330d3a0998c7af887865e0e420c1d50f3d168`
expanded the read-only probe to preserve redstone/control blocks and passed the
research suite, Java 25 build and full Paper 26.2 restart smoke.

The To Bee archive contains only **49** surviving redstone/control blocks:

- **32 levers**
- **17 redstone blocks**
- **0** redstone wire
- **0** repeaters
- **0** comparators
- **0** pistons/sticky pistons
- **0** observers
- **0** redstone torches

The 49 blocks form 21 conservative physical-proximity components. Four
single-lever components lie near buttons, but **0** components lie near the
48 dispensers / 32 hoppers and **0** components bridge a button to a mechanism.

This is strong negative evidence that the archive retains a traceable physical
redstone activation network. It supports the existing conclusion that important
trap control logic lived outside the surviving world geometry or was stripped
before archival. It does not prove the exact form of that missing server-side
logic.

One useful positive correlation survives: all **17** redstone blocks occupy
`bbox=-64,24,-13:-56,24,-12`, directly one block above the **17** numeric
`6 | 3` signs at y=23. CI now checks this 17/17 relationship explicitly.
That makes this particular numeric-sign component look like a built display or
status panel rather than an independent checkpoint encoding. The other numeric
components remain unexplained and are not assigned gameplay meaning.

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
- Run #254 / commit `5d5404c3c913af8e9e61ef06195767884eacc53b`
  added a second conservative control-axis check (lateral + vertical alignment)
- the axis pass still produces exactly **1** unique candidate and does not
  manufacture unique links from the three ambiguous high-stage button sets

Current unique control-button candidate:

- action sign: `Release fire snake` at `76,25,56`
- candidate button: `76,25,47` (`minecraft:oak_button`, wall-facing south)

Even this remains a **candidate** until visual or behavioral evidence confirms
it. Three other signs reduce to small multi-button panel sets; most do not.

## Death stage-control evidence

The archive preserves exactly two named, invisible marker ArmorStands:

- `Next Stage` at `24.500,45.250,13.500`
- `Previous Stage` at `27.500,30.250,-11.500`

`tools/anvil_stage_evidence.py` correlates these markers with nearby control
geometry without assigning a stage number or Death spawn.

The `Next Stage` marker has a **light weighted pressure plate directly beneath it**
at `24,44,13`, and is surrounded by:

- **40** nearby stone buttons
- **3** nearby directional action signs:
  - `Set the coals on fire` at `30,44,15`
  - `Drop TNT` at `29,44,9`
  - `Flood the floor` at `27,44,21`

The `Previous Stage` marker has a **light weighted pressure plate directly beneath it**
at `27,29,-12`, and is surrounded by:

- **5** nearby oak buttons
- **2** nearby directional action signs:
  - `Make the floor fall` at `28,29,-17`
  - `Summon a random wall` at `23,29,-19`

Run #258 / the current stage-evidence gate requires both named markers to retain
their direct pressure-plate anchors. This is stronger evidence that the archived
world retains a physical Death stage-control/navigation layer. It does **not**
establish the original stage numbering, Death spawn position, or exact
button-to-action mapping.

## BlockEntity and container evidence

The To Bee archive contains **460** BlockEntities:

- 226 skulls
- 154 signs
- 48 dispensers
- 32 hoppers

The 306 non-sign BlockEntities contain no route-keyword metadata. The original
container inventories are also absent: **0 inventory entities / 0 inventory
stacks** survive in the archive. Therefore dispenser/hopper contents cannot be
used to reconstruct the original trap payloads.

The block geometry itself is still useful because modern BlockState preserves
mechanism direction. All 48 dispensers retain a `facing` property, and all
32 hoppers retain `facing=down` plus `enabled=true`.

## Warning/action/mechanism facing corroboration

Run #252 / commit `413b706f0e1a6f75826daecea04f4fb6d74f37a7`
passed the research suite, Java 25 build and full Paper 26.2 smoke with
`tools/anvil_mechanism_evidence.py`.

Using the same 20 warning/action candidates and a 14-block mechanism review
radius:

- **11/20** pairs have at least one nearby mechanism
- **10/20** have at least one nearby dispenser
- **6/20** have at least one dispenser whose preserved facing points
  approximately toward the Runner warning-sign area
- critically, **3/3 Fire Arrows candidates** have facing-consistent dispenser
  evidence

The three Fire Arrows instances are now independently corroborated by four
surviving evidence layers: Runner warning text, Death action text, dispenser
geometry, and dispenser facing.

| Warning coordinate | Action coordinate | Nearby dispensers | Facing-consistent dispensers | Structural evidence |
| --- | --- | ---: | ---: | --- |
| `-41,18,-34` | `-35,29,-19` | 6 | 3 | south-facing array points toward warning area |
| `-19,25,20` | `-23,29,10` | 7 | 3 | north-facing row points toward warning area |
| `6,25,72` | `10,27,67` | 10 | 7 | opposing east/west arrays converge on warning area |

Other mechanism-correlated candidates include both `Flood the floor` instances
and the first `Release fire snake` instance, but a nearby/facing mechanism is
not by itself proof of the original effect. For example, the minefield area has
a dense dispenser row whose facing does not point at the warning sign under the
current conservative rule.

CI now requires all **3 Fire Arrows** candidates to retain facing-consistent
dispenser evidence. This protects the evidence pipeline from silent regression.

Run #257 / commit `cfbccbba35b28292936af8f1f2526386d2047617`
also groups the preserved Minecraft dispenser BlockStates into facing banks.
All **3/3** Fire Arrows candidates have at least one dispenser bank and **2/3**
have explicit opposing banks:

- warning `-19,25,20`: north/south banks overlap across
  `x=-25..-22` at `y=26`
- warning `6,25,72`: west/east banks overlap across
  `z=66..70` at `y=25..26`
- warning `-41,18,-34`: one south-facing bank of six dispensers remains,
  without an opposing bank inside the conservative review radius

These bank overlaps materially narrow in-game lane geometry, but they still do
**not** authorize inventing an exact Classic26 target cuboid, projectile path,
duration, payload, or reset behavior.

## Import procedure

1. Keep the pinned archive immutable and use it only as source evidence.
2. Ignore the placeholder `level.dat` spawn.
3. Use action/warning signs as the strongest surviving trap-concept evidence.
4. Treat the 20 one-to-one pairs as a review queue, not as authoritative trap
   definitions.
5. Use button-facing evidence only to reduce candidates; do not guess remaining
   button bindings.
6. Use dispenser facing/bank geometry as target-lane corroboration for Fire
   Arrows and other mechanism-backed candidates; do not turn it into an exact
   cuboid without independent geometry/behavior evidence.
7. Establish all **9 checkpoint** trigger regions from independent evidence.
8. Establish Runner/Death spawns, start barrier and finish region independently.
9. For every trap selected for implementation, confirm:
   - control button
   - target cuboid/geometry
   - activation behavior
   - active duration
   - restoration/reset behavior
10. Run `/dr map check`, `/dr map manifest`, backup/restore verification and
   the human acceptance trace before considering the imported map playable.

## Still unknown / blockers

- Exact Runner spawn(s)
- Exact Death spawn(s)
- Exact 9 checkpoint trigger regions and checkpoint spawn points
- Checkpoint point values
- Exact finish trigger
- Exact control button for 22/23 action-sign instances
- Exact target region/cuboid for each trap (Fire Arrows target lanes are now
  structurally narrowed by dispenser facing, but not exact)
- Trap active durations, projectile payloads and reset state
- Exact form of the original server-side control logic that is absent from the archive

The archive is now strong enough to recover the **trap concept catalog and a
20-instance warning/action review set**, but not yet strong enough to claim a
faithful formal Classic26 map configuration.
