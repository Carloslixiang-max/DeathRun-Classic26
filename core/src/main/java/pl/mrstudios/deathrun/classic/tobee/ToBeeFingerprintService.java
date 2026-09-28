package pl.mrstudios.deathrun.classic.tobee;

import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.deathrun.api.arena.trap.ITrap;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.config.impl.MapConfiguration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Deterministic serialization fingerprint for the promoted To Bee runtime.
 *
 * This is deliberately derived from the loaded map definition rather than
 * from the source constants. CI compares fingerprints before trap exercise,
 * after all 23 trap lifecycles, and after a clean Paper restart. A matching
 * count alone therefore cannot hide reordered or shifted course data.
 */
public final class ToBeeFingerprintService {

    private final ArenaManager arenaManager;

    public ToBeeFingerprintService(@NotNull ArenaManager arenaManager) {
        this.arenaManager = arenaManager;
    }

    public @NotNull Result fingerprint(@NotNull World world) {
        ArenaManager.ArenaRuntime runtime = this.arenaManager.runtimeByMapId(
                ToBeeCandidateProfileService.MAP_ID
        );
        if (runtime == null)
            return new Result(false, "", 0, List.of("runtime-unavailable"));

        if (runtime.map().world == null || !runtime.map().world.equalsIgnoreCase(world.getName()))
            return new Result(false, "", 0, List.of("world-mismatch:" + runtime.map().world));

        String canonical = canonical(runtime.map());
        byte[] bytes = canonical.getBytes(StandardCharsets.UTF_8);
        return new Result(true, sha256(bytes), bytes.length, List.of());
    }

    static @NotNull String canonical(@NotNull MapConfiguration.MapDefinition map) {
        StringBuilder out = new StringBuilder(16_384);
        field(out, "id", map.id);
        field(out, "name", map.name);
        field(out, "creator", map.creator);
        field(out, "world", map.world);
        field(out, "setup", map.arenaSetupEnabled);
        field(out, "maxPlayers", map.arenaMaxPlayers);
        field(out, "requiredPlayers", map.arenaRequiredPlayersToStart);
        field(out, "finishCheckpoint", map.arenaFinishCheckpointId);

        locations(out, "runner", map.arenaRunnerSpawnLocations);
        locations(out, "death", map.arenaDeathSpawnLocations);
        locations(out, "barrier", map.arenaStartBarrierBlocks);

        for (int i = 0; i < map.arenaStartBarrierRestoreMaterials.size(); i++)
            field(out, "barrierMaterial[" + i + "]", map.arenaStartBarrierRestoreMaterials.get(i));
        for (int i = 0; i < map.arenaCheckpointPoints.size(); i++)
            field(out, "checkpointPoints[" + i + "]", map.arenaCheckpointPoints.get(i));

        for (int i = 0; i < map.arenaCheckpoints.size(); i++) {
            var checkpoint = map.arenaCheckpoints.get(i);
            field(out, "checkpoint[" + i + "].id", checkpoint.id());
            field(out, "checkpoint[" + i + "].name", checkpoint.name());
            field(out, "checkpoint[" + i + "].spawn", location(checkpoint.spawn()));
            for (int j = 0; j < checkpoint.locations().size(); j++)
                field(out, "checkpoint[" + i + "].location[" + j + "]", location(checkpoint.locations().get(j)));
        }

        for (int i = 0; i < map.arenaTraps.size(); i++) {
            ITrap trap = map.arenaTraps.get(i);
            field(out, "trap[" + i + "].type", trap.getClass().getName());
            field(out, "trap[" + i + "].button", location(trap.getButton()));
            for (int j = 0; j < trap.getLocations().size(); j++)
                field(out, "trap[" + i + "].location[" + j + "]", location(trap.getLocations().get(j)));
        }

        return out.toString();
    }

    private static void locations(
            @NotNull StringBuilder out,
            @NotNull String prefix,
            @NotNull List<Location> locations
    ) {
        for (int i = 0; i < locations.size(); i++)
            field(out, prefix + "[" + i + "]", location(locations.get(i)));
    }

    private static @NotNull String location(Location location) {
        if (location == null)
            return "<null>";
        String world = location.getWorld() == null ? "<null-world>" : location.getWorld().getName();
        return world
                + ":" + Double.toHexString(location.getX())
                + "," + Double.toHexString(location.getY())
                + "," + Double.toHexString(location.getZ())
                + ",yaw=" + Float.toHexString(location.getYaw())
                + ",pitch=" + Float.toHexString(location.getPitch());
    }

    private static void field(@NotNull StringBuilder out, @NotNull String key, Object value) {
        out.append(key).append('=').append(String.valueOf(value)).append('\n');
    }

    private static @NotNull String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record Result(
            boolean success,
            @NotNull String fingerprint,
            int canonicalBytes,
            @NotNull List<String> issues
    ) {}
}
