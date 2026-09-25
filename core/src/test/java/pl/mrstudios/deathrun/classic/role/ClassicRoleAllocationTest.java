package pl.mrstudios.deathrun.classic.role;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClassicRoleAllocationTest {

    @Test
    void fullClassicRoomIsTwentyRunnersTwoDeaths() {
        assertEquals(2, ClassicRoleAllocation.deathCount(22, 2));
        assertEquals(20, 22 - ClassicRoleAllocation.deathCount(22, 2));
    }

    @Test
    void engineeringSoloForceStartKeepsOneRunner() {
        assertEquals(0, ClassicRoleAllocation.deathCount(1, 2));
    }

    @Test
    void smallRoomsAlwaysKeepAtLeastOneRunner() {
        assertEquals(1, ClassicRoleAllocation.deathCount(2, 2));
        assertEquals(2, ClassicRoleAllocation.deathCount(3, 2));
    }

    @Test
    void invalidConfigurationCannotCreateNegativeRoles() {
        assertEquals(0, ClassicRoleAllocation.deathCount(0, 2));
        assertEquals(0, ClassicRoleAllocation.deathCount(22, -4));
    }
}
