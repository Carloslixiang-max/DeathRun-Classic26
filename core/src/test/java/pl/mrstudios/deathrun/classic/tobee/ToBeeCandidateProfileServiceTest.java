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
        assertEquals(726, ToBeeCandidateProfileService.startSearchColumnCount());
        assertEquals(14, ToBeeCandidateProfileService.startBarrierPositions().size());
        assertEquals(3, ToBeeCandidateProfileService.fireArrowTrapCount());
        assertEquals(10, ToBeeCandidateProfileService.implementedTrapCount());
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
        assertEquals(List.of("pair-006-red-A", "pair-012-red-B", "pair-021-red-D"),
                reds.stream().map(ToBeeCandidateProfileService.MaterialTrapEvidence::id).toList());
        assertEquals(List.of(7, 15, 21), reds.stream().map(item -> item.targets().size()).toList());
        assertTrue(reds.stream().allMatch(item -> item.expectedMaterialName().equals("RED_TERRACOTTA")));
        assertEquals(43, reds.stream().mapToInt(item -> item.targets().size()).sum());

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
        assertEquals(1, wood.size());
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
                "original-waiting-lobby-not-recovered",
                "runner-start-layout-generated-from-safe-archive-geometry-not-original",
                "death-spawns-generated-from-first-stage-control-geometry-not-original",
                "death-button-to-trap-bindings-partially-reconstructed-runtime-subset",
                "trap-target-geometry-recovered-10-runtime-traps",
                "remaining-trap-targets-and-original-reset-parameters-not-recovered",
                "start-barrier-generated-outside-gate-006-not-original",
                "original-checkpoint-score-values-not-recovered"
        ), ToBeeCandidateProfileService.knownRemainingBlockers());
    }
}
