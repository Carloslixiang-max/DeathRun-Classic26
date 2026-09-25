package pl.mrstudios.deathrun.config.impl;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Header;
import eu.okaeri.configs.annotation.Names;
import org.bukkit.Location;
import org.bukkit.Material;
import pl.mrstudios.deathrun.api.arena.trap.ITrap;
import pl.mrstudios.deathrun.arena.checkpoint.Checkpoint;
import pl.mrstudios.deathrun.arena.pad.TeleportPad;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static eu.okaeri.configs.annotation.NameModifier.TO_LOWER_CASE;
import static eu.okaeri.configs.annotation.NameStrategy.HYPHEN_CASE;

@Header({
        " ",
        "--------------------------------------------------------------------------",
        "                                INFORMATION",
        "--------------------------------------------------------------------------",
        " ",
        " Please dont modify this file, any modifications may cause problems with",
        " plugin, modify only if you know what you are doing.",
        " ",
        "--------------------------------------------------------------------------",
        " "
}) @SuppressWarnings("deprecation")
@Names(strategy = HYPHEN_CASE, modifier = TO_LOWER_CASE)
public class MapConfiguration extends OkaeriConfig {

    public List<MapDefinition> maps = new ArrayList<>();

    public String arenaName;

    /* Spawns */
    public Location arenaWaitingLobbyLocation;

    public List<Location> arenaRunnerSpawnLocations = new ArrayList<>();;
    public List<Location> arenaDeathSpawnLocations = new ArrayList<>();;

    /* Traps */
    public List<ITrap> arenaTraps = new ArrayList<>();;

    /* Checkpoints */
    public List<Checkpoint> arenaCheckpoints = new ArrayList<>();;
    public Integer arenaFinishCheckpointId;

    /* Misc */
    public List<TeleportPad> teleportPads = new ArrayList<>();
    public List<Location> arenaStartBarrierBlocks = new ArrayList<>();;
    public List<Material> arenaStartBarrierRestoreMaterials = new ArrayList<>();
    public String arenaBackgroundSongFileName;
    public Boolean arenaBackgroundSongLoop;
    public Integer arenaMaxPlayers;
    public Integer arenaRequiredPlayersToStart;

    /* Setup Status */
    public boolean arenaSetupEnabled = true;

    public List<MapDefinition> resolvedMaps() {
        if (!this.maps.isEmpty()) {
            this.maps.forEach((map) -> {
                if (map.id == null || map.id.isBlank())
                    map.id = this.normalizedMapId(map.name);

                this.ensureMutableSetupLists(map);
            });
            return this.maps;
        }

        MapDefinition legacyMap = new MapDefinition();
        legacyMap.id = this.normalizedMapId(this.arenaName);
        legacyMap.name = this.arenaName;
        legacyMap.world = this.arenaWaitingLobbyLocation == null ? "" : this.arenaWaitingLobbyLocation.getWorld().getName();
        legacyMap.arenaWaitingLobbyLocation = this.arenaWaitingLobbyLocation;
        legacyMap.arenaRunnerSpawnLocations = new ArrayList<>(this.arenaRunnerSpawnLocations);
        legacyMap.arenaDeathSpawnLocations = new ArrayList<>(this.arenaDeathSpawnLocations);
        legacyMap.arenaTraps = new ArrayList<>(this.arenaTraps);
        legacyMap.arenaCheckpoints = new ArrayList<>(this.arenaCheckpoints);
        legacyMap.arenaFinishCheckpointId = this.arenaFinishCheckpointId;
        legacyMap.teleportPads = new ArrayList<>(this.teleportPads);
        legacyMap.arenaStartBarrierBlocks = new ArrayList<>(this.arenaStartBarrierBlocks);
        legacyMap.arenaStartBarrierRestoreMaterials = new ArrayList<>(this.arenaStartBarrierRestoreMaterials);
        legacyMap.arenaBackgroundSongFileName = this.arenaBackgroundSongFileName;
        legacyMap.arenaBackgroundSongLoop = this.arenaBackgroundSongLoop;
        legacyMap.arenaMaxPlayers = null;
        legacyMap.arenaRequiredPlayersToStart = null;
        legacyMap.arenaSetupEnabled = this.arenaSetupEnabled;
        this.ensureMutableSetupLists(legacyMap);
        return List.of(legacyMap);
    }

