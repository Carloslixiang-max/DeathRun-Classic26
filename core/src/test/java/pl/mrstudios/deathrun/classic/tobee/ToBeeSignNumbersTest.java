package pl.mrstudios.deathrun.classic.tobee;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ToBeeSignNumbersTest {
    @Test void everyRuntimeControlMapsToOneExistingActionSign() {
        var mapping=ToBeeSignTranslations.actionSignsByButton();
        assertEquals(23,mapping.size()); assertEquals(23,new java.util.HashSet<>(mapping.values()).size());
        var signs=ToBeeSignTranslations.ENTRIES.stream()
                .map(e->new ToBeeCandidateProfileService.BlockPos(e.x(),e.y(),e.z())).toList();
        assertTrue(signs.containsAll(mapping.values()));
        assertEquals(ToBeeCandidateProfileService.deathControlActionAnchor(),mapping.get(ToBeeCandidateProfileService.deathControlButtonCandidate()));
    }
}
