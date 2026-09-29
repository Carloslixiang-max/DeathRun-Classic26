package pl.mrstudios.deathrun.classic.playtest;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaytestAcceptanceAnalyzerTest {

    @Test
    void completeTracePassesStrictClassicAcceptance() {
        List<String> lines = new ArrayList<>();
        lines.add("t | JOIN | player=Runner role=NONE snapshotPending=true");
        lines.add("t | JOIN | player=Death role=NONE snapshotPending=true");
        lines.add("t | ROLE | player=Runner role=RUNNER");
        lines.add("t | ROLE | player=Death role=DEATH");
        lines.add("t | STATE | state=PLAYING users=2");
        lines.add("t | STRAFE | player=Runner direction=LEFT cooldown=60");
        lines.add("t | STRAFE | player=Runner direction=BACK cooldown=60");
        lines.add("t | STRAFE | player=Runner direction=RIGHT cooldown=60");
        lines.add("t | DEATH_NAV | player=Death action=PREVIOUS selected=1");
        lines.add("t | DEATH_NAV | player=Death action=NEXT selected=2");
        lines.add("t | DEATH_NAV | player=Death action=JUMP success=true selected=2");
        lines.add("t | DEATH_NAV | player=Death action=ACTIVATE result=ACTIVATED selected=2");
        lines.add("t | TRAP_INPUT | player=Death method=BUTTON index=1 result=ACTIVATED");
        for (int cp = 1; cp <= 4; cp++)
            lines.add("t | CHECKPOINT | player=Runner cp=" + cp + " lives=2 points=3 eliminated=false");
        for (int trap = 1; trap <= 17; trap++)
            lines.add("t | TRAP_ACTIVE | index=" + trap + " type=T" + trap + " death=Death");
        lines.add("t | DEATH | player=Runner deaths=1 lives=1 eliminated=false trap=-");
        lines.add("t | DEATH | player=Runner deaths=2 lives=0 eliminated=true trap=-");
        lines.add("t | FINISH | player=Runner position=1 time=42 lives=8 points=41 remaining=60");
        lines.add("t | STATE | state=ENDING users=2");
        lines.add("t | STATE | state=WAITING users=0");
        lines.add("t | RESTORE | player=Runner online=true snapshotPending=false");
        lines.add("t | DISCONNECT | player=Runner state=PLAYING");
        lines.add("t | RECONNECT_RECOVERY | player=Runner pendingBefore=false pendingAfter=false");

        PlaytestAcceptanceAnalyzer.Result result = PlaytestAcceptanceAnalyzer.analyze(
                lines, List.of(1, 2, 3, 4), 17
        );

        assertTrue(result.complete());
        assertTrue(result.checks().stream().allMatch(PlaytestAcceptanceAnalyzer.Check::passed));
    }

    @Test
    void emptyTraceIsIncomplete() {
        PlaytestAcceptanceAnalyzer.Result result = PlaytestAcceptanceAnalyzer.analyze(
                List.of(), List.of(1, 2, 3, 4), 17
        );

        assertFalse(result.complete());
        assertTrue(result.checks().stream().anyMatch(check -> !check.passed()));
    }

    private static boolean passes(String key, String... events) {
        return PlaytestAcceptanceAnalyzer.analyze(List.of(events), List.of(), 0)
                .checks().stream().filter(check -> check.key().equals(key))
                .findFirst().orElseThrow().passed();
    }

    @Test
    void resetRequiresEndingImmediatelyBeforeWaitingAmongStateEvents() {
        assertFalse(passes("round-reset", "t | STATE | state=WAITING", "t | STATE | state=ENDING"));
        assertFalse(passes("round-reset", "t | STATE | state=ENDING",
                "t | STATE | state=PLAYING", "t | STATE | state=WAITING"));
        assertTrue(passes("round-reset", "t | STATE | state=ENDING",
                "t | RESTORE | player=Runner snapshotPending=false", "t | STATE | state=WAITING"));
    }

    @Test
    void disconnectRequiresPlayingAndAnIdentifiedPlayer() {
        assertFalse(passes("disconnect", "t | DISCONNECT | player=Runner state=WAITING"));
        assertFalse(passes("disconnect", "t | DISCONNECT | state=PLAYING"));
        assertFalse(passes("disconnect", "t | DISCONNECT | player=Runner oldstate=PLAYING"));
        assertTrue(passes("disconnect", "t | DISCONNECT | player=Runner state=PLAYING"));
    }

    @Test
    void recoveryRequiresEarlierActiveDisconnectForSamePlayer() {
        String disconnect = "t | DISCONNECT | player=Runner state=PLAYING";
        String recovered = "t | RECONNECT_RECOVERY | player=Runner pendingAfter=false";
        assertFalse(passes("reconnect-recovery", recovered, disconnect));
        assertFalse(passes("reconnect-recovery", disconnect,
                "t | RECONNECT_RECOVERY | player=Other pendingAfter=false"));
        assertFalse(passes("reconnect-recovery",
                "t | DISCONNECT | player=Runner state=WAITING", recovered));
        assertFalse(passes("reconnect-recovery", disconnect,
                "t | RECONNECT_RECOVERY | player=Runner pendingAfter=true"));
        assertTrue(passes("reconnect-recovery", disconnect, recovered));
    }

    @Test
    void recoveryConsumesDisconnectAndNewDisconnectReplacesItsState() {
        String disconnect = "t | DISCONNECT | player=Runner state=PLAYING";
        String recovered = "t | RECONNECT_RECOVERY | player=Runner pendingAfter=false";
        assertFalse(passes("reconnect-recovery", disconnect,
                "t | RECONNECT_RECOVERY | player=Runner pendingAfter=true", recovered));
        assertFalse(passes("reconnect-recovery", disconnect,
                "t | DISCONNECT | player=Runner state=WAITING", recovered));
        assertTrue(passes("reconnect-recovery", disconnect,
                "t | RECONNECT_RECOVERY | player=Runner pendingAfter=true", disconnect, recovered));
    }
}
