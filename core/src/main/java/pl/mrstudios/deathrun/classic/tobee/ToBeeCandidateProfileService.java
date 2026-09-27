package pl.mrstudios.deathrun.classic.tobee;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.arena.checkpoint.Checkpoint;
import pl.mrstudios.deathrun.arena.trap.impl.TrapArrows;
import pl.mrstudios.deathrun.arena.trap.impl.TrapDisappearingBlocks;
import pl.mrstudios.deathrun.arena.trap.impl.TrapFireFloor;
import pl.mrstudios.deathrun.arena.trap.impl.TrapFlood;
import pl.mrstudios.deathrun.config.Configuration;
import pl.mrstudios.deathrun.config.impl.MapConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts only archive-backed To Bee evidence into a setup-locked map profile.
 *
 * This intentionally does not mislabel reconstructed playability geometry as
 * recovered Hive server-side data. Runner/Death starts and the start barrier
 * are explicitly geometry-derived; button-to-trap bindings, trap target
 * cuboids and original scoring remain unresolved until independently recovered
 * or deliberately reconstructed and reviewed.
 */
public final class ToBeeCandidateProfileService {

    public static final String MAP_ID = "to-bee-or-not-to-bee";
    public static final String MAP_NAME = "To Bee Or Not To Bee";

    private static final List<Integer> ROUTE_GATE_IDS = List.of(6, 7, 2, 1, 4, 5, 3);
    private static final List<Integer> CHECKPOINT_GATE_IDS = List.of(7, 2, 1, 4, 5, 3);

    private static final Map<Integer, GateEvidence> GATES = buildGates();
    private static final BlockPos DEATH_CONTROL_BUTTON = new BlockPos(76, 25, 47);
    private static final BlockPos DEATH_CONTROL_ACTION = new BlockPos(76, 25, 56);

    private static final List<FireArrowEvidence> FIRE_ARROW_TRAPS = List.of(
            new FireArrowEvidence(
                    "pair-011",
                    new BlockPos(10, 27, 67),
                    new BlockPos(15, 35, 62),
                    List.of(
                            new BlockPos(9, 26, 70),
                            new BlockPos(9, 25, 69),
                            new BlockPos(9, 26, 68),
                            new BlockPos(9, 25, 67),
                            new BlockPos(9, 26, 66),
                            new BlockPos(-2, 25, 70),
                            new BlockPos(-2, 26, 69),
                            new BlockPos(-2, 25, 68),
                            new BlockPos(-2, 26, 67),
                            new BlockPos(-2, 25, 66)
                    ),
                    "nearest-button reconstruction; opposing east/west archive banks"
            ),
            new FireArrowEvidence(
                    "pair-007",
                    new BlockPos(-23, 29, 10),
                    new BlockPos(-24, 35, 6),
                    List.of(
                            new BlockPos(-22, 26, 18),
                            new BlockPos(-24, 26, 18),
                            new BlockPos(-26, 26, 18),
                            new BlockPos(-21, 26, 28),
                            new BlockPos(-23, 26, 28),
                            new BlockPos(-28, 25, 18),
                            new BlockPos(-25, 26, 28)
                    ),
                    "nearest-button reconstruction; opposing north/south archive banks"
            ),
            new FireArrowEvidence(
                    "pair-005",
                    new BlockPos(-35, 29, -19),
                    new BlockPos(-34, 35, -15),
                    List.of(
                            new BlockPos(-39, 19, -38),
                            new BlockPos(-37, 19, -37),
                            new BlockPos(-38, 19, -39),
                            new BlockPos(-35, 19, -40),
                            new BlockPos(-33, 19, -38),
                            new BlockPos(-32, 19, -37)
                    ),
                    "nearest-button reconstruction; single surviving south-facing archive bank"
            )
    );

