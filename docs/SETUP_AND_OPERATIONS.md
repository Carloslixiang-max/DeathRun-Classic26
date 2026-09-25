# DeathRun Setup And Operations Guide

This document contains full installation, setup, administration, and testing notes.

## Requirements

- Java 21
- Paper 1.21.10 (recommended)
- WorldEdit 7.2.9+

Spigot may work, but current development and testing are focused on Paper.

## Build

```bash
./gradlew :core:shadowJar -x test
```

Built jar:

```text
core/build/libs/deathrun-core-1.3.3-PATCHED.jar
```

## Install

1. Place the jar in your server plugins folder.
2. Install WorldEdit.
3. Start server once to generate config files.
4. Configure maps with setup commands.

## Copy-Paste Setup Commands (Functional Lobby)

Replace `<map_id>` and `<world_name>` with your values.

1. Create and select map:

```text
/deathrun setup maps create <map_id> <world_name>
/deathrun setup maps use <map_id>
```

2. Set waiting lobby (stand in lobby location first):

```text
/deathrun setup setwaitinglobby
```

3. Add spawns (stand in each spawn position before command):

```text
/deathrun setup addspawn RUNNER
/deathrun setup addspawn DEATH
```

4. Set start barrier (select barrier with WorldEdit first):

```text
/deathrun setup setstartbarrier
```

5. Add at least one checkpoint (select checkpoint region with WorldEdit first):

```text
/deathrun setup addcheckpoint
```

6. Validate setup:

```text
/deathrun setup maps check <map_id>
/deathrun setup maps status <map_id>
```

7. Auto-fix snapshot/backup (recommended):

```text
/deathrun setup maps autofix <map_id>
```

8. Finalize map (runs preflight, creates backup, disables setup mode):

```text
/deathrun setup save
```

9. Final verification:

```text
/deathrun setup maps status <map_id>
```

Expected for fully functional map: `setup-disabled` and healthy/no issues.

Optional, if you need to re-edit an already finalized map:

```text
/deathrun setup maps enable <map_id>
/deathrun setup maps use <map_id>
```

## Core Player Commands

- /deathrun
- /deathrun join <map>
- /deathrun join lobby
- /deathrun start
- /deathrun start <map>
- /deathrun stop
- /deathrun stop <map>
- /deathrun reload
- /deathrun maps
- /deathrun leave

For hub plugins (DeluxeHub, etc.) you can use placeholder-friendly forced join:

- /deathrun join <map> <player>

Example:

```text
/deathrun join map1 %player%
```

## Admin Setup Commands

All setup commands require permission `mrstudios.command.deathrun.setup`.

### Map Management

- /deathrun setup maps list
- /deathrun setup maps use <id>
- /deathrun setup maps create <id> <world>
- /deathrun setup maps delete <id>
- /deathrun setup maps enable <id>
- /deathrun setup maps disable <id>

### Health And Status

- /deathrun setup maps check
- /deathrun setup maps check <id>
- /deathrun setup maps status
- /deathrun setup maps status <id>

### Maintenance And Recovery

- /deathrun setup maps fixbarrier <id>
- /deathrun setup maps backup <id>
- /deathrun setup maps autofix <id>
- /deathrun setup maps restore <id>

### Map Content Editing

- /deathrun setup setname <name>
- /deathrun setup setwaitinglobby
- /deathrun setup setstartbarrier (material)
- /deathrun setup addspawn <death|runner>
- /deathrun setup addtrap <type> (...args)
- /deathrun setup addcheckpoint
- /deathrun setup addteleport
- /deathrun setup save

## Multi-Map Runtime Model

- One active match runtime per map world.
- Players choose a map through /deathrun maps selector.
- Runtime tracks state independently per map: WAITING, STARTING, PLAYING, ENDING.
- End of match returns map runtime to WAITING.

## Safety Features

### Preflight Gates

Map finalize operations are blocked when critical requirements are missing.

/deathrun setup save and /deathrun setup maps disable <id> validate:

- world configured and loaded
- waiting lobby configured
- runner spawn exists
- death spawn exists
- checkpoints exist
- start barrier exists
- barrier restore snapshot consistency
- backup existence (for disable path)

### Recovery Tools

- fixbarrier rebuilds barrier restore snapshot from live blocks
- backup refreshes backup zip for target world
- autofix applies safe fixes in one pass
- restore reloads world from backup zip, rebinds map references, and reloads runtime

## Recommended Admin Workflow

1. Create/select map.
2. Configure map content.
3. Run /deathrun setup maps check <id>.
4. Run /deathrun setup maps autofix <id> if needed.
5. Run /deathrun setup save.
6. Verify with /deathrun setup maps status <id>.

## Quick Test Plan

1. Start server with plugin + WorldEdit.
2. Confirm no startup errors.
3. Run /deathrun setup maps status and /deathrun setup maps check.
4. Open selector with /deathrun maps and join map.
5. Start a round and verify:
   - match flow runs
   - end transitions back to WAITING
   - barrier restore behaves correctly
6. Test maintenance:
   - /deathrun setup maps backup <id>
   - /deathrun setup maps restore <id> when map has no players

## Configuration Files

Generated under plugin data folder:

- config.yml: gameplay timings, sounds, boosters, effects
- language.yml: all message and UI text
- map.yml: map definitions, setup data, checkpoints, traps, barriers, backups

### Scoreboard Config (language.yml)

Scoreboard is configured in `language.yml` using:

- `arena-scoreboard-enabled`
- `arena-scoreboard-update-ticks`
- `arena-scoreboard-title`
- `arena-scoreboard-lines-waiting`
- `arena-scoreboard-lines-starting`
- `arena-scoreboard-lines-playing`

Available placeholders include:

- `<map>`
- `<currentPlayers>`
- `<maxPlayers>`
- `<timer>`
- `<time>`
- `<timeFormatted>`
- `<runners>`
- `<deaths>` (death counter for current viewer)
- `<deathPlayers>` (count of players with death role)
- `<role>`

## Known Notes

- WorldEdit is required and checked on plugin enable.
- Map restore is blocked while players are active in that map runtime.
- If map IDs are missing in old config format, they are normalized automatically.
