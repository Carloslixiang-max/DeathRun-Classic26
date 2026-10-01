package pl.mrstudios.deathrun.player;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryTransactionTest {
    @Test void rejectedTeleportDoesNotTouchInventoryOrJournal() {
        List<String> calls = new ArrayList<>();
        assertThrows(IOException.class, () -> RecoveryTransaction.restore(RecoveryTransaction.Phase.PENDING,
                () -> { calls.add("teleport"); throw new IOException("denied"); },
                () -> calls.add("restoring"), () -> calls.add("inventory"),
                () -> calls.add("applied"), () -> calls.add("retire")));
        assertEquals(List.of("teleport"), calls);
    }
    @Test void aFailedPhaseWriteCannotExposeSavedInventory() {
        List<String> calls = new ArrayList<>();
        assertThrows(IOException.class, () -> RecoveryTransaction.restore(RecoveryTransaction.Phase.PENDING,
                () -> calls.add("teleport"), () -> { throw new IOException("disk full"); },
                () -> calls.add("inventory"), () -> calls.add("applied"), () -> calls.add("retire")));
        assertEquals(List.of("teleport"), calls);
    }
    @Test void completedPhaseOnlyRetriesJournalRetirement() {
        int[] inventoryApplications = {0};
        assertThrows(IOException.class, () -> RecoveryTransaction.restore(RecoveryTransaction.Phase.APPLIED,
                () -> fail("must not teleport again"), () -> fail("must not reset phase"),
                () -> inventoryApplications[0]++, () -> fail("must not reset phase"),
                () -> { throw new IOException("journal delete denied"); }));
        assertEquals(0, inventoryApplications[0]);
    }
    @Test void partialRecoveryCanResumeOnlyThroughFullProtectedSequence() throws Exception {
        List<String> calls = new ArrayList<>();
        RecoveryTransaction.restore(RecoveryTransaction.Phase.RESTORING, () -> calls.add("teleport"),
                () -> calls.add("restoring"), () -> calls.add("apply-and-save"),
                () -> calls.add("applied"), () -> calls.add("retire"));
        assertEquals(List.of("teleport", "restoring", "apply-and-save", "applied", "retire"), calls);
    }
    @Test void recoveryCommandExceptionsCannotBeUsedForOtherCommands() {
        for (String command : List.of("/dr recover", "/deathrun leave", "/deathrun:dr recover", "/DR   RECOVER"))
            assertTrue(RecoveryCommands.allowed(command), command);
        for (String command : List.of("/sellall", "/dr join foo", "/dr recover extra", "/other:dr recover", "/dr recover; /sellall"))
            assertFalse(RecoveryCommands.allowed(command), command);
    }
}
