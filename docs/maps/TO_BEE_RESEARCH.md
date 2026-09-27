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

## Version-sensitive gameplay metadata

The archived source repository describes itself as an **archive of HiveMC Java
maps**, and this world is the Java map `To Bee Or Not To Bee`
(`DR2BORNOT2B`).

External community/speedrun sources merge or associate the names
`To Bee Or Not To Bee / Gardens` and expose both Java and Bedrock categories.
A 2021 Hive forum post describes Gardens / To Bee Or Not To Bee as having
**9 checkpoints**, but that statement is not treated as an archive-specific
Java geometry fact:

- https://forum.playhive.com/t/a-map-with-ten-checkpoints/37294
- https://www.speedrun.com/mcm_hivemc/levels
- https://www.speedrun.com/mcm_hivemc/runs/m35jppgy

Current Bedrock DeathRun documentation also describes checkpoints as Nether
Portal blocks, but the page explicitly documents the Bedrock-era game. It is
useful comparative context, not direct proof of the Java implementation:

- https://hivemc.wiki.gg/wiki/Death_Run

Therefore **9 checkpoints is no longer a hard Classic26 import gate for this
Java archive**. The Java world evidence itself takes priority. The archive must
first establish how many checkpoint gates survive, their order, respawn
locations, and whether any gates were stripped or changed between Java and
Bedrock versions.

## Archived Anvil evidence

Classic26 CI probes the complete four-region archive read-only.

The pinned archive currently produces:

- 202 parsed chunks
- 0 failed chunks
- 0 external chunks
- 855 original button/pressure-plate/portal candidates
- **12,201** enriched probe candidates total
  - 1,138 prior gameplay/research candidates
  - 11,063 additional `ROUTE_STRUCTURE` blocks
    (`barrier`, `iron_bars`, and fence-gate family)
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

Run #265 / commit `f5071283f3024045dd7550b417ab454c20bdd5e8`
also exposes the archived `Player.Pos` / `Rotation` save state without
relabeling it as a gameplay spawn. To Bee retains:

- saved player position: `20.9998,70.6084,43.1460`
- saved rotation: `yaw=-244.826, pitch=18.833`
- saved dimension: `minecraft:overworld`

The y≈70 position sits well above the surviving Runner-warning/action layers
around y≈18–45 and is therefore treated only as an **author/save-camera
anchor**. It is useful for screenshot/orientation reconstruction, not as
Runner/Death spawn evidence.


Run #275 / commit `1c01009d3f03c89bbc723a57ee75e92aac876f44`
uses Minecraft yaw/pitch semantics to compare that saved camera direction with
all seven portal-gate centers and the surviving title sign. The full Java 25
build and Paper 26.2 restart smoke pass with:

- best-aligned portal: **#004**, center `-58.00,28.37,6.00`
- camera → Portal #004 angle: **6.99°**, distance **96.98**
- camera → next-best Portal #001 angle: **28.41°**
- camera → title sign `26,35,51` angle: **83.68°**, distance **36.81**

Therefore the archived save camera is not looking toward the nearby title sign;
it is strongly aimed toward the **Portal #004 / western route area**. This is
useful orientation/screenshot evidence only. It does **not** make Portal #004 a
start, finish, first checkpoint, or player spawn.

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

This remains strong negative evidence against treating pressure plates as a
direct checkpoint encoding. Portal blocks are now handled separately because
their surviving geometry is strongly gate-like and the associated Bedrock-era
gameplay documentation uses Nether Portal checkpoint gates.

Run #266 / commit `76c9823ef14850168d85a2cfc06270cc58283733`
adds a route-context probe for the two rare heavy weighted pressure plates:

- `30,25,57`
- `32,25,57`

Their nearest Runner warning is `WARNING: Unstable floor ahead!` at
`50,25,53`, **20.40 / 18.44 blocks** away respectively. Their nearest action
sign is `Remove red blocks` at `32,25,76`, **19.10 / 19.00 blocks** away.
The nearest physical portal component is still over 31 blocks away.

This makes the two heavy plates poor candidates for a direct checkpoint/start/
finish trigger under the current geometry. They remain physical evidence only;
no gameplay role is assigned.

A separate portal-to-warning geometry pass uses a conservative 20-block 3D
radius around each of the 7 physical Nether Portal components. It is intended
only to identify route-structure candidates, not to relabel portals as
checkpoints. The current archive has **6/7** portal components near at least one
Runner warning sign and **1/7** portal component with no warning inside that
radius. The warning-free component is Portal #006, centered at
`83.00,28.76,81.91` with bbox `83,25,79:83,32,85`.

