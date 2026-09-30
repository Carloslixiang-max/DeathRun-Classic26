package pl.mrstudios.deathrun.classic.tobee;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class ToBeeSpawnGeometryTest {
    @Test void oldGrassPitAndSideDecorationAreRejected() {
        assertFalse(ToBeeSpawnGeometry.runnerRegion(85, 25, 77));
        assertFalse(ToBeeSpawnGeometry.runnerRegion(89, 26, 85));
        assertFalse(ToBeeSpawnGeometry.runnerRegion(83, 25, 81));
        assertFalse(ToBeeSpawnGeometry.DEATH_STARTS.contains(new ToBeeSpawnGeometry.Pos(76, 24, 51)));
        assertTrue(ToBeeSpawnGeometry.runnerRegion(85, 25, 82));
        assertEquals(90f, ToBeeSpawnGeometry.RUNNER_YAW);
    }
    @Test void eachControlHasOneReviewedLandingAndTwoDistinctDeathStarts() {
        assertEquals(23, ToBeeSpawnGeometry.CONTROLS.size());
        assertEquals(23, new HashSet<>(ToBeeSpawnGeometry.CONTROLS.stream().map(ToBeeSpawnGeometry.ControlLanding::button).toList()).size());
        assertEquals(2, new HashSet<>(ToBeeSpawnGeometry.DEATH_STARTS).size());
        var fireSnake = ToBeeSpawnGeometry.CONTROLS.stream().filter(c -> c.id().equals("pair-020-fire-snake-B")).findFirst().orElseThrow();
        assertEquals(new ToBeeSpawnGeometry.Pos(75, 25, 56), fireSnake.landing());
    }
}
