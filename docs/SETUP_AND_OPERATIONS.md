# DeathRun Classic26 Setup And Operations Guide

This guide matches the current `main` branch of DeathRun Classic26.

## Supported server

- **Paper 26.2**
- **Java 25**
- **WorldEdit 7.4.5**
- DeathRun Classic26 plugin JAR from the latest green GitHub Actions build

Do not use the old Java 21 / Paper 1.21.x setup instructions with this branch.

## Install

1. Stop the Paper server.
2. Put WorldEdit 7.4.5 and the DeathRun JAR in `plugins/`.
3. Start Paper 26.2 once.
4. Confirm the console contains:
   - DeathRun enabled
   - all listeners registered
   - no DeathRun ERROR/SEVERE messages
5. Keep the server stopped while copying/importing a real map world.

## Paper 26.2 world layout

Paper 26.2 stores dimensions under the primary level, for example:

```text
world/
  dimensions/
    minecraft/
      overworld/
      the_nether/
      the_end/
      safari-valley/
```

DeathRun supports both this Paper 26.2 dimension layout and legacy top-level Bukkit world folders when loading existing configured maps.

**Safety rule:** DeathRun will not silently generate a missing production map merely because `map.yml` contains a world name. The world must already exist on disk or be loaded before it can become playable.

## Engineering fixture

Before configuring a real map, verify the plugin itself:

```text
/dr playtest create
/dr playtest verify
/dr join classic26-playtest
/dr start classic26-playtest
```

The verifier checks:

- max 22 / required 11
- 20 Runner spawns / 2 Death spawns
- checkpoints and finish
- start barrier and restore snapshot
- all 17 engineering trap implementations
- all locations bound to the correct world
- live arena runtime

GitHub CI also performs a real backup → restore → verify cycle and a full Paper restart/deserialization cycle.

## Creating a real Classic26 map

Copy/import the real map world while the server is stopped, then start Paper.
The world does **not** need to be preloaded by Multiverse or another plugin.
`/dr map create` first uses an already loaded world; if needed it safely loads
existing legacy/Paper 26.2 world data from disk. If no real world data exists,
creation is refused instead of silently generating an empty production map.

```text
/dr map create <id> <world>
/dr map profile classic <id>
/dr map creator <id> <creator>
/dr map edit <id>
```

`/dr map profile classic <id>` applies only the locked Classic26 room rules:

- max players: 22
- required players to start: 11

It deliberately does **not** invent creator, checkpoint points, finish, traps, or coordinates.

### Waiting lobby

Stand at the waiting location:

```text
/dr setlobby
```

### Runner / Death spawns

Classic26 full-room allocation is 20 Runner + 2 Death. Configure enough unique spawns to avoid stacked players.

Add at your current position:

```text
/dr spawn add runner
/dr spawn add death
```

The legacy aliases remain valid:

```text
/dr addspawn runner
/dr addspawn death
```

Review and correct the recorded positions without editing YAML:

```text
/dr spawn list runner
/dr spawn list death
/dr spawn tp runner <index>
/dr spawn tp death <index>
/dr spawn move runner <index>
/dr spawn move death <index>
/dr spawn delete runner <index>
/dr spawn delete death <index>
/dr spawn clear runner
/dr spawn clear death
```

Spawn indices are 1-based. Add/move is rejected unless you are standing in the map's configured world.

For a 22-player map, preflight requires at least:

- 20 Runner spawn locations
- 2 Death spawn locations

### Checkpoints

Select the checkpoint trigger region with WorldEdit, then:

```text
/dr cp add
/dr cp list
/dr cp setname <id> <name>
/dr cp move <id>
/dr cp setorder <id> <position>
/dr cp points <id> <points>
/dr cp setfinish <id>
```

The finish checkpoint must be the final checkpoint in order.

### Start barrier

Select the full start barrier with WorldEdit:

```text
/dr setbarrier
```

### Traps

Look at the activation button, make the needed WorldEdit selection/arguments for that trap type, then:

```text
/dr trap add <type> ...
/dr trap list
/dr trap tp <id>
/dr trap delete <id>
```

Every trap button and every trap target location must belong to the configured map world.

### Optional teleport pads

```text
/dr addteleport
```

If a teleport pad is configured, both ends must belong to the map world.

## Preflight and finalization

During setup:

```text
/dr map check <id>
/dr map status <id>
/dr map manifest <id>
```

`/dr map manifest <id>` is the full authoring audit. It prints map metadata, waiting lobby,
every Runner/Death spawn, checkpoint order/points/finish/region bounds, trap order/type/button/
target bounds, start barrier, teleport pads, and the final preflight result. It is intended for
final Safari/Temple coordinate review and can be run from the server console.

The current preflight catches, among other things:

- missing/unloaded world
- waiting lobby missing, unresolved, or in the wrong world
- Runner/Death spawn locations missing, unresolved, or in the wrong world
- insufficient spawn capacity for the configured max-player Classic role split
- invalid required-player count
- missing/invalid checkpoints
- finish not set or not last
- checkpoint point list size mismatch
- checkpoint coordinates in the wrong world
- missing/invalid trap buttons or regions
- trap coordinates in the wrong world
- missing/invalid start barrier
- barrier restore snapshot mismatch
- invalid teleport pads
- missing backup when finalizing

Create/refresh the map backup:

```text
/dr map backup <id>
```

Then finalize:

```text
/dr save
```

or, outside edit mode:

```text
/dr map disable <id>
```

Finalization is blocked until critical preflight issues are fixed.

## Backup and restore

Backup:

```text
/dr map backup <id>
```

Restore:

```text
/dr map restore <id>
```

Restrictions:

- no players may be queued or playing on that map
- restore uses the map's real `World#getWorldFolder()` location
- this works with Paper 26.2's `world/dimensions/.../<key>` layout and legacy layouts
- the runtime is rebuilt and locations are rebound after restore

Backups are stored under:

```text
plugins/DeathRun/backup/<world>.zip
```

## Interstellar profile

First configure exactly 8 checkpoints, then run:

```text
/dr map profile interstellar <id>
```

It applies the already-confirmed Interstellar rules:

- Creator: Dlimit
- 8 checkpoints
- checkpoint points: 3 / 7 / 10 / 13 / 16 / 20 / 23 / 26
- finish: checkpoint 8
- max 22
- required 11

It does not create the world, coordinates, trap regions, or trap buttons.

## Real-server acceptance

After the map passes preflight, test with at least two real clients.

Check:

- queue/vote/join flow
- Runner/Death assignment
- countdown and preshow
- HUD
- Left / Back / Right strafes and independent cooldowns
- lives and checkpoint scoring
- finish + remaining-life points
- first finisher clamps remaining round time to 60 seconds
- death/respawn/elimination
- Death navigator and trap jumper
- button activation and hotbar activation
- every configured trap
- round reset
- leave/disconnect/reconnect/recovery
- non-DeathRun players/worlds remain unaffected

## Current formal-map order

1. Safari Valley
2. Temple
3. Interstellar
4. Toxic Factory
5. Yagrium
6. To Bee Or Not To Bee / Gardens

World files and map-specific coordinates remain private content and should not be committed to the public GitHub repository unless their redistribution license is confirmed.