Run #263 / commit `8b8d867de883bbc53e44b2541f4aa8bfa322a6cd`
adds independent Hint-sign correlation. The archive has exactly one remaining
unclassified non-empty Hint sign, `Hint: Look down!` at `77,25,80`, and it
lies **7.34 blocks** from Portal #006. This makes the warning-free portal a
stronger route-transition / vertical-movement review target and reduces the
case for treating it as an unexplained finish candidate. The hint still does
**not** prove checkpoint, start, finish, teleport, drop target, or route order.


Run #269 / commit `056802faa146245f0f1c763c00af691c41e14b23`
adds a shape pass over all 7 surviving portal components. **7/7 are single-plane
vertical gate/wall structures**, and all 7 fill at least 80% of their minimal
rectangular plane:

| Portal | BBox | Plane | Size | Filled / rectangle | Fill |
| --- | --- | --- | ---: | ---: | ---: |
| #001 | `-13,25,19:-13,32,27` | x=-13 | 70 | 70/72 | 97.2% |
| #002 | `8,25,79:8,34,84` | x=8 | 56 | 56/60 | 93.3% |
| #003 | `31,25,-10:37,33,-10` | z=-10 | 55 | 55/63 | 87.3% |
| #004 | `-61,25,6:-55,32,6` | z=6 | 54 | 54/56 | 96.4% |
| #005 | `-6,18,-40:-6,24,-34` | x=-6 | 47 | 47/49 | 95.9% |
| #006 | `83,25,79:83,32,85` | x=83 | 46 | 46/56 | 82.1% |
| #007 | `32,45,36:36,52,36` | z=36 | 38 | 38/40 | 95.0% |

This is strong **Java-world structural evidence for seven surviving checkpoint-
gate candidates**. It still does not prove checkpoint numbering, route order,
respawn coordinates, or that exactly seven checkpoints existed in the live Java
version. The outstanding question is now whether the Java layout truly used
seven gates or whether additional gates/configuration were stripped or changed
in later Gardens/Bedrock revisions.


## Portal trap-segment partition evidence

Run #273 / commit `53ac2f72c621acb2b050cb487b21078d148412da`
partitions the stable **21 warning/action review pairs** by the nearest surviving
portal gate to the Runner warning position. This is a nearest-center geometry
partition only; it does not assign checkpoint numbers or route order.

Real archive result:

- **21** warning/action pairs
- **7** surviving portal gates
- **7/7** gates receive at least one Runner warning pair
- **19/21** pairs have both the Runner warning and Death action nearest to the
  same portal gate
- **2/21** pairs cross nearest-gate assignments, and both use Portal **#005**
  on the Runner-warning side

Per-gate warning-pair groups:

| Portal | Warning pairs | Surviving action labels |
| --- | ---: | --- |
| #001 | 3 | Fire Arrows; Summon a random wall; Explode the minefield |
| #002 | 4 | Release fire snake; Fire Arrows; Remove dark wood; Remove red blocks |
| #003 | 2 | Summon a random wall; Make the floor fall |
| #004 | 4 | Melt the ice; Remove dark wood; Summon a random wall; Flood the floor |
| #005 | 3 | Fire Arrows; Remove red blocks; Remove red blocks |
| #006 | 2 | Release fire snake; Remove red blocks |
| #007 | 3 | Set the coals on fire; Flood the floor; Make the floor fall |

The two cross-gate pair candidates are:

- Runner warning `-41,18,-34` is nearest Portal **#005**, while its
  `Fire Arrows` action `-35,29,-19` is nearest Portal **#004**
- Runner warning `7,25,-36` is nearest Portal **#005**, while its
  `Remove red blocks` action `13,29,-19` is nearest Portal **#003**

This makes Portal #005 a strong **structural bridge review candidate between the
#004/#003 control neighborhoods**. The direction of that relationship is not a
checkpoint-order claim: warning-side vs action-side geometry is not equivalent
to Runner travel direction.

The fact that all seven gates receive trap-pair assignments also means there is
no surviving portal gate that can be identified as start/finish merely because
it lacks nearby trap evidence.


Run #277 / commit `156671c2e34e72ddd9d50defab9dbd2c202709fb`
adds an undirected warning-segment proximity graph. For every pair of portal
segments, the edge weight is the nearest 3D distance between their Runner
warning signs. The complete 7-node graph has 21 edges; its minimum spanning
tree has 6 edges and is **exactly a path**, with endpoints **#003 and #006**.

The six MST edges are:

- #003 ↔ #005: **15.30**
- #005 ↔ #004: **26.55**
- #004 ↔ #001: **27.00**
- #001 ↔ #002: **14.00**
- #002 ↔ #007: **35.69**
- #007 ↔ #006: **20.00**

