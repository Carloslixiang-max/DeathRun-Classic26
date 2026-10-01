package pl.mrstudios.deathrun.classic.tobee;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class ToBeeCheckpointRepairPlanTest {
    @Test void rejectsAddedMissingDuplicateReorderedAndWrongFinishCheckpoints() {
        assertTrue(ToBeeCheckpointRepairPlan.valid(List.of(1,2,3,4,5,6), 6));
        for (List<Integer> ids : List.of(List.of(1,2,3,4,5), List.of(1,2,3,4,5,6,7),
                List.of(1,2,3,4,5,5), List.of(2,1,3,4,5,6)))
            assertFalse(ToBeeCheckpointRepairPlan.valid(ids, 6));
        assertFalse(ToBeeCheckpointRepairPlan.valid(List.of(1,2,3,4,5,6), 5));
        assertFalse(ToBeeCheckpointRepairPlan.valid(List.of(1,2,3,4,5,6), null));
        assertThrows(IllegalArgumentException.class, () -> ToBeeCheckpointRepairPlan.gateForCheckpoint(7));
    }
    @Test void gateMappingUsesIdentity() {
        assertEquals(7, ToBeeCheckpointRepairPlan.gateForCheckpoint(1));
        assertEquals(3, ToBeeCheckpointRepairPlan.gateForCheckpoint(6));
    }
}
