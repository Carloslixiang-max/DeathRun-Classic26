package pl.mrstudios.deathrun.classic.playtest;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PlaytestAcceptanceAnalyzer {

    private PlaytestAcceptanceAnalyzer() {}

    public static @NotNull Result analyze(
            @NotNull List<String> lines,
            @NotNull List<Integer> expectedCheckpointIds,
            int expectedTrapCount
    ) {
        Set<String> joinedPlayers = new LinkedHashSet<>();
        Set<Integer> checkpointIds = new LinkedHashSet<>();
        Set<Integer> trapIndexes = new LinkedHashSet<>();

        boolean statePlaying = false;
        boolean stateEnding = false;
        boolean stateWaiting = false;
        boolean runnerRole = false;
        boolean deathRole = false;
        boolean strafeLeft = false;
        boolean strafeBack = false;
        boolean strafeRight = false;
        boolean navPrevious = false;
        boolean navNext = false;
        boolean navJump = false;
        boolean navActivate = false;
        boolean buttonActivate = false;
        boolean nonEliminatingDeath = false;
        boolean eliminatedDeath = false;
        boolean firstFinishClamp = false;
        boolean restoreComplete = false;
        boolean disconnected = false;
        boolean reconnectRecovered = false;

        for (String line : lines) {
            if (line.contains(" | JOIN | ")) {
                String player = field(line, "player");
                if (player != null && !player.isBlank())
                    joinedPlayers.add(player);
            }

            if (line.contains(" | STATE | ")) {
                String state = field(line, "state");
                statePlaying |= "PLAYING".equals(state);
                stateEnding |= "ENDING".equals(state);
                stateWaiting |= "WAITING".equals(state);
            }

            if (line.contains(" | ROLE | ")) {
                String role = field(line, "role");
                runnerRole |= "RUNNER".equals(role);
                deathRole |= "DEATH".equals(role);
            }

            if (line.contains(" | STRAFE | ")) {
                String direction = field(line, "direction");
                strafeLeft |= "LEFT".equals(direction);
                strafeBack |= "BACK".equals(direction);
                strafeRight |= "RIGHT".equals(direction);
            }

            if (line.contains(" | DEATH_NAV | ")) {
                String action = field(line, "action");
                navPrevious |= "PREVIOUS".equals(action);
                navNext |= "NEXT".equals(action);
                navJump |= "JUMP".equals(action) && "true".equals(field(line, "success"));
                navActivate |= "ACTIVATE".equals(action) && "ACTIVATED".equals(field(line, "result"));
            }

            if (line.contains(" | TRAP_INPUT | ")
                    && "BUTTON".equals(field(line, "method"))
                    && "ACTIVATED".equals(field(line, "result")))
                buttonActivate = true;

            if (line.contains(" | CHECKPOINT | ")) {
                Integer cp = intField(line, "cp");
                if (cp != null)
                    checkpointIds.add(cp);
            }

            if (line.contains(" | TRAP_ACTIVE | ")) {
                Integer index = intField(line, "index");
                if (index != null)
                    trapIndexes.add(index);
            }

            if (line.contains(" | DEATH | ")) {
                String eliminated = field(line, "eliminated");
                nonEliminatingDeath |= "false".equals(eliminated);
                eliminatedDeath |= "true".equals(eliminated);
            }

            if (line.contains(" | FINISH | ")
                    && Integer.valueOf(1).equals(intField(line, "position"))) {
                Integer remaining = intField(line, "remaining");
                firstFinishClamp |= remaining != null && remaining >= 0 && remaining <= 60;
            }

            if (line.contains(" | RESTORE | ")
                    && "false".equals(field(line, "snapshotPending")))
                restoreComplete = true;

            if (line.contains(" | DISCONNECT | "))
                disconnected = true;

            if (line.contains(" | RECONNECT_RECOVERY | ")
                    && "false".equals(field(line, "pendingAfter")))
                reconnectRecovered = true;
        }

        Set<Integer> expectedCheckpoints = new LinkedHashSet<>(expectedCheckpointIds);
        Set<Integer> expectedTraps = new LinkedHashSet<>();
        for (int i = 1; i <= Math.max(0, expectedTrapCount); i++)
            expectedTraps.add(i);

        List<Check> checks = new ArrayList<>();
        add(checks, "two-participants", joinedPlayers.size() >= 2, "joined=" + joinedPlayers);
        add(checks, "playing-state", statePlaying, "PLAYING observed");
        add(checks, "runner-role", runnerRole, "RUNNER role assigned");
        add(checks, "death-role", deathRole, "DEATH role assigned");
        add(checks, "strafe-left", strafeLeft, "LEFT successful");
        add(checks, "strafe-back", strafeBack, "BACK successful");
        add(checks, "strafe-right", strafeRight, "RIGHT successful");
        add(checks, "death-nav-previous", navPrevious, "PREVIOUS used");
        add(checks, "death-nav-next", navNext, "NEXT used");
        add(checks, "death-trap-jumper", navJump, "JUMP success=true");
        add(checks, "death-hotbar-activate", navActivate, "ACTIVATE result=ACTIVATED");
        add(checks, "death-button-activate", buttonActivate, "physical button result=ACTIVATED");
        add(checks, "all-checkpoints", checkpointIds.containsAll(expectedCheckpoints),
                "seen=" + checkpointIds + " expected=" + expectedCheckpoints);
        add(checks, "all-traps", trapIndexes.containsAll(expectedTraps),
                "seen=" + trapIndexes.size() + "/" + expectedTraps.size());
        add(checks, "death-respawn", nonEliminatingDeath, "death with eliminated=false");
        add(checks, "zero-lives-elimination", eliminatedDeath, "death with eliminated=true");
        add(checks, "first-finish-60s-clamp", firstFinishClamp, "position=1 and remaining<=60");
        add(checks, "round-ending", stateEnding, "ENDING observed");
        add(checks, "round-reset", stateWaiting, "WAITING observed after trace start");
        add(checks, "state-restore", restoreComplete, "snapshotPending=false");
        add(checks, "disconnect", disconnected, "active player disconnect observed");
        add(checks, "reconnect-recovery", reconnectRecovered, "pendingAfter=false");

        boolean complete = checks.stream().allMatch(Check::passed);
        return new Result(complete, List.copyOf(checks), Set.copyOf(checkpointIds), Set.copyOf(trapIndexes));
    }

    private static void add(@NotNull List<Check> checks, @NotNull String key, boolean passed, @NotNull String detail) {
        checks.add(new Check(key, passed, detail));
    }

    private static Integer intField(@NotNull String line, @NotNull String key) {
        String raw = field(line, key);
        if (raw == null)
            return null;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String field(@NotNull String line, @NotNull String key) {
        String needle = key + "=";
        int start = line.indexOf(needle);
        if (start < 0)
            return null;
        start += needle.length();
        int end = line.indexOf(' ', start);
        return end < 0 ? line.substring(start) : line.substring(start, end);
    }

    public record Check(@NotNull String key, boolean passed, @NotNull String detail) {}

    public record Result(
            boolean complete,
            @NotNull List<Check> checks,
            @NotNull Set<Integer> checkpointIds,
            @NotNull Set<Integer> trapIndexes
    ) {}
}