    private static final MaterialTrapEvidence ICE_MELT_TRAP = new MaterialTrapEvidence(
            "pair-001-melt-ice",
            new BlockPos(-45, 29, 7),
            new BlockPos(-41, 35, 3),
            iceTargetPositions(),
            "PACKED_ICE",
            "TrapFlood: replace packed ice with water for Classic26 active duration",
            "target geometry archive-backed; nearest-button/effect-duration reconstruction"
    );

    private static final MaterialTrapEvidence COAL_FIRE_TRAP = new MaterialTrapEvidence(
            "pair-015-coal-fire",
            new BlockPos(30, 44, 15),
            new BlockPos(31, 48, 14),
            coalTargetPositions(),
            "COAL_BLOCK",
            "TrapFireFloor: replace coal with magma and use active trap contact",
            "target geometry archive-backed; nearest-button/effect-duration reconstruction"
    );

    private static final MaterialTrapEvidence RED_D_TRAP = new MaterialTrapEvidence(
            "pair-021-red-D",
            new BlockPos(89, 25, 65),
            new BlockPos(81, 25, 54),
            redDTargetPositions(),
            "RED_TERRACOTTA",
            "TrapDisappearingBlocks: remove exact route-level red terracotta",
            "target geometry archive-backed; nearest existing low-control button reconstruction"
    );

    private static final MaterialTrapEvidence SEA_LANTERN_TRAP = new MaterialTrapEvidence(
            "action-021-sea-lantern",
            new BlockPos(72, 25, 76),
            new BlockPos(74, 25, 54),
            seaLanternTargetPositions(),
            "SEA_LANTERN",
            "TrapDisappearingBlocks: remove two large archive sea-lantern route components",
            "target geometry archive-backed; nearest existing low-control button reconstruction"
    );

    private static final MaterialTrapEvidence RED_A_TRAP = new MaterialTrapEvidence(
            "pair-006-red-A",
            new BlockPos(-21, 29, -19),
            new BlockPos(-21, 35, -15),
            redATargetPositions(),
            "RED_TERRACOTTA",
            "TrapDisappearingBlocks: remove exact route-level red terracotta",
            "target geometry archive-backed; nearest-button reconstruction"
    );

    private static final MaterialTrapEvidence RED_B_TRAP = new MaterialTrapEvidence(
            "pair-012-red-B",
            new BlockPos(13, 29, -19),
            new BlockPos(13, 35, -15),
            redBTargetPositions(),
            "RED_TERRACOTTA",
            "TrapDisappearingBlocks: remove the three route-level five-block runs",
            "target geometry archive-backed; nearest-button reconstruction"
    );
    private static final MaterialTrapEvidence DARK_WOOD_A_TRAP = new MaterialTrapEvidence(
            "pair-002-dark-wood-A",
            new BlockPos(-45, 29, -18),
            new BlockPos(-41, 35, -14),
            List.of(
                    new BlockPos(-61, 24, -20),
                    new BlockPos(-60, 24, -20),
                    new BlockPos(-59, 24, -20),
                    new BlockPos(-58, 24, -20)
            ),
            "SPRUCE_WOOD",
            "TrapDisappearingBlocks: remove nearest route-level four-block wood run",
            "target geometry archive-backed; nearest-button reconstruction"
    );
    private static final int START_BARRIER_X = 84;
    private static final int START_BARRIER_MIN_Y = 25;
    private static final int START_BARRIER_MAX_Y = 26;
    private static final int START_BARRIER_MIN_Z = 79;
    private static final int START_BARRIER_MAX_Z = 85;

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

        List<BlockPos> runnerStarts = runnerStartCandidates(world, 20);
        if (runnerStarts.size() < 20)
            return new Result(false, "insufficient-safe-runner-starts",
                    List.of(runnerStarts.size() + "/20"));

        map.arenaWaitingLobbyLocation = feetLocation(world, runnerStarts.get(0));
        runnerStarts.stream()
                .map(pos -> feetLocation(world, pos))
                .forEach(map.arenaRunnerSpawnLocations::add);

