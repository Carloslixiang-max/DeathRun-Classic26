# To Bee Or Not To Bee / Gardens — research handoff

This file records evidence for importing the archived HiveMC DeathRun map into
Classic26. It deliberately separates confirmed evidence from unknown gameplay
configuration. Do not invent missing coordinates or trap types.

## Confirmed public archive

Pinned original-world evidence currently comes from:

- Repository: `Mqzn/HiveMCMaps`
- Historical commit: `9a5b49593be61716ff3419aff24ce313567df14f`
- Path: `death-run/to-bee-or-not-to-bee/`
- Archived files at that commit: `level.dat`, four Anvil region files, and
  `screenshot.png`.
- The binary world files are downloaded only into ephemeral GitHub Actions
  storage. They are not committed to or uploaded as artifacts by this project.

## Confirmed gameplay metadata

Public Hive community evidence identifies **Gardens / To Bee Or Not To Bee** as
one of the Hive DeathRun maps with **9 checkpoints**:

- https://forum.playhive.com/t/a-map-with-ten-checkpoints/37294
- https://www.speedrun.com/mcm_hivemc/levels

This confirms the checkpoint count and the Gardens/To Bee naming association.
It does **not** establish the exact checkpoint coordinates, checkpoint point
values, Runner/Death spawns, finish region, or trap definitions.

## Archived Anvil evidence

Classic26 CI probes the complete four-region archive without modifying it.

At `a35468bc01f021c4dda3659eb1cac19062f82cb7`, the archived map produced:

- 202 parsed chunks
- 0 failed chunks
- 0 external chunks
- 855 candidate blocks
- 0 command-block entities
- 409 buttons
- 80 pressure plates
- 366 portal blocks

The bounded local review pass (`radius=4`, `vertical-radius=3`) turns the
489 interactive candidates into:

- 110 local evidence groups
- 15 HIGH structural-review groups
- 79 MEDIUM structural-review groups
- 16 LOW structural-review groups
- largest group: 17 candidates

The priority is only a review queue. It is **not** a statement that a group is
a DeathRun trap. Dense decorative architecture can contain buttons and pressure
plates, so trap type must be corroborated visually or behaviorally.

## level.dat evidence

The pinned `level.dat` parses successfully as:

- DataVersion: **2580**
- Minecraft version: **1.16.3**
- LevelName: **HiveMC - DR2BORNOT2B**
- SpawnX/Y/Z: **0,0,0**
- initialized: **0**
- game type: **0**
- difficulty: **1**

Because this archive explicitly reports `initialized=0` together with a zero
world spawn, Classic26 marks this as `spawn_status=placeholder`. It is **not**
usable as a Runner spawn, Death spawn, waiting lobby, or orientation anchor.

## Physical topology evidence

`tools/anvil_topology.py` groups only physically adjacent pressure plates and
portal blocks. This is stricter than the spatial review clustering and is meant
to answer questions such as "how many separate pressure-plate surfaces survive
in the archive?" It still does not label any component as a checkpoint or trap.

## Import procedure

1. Keep the pinned archive immutable and use it only as source evidence.
2. Ignore the placeholder `level.dat` spawn for gameplay configuration.
3. Use physical pressure-plate/portal components to identify reviewable geometry.
4. Review HIGH then MEDIUM local evidence groups in the original world.
5. Correlate candidate groups with visible route geometry and surviving portal
   structures. Do not infer trap effects from block category alone.
6. Establish all 9 checkpoint regions from world/video evidence before creating
   a formal Classic26 map profile.
7. Establish Runner/Death spawns, start barrier, finish region, trap buttons and
   target regions independently.
8. Run `/dr map check`, `/dr map manifest`, backup/restore verification and
   the human acceptance trace before considering the imported map playable.

## Still unknown

- Exact Runner spawn(s)
- Exact Death spawn(s)
- Exact 9 checkpoint trigger regions and spawn points
- Checkpoint point values
- Which checkpoint is represented by which surviving geometry
- Exact finish trigger
- Exact trap count/order/types
- Exact trap target regions and reset behavior
- Whether any original control logic was stripped before the public archive

These remain blockers for a faithful formal map configuration.
