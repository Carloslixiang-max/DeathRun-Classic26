package pl.mrstudios.deathrun.classic.tobee;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToBeeCandidateProfileServiceTest {

    @Test
    void archiveBackedRouteManifestKeepsCrossCorroboratedOrder() {
        assertEquals(List.of(6, 7, 2, 1, 4, 5, 3), ToBeeCandidateProfileService.routeGateIds());
        assertEquals(List.of(7, 2, 1, 4, 5, 3), ToBeeCandidateProfileService.checkpointGateIds());
        assertEquals(3, ToBeeCandidateProfileService.finishGateId());
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
                "runner-start-layout-only-1-of-20-safe-candidates-recovered",
                "death-spawns-not-recovered",
                "death-button-to-trap-bindings-not-recovered",
                "trap-target-cuboids-and-reset-parameters-not-recovered",
                "start-barrier-not-recovered",
                "original-checkpoint-score-values-not-recovered"
        ), ToBeeCandidateProfileService.knownRemainingBlockers());
    }
}
