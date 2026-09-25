package pl.mrstudios.deathrun.classic.playtest;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pl.mrstudios.deathrun.api.arena.trap.ITrap;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.arena.checkpoint.Checkpoint;
import pl.mrstudios.deathrun.arena.trap.impl.*;
import pl.mrstudios.deathrun.config.Configuration;
import pl.mrstudios.deathrun.config.impl.MapConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ClassicPlaytestService {

    public static final String MAP_ID = "classic26-playtest";
    public static final String WORLD_NAME = "deathrun_playtest";
    private static final int FLOOR_Y = 200;

    private final Plugin plugin;
    private final Configuration configuration;
    private final ArenaManager arenaManager;

    public ClassicPlaytestService(
            @NotNull Plugin plugin,
            @NotNull Configuration configuration,
            @NotNull ArenaManager arenaManager
    ) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.arenaManager = arenaManager;
    }

    public @NotNull Result create(@Nullable Player actor) {
        try {
            World world = Bukkit.getWorld(WORLD_NAME);
            if (world == null)
                world = Bukkit.createWorld(new WorldCreator(WORLD_NAME).generateStructures(false));
            if (world == null)
                return new Result(false, "Paper could not create " + WORLD_NAME);

            this.buildCourse(world);

            MapConfiguration.MapDefinition map = new MapConfiguration.MapDefinition();
            map.id = MAP_ID;
            map.name = "Classic26 Playtest";
            map.creator = "Classic26 Engineering";
            map.world = world.getName();
            map.arenaSetupEnabled = false;
            map.arenaWaitingLobbyLocation = new Location(world, 0.5, FLOOR_Y + 1, -11.5, 0f, 0f);

            for (int row = 0; row < 4; row++)
                for (int x = -2; x <= 2; x++)
                    map.arenaRunnerSpawnLocations.add(
                            new Location(world, x + 0.5, FLOOR_Y + 1, row + 0.5, 0f, 0f)
                    );

            map.arenaDeathSpawnLocations.add(new Location(world, 9.5, FLOOR_Y + 4, 25.5, 0f, 0f));
            map.arenaDeathSpawnLocations.add(new Location(world, 9.5, FLOOR_Y + 4, 135.5, 180f, 0f));

            map.arenaCheckpoints.add(checkpoint(world, 1, 38, "Checkpoint 1"));
            map.arenaCheckpoints.add(checkpoint(world, 2, 78, "Checkpoint 2"));
            map.arenaCheckpoints.add(checkpoint(world, 3, 118, "Checkpoint 3"));
            map.arenaCheckpoints.add(checkpoint(world, 4, 198, "Finish"));
            map.arenaCheckpointPoints = new ArrayList<>(List.of(3, 7, 10, 13));
            map.arenaFinishCheckpointId = 4;

            for (int x = -3; x <= 3; x++)
                for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 4; y++) {
                    Location barrier = new Location(world, x, y, 5);
                    barrier.getBlock().setType(Material.BARRIER, false);
                    map.arenaStartBarrierBlocks.add(barrier);
                    map.arenaStartBarrierRestoreMaterials.add(Material.BARRIER);
                }

            this.addTrap(map, new TrapDisappearingParkour(), button(world, 14), floorRegion(world, 15, 18));
            this.addTrap(map, new TrapKnockBack(), button(world, 26), hitboxRegion(world, 27, 30));
            this.addTrap(map, new TrapArrows(), button(world, 34), arrowDispensers(world, 35));
            this.addTrap(map, new TrapFireFloor(), button(world, 46), floorRegion(world, 47, 50));
            this.addTrap(map, new TrapFlood(), button(world, 58), feetRegion(world, 59, 62));
            this.addTrap(map, new TrapWallSpawn(), button(world, 70), wallRegion(world, 71));
            this.addTrap(map, new TrapLaunchPlayers(), button(world, 86), hitboxRegion(world, 87, 90));
            this.addTrap(map, new TrapGiant(), button(world, 98), hitboxRegion(world, 99, 102));
            this.addTrap(map, new TrapFireTrail(), button(world, 110), floorRegion(world, 111, 114));
            this.addTrap(map, new TrapTNT(), button(world, 122), tntPads(world, 123));
            this.addTrap(map, new TrapGlassFloor(), button(world, 126), floorRegion(world, 127, 130));
            this.addTrap(map, new TrapQuicksand(), button(world, 138), floorRegion(world, 139, 142));
            this.addTrap(map, new TrapBlockReplace(), button(world, 146), wallRegion(world, 147));
            this.addTrap(map, new TrapMinefield(), button(world, 150), feetRegion(world, 151, 154));

            TrapAppearingBlocks appearing = new TrapAppearingBlocks();
            appearing.setMaterial(Material.IRON_BLOCK);
            this.addTrap(map, appearing, button(world, 158), feetRegion(world, 159, 162));

            TrapDisappearingBlocks disappearing = new TrapDisappearingBlocks();
            this.addTrap(map, disappearing, button(world, 170), floorRegion(world, 171, 174));

            TrapParticles particles = new TrapParticles();
            particles.setExtra(org.bukkit.Particle.FLAME, 8, 0.25d);
            this.addTrap(map, particles, button(world, 182), hitboxRegion(world, 183, 186));

            map.arenaMaxPlayers = 22;
            map.arenaRequiredPlayersToStart = 11;

            this.configuration.map().ensureMapsMutable();
            this.configuration.map().maps.removeIf(existing ->
                    MAP_ID.equalsIgnoreCase(this.configuration.map().normalizedMapId(existing.id))
            );
            this.configuration.map().maps.add(map);
            this.configuration.map().save();
            this.arenaManager.reloadRuntime(MAP_ID);

            if (actor != null)
                actor.teleport(map.arenaWaitingLobbyLocation);

            this.plugin.getLogger().info(
                    "[DeathRun] Rebuilt full Classic26 engineering playtest map "
                            + MAP_ID + " with " + map.arenaTraps.size() + " trap types."
            );
            return new Result(true, "ready");
        } catch (Exception exception) {
            this.plugin.getLogger().severe(
                    "[DeathRun] Playtest map creation failed: "
                            + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
            return new Result(false, exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }


    public @NotNull Result verify() {
        try {
            this.configuration.map().ensureMapsMutable();
            MapConfiguration.MapDefinition map = this.configuration.map().getMapById(MAP_ID);
            if (map == null)
                return new Result(false, "map-not-configured");

            List<String> issues = new ArrayList<>();
            World world = map.world == null || map.world.isBlank() ? null : Bukkit.getWorld(map.world);

            if (world == null)
                issues.add("world-not-loaded");
            else if (!WORLD_NAME.equalsIgnoreCase(world.getName()))
                issues.add("unexpected-world:" + world.getName());

            if (map.arenaSetupEnabled)
                issues.add("setup-enabled");
            if (map.arenaMaxPlayers != 22)
                issues.add("max-players:" + map.arenaMaxPlayers);
            if (map.arenaRequiredPlayersToStart != 11)
                issues.add("required-players:" + map.arenaRequiredPlayersToStart);
            if (map.arenaRunnerSpawnLocations.size() != 20)
                issues.add("runner-spawns:" + map.arenaRunnerSpawnLocations.size());
            if (map.arenaDeathSpawnLocations.size() != 2)
                issues.add("death-spawns:" + map.arenaDeathSpawnLocations.size());

            if (map.arenaCheckpoints.size() != 4)
                issues.add("checkpoints:" + map.arenaCheckpoints.size());
            if (!map.arenaCheckpointPoints.equals(List.of(3, 7, 10, 13)))
                issues.add("checkpoint-points:" + map.arenaCheckpointPoints);

            if (map.arenaCheckpoints.isEmpty() || map.arenaFinishCheckpointId == null) {
                issues.add("finish-not-set");
            } else {
                Integer expectedFinish = map.arenaCheckpoints.get(map.arenaCheckpoints.size() - 1).id();
                if (!expectedFinish.equals(map.arenaFinishCheckpointId))
                    issues.add("finish-not-last:" + map.arenaFinishCheckpointId);
            }

            if (map.arenaStartBarrierBlocks.isEmpty())
                issues.add("barrier-empty");
            if (map.arenaStartBarrierBlocks.size() != map.arenaStartBarrierRestoreMaterials.size())
                issues.add("barrier-restore-size:"
                        + map.arenaStartBarrierBlocks.size() + "/" + map.arenaStartBarrierRestoreMaterials.size());

            if (map.arenaTraps.size() != 17)
                issues.add("trap-count:" + map.arenaTraps.size());

            Set<String> expectedTrapTypes = Set.of(
                    "TrapDisappearingParkour",
                    "TrapKnockBack",
                    "TrapArrows",
                    "TrapFireFloor",
                    "TrapFlood",
                    "TrapWallSpawn",
                    "TrapLaunchPlayers",
                    "TrapGiant",
                    "TrapFireTrail",
                    "TrapTNT",
                    "TrapGlassFloor",
                    "TrapQuicksand",
                    "TrapBlockReplace",
                    "TrapMinefield",
                    "TrapAppearingBlocks",
                    "TrapDisappearingBlocks",
                    "TrapParticles"
            );
            Set<String> actualTrapTypes = new HashSet<>();
            for (ITrap trap : map.arenaTraps) {
                actualTrapTypes.add(trap.getClass().getSimpleName());
                if (!this.locationOnWorld(trap.getButton(), world))
                    issues.add("trap-button-world:" + trap.getClass().getSimpleName());
                if (trap.getLocations() == null || trap.getLocations().isEmpty()) {
                    issues.add("trap-region-empty:" + trap.getClass().getSimpleName());
                } else if (trap.getLocations().stream().anyMatch(location -> !this.locationOnWorld(location, world))) {
                    issues.add("trap-region-world:" + trap.getClass().getSimpleName());
                }
            }
            if (!actualTrapTypes.equals(expectedTrapTypes))
                issues.add("trap-types:" + actualTrapTypes);

            if (map.arenaRunnerSpawnLocations.stream().anyMatch(location -> !this.locationOnWorld(location, world)))
                issues.add("runner-spawn-world");
            if (map.arenaDeathSpawnLocations.stream().anyMatch(location -> !this.locationOnWorld(location, world)))
                issues.add("death-spawn-world");
            if (map.arenaStartBarrierBlocks.stream().anyMatch(location -> !this.locationOnWorld(location, world)))
                issues.add("barrier-world");
            if (map.arenaCheckpoints.stream().anyMatch(checkpoint ->
                    !this.locationOnWorld(checkpoint.spawn(), world)
                            || checkpoint.locations().isEmpty()
                            || checkpoint.locations().stream().anyMatch(location -> !this.locationOnWorld(location, world))))
                issues.add("checkpoint-world");

            if (this.arenaManager.runtimeByMapId(MAP_ID) == null)
                issues.add("runtime-missing");

            if (!issues.isEmpty()) {
                String message = String.join(",", issues);
                this.plugin.getLogger().severe("[DeathRun] Classic26 playtest verification FAILED: " + message);
                return new Result(false, message);
            }

            this.plugin.getLogger().info(
                    "[DeathRun] Classic26 playtest verification PASS: "
                            + "20 runner spawns, 2 death spawns, 4 checkpoints, 17 traps, runtime ready."
            );
            return new Result(true, "verified");
        } catch (Exception exception) {
            this.plugin.getLogger().severe(
                    "[DeathRun] Classic26 playtest verification FAILED: "
                            + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
            return new Result(false, exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private boolean locationOnWorld(@Nullable Location location, @Nullable World world) {
        return location != null
                && world != null
                && location.getWorld() != null
                && location.getWorld().getUID().equals(world.getUID());
    }

    private void buildCourse(@NotNull World world) {
        for (int x = -15; x <= 15; x++)
            for (int z = -20; z <= 210; z++)
                for (int y = FLOOR_Y - 15; y <= FLOOR_Y + 8; y++)
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);

        fill(world, -5, 5, FLOOR_Y, FLOOR_Y, -15, -8, Material.SMOOTH_STONE);
        fill(world, -3, 3, FLOOR_Y, FLOOR_Y, -1, 202, Material.SMOOTH_STONE);
        fill(world, 8, 10, FLOOR_Y + 3, FLOOR_Y + 3, 0, 202, Material.POLISHED_BLACKSTONE);

        for (int z : new int[]{14, 26, 34, 46, 58, 70, 86, 98, 110, 122, 126, 138, 146, 150, 158, 170, 182}) {
            world.getBlockAt(8, FLOOR_Y + 3, z).setType(Material.POLISHED_BLACKSTONE, false);
            world.getBlockAt(8, FLOOR_Y + 4, z).setType(Material.STONE_BUTTON, false);
        }

        for (int z : new int[]{38, 78, 118, 198})
            for (int x = -3; x <= 3; x++)
                world.getBlockAt(x, FLOOR_Y, z).setType(Material.GOLD_BLOCK, false);

        world.getBlockAt(0, FLOOR_Y, 198).setType(Material.EMERALD_BLOCK, false);
    }

    private void addTrap(
            @NotNull MapConfiguration.MapDefinition map,
            @NotNull ITrap trap,
            @NotNull Location button,
            @NotNull List<Location> locations
    ) {
        trap.setButton(button);
        trap.setLocations(trap.filter(locations));
        map.arenaTraps.add(trap);
    }

    private void fill(
            @NotNull World world,
            int minX, int maxX,
            int minY, int maxY,
            int minZ, int maxZ,
            @NotNull Material material
    ) {
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++)
                    world.getBlockAt(x, y, z).setType(material, false);
    }

    private @NotNull Checkpoint checkpoint(@NotNull World world, int id, int z, @NotNull String name) {
        return new Checkpoint(
                id,
                new Location(world, 0.5, FLOOR_Y + 1, z + 1.5, 0f, 0f),
                List.of(
                        new Location(world, -3, FLOOR_Y + 1, z),
                        new Location(world, 3, FLOOR_Y + 4, z)
                ),
                name
        );
    }

    private @NotNull Location button(@NotNull World world, int z) {
        return new Location(world, 8, FLOOR_Y + 4, z);
    }

    private @NotNull List<Location> arrowDispensers(@NotNull World world, int z) {
        List<Location> locations = new ArrayList<>();
        for (int x : new int[]{-5, 5}) {
            var block = world.getBlockAt(x, FLOOR_Y + 1, z);
            block.setType(Material.DISPENSER, false);
            if (block.getBlockData() instanceof Directional directional) {
                directional.setFacing(x < 0 ? BlockFace.EAST : BlockFace.WEST);
                block.setBlockData(directional, false);
            }
            locations.add(block.getLocation());
        }
        return locations;
    }

    private @NotNull List<Location> tntPads(@NotNull World world, int z) {
        List<Location> locations = new ArrayList<>();
        for (int x : new int[]{-4, 4}) {
            var block = world.getBlockAt(x, FLOOR_Y + 1, z);
            block.setType(Material.TNT, false);
            locations.add(block.getLocation());
        }
        return locations;
    }

    private @NotNull List<Location> floorRegion(@NotNull World world, int minZ, int maxZ) {
        List<Location> locations = new ArrayList<>();
        for (int x = -3; x <= 3; x++)
            for (int z = minZ; z <= maxZ; z++)
                locations.add(new Location(world, x, FLOOR_Y, z));
        return locations;
    }

    private @NotNull List<Location> feetRegion(@NotNull World world, int minZ, int maxZ) {
        List<Location> locations = new ArrayList<>();
        for (int x = -3; x <= 3; x++)
            for (int z = minZ; z <= maxZ; z++)
                locations.add(new Location(world, x, FLOOR_Y + 1, z));
        return locations;
    }

    private @NotNull List<Location> wallRegion(@NotNull World world, int z) {
        List<Location> locations = new ArrayList<>();
        for (int x = -3; x <= 3; x++)
            for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 4; y++)
                locations.add(new Location(world, x, y, z));
        return locations;
    }

    private @NotNull List<Location> hitboxRegion(@NotNull World world, int minZ, int maxZ) {
        return List.of(
                new Location(world, -3, FLOOR_Y + 1, minZ),
                new Location(world, 3, FLOOR_Y + 3, maxZ)
        );
    }

    public record Result(boolean success, @NotNull String message) {}
}
