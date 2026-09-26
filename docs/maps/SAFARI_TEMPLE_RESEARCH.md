# Safari Valley / Temple research handoff

This file records only facts currently supported by public sources. It is not a substitute for the original/recreated world data and must not be used to invent trap or checkpoint coordinates.

## Primary recreation source

EpicBuilderHD's public Planet Minecraft project:

- Project: The Hive DeathRun [Playable] (Working for 1.17.x)
- URL: https://www.planetminecraft.com/project/the-hive-death-run-playable-working-for-1-16-5/
- The author explicitly says the project contains **Safari Valley** and **Temple** and was made to resemble the original Hive Java DeathRun.
- The project page reports the hub/spawn location as **-43 21 -654**.
- The project exposes `/trigger Safari` and `/trigger Temple` for its legacy datapack implementation.
- The page links a 1.16.5 archive named `DeathRun by EpicBuilderHD.zip` on MediaFire (about 40.21 MB).
- The public page currently remains the only indexed download source found; no GitHub/datapack mirror has been confirmed.

### Behavior evidence from the project comments / update log

The source page documents or confirms:

- Temple had a **Fire Snake / arrow** trap cooldown bug that was later fixed in the recreation.
- The signs beside traps are informational markers, not the activation mechanism.
- The recreation intended Death trap activation/navigation to be inventory-item driven.
- Safari and Temple had some different legacy end/reset behavior in the recreation; these old datapack differences must **not** be copied into Classic26. Final round/finish/reset behavior remains owned by the Paper plugin.
- Safari's recreation had an old start-wall reset bug that Temple did not; Classic26's barrier restore system should be treated as authoritative instead.

These points are useful as evidence for geometry/trap identity only. The old 1.16/1.17 datapack behavior is not the runtime source of truth.

## Original Hive Java evidence

Speedrun.com still lists both maps under the historical **HiveMC DeathRun (2014)** Java level set:

- Levels: https://www.speedrun.com/mcm_hivemc/levels
- Safari Valley Java example: https://www.speedrun.com/mcm_hivemc/runs/z0938q4z

The leaderboard currently shows historical Java records including Safari Valley and Temple. This confirms the map names and provides original-Hive route footage/evidence where individual run media remains available, but it does **not** provide authoritative block coordinates.

## Visual evidence

Planet Minecraft exposes screenshots for both maps:

- Safari Valley: canyon / water / stone-path environment.
- Temple: jungle / temple / stone-path environment.

Screenshots are orientation references only. Do not derive exact checkpoint or trap coordinates from image pixels when world data can be measured directly.

## Current blocker

Exact production configuration still requires the actual world/datapack bytes or another coordinate-bearing source.

The current execution environment can read the Planet Minecraft and MediaFire metadata pages but MediaFire's signed binary download redirects are rejected by the available downloader. Searches have not found a separate indexed mirror.

Therefore the following are still **unknown and must not be invented**:

- exact Safari Valley Runner/Death spawn coordinates
- exact Safari Valley checkpoint regions/spawns/points
- exact Safari Valley trap button positions and target regions
- exact Safari Valley start barrier
- exact Safari Valley teleport pads, if any
- equivalent Temple coordinates

## Import procedure once world data is available

Keep third-party map bytes private; do not commit them to this public repository unless redistribution rights are separately confirmed.

1. Copy the world into the private Paper 26.2 server world storage.
2. Let DeathRun safely load the existing world:
   `/dr map create safari-valley <world>`
3. Apply locked room rules:
   `/dr map profile classic safari-valley`
4. Enter edit mode and author measured coordinates using the in-game tools.
5. Run:
   `/dr map check safari-valley`
6. Run:
   `/dr map manifest safari-valley`
7. Compare the manifest with measured world/datapack evidence.
8. Create the backup and finalize only after preflight is healthy.
9. Repeat for Temple.

## Classic26 authority

For this Paper 26.2 project, the plugin remains authoritative for:

- 22 max / 11 required
- 20 Runner + 2 Death capacity
- Runner lives/checkpoint/finish scoring
- Death Navigator / Trap Jumper / activation
- start barrier restore
- finish timing and round reset
- player state restoration
- all trap runtime behavior

The recreation's old command blocks/datapack are research evidence, not runtime dependencies.
