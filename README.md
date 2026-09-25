![Header](./.github/assets/image/header.png)

# DeathRun Classic26

DeathRun Classic26 is an AGPLv3 fork of CIlie23/DeathRun 1.4.1, migrated to
Paper 26.2 / Java 25 and extended with a HiveMC Java Classic-style gameplay
layer. It is designed to coexist with other minigames on a normal Paper server
instead of assuming the whole server is dedicated to DeathRun.

## Runtime requirements

- Paper 26.2
- Java 25
- WorldEdit 7.4.5
- PlaceholderAPI is optional
- NoteBlockAPI is not required by the Classic26 runtime

The project builds with the bundled Gradle 9 wrapper:

```bash
./gradlew clean test shadowJar
```

The plugin JAR is produced under `core/build/libs/`.

## Classic rules implemented

- Up to 22 players; default automatic start threshold is 11.
- A full Classic room is intended for 20 Runners and 2 Deaths.
- Match timer: 300 seconds.
- The first Runner to finish clamps remaining time to 60 seconds when more
  than 60 seconds remain.
- Runners start with 2 Lives.
- Each normal checkpoint grants +2 Lives.
- Runner death costs 1 Life.
- Finish adds remaining Lives to the Runner's round points.
- Left / Back / Right Strafe are in hotbar slots 4 / 5 / 6, use independent
  60-second cooldowns, 1.78 horizontal velocity and 0.30 vertical velocity.
- Death controls use Previous Trap in slot 1, Activate in slots 2-4 and 6-8,
  Trap Jumper in slot 5, and Next Trap in slot 9.
- Five-map vote UI plus a sixth Random option is available with `/dr vote`.
- Map preshow displays the selected map and creator.

## Safety on a shared server

Classic26 scopes inventory restrictions, damage rules, projectile cleanup,
explosion protection, world autosave changes, and gameplay listeners to active
DeathRun players/worlds/entities. Entering DeathRun writes a durable recovery
snapshot before destructive player-state changes. Recovery files live in:

```text
plugins/DeathRun/recovery/<UUID>.yml
```

A recovery file is deleted only after restoration succeeds. Inventory, armor,
offhand, location, game mode, flight state, XP, health/food, effects, movement
speeds, fire/fall state, and scoreboard data are restored.

## Engineering playtest

For a quick functional test without importing a production map:

```text
/dr playtest create
/dr join classic26-playtest
/dr start classic26-playtest
```

The generated engineering course exercises checkpoints, Lives/Points, Strafe,
Death navigation, powerup blocks, and the Classic trap set including
Disappearing Parkour, Knock Back, Arrow Dispenser, Fire Floor, Flood, Wall
Spawn, Launch Players, Giant, Fire Trail, TNT, Glass Floor, Quicksand,
Block Replace and Minefield.

## Main commands

```text
/dr maps
/dr vote
/dr join <map>
/dr leave
/dr start [map]
/dr stop [map]
/dr reload
/dr map list
/dr map status <id>
/dr map profile interstellar <id>
/dr cp ...
/dr trap ...
```

Setup/admin commands require the corresponding operator permissions declared in
`plugin.yml`.

## Classic map vote rotation

The plugin configuration supports an optional ordered map-id list:

```yaml
classic-vote-map-candidates: 5
classic-vote-map-ids:
  - safari-valley
  - temple
  - interstellar
  - toxic-factory
  - yagrium
```

When `classic-vote-map-ids` is empty, the first five eligible maps are chosen
alphabetically. Only fully configured, unlocked maps in WAITING state can
appear.

## Interstellar profile

After importing/configuring an Interstellar world and defining exactly eight
checkpoints:

```text
/dr map profile interstellar <map-id>
```

applies the confirmed Classic profile:

- Creator: Dlimit
- Checkpoint points: 3, 7, 10, 13, 16, 20, 23, 26
- Finish: checkpoint 8
- Max players: 22
- Required players: 11

## Map assets and licensing

This repository contains plugin code and the generated engineering playtest,
not third-party recreated Hive/CubeCraft map worlds. World downloads or
recreations with unclear redistribution rights are research/import sources only
and should not be committed or released here without permission.

## CI

Every push to `main` is checked by GitHub Actions using Java 25. CI runs the
unit tests, builds the shaded plugin, verifies the package, downloads Paper
26.2 and WorldEdit 7.4.5, and performs a clean Paper smoke boot.

## License and upstream

This fork remains under GNU AGPLv3 in accordance with the upstream project.
Keep the license and copyright notices when distributing modified builds.
