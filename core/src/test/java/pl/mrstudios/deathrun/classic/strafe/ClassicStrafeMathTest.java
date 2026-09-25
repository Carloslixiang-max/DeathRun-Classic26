package pl.mrstudios.deathrun.classic.strafe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static pl.mrstudios.deathrun.classic.strafe.ClassicStrafeService.Direction.*;

class ClassicStrafeMathTest {

    private static final double EPSILON = 1.0e-9;

    @Test
    void facingSouthUsesCorrectLeftBackRightDirections() {
        assertHorizontal(1.0, 0.0, ClassicStrafeMath.horizontal(0.0f, LEFT));
        assertHorizontal(0.0, -1.0, ClassicStrafeMath.horizontal(0.0f, BACK));
        assertHorizontal(-1.0, 0.0, ClassicStrafeMath.horizontal(0.0f, RIGHT));
    }

    @Test
    void facingWestUsesCorrectLeftBackRightDirections() {
        assertHorizontal(0.0, 1.0, ClassicStrafeMath.horizontal(90.0f, LEFT));
        assertHorizontal(1.0, 0.0, ClassicStrafeMath.horizontal(90.0f, BACK));
        assertHorizontal(0.0, -1.0, ClassicStrafeMath.horizontal(90.0f, RIGHT));
    }

    private void assertHorizontal(double x, double z, ClassicStrafeMath.Horizontal actual) {
        assertEquals(x, actual.x(), EPSILON);
        assertEquals(z, actual.z(), EPSILON);
    }
}