Therefore the strongest current archive-only topology is the **undirected**
candidate chain:

`#003 — #005 — #004 — #001 — #002 — #007 — #006`

This is substantially stronger than sorting gates by coordinate or portal-center
distance because it is derived from the surviving Runner trap-warning geometry.
It still does **not** determine which endpoint is start-side vs finish-side, nor
does it assign checkpoint numbers or respawn coordinates.


Run #279 / commit `1ae687e7c37a595d8265fff03ad272ab0f0cebfb`
compares the two MST endpoints using the same 20-block 3D radius.

Endpoint **#003** is control/stage-rich:

- 5 buttons
- 1 pressure plate
- 2 mechanisms
- 1 Runner warning
- 2 action signs
- 1 named stage marker
- nearest stage marker: `Previous Stage`, **6.89** blocks
- nearest pressure plate: `27,29,-12`, **7.30** blocks
- nearest action: `Make the floor fall` at `28,29,-17`, **9.23** blocks

Endpoint **#006** is locally control-empty but route-hint-rich:

- 0 buttons
- 0 pressure plates
- 0 mechanisms
- 0 Runner warnings
- 2 action signs
- 1 Hint sign
- 0 stage markers
- nearest Hint: `Hint: Look down!` at `77,25,80`, **7.34** blocks
- nearest action: unmatched `Remove Sea Lanterns` at `72,25,76`,
  **13.04** blocks

This sharply distinguishes the two chain ends. #003 is embedded in preserved
Death control/stage geometry, while #006 is a comparatively clean Runner-route
endpoint with an explicit vertical-movement hint. This makes #006 the stronger
**peripheral route-end review candidate**, but it still does not by itself prove
finish-side vs start-side or route direction.


## Runner-warning orientation evidence

Run #283 / commit `bd725eded839c5ad138dd5ecd81084f99fd7c646`
adds a BlockState-facing pass for the warning signs that define the six MST
edges. **12/12 edge-side warning fronts survive**.

Five of the six MST edges show a strong paired orientation pattern: the two
warning signs on that edge agree on one local travel orientation if the sign
front is interpreted as the side from which an approaching Runner reads the
warning. The sole exception is the **#006 ↔ #007** edge, which is already the
route end associated with `Hint: Look down!` and an unusually vertical /
control-empty context.

This does not yet prove route direction because "warning front = Runner incoming
side" is a gameplay-placement assumption rather than archived server logic.
However it creates a strong, testable direction hypothesis for the core chain
instead of relying on coordinate sorting.


Run #285 / commits `5af2c69629f6f75e92041380dd896684bbcee64f` +
`ec0e28d7f70b0cb240d325b37077eb9e28b3ec0b` score both directions of the
entire seven-gate MST under that explicit incoming-facing assumption and pass
the complete Java 25 / Paper 26.2 restart smoke.

The result is strongly asymmetric:

- candidate `#006 → #007 → #002 → #001 → #004 → #005 → #003`:
  **9 agree / 2 disagree / 1 lateral**, net **+7**
- reverse `#003 → #005 → #004 → #001 → #002 → #007 → #006`:
  **2 agree / 9 disagree / 1 lateral**, net **−7**
- score margin: **14**
- four MST edges have both warning sides agreeing with the preferred direction
- only one MST edge contains a directional disagreement: **#006 ↔ #007**

Accordingly the current **high-confidence direction hypothesis** is from the
#006 side toward #003, with the important caveat that #006↔#007 is anomalous
and already corresponds to the `Hint: Look down!` vertical-transition area.
This is still not promoted to confirmed checkpoint order until an independent
gameplay/route signal corroborates it.


Run #281 / commit `1e0c3506b86d4e185cdc6c424a772640491aeb1c`
adds a deliberately narrow static route-structure family
(`barrier`, `iron_bars`, fence gates) to test whether one MST endpoint has a
distinct start/finish enclosure. The full archive contains **11,063** such
blocks, so this family is dominated by broad map boundary / anti-escape
construction rather than a unique start gate.

Within the same 20-block endpoint radius:

- #003 contains **705** route-structure blocks; nearest is a
  `minecraft:barrier` at `30,29,-9`, **4.15** blocks from the portal center
- #006 contains **615** route-structure blocks; nearest is a
  `minecraft:barrier` at `82,29,86`, **4.21** blocks from the portal center