        for (BlockPos pos : startBarrierPositions()) {
            map.arenaStartBarrierBlocks.add(blockLocation(world, pos));
            map.arenaStartBarrierRestoreMaterials.add(Material.BARRIER);
        }

        List<BlockPos> deathStarts = deathStartCandidates(world, 2);
        if (deathStarts.size() < 2)
            return new Result(false, "insufficient-safe-death-starts",
                    List.of(deathStarts.size() + "/2"));

        deathStarts.stream()
                .map(pos -> feetLocation(world, pos))
                .forEach(map.arenaDeathSpawnLocations::add);

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

        // Keep implemented controls in recovered route order so Death navigation
        // advances through the map rather than through trap-class insertion order.
        addDisappearingTrap(map, world, SEA_LANTERN_TRAP);
        addDisappearingTrap(map, world, RED_D_TRAP);
        addCoalFireTrap(map, world);
        addFireArrowTrap(map, world, FIRE_ARROW_TRAPS.get(0));
        addFireArrowTrap(map, world, FIRE_ARROW_TRAPS.get(1));
        addDisappearingTrap(map, world, DARK_WOOD_A_TRAP);
        addIceMeltTrap(map, world);
        addFireArrowTrap(map, world, FIRE_ARROW_TRAPS.get(2));
        addDisappearingTrap(map, world, RED_A_TRAP);
        addDisappearingTrap(map, world, RED_B_TRAP);

        map.arenaMaxPlayers = 22;
        map.arenaRequiredPlayersToStart = 11;

