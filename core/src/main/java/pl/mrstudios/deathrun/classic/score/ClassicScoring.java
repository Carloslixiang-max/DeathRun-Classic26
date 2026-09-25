package pl.mrstudios.deathrun.classic.score;

public final class ClassicScoring {

    public static final int RUNNER_START_LIVES = 2;
    public static final int CHECKPOINT_LIVES = 2;
    public static final int FIRST_FINISH_REMAINING_SECONDS = 60;

    private ClassicScoring() {}

    public static int afterCheckpointLives(int currentLives, boolean finishCheckpoint) {
        int lives = Math.max(0, currentLives);
        return finishCheckpoint ? lives : lives + CHECKPOINT_LIVES;
    }

    public static int afterCheckpointPoints(int currentPoints, int checkpointPoints) {
        return Math.max(0, currentPoints) + Math.max(0, checkpointPoints);
    }

    public static int afterFinishPoints(int currentPoints, int remainingLives) {
        return Math.max(0, currentPoints) + Math.max(0, remainingLives);
    }

    public static int afterDeathLives(int currentLives) {
        return Math.max(0, currentLives - 1);
    }

    public static int remainingAfterFinish(int remainingSeconds, int finishPosition) {
        int remaining = Math.max(0, remainingSeconds);
        if (finishPosition != 1)
            return remaining;

        return Math.min(remaining, FIRST_FINISH_REMAINING_SECONDS);
    }
}