Because both endpoints are similarly saturated, generic barrier/iron-bar/fence
geometry is **negative evidence for endpoint classification**. It is retained
as map-boundary evidence but must not be used to call either endpoint start or
finish.

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
- **21** Runner warning signs
- all **21/21** warnings have at least one shared-text action candidate
- all **21/21** warnings have an action sign within 30 blocks

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

- **21** pair candidates
- **0** unmatched warning signs
- **2** unmatched action signs

Run #262 / commit `2b633b5f3af0fb705d96804d8907597f464de34a`
recovered a previously missed warning prefix, `Beware!`. The sign at
`70,25,53` reads `Beware! Fire Snake is waiting for launch!` and pairs with
`Release fire snake` at `76,25,56` using the independent shared tokens
`fire + snake` at distance **6.71**.

The 21 candidates are:

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
| `70,25,53` | Release fire snake | `76,25,56` | fire, snake | 6.71 |
| `93,25,56` | Remove red blocks | `89,25,65` | block, red | 9.85 |

The two action signs left unmatched by that review heuristic are:

- `Drop TNT` at `29,44,9`
- `Remove Sea Lanterns` at `72,25,76`

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
- independent Runner-side warning: `70,25,53`, explicitly naming the Fire
  Snake and pairing to the action at distance **6.71**

The recovered warning strengthens the action-instance identification, but the
button remains only a **candidate binding** until visual or behavioral evidence
confirms it. Three other signs reduce to small multi-button panel sets; most do
not.

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
world retains a physical Death stage-control/navigation layer.

Run #271 / commit `c147331f982fffb581d933f96219255ab93c05ed`
adds independent stage-marker ↔ portal-gate geometry and passes the full Java 25
build plus Paper 26.2 restart smoke:

- `Previous Stage` at `27.500,30.250,-11.500` has Portal **#003** as its
  unique portal inside 30 blocks, only **6.89** blocks away
  (`bbox=31,25,-10:37,33,-10`)
- `Next Stage` at `24.500,45.250,13.500` has Portal **#007** as its unique
  portal inside 30 blocks, **24.62** blocks away
  (`bbox=32,45,36:36,52,36`)
- the two markers therefore select **two distinct portal gates**

Portal #003 is now a particularly strong Death-stage boundary candidate because
the named Previous Stage control marker is almost adjacent to it. Portal #007 is
a weaker but still unique high-stage association. This still does **not**
establish checkpoint numbering, Runner respawn positions, route order, Death
spawn, or exact button-to-action mapping.

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

Using the current 21 warning/action candidates and a 14-block mechanism review
radius:

- **11/21** pairs have at least one nearby mechanism
- **10/21** have at least one nearby dispenser
- **6/21** have at least one dispenser whose preserved facing points
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
not by itself proof of the original effect. The newly recovered second Fire
Snake pair at `70,25,53 -> 76,25,56` has **0** mechanisms inside the
conservative 14-block warning review radius, so no mechanism geometry is
invented for it. For example, the minefield area has a dense dispenser row whose
facing does not point at the warning sign under the current conservative rule.

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
4. Treat the 21 one-to-one pairs as a review queue, not as authoritative trap
   definitions.
5. Use button-facing evidence only to reduce candidates; do not guess remaining
   button bindings.
6. Use dispenser facing/bank geometry as target-lane corroboration for Fire
   Arrows and other mechanism-backed candidates; do not turn it into an exact
   cuboid without independent geometry/behavior evidence.
7. Reconstruct the surviving **7 Java portal-gate candidates** from independent
   evidence and determine their route order / respawn points.
8. Treat the external **9-checkpoint Gardens** claim as version-sensitive until
   Java-specific evidence explains the 7-vs-9 difference.
9. Establish Runner/Death spawns, start barrier and finish region independently.
10. For every trap selected for implementation, confirm:
   - control button
   - target cuboid/geometry
   - activation behavior
   - active duration
   - restoration/reset behavior
11. Run `/dr map check`, `/dr map manifest`, backup/restore verification and
   the human acceptance trace before considering the imported map playable.

## Still unknown / blockers

- Exact Runner spawn(s)
- Exact Death spawn(s)
- Exact Java checkpoint count/order; 7 dense portal-gate candidates survive
- Exact checkpoint trigger regions and checkpoint respawn points
- Checkpoint point values
- Exact finish trigger
- Exact control button for 22/23 action-sign instances
- Exact target region/cuboid for each trap (Fire Arrows target lanes are now
  structurally narrowed by dispenser facing, but not exact)
- Trap active durations, projectile payloads and reset state
- Exact form of the original server-side control logic that is absent from the archive

The archive is now strong enough to recover the **trap concept catalog and a
21-instance warning/action review set**, but not yet strong enough to claim a
faithful formal Classic26 map configuration.
