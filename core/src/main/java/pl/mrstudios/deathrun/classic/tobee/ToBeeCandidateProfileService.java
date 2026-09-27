package pl.mrstudios.deathrun.classic.tobee;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.arena.checkpoint.Checkpoint;
import pl.mrstudios.deathrun.config.Configuration;
import pl.mrstudios.deathrun.config.impl.MapConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts only archive-backed To Bee evidence into a setup-locked map profile.
 *
 * This intentionally does not invent the missing Hive server-side data:
 * Death spawn positions, button-to-trap bindings, trap target cuboids, start
 * barrier and original scoring remain unset until independently recovered or
 * deliberately reconstructed and reviewed.
 */
public final class ToBeeCandidateProfileService {

    public static final String MAP_ID = "to-bee-or-not-to-bee";
    public static final String MAP_NAME = "To Bee Or Not To Bee";

    private static final List<Integer> ROUTE_GATE_IDS = List.of(6, 7, 2, 1, 4, 5, 3);
    private static final List<Integer> CHECKPOINT_GATE_IDS = List.of(7, 2, 1, 4, 5, 3);

    private static final Map<Integer, GateEvidence> GATES = buildGates();
    private static final Map<Integer, BlockPos> SAFE_ROUTE_SIDE_CANDIDATES = Map.of(
            6, new BlockPos(84, 25, 81),
            7, new BlockPos(33, 45, 35),
            2, new BlockPos(7, 25, 81),
            1, new BlockPos(-14, 25, 22),
            4, new BlockPos(-59, 25, 5),
            5, new BlockPos(-5, 18, -38),
            3, new BlockPos(33, 25, -9)
    );

    private final Configuration configuration;
    private final ArenaManager arenaManager;

    public ToBeeCandidateProfileService(
            @NotNull Configuration configuration,
            @NotNull ArenaManager arenaManager
    ) {
        this.configuration = configuration;
        this.arenaManager = arenaManager;
    }

    public @NotNull Result bootstrap(@NotNull World world) {
        List<String> evidenceIssues = archiveEvidenceIssues(world);
        if (!evidenceIssues.isEmpty())
            return new Result(false, "archive-evidence-mismatch", evidenceIssues);

        this.configuration.map().ensureMapsMutable();
        MapConfiguration.MapDefinition existing = this.configuration.map().getMapById(MAP_ID);
        if (existing != null && !existing.arenaSetupEnabled)
            return new Result(false, "refusing-to-overwrite-promoted-map", List.of(MAP_ID));

        MapConfiguration.MapDefinition map = new MapConfiguration.MapDefinition();
        map.id = MAP_ID;
        map.name = MAP_NAME;
        map.creator = "Unknown (archived HiveMC Java world)";
        map.world = world.getName();

        BlockPos start = SAFE_ROUTE_SIDE_CANDIDATES.get(6);
        map.arenaWaitingLobbyLocation = feetLocation(world, start);
        map.arenaRunnerSpawnLocations.add(feetLocation(world, start));

        int checkpointId = 1;
        for (int gateId : CHECKPOINT_GATE_IDS) {
            GateEvidence gate = GATES.get(gateId);
            BlockPos respawn = SAFE_ROUTE_SIDE_CANDIDATES.get(gateId);
            map.arenaCheckpoints.add(new Checkpoint(
                    checkpointId,
                    feetLocation(world, respawn),
                    List.of(
                            blockLocation(world, gate.min()),
                            blockLocation(world, gate.max())
                    ),
                    gateId == 3
                            ? "Finish [archive candidate gate #003]"
                            : "Checkpoint " + checkpointId + " [archive candidate gate #" + String.format("%03d", gateId) + "]"
            ));
            map.arenaCheckpointPoints.add(0);
            checkpointId++;
        }
        map.arenaFinishCheckpointId = map.arenaCheckpoints.size();

        map.arenaMaxPlayers = 22;
        map.arenaRequiredPlayersToStart = 11;

        // Critical safety lock: the archive does not contain enough server-side
        // logic to claim production fidelity yet. Promotion must remain manual
        // after Death spawns, 20 unique Runner starts, traps and start barrier
        // are reconstructed and pass the normal /dr map disable preflight.
        map.arenaSetupEnabled = true;

        this.configuration.map().maps.removeIf(candidate ->
                MAP_ID.equalsIgnoreCase(this.configuration.map().normalizedMapId(candidate.id))
        );
        this.configuration.map().maps.add(map);
        this.configuration.map().save();
        this.arenaManager.reloadRuntime(MAP_ID);

        return new Result(true, "candidate-profile-created", knownRemainingBlockers());
    }

    public @NotNull Result verify(@NotNull World world) {
        List<String> issues = new ArrayList<>(archiveEvidenceIssues(world));

        this.configuration.map().ensureMapsMutable();
        MapConfiguration.MapDefinition map = this.configuration.map().getMapById(MAP_ID);
        if (map == null) {
            issues.add("profile-not-configured");
        } else {
            if (!world.getName().equals(map.world))
                issues.add("profile-world-mismatch:" + map.world);
            if (!map.arenaSetupEnabled)
                issues.add("candidate-profile-unexpectedly-promoted");
            if (map.arenaCheckpoints.size() != CHECKPOINT_GATE_IDS.size())
                issues.add("checkpoint-count:" + map.arenaCheckpoints.size());
            if (map.arenaFinishCheckpointId == null
                    || map.arenaFinishCheckpointId != map.arenaCheckpoints.size())
                issues.add("finish-checkpoint:" + map.arenaFinishCheckpointId);
        }

        if (!issues.isEmpty())
            return new Result(false, "verification-failed", List.copyOf(issues));

        return new Result(true, "verified", knownRemainingBlockers());
    }

