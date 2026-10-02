package pl.mrstudios.deathrun.classic.tobee;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToBeeCandidateProfileServiceTest {

    @Test
    void archiveBackedRouteManifestKeepsCrossCorroboratedOrder() {
        assertEquals(List.of(6, 7, 2, 1, 4, 5, 3), ToBeeCandidateProfileService.routeGateIds());
        assertEquals(List.of(7, 2, 1, 4, 5, 3), ToBeeCandidateProfileService.checkpointGateIds());
        assertEquals(3, ToBeeCandidateProfileService.finishGateId());
        assertEquals(20, ToBeeCandidateProfileService.runnerSpawnTarget());
        assertEquals(2, ToBeeCandidateProfileService.deathSpawnTarget());
        assertEquals(56, ToBeeCandidateProfileService.startSearchColumnCount());
        assertEquals(14, ToBeeCandidateProfileService.startBarrierPositions().size());
        assertEquals(3, ToBeeCandidateProfileService.fireArrowTrapCount());
        assertEquals(23, ToBeeCandidateProfileService.implementedTrapCount());
        assertEquals(366, ToBeeCandidateProfileService.expectedPortalBlockTotal());
        assertEquals(7, new HashSet<>(ToBeeCandidateProfileService.routeGateIds()).size());
    }

    @Test
    void allSevenResolvedRouteSidesStayPinned() {
        assertEquals(new ToBeeCandidateProfileService.BlockPos(84, 25, 81),
                ToBeeCandidateProfileService.safeRouteSideCandidate(6));
        assertEquals(new ToBeeCandidateProfileService.BlockPos(33, 45, 35),
                ToBeeCandidateProfileService.safeRouteSideCandidate(7));
        assertEquals(new ToBeeCandidateProfileService.BlockPos(7, 25, 81),
                ToBeeCandidateProfileService.safeRouteSideCandidate(2));
        assertEquals(new ToBeeCandidateProfileService.BlockPos(-14, 25, 22),
                ToBeeCandidateProfileService.safeRouteSideCandidate(1));
        assertEquals(new ToBeeCandidateProfileService.BlockPos(-59, 25, 5),
                ToBeeCandidateProfileService.safeRouteSideCandidate(4));
        assertEquals(new ToBeeCandidateProfileService.BlockPos(-5, 18, -38),
                ToBeeCandidateProfileService.safeRouteSideCandidate(5));
        assertEquals(new ToBeeCandidateProfileService.BlockPos(33, 25, -9),
                ToBeeCandidateProfileService.safeRouteSideCandidate(3));
    }

    @Test
    void startBarrierAllowsOnlyAirBarrierAndReplaceableVegetation() {
        assertTrue(ToBeeCandidateProfileService.startBarrierOverlayReplaceableName("AIR"));
        assertTrue(ToBeeCandidateProfileService.startBarrierOverlayReplaceableName("CAVE_AIR"));
        assertTrue(ToBeeCandidateProfileService.startBarrierOverlayReplaceableName("VOID_AIR"));
        assertTrue(ToBeeCandidateProfileService.startBarrierOverlayReplaceableName("BARRIER"));
        assertTrue(ToBeeCandidateProfileService.startBarrierOverlayReplaceableName("SHORT_GRASS"));
        assertTrue(ToBeeCandidateProfileService.startBarrierOverlayReplaceableName("TALL_GRASS"));
        assertTrue(ToBeeCandidateProfileService.startBarrierOverlayReplaceableName("FERN"));
        assertTrue(ToBeeCandidateProfileService.startBarrierOverlayReplaceableName("LARGE_FERN"));

        org.junit.jupiter.api.Assertions.assertFalse(
                ToBeeCandidateProfileService.startBarrierOverlayReplaceableName("WATER"));
        org.junit.jupiter.api.Assertions.assertFalse(
                ToBeeCandidateProfileService.startBarrierOverlayReplaceableName("LAVA"));
        org.junit.jupiter.api.Assertions.assertFalse(
                ToBeeCandidateProfileService.startBarrierOverlayReplaceableName("OAK_BUTTON"));
        org.junit.jupiter.api.Assertions.assertFalse(
                ToBeeCandidateProfileService.startBarrierOverlayReplaceableName("OAK_SIGN"));
        org.junit.jupiter.api.Assertions.assertFalse(
                ToBeeCandidateProfileService.startBarrierOverlayReplaceableName("STONE"));
    }

    @Test
    void reconstructedStartBarrierStaysOutsidePortal006AndHasNoDuplicateCells() {
        List<ToBeeCandidateProfileService.BlockPos> barrier = ToBeeCandidateProfileService.startBarrierPositions();
        assertEquals(14, new HashSet<>(barrier).size());
        assertTrue(barrier.stream().allMatch(pos -> pos.x() == 84));
        assertTrue(barrier.stream().allMatch(pos -> pos.y() >= 25 && pos.y() <= 26));
        assertTrue(barrier.stream().allMatch(pos -> pos.z() >= 79 && pos.z() <= 85));
    }

    @Test
    void fireArrowEvidencePinsAllThreeArchiveDispenserBanks() {
        var evidence = ToBeeCandidateProfileService.fireArrowEvidence();
        assertEquals(3, evidence.size());
        assertEquals(List.of("pair-011", "pair-007", "pair-005"),
                evidence.stream().map(ToBeeCandidateProfileService.FireArrowEvidence::id).toList());
        assertEquals(List.of(10, 7, 6),
                evidence.stream().map(item -> item.dispensers().size()).toList());
        assertEquals(23, evidence.stream().mapToInt(item -> item.dispensers().size()).sum());
        assertEquals(23, new HashSet<>(evidence.stream()
                .flatMap(item -> item.dispensers().stream())
                .toList()).size());
    }

    @Test
    void semanticMaterialTrapsPinCleanArchiveTargets() {
        var ice = ToBeeCandidateProfileService.iceMeltEvidence();
        assertEquals("pair-001-melt-ice", ice.id());
        assertEquals("PACKED_ICE", ice.expectedMaterialName());
        assertEquals(12, ice.targets().size());
        assertEquals(12, new HashSet<>(ice.targets()).size());
        assertEquals(new ToBeeCandidateProfileService.BlockPos(-45, 29, 7), ice.actionSign());
        assertEquals(new ToBeeCandidateProfileService.BlockPos(-41, 35, 3), ice.button());

        var coal = ToBeeCandidateProfileService.coalFireEvidence();
        assertEquals("pair-015-coal-fire", coal.id());
        assertEquals("COAL_BLOCK", coal.expectedMaterialName());
        assertEquals(35, coal.targets().size());
        assertEquals(35, new HashSet<>(coal.targets()).size());
        assertEquals(new ToBeeCandidateProfileService.BlockPos(30, 44, 15), coal.actionSign());
        assertEquals(new ToBeeCandidateProfileService.BlockPos(31, 48, 14), coal.button());
    }

    @Test
    void disappearingMaterialTrapsPinArchiveRouteGeometry() {
        var reds = ToBeeCandidateProfileService.redBlockEvidence();
        assertEquals(List.of("pair-006-red-A", "pair-012-red-B", "pair-018-red-C", "pair-021-red-D"),
                reds.stream().map(ToBeeCandidateProfileService.MaterialTrapEvidence::id).toList());
        assertEquals(List.of(7, 15, 6, 21), reds.stream().map(item -> item.targets().size()).toList());
        assertTrue(reds.stream().allMatch(item -> item.expectedMaterialName().equals("RED_TERRACOTTA")));
        assertEquals(49, reds.stream().mapToInt(item -> item.targets().size()).sum());
        assertEquals(new ToBeeCandidateProfileService.BlockPos(15, 35, 60), reds.get(2).button());

        var sea = ToBeeCandidateProfileService.seaLanternEvidence();
        assertEquals("action-021-sea-lantern", sea.id());
        assertEquals("SEA_LANTERN", sea.expectedMaterialName());
        assertEquals(56, sea.targets().size());
        assertEquals(56, new HashSet<>(sea.targets()).size());
        assertEquals(new ToBeeCandidateProfileService.BlockPos(74, 25, 54), sea.button());
    }
    @Test
    void darkWoodTrapPinsNearestRouteLevelArchiveRun() {
        var wood = ToBeeCandidateProfileService.darkWoodEvidence();
        assertEquals(2, wood.size());
        var first = wood.get(0);
        assertEquals("pair-002-dark-wood-A", first.id());
        assertEquals("SPRUCE_WOOD", first.expectedMaterialName());
        assertEquals(new ToBeeCandidateProfileService.BlockPos(-45, 29, -18), first.actionSign());
        assertEquals(new ToBeeCandidateProfileService.BlockPos(-41, 35, -14), first.button());
        assertEquals(List.of(
                new ToBeeCandidateProfileService.BlockPos(-61, 24, -20),
                new ToBeeCandidateProfileService.BlockPos(-60, 24, -20),
                new ToBeeCandidateProfileService.BlockPos(-59, 24, -20),
                new ToBeeCandidateProfileService.BlockPos(-58, 24, -20)
        ), first.targets());
    }

    @Test
    void surfaceEvidencePromotesFloorMinefieldAndFloodTargets() {
        var floor = ToBeeCandidateProfileService.floorFallEvidence();
        assertEquals("pair-016-floor-fall-A", floor.id());
        assertEquals("LIGHT_BLUE_STAINED_GLASS", floor.expectedMaterialName());
        assertEquals(82, floor.targets().size());
        assertEquals(82, new HashSet<>(floor.targets()).size());

        var minefield = ToBeeCandidateProfileService.minefieldEvidence();
        assertEquals("pair-009-minefield", minefield.id());
        assertEquals("TNT", minefield.expectedMaterialName());
        assertEquals(32, minefield.targets().size());
        assertEquals(32, new HashSet<>(minefield.targets()).size());

        var flood = ToBeeCandidateProfileService.floodEvidence();
        assertEquals("pair-017-flood-B", flood.id());
        assertEquals("STONE_BRICKS", flood.expectedMaterialName());
        assertEquals(79, flood.targets().size());
        assertEquals(79, new HashSet<>(flood.targets()).size());

        var floods = ToBeeCandidateProfileService.floodEvidenceAll();
        assertEquals(List.of("pair-004-flood-A", "pair-017-flood-B"),
                floods.stream().map(ToBeeCandidateProfileService.MaterialTrapEvidence::id).toList());
        assertEquals(List.of(90, 79), floods.stream().map(item -> item.targets().size()).toList());
        assertEquals("GRASS_BLOCK", floods.get(0).expectedMaterialName());
        assertEquals(new ToBeeCandidateProfileService.BlockPos(-39, 35, 6), floods.get(0).button());
    }

    @Test
    void randomWallReconstructionsUseArchiveStandingSeams() {
        var walls = ToBeeCandidateProfileService.randomWallEvidence();
        assertEquals(List.of("pair-003-random-wall-A", "pair-008-random-wall-B", "pair-013-random-wall-C"),
                walls.stream().map(ToBeeCandidateProfileService.MaterialTrapEvidence::id).toList());
        assertEquals(List.of(12, 18, 10), walls.stream().map(item -> item.targets().size()).toList());
        assertTrue(walls.stream().allMatch(item -> item.expectedMaterialName().equals("AIR")));
        assertEquals(40, walls.stream().mapToInt(item -> item.targets().size()).sum());
        assertEquals(40, new HashSet<>(walls.stream().flatMap(item -> item.targets().stream()).toList()).size());
        assertEquals(new ToBeeCandidateProfileService.BlockPos(-41, 35, 1), walls.get(0).button());
        assertEquals(new ToBeeCandidateProfileService.BlockPos(-6, 35, 6), walls.get(1).button());
        assertEquals(new ToBeeCandidateProfileService.BlockPos(22, 35, -15), walls.get(2).button());
    }

    @Test
    void secondFloorFallCompletesLowStagePanelWithoutButtonReuse() {
        var floor = ToBeeCandidateProfileService.floorFallBEvidence();
        assertEquals("pair-019-floor-fall-B", floor.id());
        assertEquals("GREEN_STAINED_GLASS", floor.expectedMaterialName());
        assertEquals(78, floor.targets().size());
        assertEquals(78, new HashSet<>(floor.targets()).size());
        assertEquals(new ToBeeCandidateProfileService.BlockPos(52, 25, 56), floor.actionSign());
        assertEquals(new ToBeeCandidateProfileService.BlockPos(79, 25, 54), floor.button());

        var lowPanelButtons = List.of(
                ToBeeCandidateProfileService.deathControlButtonCandidate(),
                ToBeeCandidateProfileService.seaLanternEvidence().button(),
                ToBeeCandidateProfileService.redBlockEvidence().get(2).button(),
                floor.button()
        );
        assertEquals(4, new HashSet<>(lowPanelButtons).size());
    }

    @Test
    void fireSnakeAUsesPreservedSouthFacingSourceAndRoutePath() {
        var snake = ToBeeCandidateProfileService.fireSnakeEvidence();
        assertEquals("pair-010-fire-snake-A", snake.id());
        assertEquals(new ToBeeCandidateProfileService.BlockPos(6, 25, 60), snake.warning());
        assertEquals(new ToBeeCandidateProfileService.BlockPos(11, 29, 55), snake.actionSign());
        assertEquals(new ToBeeCandidateProfileService.BlockPos(15, 35, 55), snake.button());
        assertEquals(9, snake.sourceDispensers().size());
        assertEquals(125, snake.path().size());
        assertEquals(125, new HashSet<>(snake.path().stream()
                .map(ToBeeCandidateProfileService.BlockExpectation::pos).toList()).size());
        assertEquals(63, snake.path().stream().filter(cell -> cell.materialName().equals("CYAN_TERRACOTTA")).count());
        assertEquals(18, snake.path().stream().filter(cell -> cell.materialName().equals("STONE_BRICK_STAIRS")).count());
        assertEquals(44, snake.path().stream().filter(cell -> cell.materialName().equals("GRASS_BLOCK")).count());
    }

    @Test
    void fireSnakeBUsesEightWestSourcesAndTwentyOneWestboundSlices() {
        var snakes = ToBeeCandidateProfileService.fireSnakeEvidenceAll();
        assertEquals(2, snakes.size());

        var snake = snakes.get(1);
        assertEquals("pair-020-fire-snake-B", snake.id());
        assertEquals(new ToBeeCandidateProfileService.BlockPos(70, 25, 53), snake.warning());
        assertEquals(new ToBeeCandidateProfileService.BlockPos(76, 25, 56), snake.actionSign());
        assertEquals(new ToBeeCandidateProfileService.BlockPos(76, 25, 47), snake.button());
        assertEquals("WEST", snake.sourceFacingName());
        assertTrue(snake.reverse());
        assertEquals(8, snake.sourceDispensers().size());
        assertEquals(147, snake.path().size());
        assertEquals(147, new HashSet<>(snake.path().stream()
                .map(ToBeeCandidateProfileService.BlockExpectation::pos).toList()).size());
        assertEquals(21, snake.path().stream()
                .map(cell -> cell.pos().x())
                .distinct()
                .count());
        assertEquals(6, snake.path().stream()
                .filter(cell -> cell.materialName().equals("STONE_BRICK_STAIRS")).count());
        assertTrue(snake.path().stream().noneMatch(cell ->
                cell.materialName().endsWith("_SIGN") || cell.materialName().endsWith("_FENCE")));
    }

    @Test
    void finalDarkWoodAndDropTntEvidenceCompletesActionCatalog() {
        var woods = ToBeeCandidateProfileService.darkWoodEvidence();
        assertEquals(2, woods.size());
        var darkB = woods.get(1);
        assertEquals("pair-014-dark-wood-B", darkB.id());
        assertEquals("SPRUCE_SLAB", darkB.expectedMaterialName());
        assertEquals(17, darkB.targets().size());
        assertEquals(17, new HashSet<>(darkB.targets()).size());
        assertEquals(new ToBeeCandidateProfileService.BlockPos(16, 25, 76), darkB.actionSign());
        assertEquals(new ToBeeCandidateProfileService.BlockPos(15, 35, 57), darkB.button());

        var drop = ToBeeCandidateProfileService.dropTntEvidence();
        assertEquals("action-017-drop-TNT", drop.id());
        assertEquals("TNT", drop.expectedMaterialName());
        assertEquals(5, drop.targets().size());
        assertEquals(5, new HashSet<>(drop.targets()).size());
        assertEquals(new ToBeeCandidateProfileService.BlockPos(29, 44, 9), drop.actionSign());
        assertEquals(new ToBeeCandidateProfileService.BlockPos(28, 48, 7), drop.button());

        var allKnownButtons = List.of(
                darkB.button(),
                drop.button(),
                ToBeeCandidateProfileService.coalFireEvidence().button(),
                ToBeeCandidateProfileService.floodEvidence().button(),
                ToBeeCandidateProfileService.fireSnakeEvidence().button(),
                ToBeeCandidateProfileService.fireArrowEvidence().get(0).button(),
                ToBeeCandidateProfileService.redBlockEvidence().get(2).button()
        );
        assertEquals(allKnownButtons.size(), new HashSet<>(allKnownButtons).size());
    }

    @Test
    void deathControlStartAnchorsStayPinnedToUniqueArchiveCandidate() {
        assertEquals(new ToBeeCandidateProfileService.BlockPos(76, 25, 47),
                ToBeeCandidateProfileService.deathControlButtonCandidate());
        assertEquals(new ToBeeCandidateProfileService.BlockPos(76, 25, 56),
                ToBeeCandidateProfileService.deathControlActionAnchor());
    }

    @Test
    void portalComponentBoundsStayPinnedToArchiveEvidence() {
        var start = ToBeeCandidateProfileService.gateEvidence(6);
        assertEquals(new ToBeeCandidateProfileService.BlockPos(83, 25, 79), start.min());
        assertEquals(new ToBeeCandidateProfileService.BlockPos(83, 32, 85), start.max());
        assertEquals(46, start.expectedPortalBlocks());

        var finish = ToBeeCandidateProfileService.gateEvidence(3);
        assertEquals(new ToBeeCandidateProfileService.BlockPos(31, 25, -10), finish.min());
        assertEquals(new ToBeeCandidateProfileService.BlockPos(37, 33, -10), finish.max());
        assertEquals(55, finish.expectedPortalBlocks());

        assertThrows(IllegalArgumentException.class,
                () -> ToBeeCandidateProfileService.safeRouteSideCandidate(99));
    }

    @Test
    void candidateProfileRetainsExplicitUnknownsInsteadOfInventingThem() {
        assertEquals(List.of(
                "runner-start-layout-generated-from-safe-archive-geometry-not-original",
                "death-spawns-generated-from-first-stage-control-geometry-not-original",
                "death-button-to-trap-bindings-partially-reconstructed-runtime-set",
                "trap-target-geometry-recovered-23-of-23-action-traps",
                "all-surviving-action-signs-have-runtime-traps",
                "start-barrier-generated-outside-gate-006-not-original",
                "original-checkpoint-score-values-not-recovered"
        ), ToBeeCandidateProfileService.knownRemainingBlockers());
    }
}
