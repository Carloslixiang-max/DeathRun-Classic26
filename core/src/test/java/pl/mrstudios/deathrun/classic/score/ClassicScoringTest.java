package pl.mrstudios.deathrun.classic.score;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClassicScoringTest {

    @Test
    void runnerStartsWithLockedClassicLives() {
        assertEquals(2, ClassicScoring.RUNNER_START_LIVES);
    }

    @Test
    void normalCheckpointAddsTwoLivesButFinishDoesNot() {
        assertEquals(4, ClassicScoring.afterCheckpointLives(2, false));
        assertEquals(8, ClassicScoring.afterCheckpointLives(8, true));
    }

    @Test
    void deathCostsExactlyOneLifeWithoutGoingNegative() {
        assertEquals(1, ClassicScoring.afterDeathLives(2));
        assertEquals(0, ClassicScoring.afterDeathLives(1));
        assertEquals(0, ClassicScoring.afterDeathLives(0));
    }

    @Test
    void finishAddsRemainingLivesToRoundPoints() {
        assertEquals(133, ClassicScoring.afterFinishPoints(118, 15));
    }

    @Test
    void firstFinishClampsOnlyWhenMoreThanSixtySecondsRemain() {
        assertEquals(60, ClassicScoring.remainingAfterFinish(210, 1));
        assertEquals(48, ClassicScoring.remainingAfterFinish(48, 1));
        assertEquals(210, ClassicScoring.remainingAfterFinish(210, 2));
    }
}
