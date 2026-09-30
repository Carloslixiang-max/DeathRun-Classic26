package pl.mrstudios.deathrun.classic.playtest;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaytestRulesAnalyzerTest {

    private static PlaytestRulesAnalyzer.Result analyze(List<String> lines) {
        return PlaytestRulesAnalyzer.analyze(lines, List.of(1, 2), List.of(3, 7), 2);
    }

    private static boolean passes(List<String> lines, String key) {
        return analyze(lines).checks().stream().filter(check -> check.key().equals(key))
                .findFirst().orElseThrow().passed();
    }

    private static List<String> completeTrace() {
        return new ArrayList<>(List.of(
                "t | JOIN | player=Runner snapshotPending=true",
                "t | JOIN | player=Death snapshotPending=true",
                "t | ROLE | player=Runner role=RUNNER lives=2 points=0",
                "t | DEATH | player=Runner livesBefore=2 lives=1 eliminated=false",
                "t | CHECKPOINT | player=Runner cp=1 finish=false livesBefore=1 lives=3 pointsBefore=0 awardedPoints=3 points=3",
                "t | CHECKPOINT | player=Runner cp=2 finish=true livesBefore=3 lives=3 pointsBefore=3 awardedPoints=7 points=10",
                "t | FINISH | player=Runner position=1 lives=3 pointsBefore=10 points=13 remainingBefore=200 remaining=60",
                "t | STRAFE | player=Runner direction=LEFT cooldown=60 horizontal=1.78 vertical=0.30 beforeLeft=0 beforeBack=0 beforeRight=0 afterLeft=59999 afterBack=0 afterRight=0 sampleElapsedMillis=10",
                "t | STRAFE | player=Runner direction=BACK cooldown=60 horizontal=1.78 vertical=0.30 beforeLeft=59000 beforeBack=0 beforeRight=0 afterLeft=58999 afterBack=59999 afterRight=0 sampleElapsedMillis=10",
                "t | STRAFE | player=Runner direction=RIGHT cooldown=60 horizontal=1.78 vertical=0.30 beforeLeft=58000 beforeBack=59000 beforeRight=0 afterLeft=57999 afterBack=58999 afterRight=59999 sampleElapsedMillis=10",
                "t | STRAFE_BLOCKED | player=Runner direction=LEFT remainingMillis=56000",
                "t | STRAFE_BLOCKED | player=Runner direction=BACK remainingMillis=57000",
                "t | STRAFE_BLOCKED | player=Runner direction=RIGHT remainingMillis=58000",
                "t | LEAVE | player=Runner",
                "t | RESTORE | player=Runner online=true snapshotPending=false",
                "t | DISCONNECT | player=Death state=PLAYING",
                "t | LEAVE | player=Death",
                "t | RESTORE | player=Death online=false snapshotPending=unknown",
                "t | RECONNECT_RECOVERY | player=Death recovered=true pendingAfter=false"
        ));
    }

    @Test
    void fullNumericEvidencePasses() {
        assertTrue(analyze(completeTrace()).complete());
    }

    @Test
    void emptyAndLegacyCoverageDoNotProveNumericRules() {
        assertFalse(analyze(List.of()).complete());
        assertFalse(analyze(List.of("t | ROLE | player=Runner role=RUNNER",
                "t | CHECKPOINT | player=Runner cp=1 lives=2 points=3")).complete());
    }

    @Test
    void laterCorrectRoundCannotHideWrongStartingLives() {
        List<String> lines = completeTrace();
        lines.add("t | ROLE | player=Other role=RUNNER lives=3 points=0");
        assertFalse(passes(lines, "runner-start-lives"));
    }

    @Test
    void everyConfiguredCheckpointNeedsItsOwnNumericEvidence() {
        List<String> lines = completeTrace();
        lines.removeIf(line -> line.contains("cp=1 "));
        assertFalse(passes(lines, "checkpoint-lives"));
        assertFalse(passes(lines, "checkpoint-points"));
    }

    @Test
    void finishGateCannotGrantNormalCheckpointLives() {
        List<String> lines = completeTrace();
        lines.add("t | CHECKPOINT | player=Runner cp=2 finish=true livesBefore=3 lives=5 pointsBefore=3 awardedPoints=7 points=10");
        assertFalse(passes(lines, "finish-gate-lives"));
    }

    @Test
    void checkpointAwardsMustMatchMapInsteadOfSelfReportedValue() {
        List<String> lines = completeTrace();
        lines.add("t | CHECKPOINT | player=Runner cp=1 finish=false livesBefore=2 lives=4 pointsBefore=0 awardedPoints=99 points=99");
        assertFalse(passes(lines, "checkpoint-points"));
    }

    @Test
    void invalidLivesAndEliminationAreRejected() {
        for (String bad : List.of(
                "livesBefore=2 lives=0 eliminated=true",
                "livesBefore=1 lives=0 eliminated=false",
                "livesBefore=2 lives=1 eliminated=true",
                "livesBefore=-1 lives=0 eliminated=true",
                "livesBefore=oops lives=1 eliminated=false")) {
            List<String> lines = completeTrace();
            lines.add("t | DEATH | player=Runner " + bad);
            assertFalse(passes(lines, "death-life-cost"), bad);
        }
        List<String> lines = completeTrace();
        lines.add("t | DEATH | player=Other livesBefore=1 lives=0 eliminated=true");
        assertTrue(passes(lines, "death-life-cost"));
    }

    @Test
    void finishPointsMustIncludeRemainingLivesExactlyOnce() {
        List<String> lines = completeTrace();
        lines.add("t | FINISH | player=Runner position=1 lives=3 pointsBefore=10 points=16 remainingBefore=200 remaining=60");
        assertFalse(passes(lines, "finish-life-points"));
    }

    @Test
    void timerMustClampWithoutExtendingLateFinishOrChangingSecondPlace() {
        List<String> lines = completeTrace();
        lines.add("t | FINISH | player=Runner position=1 lives=3 pointsBefore=10 points=13 remainingBefore=40 remaining=40");
        lines.add("t | FINISH | player=Other position=2 lives=3 pointsBefore=10 points=13 remainingBefore=39 remaining=39");
        assertTrue(passes(lines, "finish-timer-rule"));
        for (String bad : List.of("position=1 remainingBefore=40 remaining=60",
                "position=1 remainingBefore=200 remaining=59",
                "position=2 remainingBefore=200 remaining=60")) {
            List<String> broken = completeTrace();
            broken.add("t | FINISH | player=Runner lives=3 pointsBefore=10 points=13 " + bad);
            assertFalse(passes(broken, "finish-timer-rule"), bad);
        }
    }

    @Test
    void finishingAfterSixtySecondsDoesNotExerciseHighTimeClamp() {
        assertFalse(passes(List.of(
                "t | FINISH | player=Runner position=1 lives=3 pointsBefore=10 points=13 remainingBefore=40 remaining=40"
        ), "first-finish-clamp-exercised"));
    }

    @Test
    void sharedOrResetOtherCooldownCannotPassIndependentTimers() {
        List<String> lines = completeTrace();
        lines.add("t | STRAFE | player=Runner direction=LEFT cooldown=60 horizontal=1.78 vertical=0.30 beforeLeft=0 beforeBack=20000 beforeRight=0 afterLeft=59999 afterBack=60000 afterRight=60000 sampleElapsedMillis=10");
        assertFalse(passes(lines, "strafe-independent-cooldowns"));
    }

    @Test
    void clearingOtherTimerOrShorteningOwnTimerIsRejected() {
        for (String after : List.of("afterLeft=59999 afterBack=0 afterRight=0",
                "afterLeft=1000 afterBack=19999 afterRight=0")) {
            List<String> lines = completeTrace();
            lines.add("t | STRAFE | player=Runner direction=LEFT cooldown=60 horizontal=1.78 vertical=0.30"
                    + " beforeLeft=0 beforeBack=20000 beforeRight=0 " + after + " sampleElapsedMillis=10");
            assertFalse(passes(lines, "strafe-independent-cooldowns"));
        }
    }

    @Test
    void cooldownsExercisedInSeparateRoundsDoNotProveIndependence() {
        List<String> lines = completeTrace();
        lines.removeIf(line -> line.contains(" | STRAFE | "));
        for (String dir : List.of("LEFT", "BACK", "RIGHT")) {
            String left = dir.equals("LEFT") ? "59999" : "0";
            String back = dir.equals("BACK") ? "59999" : "0";
            String right = dir.equals("RIGHT") ? "59999" : "0";
            lines.add("t | STRAFE | player=Runner direction=" + dir
                    + " cooldown=60 horizontal=1.78 vertical=0.30 beforeLeft=0 beforeBack=0 beforeRight=0"
                    + " afterLeft=" + left + " afterBack=" + back + " afterRight=" + right + " sampleElapsedMillis=10");
        }
        assertTrue(passes(lines, "strafe-values"));
        assertFalse(passes(lines, "strafe-independent-cooldowns"));
    }

    @Test
    void failedRetryAndWrongVelocityAreNotSuccessEvidence() {
        List<String> lines = completeTrace();
        lines.add("t | STRAFE_BLOCKED | player=Runner direction=LEFT remainingMillis=0");
        assertFalse(passes(lines, "strafe-blocked-left"));
        lines = completeTrace();
        lines.add("t | STRAFE | player=Runner direction=LEFT cooldown=60 horizontal=NaN vertical=0.30");
        assertFalse(passes(lines, "strafe-values"));
    }

    @Test
    void onePlayerRestoreDoesNotCoverOtherPlayerOrANewJoin() {
        List<String> lines = completeTrace();
        lines.removeIf(line -> line.contains("RECONNECT_RECOVERY"));
        assertFalse(passes(lines, "all-player-restores"));
        lines = completeTrace();
        lines.add("t | JOIN | player=Runner snapshotPending=true");
        assertFalse(passes(lines, "all-player-restores"));
    }

    @Test
    void unrelatedOrPrematureRestoreDoesNotCount() {
        List<String> lines = new ArrayList<>(List.of(
                "t | RESTORE | player=Runner online=true snapshotPending=false",
                "t | JOIN | player=Runner",
                "t | JOIN | player=Death",
                "t | RESTORE | player=Death online=true snapshotPending=false",
                "t | RECONNECT_RECOVERY | player=Runner recovered=true pendingAfter=false"
        ));
        assertFalse(passes(lines, "all-player-restores"));
        lines.add("t | LEAVE | player=Runner");
        lines.add("t | LEAVE | player=Death");
        lines.add("t | RESTORE | player=Runner online=true snapshotPending=false");
        lines.add("t | RESTORE | player=Death online=true snapshotPending=false");
        assertTrue(passes(lines, "all-player-restores"));
    }

    @Test
    void missingPlayerAndFieldNameSubstringsAreRejected() {
        List<String> lines = completeTrace();
        lines.add("t | ROLE | role=RUNNER lives=2 points=0");
        assertFalse(passes(lines, "runner-start-lives"));
        lines = completeTrace();
        lines.add("t | DEATH | player=Runner oldlivesBefore=2 lives=1 eliminated=false");
        assertFalse(passes(lines, "death-life-cost"));
    }
}
