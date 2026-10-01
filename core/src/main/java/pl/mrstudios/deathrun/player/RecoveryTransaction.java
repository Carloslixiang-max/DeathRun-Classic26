package pl.mrstudios.deathrun.player;

/** Durable recovery phases. A caller must keep the player isolated until finish succeeds. */
public final class RecoveryTransaction {
    private RecoveryTransaction() {}

    public enum Phase { PENDING, RESTORING, APPLIED }

    @FunctionalInterface
    public interface Step { void run() throws Exception; }

    public static void restore(Phase phase, Step validateAndTeleport, Step markRestoring,
                               Step applyAndSavePlayer, Step markApplied, Step finish) throws Exception {
        if (phase != Phase.APPLIED) {
            validateAndTeleport.run();
            markRestoring.run();
            applyAndSavePlayer.run();
            markApplied.run();
        }
        // Once APPLIED is durable, a failed journal removal must never replay inventory.
        finish.run();
    }
}