        // Critical safety lock: the archive does not contain enough server-side
        // logic to claim production fidelity yet. Promotion must remain manual
        // until traps are reconstructed/reviewed and the normal /dr map disable
        // preflight passes. Runner/Death starts and the barrier above are
        // explicitly reconstructed playability geometry, not claimed originals.
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
            if (map.arenaRunnerSpawnLocations.size() != 20)
                issues.add("runner-spawn-count:" + map.arenaRunnerSpawnLocations.size());
            if (map.arenaDeathSpawnLocations.size() != 2)
                issues.add("death-spawn-count:" + map.arenaDeathSpawnLocations.size());
            if (map.arenaStartBarrierBlocks.size() != startBarrierPositions().size())
                issues.add("start-barrier-count:" + map.arenaStartBarrierBlocks.size());
            if (map.arenaStartBarrierRestoreMaterials.size() != startBarrierPositions().size())
                issues.add("start-barrier-restore-count:" + map.arenaStartBarrierRestoreMaterials.size());
            if (map.arenaTraps.size() != implementedTrapCount())
                issues.add("implemented-trap-count:" + map.arenaTraps.size() + "/" + implementedTrapCount());
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
            if (!routeSideAnchorValid(world, gateId, candidate))
                issues.add("unsafe-route-side-candidate-" + String.format("%03d", gateId)
                        + ":" + candidate.compact());
        }

        for (BlockPos barrier : startBarrierPositions()) {
            Material material = world.getBlockAt(barrier.x(), barrier.y(), barrier.z()).getType();
            if (!material.isAir() && material != Material.BARRIER)
                issues.add("start-barrier-overlay-not-clear:" + barrier.compact() + ":" + material.name());
        }

        int runnerStartCount = runnerStartCandidates(world, 20).size();
        if (runnerStartCount < 20)
            issues.add("safe-runner-start-capacity:" + runnerStartCount + "/20");

        if (!world.getBlockAt(DEATH_CONTROL_BUTTON.x(), DEATH_CONTROL_BUTTON.y(), DEATH_CONTROL_BUTTON.z())
                .getType().name().endsWith("_BUTTON"))
            issues.add("death-control-button-missing:" + DEATH_CONTROL_BUTTON.compact());

        if (!world.getBlockAt(DEATH_CONTROL_ACTION.x(), DEATH_CONTROL_ACTION.y(), DEATH_CONTROL_ACTION.z())
                .getType().name().endsWith("_SIGN"))
            issues.add("death-control-action-sign-missing:" + DEATH_CONTROL_ACTION.compact());

        int deathStartCount = deathStartCandidates(world, 2).size();
        if (deathStartCount < 2)
            issues.add("safe-death-start-capacity:" + deathStartCount + "/2");

        for (FireArrowEvidence evidence : FIRE_ARROW_TRAPS) {
            if (!world.getBlockAt(evidence.actionSign().x(), evidence.actionSign().y(), evidence.actionSign().z())
                    .getType().name().endsWith("_SIGN"))
                issues.add("fire-arrow-action-missing:" + evidence.id() + ":" + evidence.actionSign().compact());

            if (!world.getBlockAt(evidence.button().x(), evidence.button().y(), evidence.button().z())
                    .getType().name().endsWith("_BUTTON"))
                issues.add("fire-arrow-button-missing:" + evidence.id() + ":" + evidence.button().compact());

            for (BlockPos dispenser : evidence.dispensers()) {
                if (world.getBlockAt(dispenser.x(), dispenser.y(), dispenser.z()).getType() != Material.DISPENSER)
                    issues.add("fire-arrow-dispenser-missing:" + evidence.id() + ":" + dispenser.compact());
            }
        }

        for (MaterialTrapEvidence evidence : List.of(
                ICE_MELT_TRAP, COAL_FIRE_TRAP,
                RED_A_TRAP, RED_B_TRAP, RED_D_TRAP, SEA_LANTERN_TRAP, DARK_WOOD_A_TRAP
        )) {
            if (!world.getBlockAt(evidence.actionSign().x(), evidence.actionSign().y(), evidence.actionSign().z())
                    .getType().name().endsWith("_SIGN"))
                issues.add("material-trap-action-missing:" + evidence.id() + ":" + evidence.actionSign().compact());

            if (!world.getBlockAt(evidence.button().x(), evidence.button().y(), evidence.button().z())
                    .getType().name().endsWith("_BUTTON"))
                issues.add("material-trap-button-missing:" + evidence.id() + ":" + evidence.button().compact());

            Material expected = Material.matchMaterial(evidence.expectedMaterialName());
            if (expected == null) {
                issues.add("material-trap-expected-material-invalid:" + evidence.id()
                        + ":" + evidence.expectedMaterialName());
                continue;
            }

            for (BlockPos target : evidence.targets()) {
                Material actual = world.getBlockAt(target.x(), target.y(), target.z()).getType();
                if (actual != expected)
                    issues.add("material-trap-target-missing:" + evidence.id() + ":"
                            + target.compact() + ":" + actual.name() + "/" + evidence.expectedMaterialName());
            }
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

    public static int runnerSpawnTarget() {
        return 20;
    }

    public static int deathSpawnTarget() {
        return 2;
    }

    public static @NotNull BlockPos deathControlButtonCandidate() {
        return DEATH_CONTROL_BUTTON;
    }

    public static @NotNull BlockPos deathControlActionAnchor() {
        return DEATH_CONTROL_ACTION;
    }

    public static int startSearchColumnCount() {
        GateEvidence gate = GATES.get(6);
        int normalDistances = 11; // d=2..12, kept behind the reconstructed x=84 barrier.
        int lateralColumns = (gate.max().z() - gate.min().z() + 1) + 4;
        int verticalColumns = 6;
        return normalDistances * lateralColumns * verticalColumns;
    }

    public static @NotNull List<BlockPos> startBarrierPositions() {
        List<BlockPos> positions = new ArrayList<>();
        for (int y = START_BARRIER_MIN_Y; y <= START_BARRIER_MAX_Y; y++)
            for (int z = START_BARRIER_MIN_Z; z <= START_BARRIER_MAX_Z; z++)
                positions.add(new BlockPos(START_BARRIER_X, y, z));
        return List.copyOf(positions);
    }

    public static int fireArrowTrapCount() {
        return FIRE_ARROW_TRAPS.size();
    }

    public static @NotNull List<FireArrowEvidence> fireArrowEvidence() {
        return FIRE_ARROW_TRAPS;
    }

    public static int implementedTrapCount() {
        return FIRE_ARROW_TRAPS.size() + 7;
    }

    public static @NotNull MaterialTrapEvidence iceMeltEvidence() {
        return ICE_MELT_TRAP;
    }

    public static @NotNull MaterialTrapEvidence coalFireEvidence() {
        return COAL_FIRE_TRAP;
    }

    public static @NotNull List<MaterialTrapEvidence> redBlockEvidence() {
        return List.of(RED_A_TRAP, RED_B_TRAP, RED_D_TRAP);
    }

    public static @NotNull MaterialTrapEvidence seaLanternEvidence() {
        return SEA_LANTERN_TRAP;
    }
    public static @NotNull List<MaterialTrapEvidence> darkWoodEvidence() {
        return List.of(DARK_WOOD_A_TRAP);
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
                "runner-start-layout-generated-from-safe-archive-geometry-not-original",
                "death-spawns-generated-from-first-stage-control-geometry-not-original",
                "death-button-to-trap-bindings-partially-reconstructed-runtime-subset",
                "trap-target-geometry-recovered-10-runtime-traps",
                "remaining-trap-targets-and-original-reset-parameters-not-recovered",
                "start-barrier-generated-outside-gate-006-not-original",
                "original-checkpoint-score-values-not-recovered"
        );
    }

    private static void addFireArrowTrap(
            @NotNull MapConfiguration.MapDefinition map,
            @NotNull World world,
            @NotNull FireArrowEvidence evidence
    ) {
        TrapArrows trap = new TrapArrows();
        trap.setButton(blockLocation(world, evidence.button()));
        trap.setLocations(evidence.dispensers().stream()
                .map(pos -> blockLocation(world, pos))
                .toList());
        map.arenaTraps.add(trap);
    }

    private static void addIceMeltTrap(
            @NotNull MapConfiguration.MapDefinition map,
            @NotNull World world
    ) {
        TrapFlood trap = new TrapFlood();
        trap.setButton(blockLocation(world, ICE_MELT_TRAP.button()));
        trap.setLocations(ICE_MELT_TRAP.targets().stream()
                .map(pos -> blockLocation(world, pos))
                .toList());
        map.arenaTraps.add(trap);
    }

    private static void addDisappearingTrap(
            @NotNull MapConfiguration.MapDefinition map,
            @NotNull World world,
            @NotNull MaterialTrapEvidence evidence
    ) {
        TrapDisappearingBlocks trap = new TrapDisappearingBlocks();
        Material material = Material.matchMaterial(evidence.expectedMaterialName());
        if (material != null)
            trap.setMaterial(material);
        trap.setButton(blockLocation(world, evidence.button()));
        trap.setLocations(evidence.targets().stream()
                .map(pos -> blockLocation(world, pos))
                .toList());
        map.arenaTraps.add(trap);
    }
    private static void addCoalFireTrap(
            @NotNull MapConfiguration.MapDefinition map,
            @NotNull World world
    ) {
        TrapFireFloor trap = new TrapFireFloor();
        trap.setButton(blockLocation(world, COAL_FIRE_TRAP.button()));
        trap.setLocations(COAL_FIRE_TRAP.targets().stream()
                .map(pos -> blockLocation(world, pos))
                .toList());
        map.arenaTraps.add(trap);
    }

    private static @NotNull List<BlockPos> iceTargetPositions() {
        List<BlockPos> positions = new ArrayList<>();
        addRectangle(positions, -65, -64, 24, 19, 20);
        addRectangle(positions, -63, -62, 24, 15, 16);
        addRectangle(positions, -61, -60, 24, 11, 12);
        return List.copyOf(positions);
    }

    private static @NotNull List<BlockPos> coalTargetPositions() {
        List<BlockPos> positions = new ArrayList<>();
        addRectangle(positions, 32, 36, 43, 13, 19);
        return List.copyOf(positions);
    }

    private static @NotNull List<BlockPos> redATargetPositions() {
        return List.of(
                new BlockPos(-20, 17, -35),
                new BlockPos(-21, 17, -29),
                new BlockPos(-17, 17, -38),
                new BlockPos(-18, 17, -40),
                new BlockPos(-14, 17, -34),
                new BlockPos(-14, 17, -37),
                new BlockPos(-11, 17, -36)
        );
    }

    private static @NotNull List<BlockPos> redBTargetPositions() {
        List<BlockPos> positions = new ArrayList<>();
        for (int x = 8; x <= 12; x++)
            positions.add(new BlockPos(x, 24, -34));
        for (int z = -39; z <= -35; z++)
            positions.add(new BlockPos(13, 24, z));
        for (int x = 14; x <= 18; x++)
            positions.add(new BlockPos(x, 24, -40));
        return List.copyOf(positions);
    }

    private static @NotNull List<BlockPos> redDTargetPositions() {
        return List.of(
                new BlockPos(100, 24, 57), new BlockPos(99, 24, 60),
                new BlockPos(87, 24, 64), new BlockPos(88, 24, 64), new BlockPos(89, 24, 64),
                new BlockPos(89, 24, 66), new BlockPos(87, 24, 66),
                new BlockPos(87, 24, 68), new BlockPos(88, 24, 68), new BlockPos(89, 24, 68),
                new BlockPos(79, 24, 56), new BlockPos(79, 24, 57), new BlockPos(79, 24, 58),
                new BlockPos(100, 24, 69), new BlockPos(77, 24, 56), new BlockPos(77, 24, 58),
                new BlockPos(102, 24, 70), new BlockPos(99, 24, 72),
                new BlockPos(75, 24, 56), new BlockPos(75, 24, 57), new BlockPos(75, 24, 58)
        );
    }

    private static @NotNull List<BlockPos> seaLanternTargetPositions() {
        List<BlockPos> positions = new ArrayList<>();
        for (int x = 67; x <= 68; x++)
            for (int z = 80; z <= 85; z++)
                positions.add(new BlockPos(x, 20, z));
        for (int x = 69; x <= 75; x++)
            for (int z = 84; z <= 85; z++)
                positions.add(new BlockPos(x, 20, z));
        for (int z = 84; z <= 85; z++) positions.add(new BlockPos(55, 20, z));
        for (int z = 83; z <= 85; z++) positions.add(new BlockPos(56, 20, z));
        for (int z = 82; z <= 84; z++) positions.add(new BlockPos(57, 20, z));
        for (int z = 81; z <= 83; z++) positions.add(new BlockPos(58, 20, z));
        for (int z = 80; z <= 82; z++) positions.add(new BlockPos(59, 20, z));
        for (int z = 79; z <= 81; z++) positions.add(new BlockPos(60, 20, z));
        for (int z = 78; z <= 80; z++) positions.add(new BlockPos(61, 20, z));
        for (int x = 62; x <= 66; x++)
            for (int z = 78; z <= 79; z++)
                positions.add(new BlockPos(x, 20, z));
        return List.copyOf(positions);
    }
    private static void addRectangle(
            @NotNull List<BlockPos> positions,
            int minX, int maxX, int y, int minZ, int maxZ
    ) {
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++)
                positions.add(new BlockPos(x, y, z));
    }

    private static @NotNull List<BlockPos> deathStartCandidates(
            @NotNull World world,
            int limit
    ) {
        List<BlockPos> candidates = new ArrayList<>();

        // The only action/button pair that independently collapses to one
        // panel/axis candidate is Release fire snake:
        // action sign 76,25,56 -> button 76,25,47. Search only the corridor
        // between those two archive-backed control anchors.
        for (int x = 74; x <= 78; x++) {
            for (int z = 48; z <= 55; z++) {
                for (int y = 23; y <= 28; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (safeStandingColumn(world, pos))
                        candidates.add(pos);
                }
            }
        }

        final double centerX = 76.5;
        final double centerY = 25.0;
        final double centerZ = 51.5;
        candidates.sort(
                Comparator.comparingDouble((BlockPos pos) -> {
                            double dx = pos.x() + 0.5 - centerX;
                            double dy = pos.y() - centerY;
                            double dz = pos.z() + 0.5 - centerZ;
                            return dx * dx + dy * dy + dz * dz;
                        })
                        .thenComparingInt(BlockPos::x)
                        .thenComparingInt(BlockPos::y)
                        .thenComparingInt(BlockPos::z)
        );

        if (candidates.size() <= limit)
            return List.copyOf(candidates);
        return List.copyOf(candidates.subList(0, limit));
    }

    private static @NotNull List<BlockPos> runnerStartCandidates(
            @NotNull World world,
            int limit
    ) {
        GateEvidence gate = GATES.get(6);
        List<BlockPos> candidates = new ArrayList<>();

        for (int normalDistance = 2; normalDistance <= 12; normalDistance++) {
            int x = gate.max().x() + normalDistance;
            for (int z = gate.min().z() - 2; z <= gate.max().z() + 2; z++) {
                for (int y = gate.min().y() - 2; y < gate.min().y() + 4; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (safeStandingColumn(world, pos))
                        candidates.add(pos);
                }
            }
        }

        final double centerY = 28.76;
        final double centerZ = 81.91;
        candidates.sort(
                Comparator.comparingInt((BlockPos pos) -> pos.x() - gate.max().x())
                        .thenComparingInt(pos -> Math.abs(pos.y() - gate.min().y()))
                        .thenComparingDouble(pos -> {
                            double dy = pos.y() - centerY;
                            double dz = pos.z() + 0.5 - centerZ;
                            return dy * dy + dz * dz;
                        })
                        .thenComparingInt(BlockPos::x)
                        .thenComparingInt(BlockPos::y)
                        .thenComparingInt(BlockPos::z)
        );

        if (candidates.size() <= limit)
            return List.copyOf(candidates);
        return List.copyOf(candidates.subList(0, limit));
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

    private static boolean routeSideAnchorValid(
            @NotNull World world,
            int gateId,
            @NotNull BlockPos candidate
    ) {
        if (safeStandingColumn(world, candidate))
            return true;

        // Once the reconstructed start barrier is active, the original #006
        // anchor column is intentionally occupied by two BARRIER blocks.
        if (gateId != 6 || candidate.x() != START_BARRIER_X)
            return false;

        return world.getBlockAt(candidate.x(), candidate.y(), candidate.z()).getType() == Material.BARRIER
                && world.getBlockAt(candidate.x(), candidate.y() + 1, candidate.z()).getType() == Material.BARRIER
                && world.getBlockAt(candidate.x(), candidate.y() - 1, candidate.z()).getType().isSolid();
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

    public record FireArrowEvidence(
            @NotNull String id,
            @NotNull BlockPos actionSign,
            @NotNull BlockPos button,
            @NotNull List<BlockPos> dispensers,
            @NotNull String confidence
    ) {}
    public record MaterialTrapEvidence(
            @NotNull String id,
            @NotNull BlockPos actionSign,
            @NotNull BlockPos button,
            @NotNull List<BlockPos> targets,
            @NotNull String expectedMaterialName,
            @NotNull String behavior,
            @NotNull String confidence
    ) {}


    public record Result(
            boolean success,
            @NotNull String message,
            @NotNull List<String> details
    ) {}
}
