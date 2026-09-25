package pl.mrstudios.deathrun.classic.checkpoint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SegmentAabbTest {

    @Test
    void highSpeedSegmentCrossesCheckpoint() {
        assertTrue(SegmentAabb.intersects(
                0, 64, 0,
                12, 64, 0,
                5, 63, -1,
                7, 66, 1
        ));
    }

    @Test
    void parallelSegmentOutsideCheckpointMisses() {
        assertFalse(SegmentAabb.intersects(
                0, 70, 0,
                12, 70, 0,
                5, 63, -1,
                7, 66, 1
        ));
    }

    @Test
    void segmentStartingInsideCountsAsHit() {
        assertTrue(SegmentAabb.intersects(
                6, 64, 0,
                20, 64, 0,
                5, 63, -1,
                7, 66, 1
        ));
    }

    @Test
    void reverseMovementStillCrosses() {
        assertTrue(SegmentAabb.intersects(
                12, 64, 0,
                0, 64, 0,
                5, 63, -1,
                7, 66, 1
        ));
    }

    @Test
    void touchingBoundaryCountsAsHit() {
        assertTrue(SegmentAabb.intersects(
                0, 66, 1,
                12, 66, 1,
                5, 63, -1,
                7, 66, 1
        ));
    }
}