    public MapDefinition getMapById(String id) {
        return this.resolvedMaps().stream()
                .filter((map) -> this.normalizedMapId(map.id).equalsIgnoreCase(this.normalizedMapId(id)))
                .findFirst()
                .orElse(null);
    }

    public void ensureMapsMutable() {
        if (!this.maps.isEmpty()) {
            this.maps.forEach((map) -> {
                map.id = this.normalizedMapId(map.id);
                this.ensureMutableSetupLists(map);
            });
            return;
        }

        MapDefinition legacy = this.resolvedMaps().get(0);
        MapDefinition migrated = new MapDefinition();

        migrated.id = this.normalizedMapId(legacy.id);
        migrated.name = legacy.name;
        migrated.world = legacy.world;
        migrated.arenaWaitingLobbyLocation = legacy.arenaWaitingLobbyLocation;
        migrated.arenaRunnerSpawnLocations = new ArrayList<>(legacy.arenaRunnerSpawnLocations);
        migrated.arenaDeathSpawnLocations = new ArrayList<>(legacy.arenaDeathSpawnLocations);
        migrated.arenaTraps = new ArrayList<>(legacy.arenaTraps);
        migrated.arenaCheckpoints = new ArrayList<>(legacy.arenaCheckpoints);
        migrated.arenaFinishCheckpointId = legacy.arenaFinishCheckpointId;
        migrated.teleportPads = new ArrayList<>(legacy.teleportPads);
        migrated.arenaStartBarrierBlocks = new ArrayList<>(legacy.arenaStartBarrierBlocks);
        migrated.arenaStartBarrierRestoreMaterials = new ArrayList<>(legacy.arenaStartBarrierRestoreMaterials);
        migrated.arenaBackgroundSongFileName = legacy.arenaBackgroundSongFileName;
        migrated.arenaBackgroundSongLoop = legacy.arenaBackgroundSongLoop;
        migrated.arenaMaxPlayers = legacy.arenaMaxPlayers;
        migrated.arenaRequiredPlayersToStart = legacy.arenaRequiredPlayersToStart;
        migrated.arenaSetupEnabled = legacy.arenaSetupEnabled;
        this.ensureMutableSetupLists(migrated);

        this.maps.add(migrated);
    }

    private void ensureMutableSetupLists(
            MapDefinition map
    ) {
        map.arenaRunnerSpawnLocations = new ArrayList<>(map.arenaRunnerSpawnLocations);
        map.arenaDeathSpawnLocations = new ArrayList<>(map.arenaDeathSpawnLocations);
        map.arenaCheckpoints = new ArrayList<>(map.arenaCheckpoints);
        map.arenaTraps = new ArrayList<>(map.arenaTraps);
        map.teleportPads = new ArrayList<>(map.teleportPads);
        map.arenaStartBarrierBlocks = new ArrayList<>(map.arenaStartBarrierBlocks);
        map.arenaStartBarrierRestoreMaterials = new ArrayList<>(map.arenaStartBarrierRestoreMaterials);
    }

    public String normalizedMapId(String source) {
        if (source == null || source.isBlank())
            return "default";

        return source.toLowerCase(Locale.ROOT).replace(" ", "-");
    }

    @SuppressWarnings("deprecation")
    @Names(strategy = HYPHEN_CASE, modifier = TO_LOWER_CASE)
    public static class MapDefinition extends OkaeriConfig {

        public String id;
        public String name;
        public String world;

        public Location arenaWaitingLobbyLocation;

        public List<Location> arenaRunnerSpawnLocations = new ArrayList<>();
        public List<Location> arenaDeathSpawnLocations = new ArrayList<>();

        public List<ITrap> arenaTraps = new ArrayList<>();

        public List<Checkpoint> arenaCheckpoints = new ArrayList<>();
        public Integer arenaFinishCheckpointId;

        public List<TeleportPad> teleportPads = new ArrayList<>();
        public List<Location> arenaStartBarrierBlocks = new ArrayList<>();
        public List<Material> arenaStartBarrierRestoreMaterials = new ArrayList<>();
        public String arenaBackgroundSongFileName;
        public Boolean arenaBackgroundSongLoop;
        public Integer arenaMaxPlayers;
        public Integer arenaRequiredPlayersToStart;

        public boolean arenaSetupEnabled = false;

    }

}
