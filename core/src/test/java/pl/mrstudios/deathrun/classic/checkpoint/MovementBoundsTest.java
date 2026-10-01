package pl.mrstudios.deathrun.classic.checkpoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MovementBoundsTest {
    @Test void preservesSweptHitboxAndSeparateTrapCheckpointTopMargins() {
        var bounds = new MovementBounds(0,0,0,0,0,0);
        assertTrue(bounds.touches(-10,.5,.5,10,.5,.5));
        assertFalse(bounds.touches(-10,10,.5,10,10,.5));
        assertTrue(bounds.touches(.5,1.005,.5,.5,1.005,.5));
        assertFalse(bounds.touches(.5,1.005,.5,.5,1.005,.5,1.0));
    }
}