    public static @NotNull List<String> archiveEvidenceIssues(@NotNull World world) {
        List<String> issues = new ArrayList<>();

        for (int gateId : ROUTE_GATE_IDS) {
            GateEvidence gate = GATES.get(gateId);
            int actualPortalBlocks = countPortalBlocks(world, gate);
            if (actualPortalBlocks != gate.expectedPortalBlocks())
                issues.add("portal-" + String.format("%03d", gateId)
                        + "-blocks:" + actualPortalBlocks + "/" + gate.expectedPortalBlocks());

            BlockPos candidate = SAFE_ROUTE_SIDE_CANDIDATES.get(gateId);
            if (!safeStandingColumn(world, candidate))
                issues.add("unsafe-route-side-candidate-" + String.format("%03d", gateId)
                        + ":" + candidate.compact());
        }

        return issues;
    }

    public static @NotNull List<Integer> routeGateIds() {
        return ROUTE_GATE_IDS;
    }

    public static @NotNull List<Integer> checkpointGateIds() {
        return CHECKPOINT_GATE_IDS;
    }

    public static int finishGateId() {
        return 3;
    }

    public static int expectedPortalBlockTotal() {
        return GATES.values().stream().mapToInt(GateEvidence::expectedPortalBlocks).sum();
    }

    public static @NotNull BlockPos safeRouteSideCandidate(int gateId) {
        BlockPos candidate = SAFE_ROUTE_SIDE_CANDIDATES.get(gateId);
        if (candidate == null)
            throw new IllegalArgumentException("Unknown To Bee portal gate: " + gateId);
        return candidate;
    }

    public static @NotNull GateEvidence gateEvidence(int gateId) {
        GateEvidence gate = GATES.get(gateId);
        if (gate == null)
            throw new IllegalArgumentException("Unknown To Bee portal gate: " + gateId);
        return gate;
    }

    public static @NotNull List<String> knownRemainingBlockers() {
        return List.of(
                "original-waiting-lobby-not-recovered",
                "runner-start-layout-only-1-of-20-safe-candidates-recovered",
                "death-spawns-not-recovered",
                "death-button-to-trap-bindings-not-recovered",
                "trap-target-cuboids-and-reset-parameters-not-recovered",
                "start-barrier-not-recovered",
                "original-checkpoint-score-values-not-recovered"
        );
    }

    private static int countPortalBlocks(@NotNull World world, @NotNull GateEvidence gate) {
        int count = 0;
        for (int x = gate.min().x(); x <= gate.max().x(); x++)
            for (int y = gate.min().y(); y <= gate.max().y(); y++)
                for (int z = gate.min().z(); z <= gate.max().z(); z++)
                    if (world.getBlockAt(x, y, z).getType() == Material.NETHER_PORTAL)
                        count++;
        return count;
    }

    private static boolean safeStandingColumn(@NotNull World world, @NotNull BlockPos candidate) {
        return world.getBlockAt(candidate.x(), candidate.y(), candidate.z()).isPassable()
                && world.getBlockAt(candidate.x(), candidate.y() + 1, candidate.z()).isPassable()
                && world.getBlockAt(candidate.x(), candidate.y() - 1, candidate.z()).getType().isSolid();
    }

    private static @NotNull Location feetLocation(@NotNull World world, @NotNull BlockPos pos) {
        return new Location(world, pos.x() + 0.5, pos.y(), pos.z() + 0.5, 0f, 0f);
    }

    private static @NotNull Location blockLocation(@NotNull World world, @NotNull BlockPos pos) {
        return new Location(world, pos.x(), pos.y(), pos.z());
    }

    private static @NotNull Map<Integer, GateEvidence> buildGates() {
        Map<Integer, GateEvidence> gates = new LinkedHashMap<>();
        gates.put(6, new GateEvidence(6, new BlockPos(83, 25, 79), new BlockPos(83, 32, 85), 46));
        gates.put(7, new GateEvidence(7, new BlockPos(32, 45, 36), new BlockPos(36, 52, 36), 38));
        gates.put(2, new GateEvidence(2, new BlockPos(8, 25, 79), new BlockPos(8, 34, 84), 56));
        gates.put(1, new GateEvidence(1, new BlockPos(-13, 25, 19), new BlockPos(-13, 32, 27), 70));
        gates.put(4, new GateEvidence(4, new BlockPos(-61, 25, 6), new BlockPos(-55, 32, 6), 54));
        gates.put(5, new GateEvidence(5, new BlockPos(-6, 18, -40), new BlockPos(-6, 24, -34), 47));
        gates.put(3, new GateEvidence(3, new BlockPos(31, 25, -10), new BlockPos(37, 33, -10), 55));
        return Map.copyOf(gates);
    }

    public record BlockPos(int x, int y, int z) {
        public @NotNull String compact() {
            return x + "," + y + "," + z;
        }
    }

    public record GateEvidence(
            int id,
            @NotNull BlockPos min,
            @NotNull BlockPos max,
            int expectedPortalBlocks
    ) {}

    public record Result(
            boolean success,
            @NotNull String message,
            @NotNull List<String> details
    ) {}
}
