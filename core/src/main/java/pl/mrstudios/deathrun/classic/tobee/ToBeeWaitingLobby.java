package pl.mrstudios.deathrun.classic.tobee;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import pl.mrstudios.deathrun.config.AtomicConfigurationSave;
import pl.mrstudios.deathrun.config.impl.MapConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Archive courtyard with the creator sign and twenty gold plates, outside the race route. */
public final class ToBeeWaitingLobby {
    private ToBeeWaitingLobby() {}

    public static boolean needsCourtyard(Location location) {
        if (location == null) return true;
        // Previous generated waiting locations occupied the gate-006 Runner platform.
        return location.getBlockX() >= 85 && location.getBlockX() <= 95
                && location.getBlockY() >= 24 && location.getBlockY() <= 27
                && location.getBlockZ() >= 76 && location.getBlockZ() <= 85;
    }

    public static boolean courtyardMatches(World world) {
        return world.getBlockAt(34, 34, 59).getType() == Material.STONE_BRICKS
                && world.getBlockAt(34, 35, 59).isPassable()
                && world.getBlockAt(34, 36, 59).isPassable()
                && world.getBlockAt(34, 35, 45).getType() == Material.LIGHT_WEIGHTED_PRESSURE_PLATE
                && world.getBlockAt(34, 35, 57).getType() == Material.LIGHT_WEIGHTED_PRESSURE_PLATE
                && world.getBlockAt(28, 35, 51).getType() == Material.LIGHT_WEIGHTED_PRESSURE_PLATE
                && world.getBlockAt(40, 35, 51).getType() == Material.LIGHT_WEIGHTED_PRESSURE_PLATE;
    }

    public static Location choose(World world, Location configured) {
        if (configured != null && configured.getWorld() != null
                && configured.getWorld().getUID().equals(world.getUID()) && !needsCourtyard(configured))
            return configured.clone();
        if (!courtyardMatches(world)) throw new IllegalStateException("To Bee waiting courtyard is obstructed or missing");
        return new Location(world, 34.5, 35.0, 59.5, 180f, 0f);
    }

    /** Called only after all map worlds/locations have been bound, before players can join. */
    public static boolean migrate(MapConfiguration configuration, World world) throws IOException {
        var map = configuration.getMapById(ToBeeCandidateProfileService.MAP_ID);
        if (map == null || world == null || !world.getName().equals(map.world)) return false;
        Location previous = map.arenaWaitingLobbyLocation;
        String previousName = map.name;
        Location selected = choose(world, previous);
        if (selected.equals(previous) && ToBeeCandidateProfileService.MAP_NAME.equals(previousName)) return false;
        Path target = configuration.getBindFile();
        if (target == null) throw new IOException("Map configuration is not bound to a file");
        Path backup = target.resolveSibling(target.getFileName() + ".before-courtyard-v1.bak");
        if (!Files.exists(backup)) Files.copy(target, backup);
        map.arenaWaitingLobbyLocation = selected;
        map.name = ToBeeCandidateProfileService.MAP_NAME;
        try {
            AtomicConfigurationSave.save(configuration);
        } catch (IOException | RuntimeException failure) {
            map.arenaWaitingLobbyLocation = previous;
            map.name = previousName;
            throw failure;
        }
        return true;
    }
}
