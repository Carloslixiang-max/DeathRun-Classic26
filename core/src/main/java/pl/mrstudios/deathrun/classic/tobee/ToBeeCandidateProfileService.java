package pl.mrstudios.deathrun.classic.tobee;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.arena.checkpoint.Checkpoint;
import pl.mrstudios.deathrun.arena.trap.impl.TrapArrows;
import pl.mrstudios.deathrun.arena.trap.impl.TrapDisappearingBlocks;
import pl.mrstudios.deathrun.arena.trap.impl.TrapDropTNT;
import pl.mrstudios.deathrun.arena.trap.impl.TrapFireFloor;
import pl.mrstudios.deathrun.arena.trap.impl.TrapFireSnake;
import pl.mrstudios.deathrun.arena.trap.impl.TrapFlood;
import pl.mrstudios.deathrun.arena.trap.impl.TrapMinefield;
import pl.mrstudios.deathrun.arena.trap.impl.TrapWallSpawn;
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
    private static final MaterialTrapEvidence RED_C_TRAP = new MaterialTrapEvidence(
            "pair-018-red-C",
            new BlockPos(32, 25, 76),
            new BlockPos(15, 35, 60),
            redCTargetPositions(),
            "RED_TERRACOTTA",
            "TrapDisappearingBlocks: remove six isolated archive red-terracotta route cells",
            "target geometry archive-backed; distinct existing high-stage panel button reconstruction"
    );

    private static final MaterialTrapEvidence FLOOD_A_TRAP = new MaterialTrapEvidence(
            "pair-004-flood-A",
            new BlockPos(-41, 29, 10),
            new BlockPos(-39, 35, 6),
            floodATargetPositions(),
            "GRASS_BLOCK",
            "TrapFlood: replace exact 90-cell walkable grass route component with water",
            "target geometry archive-backed; nearest-button/effect-duration reconstruction"
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
    private static final MaterialTrapEvidence FLOOR_FALL_A_TRAP = new MaterialTrapEvidence(
            "pair-016-floor-fall-A",
            new BlockPos(28, 29, -17),
            new BlockPos(24, 35, -15),
            floorFallATargetPositions(),
            "LIGHT_BLUE_STAINED_GLASS",
            "TrapDisappearingBlocks: remove the exact 82-cell glass floor component",
            "target geometry archive-backed; nearest-button reconstruction"
    );

    private static final MaterialTrapEvidence MINEFIELD_TRAP = new MaterialTrapEvidence(
            "pair-009-minefield",
            new BlockPos(11, 29, 40),
            new BlockPos(15, 35, 39),
            minefieldTntPositions(),
            "TNT",
            "TrapMinefield: prime random mines one block above the 32 preserved TNT cells",
            "target geometry archive-backed; nearest-button reconstruction"
    );

    private static final MaterialTrapEvidence FLOOD_B_TRAP = new MaterialTrapEvidence(
            "pair-017-flood-B",
            new BlockPos(27, 44, 21),
            new BlockPos(26, 48, 23),
            floodBTargetPositions(),
            "STONE_BRICKS",
            "TrapFlood: replace exact 79-cell high-stage stone-brick floor with water",
            "target geometry archive-backed; nearest panel-button reconstruction"
    );
    private static final MaterialTrapEvidence FLOOR_FALL_B_TRAP = new MaterialTrapEvidence(
            "pair-019-floor-fall-B",
            new BlockPos(52, 25, 56),
            new BlockPos(79, 25, 54),
            floorFallBTargetPositions(),
            "GREEN_STAINED_GLASS",
            "TrapDisappearingBlocks: remove the exact 78-cell green glass floor component",
            "target geometry archive-backed; one-to-one completion of low-stage control panel reconstruction"
    );
    private static final FireSnakeEvidence FIRE_SNAKE_A_TRAP = new FireSnakeEvidence(
            "pair-010-fire-snake-A",
            new BlockPos(6, 25, 60),
            new BlockPos(11, 29, 55),
            new BlockPos(15, 35, 55),
            fireSnakeASourceDispensers(),
            "SOUTH",
            fireSnakeAPathCells(),
            false,
            "source dispenser row and path geometry archive-backed; nearest-button and active timing reconstruction"
    );

    private static final FireSnakeEvidence FIRE_SNAKE_B_TRAP = new FireSnakeEvidence(
            "pair-020-fire-snake-B",
            new BlockPos(70, 25, 53),
            new BlockPos(76, 25, 56),
            new BlockPos(76, 25, 47),
            fireSnakeBSourceDispensers(),
            "WEST",
            fireSnakeBPathCells(),
            true,
            "8/8 west-facing source row and 21/21 route slices archive-backed; button independently axis-unique; timing reconstruction"
    );

    private static final List<FireSnakeEvidence> FIRE_SNAKE_TRAPS = List.of(
            FIRE_SNAKE_A_TRAP,
            FIRE_SNAKE_B_TRAP
    );

    private static final MaterialTrapEvidence DARK_WOOD_B_TRAP = new MaterialTrapEvidence(
            "pair-014-dark-wood-B",
            new BlockPos(16, 25, 76),
            new BlockPos(15, 35, 57),
            darkWoodBTargetPositions(),
            "SPRUCE_SLAB",
            "TrapDisappearingBlocks: remove the nearest 17-cell high-route spruce-slab run",
            "target geometry archive-backed; closest unused preserved control button reconstruction"
    );

    private static final MaterialTrapEvidence DROP_TNT_TRAP = new MaterialTrapEvidence(
            "action-017-drop-TNT",
            new BlockPos(29, 44, 9),
            new BlockPos(28, 48, 7),
            dropTntAnchorPositions(),
            "TNT",
            "TrapDropTNT: drop primed TNT from the five preserved overhead TNT anchors",
            "anchor geometry archive-backed; nearest preserved high-stage control button reconstruction"
    );

    private static final MaterialTrapEvidence RANDOM_WALL_A_TRAP = new MaterialTrapEvidence(
            "pair-003-random-wall-A", new BlockPos(-45, 29, 2), new BlockPos(-41, 35, 1),
            randomWallATargetPositions(), "AIR",
            "TrapWallSpawn: temporarily fill a six-wide two-high route seam",
            "wall plane reconstructed from archive-backed standing-surface cross-section");
    private static final MaterialTrapEvidence RANDOM_WALL_B_TRAP = new MaterialTrapEvidence(
            "pair-008-random-wall-B", new BlockPos(-5, 29, 10), new BlockPos(-6, 35, 6),
            randomWallBTargetPositions(), "AIR",
            "TrapWallSpawn: temporarily fill a nine-wide two-high route seam",
            "wall plane reconstructed above the archive spruce-stair route threshold");
    private static final MaterialTrapEvidence RANDOM_WALL_C_TRAP = new MaterialTrapEvidence(
            "pair-013-random-wall-C", new BlockPos(23, 29, -19), new BlockPos(22, 35, -15),
            randomWallCTargetPositions(), "AIR",
            "TrapWallSpawn: temporarily fill a five-wide two-high route seam",
            "wall plane reconstructed above the archive stone-brick-slab threshold");
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
        map.creator = "Timmetatsch";
        map.world = world.getName();

        List<BlockPos> runnerStarts = runnerStartCandidates(world, 20);
        if (runnerStarts.size() < 20)
            return new Result(false, "insufficient-safe-runner-starts",
                    List.of(runnerStarts.size() + "/20"));

        try {
            map.arenaWaitingLobbyLocation = ToBeeWaitingLobby.choose(world,
                    existing == null ? null : existing.arenaWaitingLobbyLocation);
        } catch (IllegalStateException failure) {
            return new Result(false, "waiting-courtyard-unavailable", List.of(failure.getMessage()));
        }
        runnerStarts.stream()
                .map(pos -> feetLocation(world, pos, ToBeeSpawnGeometry.RUNNER_YAW))
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
                .map(pos -> feetLocation(world, pos, ToBeeSpawnGeometry.DEATH_YAW))
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
                            ? "终点"
                            : "检查点 " + checkpointId
            ));
            map.arenaCheckpointPoints.add(0);
            checkpointId++;
        }
        map.arenaFinishCheckpointId = map.arenaCheckpoints.size();

        // Keep implemented controls in recovered route order so Death navigation
        // advances through the map rather than through trap-class insertion order.
        addFireSnakeTrap(map, world, FIRE_SNAKE_B_TRAP);
        addDisappearingTrap(map, world, RED_D_TRAP);
        addDisappearingTrap(map, world, SEA_LANTERN_TRAP);
        addDisappearingTrap(map, world, FLOOR_FALL_B_TRAP);
        addCoalFireTrap(map, world);
        addDropTntTrap(map, world);
        addFloodTrap(map, world, FLOOD_B_TRAP);
        addFireArrowTrap(map, world, FIRE_ARROW_TRAPS.get(0));
        addFireSnakeTrap(map, world, FIRE_SNAKE_A_TRAP);
        addDisappearingTrap(map, world, DARK_WOOD_B_TRAP);
        addDisappearingTrap(map, world, RED_C_TRAP);
        addMinefieldTrap(map, world);
        addWallTrap(map, world, RANDOM_WALL_B_TRAP);
        addFireArrowTrap(map, world, FIRE_ARROW_TRAPS.get(1));
        addDisappearingTrap(map, world, DARK_WOOD_A_TRAP);
        addWallTrap(map, world, RANDOM_WALL_A_TRAP);
        addFloodTrap(map, world, FLOOD_A_TRAP);
        addIceMeltTrap(map, world);
        addFireArrowTrap(map, world, FIRE_ARROW_TRAPS.get(2));
        addDisappearingTrap(map, world, RED_A_TRAP);
        addDisappearingTrap(map, world, RED_B_TRAP);
        addWallTrap(map, world, RANDOM_WALL_C_TRAP);
        addDisappearingTrap(map, world, FLOOR_FALL_A_TRAP);

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
        ToBeeSignTranslations.apply(world);
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
            issues.addAll(spawnIssues(world, map));
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
            if (!startBarrierOverlayReplaceable(material))
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
                RED_A_TRAP, RED_B_TRAP, RED_C_TRAP, RED_D_TRAP, SEA_LANTERN_TRAP,
                DARK_WOOD_A_TRAP, DARK_WOOD_B_TRAP, DROP_TNT_TRAP,
                FLOOR_FALL_A_TRAP, FLOOR_FALL_B_TRAP, MINEFIELD_TRAP, FLOOD_A_TRAP, FLOOD_B_TRAP,
                RANDOM_WALL_A_TRAP, RANDOM_WALL_B_TRAP, RANDOM_WALL_C_TRAP
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

        for (FireSnakeEvidence evidence : FIRE_SNAKE_TRAPS) {
            if (!world.getBlockAt(evidence.actionSign().x(), evidence.actionSign().y(), evidence.actionSign().z())
                    .getType().name().endsWith("_SIGN"))
                issues.add("fire-snake-action-missing:" + evidence.id() + ":" + evidence.actionSign().compact());

            if (!world.getBlockAt(evidence.button().x(), evidence.button().y(), evidence.button().z())
                    .getType().name().endsWith("_BUTTON"))
                issues.add("fire-snake-button-missing:" + evidence.id() + ":" + evidence.button().compact());

            BlockFace expectedFacing;
            try {
                expectedFacing = BlockFace.valueOf(evidence.sourceFacingName());
            } catch (IllegalArgumentException exception) {
                issues.add("fire-snake-facing-invalid:" + evidence.id() + ":" + evidence.sourceFacingName());
                continue;
            }

            for (BlockPos dispenser : evidence.sourceDispensers()) {
                var block = world.getBlockAt(dispenser.x(), dispenser.y(), dispenser.z());
                if (block.getType() != Material.DISPENSER) {
                    issues.add("fire-snake-source-missing:" + evidence.id() + ":" + dispenser.compact());
                    continue;
                }
                if (!(block.getBlockData() instanceof Directional directional)
                        || directional.getFacing() != expectedFacing)
                    issues.add("fire-snake-source-facing:" + evidence.id() + ":" + dispenser.compact());
            }

            for (BlockExpectation expected : evidence.path()) {
                Material material = Material.matchMaterial(expected.materialName());
                Material actual = world.getBlockAt(expected.pos().x(), expected.pos().y(), expected.pos().z()).getType();
                if (material == null || actual != material)
                    issues.add("fire-snake-path-mismatch:" + evidence.id() + ":" + expected.pos().compact() + ":"
                            + actual.name() + "/" + expected.materialName());
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
        return 8 * 7; // x=85..92, y=25, z=79..85; never decoration or side platforms.
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
        return FIRE_ARROW_TRAPS.size() + 20;
    }

    public static @NotNull MaterialTrapEvidence iceMeltEvidence() {
        return ICE_MELT_TRAP;
    }

    public static @NotNull MaterialTrapEvidence coalFireEvidence() {
        return COAL_FIRE_TRAP;
    }

    public static @NotNull List<MaterialTrapEvidence> redBlockEvidence() {
        return List.of(RED_A_TRAP, RED_B_TRAP, RED_C_TRAP, RED_D_TRAP);
    }

    public static @NotNull MaterialTrapEvidence seaLanternEvidence() {
        return SEA_LANTERN_TRAP;
    }
    public static @NotNull List<MaterialTrapEvidence> darkWoodEvidence() {
        return List.of(DARK_WOOD_A_TRAP, DARK_WOOD_B_TRAP);
    }

    public static @NotNull MaterialTrapEvidence dropTntEvidence() {
        return DROP_TNT_TRAP;
    }
    public static @NotNull MaterialTrapEvidence floorFallEvidence() {
        return FLOOR_FALL_A_TRAP;
    }
    public static @NotNull MaterialTrapEvidence floorFallBEvidence() {
        return FLOOR_FALL_B_TRAP;
    }


    public static @NotNull MaterialTrapEvidence minefieldEvidence() {
        return MINEFIELD_TRAP;
    }

    public static @NotNull MaterialTrapEvidence floodEvidence() {
        return FLOOD_B_TRAP;
    }

    public static @NotNull List<MaterialTrapEvidence> floodEvidenceAll() {
        return List.of(FLOOD_A_TRAP, FLOOD_B_TRAP);
    }

    public static @NotNull List<MaterialTrapEvidence> randomWallEvidence() {
        return List.of(RANDOM_WALL_A_TRAP, RANDOM_WALL_B_TRAP, RANDOM_WALL_C_TRAP);
    }


    public static @NotNull FireSnakeEvidence fireSnakeEvidence() {
        return FIRE_SNAKE_A_TRAP;
    }

    public static @NotNull List<FireSnakeEvidence> fireSnakeEvidenceAll() {
        return FIRE_SNAKE_TRAPS;
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
                "runner-start-layout-generated-from-safe-archive-geometry-not-original",
                "death-spawns-generated-from-first-stage-control-geometry-not-original",
                "death-button-to-trap-bindings-partially-reconstructed-runtime-set",
                "trap-target-geometry-recovered-23-of-23-action-traps",
                "all-surviving-action-signs-have-runtime-traps",
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
    private static void addFireSnakeTrap(
            @NotNull MapConfiguration.MapDefinition map,
            @NotNull World world,
            @NotNull FireSnakeEvidence evidence
    ) {
        TrapFireSnake trap = new TrapFireSnake();
        trap.setButton(blockLocation(world, evidence.button()));
        trap.setLocations(evidence.path().stream()
                .map(cell -> blockLocation(world, cell.pos()))
                .toList());
        trap.setReverse(evidence.reverse());
        map.arenaTraps.add(trap);
    }

    private static void addMinefieldTrap(
            @NotNull MapConfiguration.MapDefinition map,
            @NotNull World world
    ) {
        TrapMinefield trap = new TrapMinefield();
        trap.setButton(blockLocation(world, MINEFIELD_TRAP.button()));
        trap.setLocations(MINEFIELD_TRAP.targets().stream()
                .map(pos -> blockLocation(world, new BlockPos(pos.x(), pos.y() + 1, pos.z())))
                .toList());
        map.arenaTraps.add(trap);
    }

    private static void addDropTntTrap(
            @NotNull MapConfiguration.MapDefinition map,
            @NotNull World world
    ) {
        TrapDropTNT trap = new TrapDropTNT();
        trap.setButton(blockLocation(world, DROP_TNT_TRAP.button()));
        trap.setLocations(DROP_TNT_TRAP.targets().stream()
                .map(pos -> blockLocation(world, pos))
                .toList());
        map.arenaTraps.add(trap);
    }

    private static void addWallTrap(@NotNull MapConfiguration.MapDefinition map, @NotNull World world,
                                    @NotNull MaterialTrapEvidence evidence) {
        TrapWallSpawn trap = new TrapWallSpawn();
        trap.setButton(blockLocation(world, evidence.button()));
        trap.setLocations(evidence.targets().stream().map(pos -> blockLocation(world, pos)).toList());
        map.arenaTraps.add(trap);
    }

    private static void addFloodTrap(
            @NotNull MapConfiguration.MapDefinition map,
            @NotNull World world,
            @NotNull MaterialTrapEvidence evidence
    ) {
        TrapFlood trap = new TrapFlood();
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

    private static @NotNull List<BlockPos> redCTargetPositions() {
        return List.of(
                new BlockPos(34, 32, 84),
                new BlockPos(31, 33, 83),
                new BlockPos(35, 33, 79),
                new BlockPos(29, 33, 85),
                new BlockPos(29, 33, 81),
                new BlockPos(27, 33, 79)
        );
    }

    private static @NotNull List<BlockPos> darkWoodBTargetPositions() {
        List<BlockPos> positions = new ArrayList<>();
        for (int x = 26; x <= 39; x++)
            positions.add(new BlockPos(x, 35, 70));
        for (int z = 71; z <= 73; z++)
            positions.add(new BlockPos(39, 35, z));
        return List.copyOf(positions);
    }

    private static @NotNull List<BlockPos> dropTntAnchorPositions() {
        return List.of(
                new BlockPos(33, 49, 4),
                new BlockPos(34, 49, 3),
                new BlockPos(34, 49, 4),
                new BlockPos(34, 49, 5),
                new BlockPos(35, 49, 4)
        );
    }

    private static @NotNull List<BlockPos> randomWallATargetPositions() {
        List<BlockPos> p = new ArrayList<>();
        for (int x = -61; x <= -56; x++) for (int y = 25; y <= 26; y++) p.add(new BlockPos(x, y, -5));
        return List.copyOf(p);
    }
    private static @NotNull List<BlockPos> randomWallBTargetPositions() {
        List<BlockPos> p = new ArrayList<>();
        for (int x = -2; x <= 6; x++) for (int y = 25; y <= 26; y++) p.add(new BlockPos(x, y, 34));
        return List.copyOf(p);
    }
    private static @NotNull List<BlockPos> randomWallCTargetPositions() {
        List<BlockPos> p = new ArrayList<>();
        for (int x = 28; x <= 32; x++) for (int y = 25; y <= 26; y++) p.add(new BlockPos(x, y, -29));
        return List.copyOf(p);
    }

    private static @NotNull List<BlockPos> floodATargetPositions() {
        Map<Integer, int[]> rows = new LinkedHashMap<>();
        rows.put(-46, new int[]{19,21,22,23,24,25,26,27});
        rows.put(-45, new int[]{19,20,21,22,23,24,25,26,27});
        rows.put(-44, new int[]{19,20,21,22,23,24,25,26});
        rows.put(-43, new int[]{19,20,21,22,23,24,25,26});
        rows.put(-42, new int[]{19,20,21,22,23,24,25,26,27});
        rows.put(-41, new int[]{19,20,21,22,23,24,25,26});
        rows.put(-40, new int[]{20,21,22,23,24,25,26,27});
        rows.put(-39, new int[]{19,20,21,22,23,24,25,27});
        rows.put(-38, new int[]{20,21,22,23,24,25,26,27});
        rows.put(-37, new int[]{19,20,21,22,23,24,25,26});
        rows.put(-36, new int[]{19,21,22,23,24,25,26,27});

        List<BlockPos> positions = new ArrayList<>();
        rows.forEach((x, zs) -> {
            for (int z : zs)
                positions.add(new BlockPos(x, 24, z));
        });
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
    private static @NotNull List<BlockPos> fireSnakeASourceDispensers() {
        List<BlockPos> positions = new ArrayList<>();
        for (int x = -1; x <= 7; x++)
            positions.add(new BlockPos(x, 24, 51));
        return List.copyOf(positions);
    }

    private static @NotNull List<BlockPos> fireSnakeBSourceDispensers() {
        List<BlockPos> positions = new ArrayList<>();
        for (int z = 47; z <= 54; z++)
            positions.add(new BlockPos(84, 24, z));
        return List.copyOf(positions);
    }

    private static @NotNull List<BlockExpectation> fireSnakeBPathCells() {
        List<BlockExpectation> cells = new ArrayList<>();
        addFireSnakeBRow(cells, 83, 23, "DGDGGGCG");
        addFireSnakeBRow(cells, 82, 23, "DCGGCDGG");
        addFireSnakeBRow(cells, 81, 23, ".GGGDDG.");
        addFireSnakeBRow(cells, 80, 23, "GCCGGDDD");
        addFireSnakeBRow(cells, 79, 23, ".DGGGCG.");
        addFireSnakeBRow(cells, 78, 23, "DGGDGCCD");
        addFireSnakeBRow(cells, 77, 23, "CGGCDDCG");
        addFireSnakeBRow(cells, 76, 23, ".GGGGGGC");
        addFireSnakeBRow(cells, 75, 23, "GGCGGDGC");
        addFireSnakeBRow(cells, 74, 23, ".GGDDGD.");
        addFireSnakeBRow(cells, 73, 23, "CCGGCDDC");
        addFireSnakeBRow(cells, 72, 23, ".GGGDCG.");
        addFireSnakeBRow(cells, 71, 24, ".SSSSSS.");
        addFireSnakeBRow(cells, 70, 24, ".GGGGG.G");
        addFireSnakeBRow(cells, 69, 24, "G.GGGGGG");
        addFireSnakeBRow(cells, 68, 24, "GGGGGGGG");
        addFireSnakeBRow(cells, 67, 24, ".GGGGGG.");
        addFireSnakeBRow(cells, 66, 24, "GGGGGGG.");
        addFireSnakeBRow(cells, 65, 24, ".GGGG.G.");
        addFireSnakeBRow(cells, 64, 24, "GGGGGGGG");
        addFireSnakeBRow(cells, 63, 24, "GGGGGGG.");
        return List.copyOf(cells);
    }

    private static void addFireSnakeBRow(
            @NotNull List<BlockExpectation> cells,
            int x,
            int y,
            @NotNull String spec
    ) {
        if (spec.length() != 8)
            throw new IllegalArgumentException("Fire Snake B row spec must span z=47..54");

        for (int index = 0; index < spec.length(); index++) {
            String material = switch (spec.charAt(index)) {
                case 'G' -> "GRASS_BLOCK";
                case 'D' -> "DIRT";
                case 'C' -> "COARSE_DIRT";
                case 'S' -> "STONE_BRICK_STAIRS";
                case '.' -> null;
                default -> throw new IllegalArgumentException("Unknown Fire Snake B row token");
            };
            if (material != null)
                cells.add(new BlockExpectation(new BlockPos(x, y, 47 + index), material));
        }
    }

    private static @NotNull List<BlockExpectation> fireSnakeAPathCells() {
        List<BlockExpectation> cells = new ArrayList<>();
        for (int z = 52; z <= 58; z++)
            for (int x = -1; x <= 7; x++)
                cells.add(new BlockExpectation(new BlockPos(x, 23, z), "CYAN_TERRACOTTA"));
        for (int x = -1; x <= 7; x++)
            cells.add(new BlockExpectation(new BlockPos(x, 24, 59), "STONE_BRICK_STAIRS"));
        for (int x = -1; x <= 7; x++) {
            for (int z = 60; z <= 64; z++) {
                if (x == 6 && z == 60)
                    continue;
                cells.add(new BlockExpectation(new BlockPos(x, 24, z), "GRASS_BLOCK"));
            }
        }
        for (int x = -1; x <= 7; x++)
            cells.add(new BlockExpectation(new BlockPos(x, 24, 65), "STONE_BRICK_STAIRS"));
        return List.copyOf(cells);
    }

    private static @NotNull List<BlockPos> floorFallATargetPositions() {
        List<BlockPos> positions = new ArrayList<>();
        for (int x = 29; x <= 37; x++) {
            for (int z = -27; z <= -18; z++) {
                if (x == 29 && z >= -24 && z <= -20)
                    continue;
                if (x == 37 && z >= -27 && z <= -25)
                    continue;
                positions.add(new BlockPos(x, 24, z));
            }
        }
        return List.copyOf(positions);
    }

    private static @NotNull List<BlockPos> floorFallBTargetPositions() {
        List<BlockPos> positions = new ArrayList<>();
        for (int z = 48; z <= 53; z++)
            positions.add(new BlockPos(51, 24, z));
        for (int x = 52; x <= 60; x++)
            for (int z = 47; z <= 54; z++)
                positions.add(new BlockPos(x, 24, z));
        return List.copyOf(positions);
    }

    private static @NotNull List<BlockPos> minefieldTntPositions() {
        return List.of(
                new BlockPos(-2,23,37), new BlockPos(-2,23,38), new BlockPos(-2,23,39),
                new BlockPos(-2,23,40), new BlockPos(-2,23,41),
                new BlockPos(-1,23,36), new BlockPos(-1,23,37), new BlockPos(-1,23,41), new BlockPos(-1,23,42),
                new BlockPos(0,23,36), new BlockPos(0,23,42),
                new BlockPos(1,23,43),
                new BlockPos(2,23,38), new BlockPos(2,23,43),
                new BlockPos(3,23,38), new BlockPos(3,23,39), new BlockPos(3,23,42), new BlockPos(3,23,43),
                new BlockPos(4,23,39), new BlockPos(4,23,40), new BlockPos(4,23,41), new BlockPos(4,23,42),
                new BlockPos(4,23,36),
                new BlockPos(5,23,36), new BlockPos(5,23,37),
                new BlockPos(6,23,37), new BlockPos(6,23,38), new BlockPos(6,23,39),
                new BlockPos(7,23,39), new BlockPos(7,23,40), new BlockPos(7,23,41), new BlockPos(7,23,42)
        );
    }

    private static @NotNull List<BlockPos> floodBTargetPositions() {
        Map<Integer, int[]> rows = Map.of(
                30, new int[]{22,23,24,25,26,27,28,29,30,31},
                31, new int[]{22,25,28,29,31},
                32, new int[]{22,23,24,25,26,27,28,29,30,31},
                33, new int[]{22,23,24,25,27,28,29,30,31},
                34, new int[]{23,24,25,26,27,28,29,30},
                35, new int[]{22,23,24,25,26,27,28,29,30,31},
                36, new int[]{22,23,24,25,26,27,29,30,31},
                37, new int[]{24,25,26,27,28,30,31},
                38, new int[]{21,22,23,24,25,26,27,28,29,30,31}
        );
        List<BlockPos> positions = new ArrayList<>();
        rows.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            for (int z : entry.getValue())
                positions.add(new BlockPos(entry.getKey(), 43, z));
        });
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
        return ToBeeSpawnGeometry.DEATH_STARTS.stream()
                .map(pos -> new BlockPos(pos.x(), pos.y(), pos.z()))
                .filter(pos -> safeStandingColumn(world, pos)).limit(limit).toList();
    }

    private static @NotNull List<BlockPos> runnerStartCandidates(@NotNull World world, int limit) {
        List<BlockPos> candidates = new ArrayList<>();
        for (int x = 85; x <= 92; x++)
            for (int z = 79; z <= 85; z++) {
                BlockPos pos = new BlockPos(x, 25, z);
                if (safeStandingColumn(world, pos)
                        && world.getBlockAt(x, 24, z).getType() == Material.GRASS_BLOCK)
                    candidates.add(pos);
            }
        candidates.sort(Comparator.comparingInt(BlockPos::x)
                .thenComparingInt(pos -> Math.abs(pos.z() - 82)).thenComparingInt(BlockPos::z));
        return candidates.stream().limit(limit).toList();
    }

    public static Location controlLanding(@NotNull World world, @NotNull Location button) {
        for (var control : ToBeeSpawnGeometry.CONTROLS) {
            var pos = control.button();
            if (pos.x() != button.getBlockX() || pos.y() != button.getBlockY() || pos.z() != button.getBlockZ())
                continue;
            var landing = control.landing();
            BlockPos feet = new BlockPos(landing.x(), landing.y(), landing.z());
            return safeStandingColumn(world, feet) ? feetLocation(world, feet, ToBeeSpawnGeometry.DEATH_YAW) : null;
        }
        return null;
    }

    public static List<String> spawnIssues(@NotNull World world, @NotNull MapConfiguration.MapDefinition map) {
        List<String> issues = new ArrayList<>();
        for (Location location : map.arenaRunnerSpawnLocations) {
            if (!spawnWorldMatches(world, location) || !ToBeeSpawnGeometry.runnerRegion(location.getBlockX(), location.getBlockY(), location.getBlockZ())
                    || !safeStandingColumn(world, new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ()))
                    || world.getBlockAt(location.getBlockX(), 24, location.getBlockZ()).getType() != Material.GRASS_BLOCK
                    || Math.abs(location.getYaw() - ToBeeSpawnGeometry.RUNNER_YAW) > 0.01f)
                issues.add("runner-spawn-outside-reviewed-start-or-unsafe:" + location);
        }
        for (Location location : map.arenaDeathSpawnLocations) {
            boolean reviewed = location != null && ToBeeSpawnGeometry.DEATH_STARTS.stream().anyMatch(pos ->
                    pos.x() == location.getBlockX() && pos.y() == location.getBlockY() && pos.z() == location.getBlockZ());
            if (!spawnWorldMatches(world, location) || !reviewed
                    || !safeStandingColumn(world, new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ()))
                    || Math.abs(location.getYaw() - ToBeeSpawnGeometry.DEATH_YAW) > 0.01f)
                issues.add("death-spawn-outside-reviewed-controls-or-unsafe:" + location);
        }
        if (map.arenaRunnerSpawnLocations.stream().distinct().count() != 20)
            issues.add("runner-spawns-not-20-distinct");
        if (map.arenaDeathSpawnLocations.stream().distinct().count() != 2)
            issues.add("death-spawns-not-2-distinct");
        for (var trap : map.arenaTraps)
            if (trap == null || trap.getButton() == null || controlLanding(world, trap.getButton()) == null)
                issues.add("unsafe-or-unreviewed-control-landing:" + (trap == null ? "null" : trap.getButton()));
        return List.copyOf(issues);
    }

    private static boolean spawnWorldMatches(World world, Location location) {
        return location != null && location.getWorld() != null && location.getWorld().getUID().equals(world.getUID());
    }

    /** Repairs an idle existing profile without recreating traps, points or rules. */
    public @NotNull Result repairSpawns(@NotNull World world) {
        this.configuration.map().ensureMapsMutable();
        MapConfiguration.MapDefinition map = this.configuration.map().getMapById(MAP_ID);
        var runtime = this.arenaManager.runtimeByMapId(MAP_ID);
        if (map == null || runtime == null || !world.getName().equals(map.world))
            return new Result(false, "profile-world-unavailable", List.of());
        if (runtime.arena().getGameState() != pl.mrstudios.deathrun.api.arena.enums.GameState.WAITING
                || !runtime.arena().getUsers().isEmpty() || !world.getPlayers().isEmpty())
            return new Result(false, "map-must-be-idle-and-empty", List.of());
        if (!ToBeeCheckpointRepairPlan.valid(map.arenaCheckpoints.stream()
                .map(cp -> cp == null ? null : cp.id()).toList(), map.arenaFinishCheckpointId))
            return new Result(false, "checkpoint-layout-not-reviewed", List.of("expected-ids-1-through-6-and-finish-6"));
        for (Checkpoint cp : map.arenaCheckpoints) {
            int gateId = ToBeeCheckpointRepairPlan.gateForCheckpoint(cp.id());
            GateEvidence gate = GATES.get(gateId);
            if (cp.locations().size() != 2 || !cp.locations().stream().allMatch(loc -> spawnWorldMatches(world, loc))
                    || !sameBlock(cp.locations().get(0), gate.min()) || !sameBlock(cp.locations().get(1), gate.max())
                    || !safeStandingColumn(world, SAFE_ROUTE_SIDE_CANDIDATES.get(gateId)))
                return new Result(false, "checkpoint-geometry-not-reviewed", List.of("checkpoint:" + cp.id()));
        }
        java.nio.file.Path configPath = this.configuration.map().getBindFile();
        byte[] previousConfig;
        try {
            if (configPath == null) throw new java.io.IOException("Map configuration has no file");
            previousConfig = java.nio.file.Files.readAllBytes(configPath);
        } catch (Exception failure) {
            return new Result(false, "map-backup-read-failed", List.of(String.valueOf(failure.getMessage())));
        }
        var runners = runnerStartCandidates(world, 20);
        var deaths = deathStartCandidates(world, 2);
        if (runners.size() != 20 || deaths.size() != 2)
            return new Result(false, "reviewed-platforms-obstructed", List.of());
        var oldRunners = map.arenaRunnerSpawnLocations;
        var oldDeaths = map.arenaDeathSpawnLocations;
        map.arenaRunnerSpawnLocations = new ArrayList<>(runners.stream()
                .map(pos -> feetLocation(world, pos, ToBeeSpawnGeometry.RUNNER_YAW)).toList());
        map.arenaDeathSpawnLocations = new ArrayList<>(deaths.stream()
                .map(pos -> feetLocation(world, pos, ToBeeSpawnGeometry.DEATH_YAW)).toList());
        // Waiting-room configuration is independent of Runner/Death race spawns.
        var issues = spawnIssues(world, map);
        if (!issues.isEmpty()) {
            map.arenaRunnerSpawnLocations = oldRunners;
            map.arenaDeathSpawnLocations = oldDeaths;
            return new Result(false, "spawn-check-failed", issues);
        }
        String oldName = map.name;
        var oldCheckpoints = map.arenaCheckpoints;
        var signBackup = ToBeeSignTranslations.ENTRIES.stream()
                .map(entry -> world.getBlockAt(entry.x(), entry.y(), entry.z()).getState())
                .filter(state -> state instanceof org.bukkit.block.Sign).toList();
        boolean saved = false;
        try {
            map.name = MAP_NAME;
            map.arenaCheckpoints = new ArrayList<>(oldCheckpoints.stream().map(cp -> {
                int gate = ToBeeCheckpointRepairPlan.gateForCheckpoint(cp.id());
                return new Checkpoint(cp.id(), feetLocation(world, SAFE_ROUTE_SIDE_CANDIDATES.get(gate)), cp.locations(),
                        cp.id().equals(map.arenaFinishCheckpointId) ? "终点" : "检查点 " + cp.id());
            }).toList());
            pl.mrstudios.deathrun.config.AtomicConfigurationSave.save(this.configuration.map());
            saved = true;
            ToBeeSignTranslations.apply(world);
            this.arenaManager.reloadRuntime(MAP_ID);
            return new Result(true, "spawn-repair-complete", List.of("20-runner", "2-death", "23-controls"));
        } catch (Exception failure) {
            map.name = oldName;
            map.arenaCheckpoints = oldCheckpoints;
            map.arenaRunnerSpawnLocations = oldRunners;
            map.arenaDeathSpawnLocations = oldDeaths;
            List<String> failures = new ArrayList<>();
            failures.add(String.valueOf(failure.getMessage()));
            try {
                if (saved) pl.mrstudios.deathrun.config.AtomicConfigurationSave.restore(configPath, previousConfig);
                for (var sign : signBackup) if (!sign.update(true, false)) failures.add("sign-rollback-failed:" + sign.getLocation());
                this.arenaManager.reloadRuntime(MAP_ID);
            } catch (Exception rollbackFailure) { failures.add("rollback-failed:" + rollbackFailure.getMessage()); }
            return new Result(false, "spawn-repair-rolled-back", failures);
        }
    }

    private static boolean sameBlock(Location location, BlockPos position) {
        return location.getBlockX() == position.x() && location.getBlockY() == position.y() && location.getBlockZ() == position.z();
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

    static boolean startBarrierOverlayReplaceable(@NotNull Material material) {
        return startBarrierOverlayReplaceableName(material.name());
    }

    static boolean startBarrierOverlayReplaceableName(@NotNull String materialName) {
        // Keep this name-based so the pure unit-test source set does not need
        // Paper/Bukkit on its compile classpath. Runtime still passes the real
        // Bukkit Material enum name through this conservative allowlist.
        return switch (materialName) {
            case "AIR", "CAVE_AIR", "VOID_AIR", "BARRIER",
                 "SHORT_GRASS", "TALL_GRASS", "FERN", "LARGE_FERN" -> true;
            default -> false;
        };
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
                && world.getBlockAt(candidate.x(), candidate.y() - 1, candidate.z()).getType().isOccluding();
    }

    private static boolean safeStandingColumn(@NotNull World world, @NotNull BlockPos candidate) {
        var feet = world.getBlockAt(candidate.x(), candidate.y(), candidate.z());
        var head = world.getBlockAt(candidate.x(), candidate.y() + 1, candidate.z());
        var floor = world.getBlockAt(candidate.x(), candidate.y() - 1, candidate.z());
        return feet.isPassable() && head.isPassable() && floor.getType().isSolid()
                && !unsafeLandingMaterial(feet.getType()) && !unsafeLandingMaterial(head.getType())
                && !unsafeLandingMaterial(floor.getType());
    }

    private static boolean unsafeLandingMaterial(Material material) {
        return switch (material) {
            case WATER, LAVA, POWDER_SNOW, FIRE, SOUL_FIRE, MAGMA_BLOCK, CACTUS, COBWEB -> true;
            default -> false;
        };
    }

    private static @NotNull Location feetLocation(@NotNull World world, @NotNull BlockPos pos) {
        float yaw = switch (pos.x()) {
            case 7, -14, 84 -> 90f;
            case -5 -> -90f;
            default -> 180f;
        };
        return feetLocation(world, pos, yaw);
    }

    private static @NotNull Location feetLocation(@NotNull World world, @NotNull BlockPos pos, float yaw) {
        return new Location(world, pos.x() + 0.5, pos.y(), pos.z() + 0.5, yaw, 0f);
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


    public record BlockExpectation(
            @NotNull BlockPos pos,
            @NotNull String materialName
    ) {}

    public record FireSnakeEvidence(
            @NotNull String id,
            @NotNull BlockPos warning,
            @NotNull BlockPos actionSign,
            @NotNull BlockPos button,
            @NotNull List<BlockPos> sourceDispensers,
            @NotNull String sourceFacingName,
            @NotNull List<BlockExpectation> path,
            boolean reverse,
            @NotNull String confidence
    ) {}

    public record Result(
            boolean success,
            @NotNull String message,
            @NotNull List<String> details
    ) {}
}
