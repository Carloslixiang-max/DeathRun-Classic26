package pl.mrstudios.deathrun.classic.playtest;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.arena.checkpoint.Checkpoint;
import pl.mrstudios.deathrun.arena.trap.impl.TrapDisappearingParkour;
import pl.mrstudios.deathrun.arena.trap.impl.TrapFireFloor;
import pl.mrstudios.deathrun.arena.trap.impl.TrapKnockBack;
import pl.mrstudios.deathrun.config.Configuration;
import pl.mrstudios.deathrun.config.impl.MapConfiguration;

import java.util.ArrayList;
import java.util.List;

public final class ClassicPlaytestService {

    public static final String MAP_ID = "classic26-playtest";
    public static final String WORLD_NAME = "deathrun_playtest";
    private static final int FLOOR_Y = 200;

    private final Plugin plugin;
    private final Configuration configuration;
    private final ArenaManager arenaManager;

    public ClassicPlaytestService(@NotNull Plugin plugin, @NotNull Configuration configuration, @NotNull ArenaManager arenaManager) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.arenaManager = arenaManager;
    }

    public @NotNull Result create(@NotNull Player actor) {
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
                    map.arenaRunnerSpawnLocations.add(new Location(world, x + 0.5, FLOOR_Y + 1, row + 0.5, 0f, 0f));

            map.arenaDeathSpawnLocations.add(new Location(world, 9.5, FLOOR_Y + 4, 20.5, 0f, 0f));
            map.arenaDeathSpawnLocations.add(new Location(world, 9.5, FLOOR_Y + 4, 50.5, 180f, 0f));

            map.arenaCheckpoints.add(checkpoint(world, 1, 20, "Checkpoint 1"));
            map.arenaCheckpoints.add(checkpoint(world, 2, 40, "Checkpoint 2"));
            map.arenaCheckpoints.add(checkpoint(world, 3, 68, "Finish"));
            map.arenaCheckpointPoints = new ArrayList<>(List.of(3, 7, 10));
            map.arenaFinishCheckpointId = 3;

            for (int x = -3; x <= 3; x++)
                for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 4; y++) {
                    Location barrier = new Location(world, x, y, 5);
                    barrier.getBlock().setType(Material.BARRIER);
                    map.arenaStartBarrierBlocks.add(barrier);
                    map.arenaStartBarrierRestoreMaterials.add(Material.BARRIER);
                }

            TrapDisappearingParkour disappearing = new TrapDisappearingParkour();
            disappearing.setButton(button(world, 29));
            disappearing.setLocations(floorRegion(world, 26, 29));
            map.arenaTraps.add(disappearing);

            TrapKnockBack knockBack = new TrapKnockBack();
            knockBack.setButton(button(world, 46));
            knockBack.setLocations(hitboxRegion(world, 44, 47));
            map.arenaTraps.add(knockBack);

            TrapFireFloor fireFloor = new TrapFireFloor();
            fireFloor.setButton(button(world, 57));
            fireFloor.setLocations(floorRegion(world, 55, 59));
            map.arenaTraps.add(fireFloor);

            map.arenaMaxPlayers = 22;
            map.arenaRequiredPlayersToStart = 11;

            this.configuration.map().ensureMapsMutable();
            this.configuration.map().maps.removeIf(existing -> MAP_ID.equalsIgnoreCase(this.configuration.map().normalizedMapId(existing.id)));
            this.configuration.map().maps.add(map);
            this.configuration.map().save();
            this.arenaManager.reloadRuntime(MAP_ID);

            actor.teleport(map.arenaWaitingLobbyLocation);
            this.plugin.getLogger().info("[DeathRun] Rebuilt engineering playtest map " + MAP_ID + " in " + WORLD_NAME);
            return new Result(true, "ready");
        } catch (Exception exception) {
            this.plugin.getLogger().severe("[DeathRun] Playtest map creation failed: " + exception.getMessage());
            return new Result(false, exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private void buildCourse(World world) {
        for (int x = -15; x <= 15; x++)
            for (int z = -20; z <= 80; z++)
                for (int y = FLOOR_Y - 15; y <= FLOOR_Y + 8; y++)
                    world.getBlockAt(x, y, z).setType(Material.AIR);

        fill(world, -5, 5, FLOOR_Y, FLOOR_Y, -15, -8, Material.SMOOTH_STONE);
        fill(world, -3, 3, FLOOR_Y, FLOOR_Y, -1, 70, Material.SMOOTH_STONE);
        fill(world, 8, 10, FLOOR_Y + 3, FLOOR_Y + 3, 0, 70, Material.POLISHED_BLACKSTONE);

        for (int z : new int[]{29, 46, 57}) {
            world.getBlockAt(8, FLOOR_Y + 3, z).setType(Material.POLISHED_BLACKSTONE);
            world.getBlockAt(8, FLOOR_Y + 4, z).setType(Material.STONE_BUTTON);
        }

        for (int z : new int[]{20, 40, 68})
            for (int x = -3; x <= 3; x++)
                world.getBlockAt(x, FLOOR_Y, z).setType(Material.GOLD_BLOCK);

        world.getBlockAt(0, FLOOR_Y, 68).setType(Material.EMERALD_BLOCK);
    }

    private void fill(World world, int minX, int maxX, int minY, int maxY, int minZ, int maxZ, Material material) {
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++)
                    world.getBlockAt(x, y, z).setType(material);
    }

    private Checkpoint checkpoint(World world, int id, int z, String name) {
        return new Checkpoint(id, new Location(world, 0.5, FLOOR_Y + 1, z + 1.5, 0f, 0f), List.of(
                new Location(world, -3, FLOOR_Y + 1, z),
                new Location(world, 3, FLOOR_Y + 4, z)
        ), name);
    }

    private Location button(World world, int z) { return new Location(world, 8, FLOOR_Y + 4, z); }

    private List<Location> floorRegion(World world, int minZ, int maxZ) {
        List<Location> locations = new ArrayList<>();
        for (int x = -3; x <= 3; x++)
            for (int z = minZ; z <= maxZ; z++)
                locations.add(new Location(world, x, FLOOR_Y, z));
        return locations;
    }

    private List<Location> hitboxRegion(World world, int minZ, int maxZ) {
        return List.of(new Location(world, -3, FLOOR_Y + 1, minZ), new Location(world, 3, FLOOR_Y + 3, maxZ));
    }

    public record Result(boolean success, @NotNull String message) {}
}
