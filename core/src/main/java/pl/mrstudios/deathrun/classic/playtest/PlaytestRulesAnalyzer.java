package pl.mrstudios.deathrun.classic.playtest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Numeric evidence complements the existing event-coverage acceptance report. */
public final class PlaytestRulesAnalyzer {

    private PlaytestRulesAnalyzer() {}

    public static Result analyze(List<String> lines, List<Integer> checkpointIds,
                                 List<Integer> checkpointPoints, Integer finishId) {
        Map<Integer, Integer> awards = new HashMap<>();
        for (int i = 0; i < checkpointIds.size(); i++) {
            Integer configured = i < checkpointPoints.size() ? checkpointPoints.get(i) : null;
            awards.put(checkpointIds.get(i), configured == null ? 0 : Math.max(0, configured));
        }
        Set<Integer> normalIds = new LinkedHashSet<>(checkpointIds);
        normalIds.remove(finishId);
        Set<Integer> normalSeen = new LinkedHashSet<>();
        Set<Integer> pointsSeen = new LinkedHashSet<>();
        Set<String> strafeSeen = new LinkedHashSet<>();
        Set<String> independentSeen = new LinkedHashSet<>();
        Set<String> blockedSeen = new LinkedHashSet<>();
        Set<String> joined = new LinkedHashSet<>();
        Set<String> unrestored = new LinkedHashSet<>();
        Set<String> left = new LinkedHashSet<>();
        Set<String> disconnected = new LinkedHashSet<>();
        List<PlaytestAcceptanceAnalyzer.Check> checks = new ArrayList<>();
        Evidence starts = new Evidence();
        Evidence cpLives = new Evidence();
        Evidence cpPoints = new Evidence();
        Evidence finishLives = new Evidence();
        Evidence deaths = new Evidence();
        Evidence finishPoints = new Evidence();
        Evidence timer = new Evidence();
        Evidence strafes = new Evidence();
        Evidence independent = new Evidence();
        Evidence blocked = new Evidence();
        boolean highFirstFinish = false;

        for (String line : lines) {
            String[] parts = line.split(" \\| ", 3);
            if (parts.length != 3)
                continue;
            String event = parts[1];
            Map<String, String> fields = new HashMap<>();
            for (String token : parts[2].split(" +")) {
                int equals = token.indexOf('=');
                if (equals > 0)
                    fields.put(token.substring(0, equals), token.substring(equals + 1));
            }
            String player = fields.get("player");
            boolean identified = player != null && !player.isBlank();
            Long before = number(fields, "livesBefore");
            Long lives = number(fields, "lives");
            Long pointsBefore = number(fields, "pointsBefore");
            Long points = number(fields, "points");
            switch (event) {
                case "ROLE" -> {
                    if ("RUNNER".equals(fields.get("role")))
                        starts.observe(identified && eq(lives, 2) && eq(points, 0));
                }
                case "CHECKPOINT" -> {
                    Long rawId = number(fields, "cp");
                    Integer id = rawId != null && rawId >= Integer.MIN_VALUE && rawId <= Integer.MAX_VALUE
                            ? rawId.intValue() : null;
                    boolean known = identified && awards.containsKey(id);
                    boolean finish = known && Objects.equals(id, finishId);
                    boolean lifeNumbers = before != null && before > 0 && lives != null;
                    boolean finishFlag = Boolean.toString(finish).equals(fields.get("finish"));
                    if (finish) {
                        finishLives.observe(finishFlag && lifeNumbers && lives.equals(before));
                    } else {
                        cpLives.observe(known && finishFlag && lifeNumbers && lives == before + 2);
                        if (known)
                            normalSeen.add(id);
                    }
                    Long award = number(fields, "awardedPoints");
                    cpPoints.observe(known && pointsBefore != null && pointsBefore >= 0
                            && eq(award, awards.getOrDefault(id, -1)) && points != null
                            && points == pointsBefore + award);
                    if (known)
                        pointsSeen.add(id);
                }
                case "DEATH" -> deaths.observe(identified && before != null && before > 0
                        && lives != null && lives == before - 1
                        && Boolean.toString(lives == 0).equals(fields.get("eliminated")));
                case "FINISH" -> {
                    finishPoints.observe(identified && lives != null && lives > 0
                            && pointsBefore != null && pointsBefore >= 0 && points != null
                            && points == pointsBefore + lives);
                    Long position = number(fields, "position");
                    Long remainingBefore = number(fields, "remainingBefore");
                    Long remaining = number(fields, "remaining");
                    boolean valid = identified && position != null && position >= 1
                            && remainingBefore != null && remainingBefore >= 0 && remaining != null
                            && remaining == (position == 1 ? Math.min(60, remainingBefore) : remainingBefore);
                    timer.observe(valid);
                    highFirstFinish |= valid && position == 1 && remainingBefore > 60;
                }
                case "STRAFE" -> {
                    String direction = fields.get("direction");
                    boolean known = Set.of("LEFT", "BACK", "RIGHT").contains(direction == null ? "" : direction);
                    boolean values = identified && known && eq(number(fields, "cooldown"), 60)
                            && near(fields.get("horizontal"), 1.78) && near(fields.get("vertical"), 0.30);
                    strafes.observe(values);
                    if (values)
                        strafeSeen.add(direction);
                    Long elapsed = number(fields, "sampleElapsedMillis");
                    boolean cooldowns = identified && known && elapsed != null && elapsed >= 0 && elapsed < 60_000;
                    boolean otherActive = false;
                    for (String name : List.of("Left", "Back", "Right")) {
                        Long old = number(fields, "before" + name);
                        Long after = number(fields, "after" + name);
                        boolean selected = name.equalsIgnoreCase(direction);
                        cooldowns &= old != null && old >= 0 && old <= 60_000
                                && after != null && after >= 0 && after <= 60_000
                                && elapsed != null && elapsed >= 0 && elapsed < 60_000
                                && (selected ? old == 0 && after > 0 && after >= 60_000 - elapsed - 1
                                : after <= old && old - after <= elapsed + 1);
                        otherActive |= !selected && old != null && old > 0;
                    }
                    independent.observe(cooldowns);
                    if (cooldowns && otherActive)
                        independentSeen.add(direction);
                }
                case "STRAFE_BLOCKED" -> {
                    String direction = fields.get("direction");
                    Long remaining = number(fields, "remainingMillis");
                    boolean valid = identified && direction != null
                            && Set.of("LEFT", "BACK", "RIGHT").contains(direction)
                            && remaining != null && remaining > 0 && remaining <= 60_000;
                    blocked.observe(valid);
                    if (valid)
                        blockedSeen.add(direction);
                }
                case "JOIN" -> {
                    if (identified) {
                        joined.add(player);
                        unrestored.add(player);
                        left.remove(player);
                        disconnected.remove(player);
                    }
                }
                case "LEAVE" -> {
                    if (identified && joined.contains(player))
                        left.add(player);
                }
                case "DISCONNECT" -> {
                    if (identified && joined.contains(player))
                        disconnected.add(player);
                }
                case "RESTORE" -> {
                    if (left.contains(player) && "true".equals(fields.get("online"))
                            && "false".equals(fields.get("snapshotPending")))
                        unrestored.remove(player);
                }
                case "RECONNECT_RECOVERY" -> {
                    if (disconnected.remove(player) && "true".equals(fields.get("recovered"))
                            && "false".equals(fields.get("pendingAfter")))
                        unrestored.remove(player);
                }
                default -> { }
            }
        }

        add(checks, "runner-start-lives", starts, true, "2 Lives / 0 points");
        add(checks, "checkpoint-lives", cpLives, normalSeen.containsAll(normalIds), "+2 Lives at " + normalSeen + " expected=" + normalIds);
        add(checks, "checkpoint-points", cpPoints, pointsSeen.containsAll(awards.keySet()), "configured awards; seen=" + pointsSeen);
        add(checks, "finish-gate-lives", finishLives, finishId != null, "finish gate grants no extra Lives");
        add(checks, "death-life-cost", deaths, true, "-1 Life; eliminate exactly at zero");
        add(checks, "finish-life-points", finishPoints, true, "remaining Lives added to points");
        add(checks, "finish-timer-rule", timer, true, "first min(before,60); later unchanged");
        checks.add(new PlaytestAcceptanceAnalyzer.Check("first-finish-clamp-exercised", highFirstFinish,
                "first finish with remainingBefore>60 and remaining=60"));
        add(checks, "strafe-values", strafes, strafeSeen.size() == 3, "all directions: 1.78 / 0.30 / 60s");
        // The first Strafe cannot have another active cooldown. Two different
        // subsequent directions within its cooldown prove separate timers.
        add(checks, "strafe-independent-cooldowns", independent, independentSeen.size() >= 2,
                "other timers preserved; exercised=" + independentSeen);
        for (String direction : List.of("LEFT", "BACK", "RIGHT"))
            add(checks, "strafe-blocked-" + direction.toLowerCase(java.util.Locale.ROOT), blocked,
                    blockedSeen.contains(direction), "retry blocked while its own timer is active");
        checks.add(new PlaytestAcceptanceAnalyzer.Check("all-player-restores", joined.size() >= 2 && unrestored.isEmpty(),
                "joined=" + joined + " awaitingRestore=" + unrestored));
        return new Result(checks.stream().allMatch(PlaytestAcceptanceAnalyzer.Check::passed), List.copyOf(checks));
    }

    private static Long number(Map<String, String> fields, String key) {
        try {
            String raw = fields.get(key);
            return raw == null ? null : Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean eq(Long value, long expected) {
        return value != null && value == expected;
    }

    private static boolean near(String raw, double expected) {
        try {
            return raw != null && Math.abs(Double.parseDouble(raw) - expected) < 0.00001;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static void add(List<PlaytestAcceptanceAnalyzer.Check> checks, String key, Evidence evidence,
                            boolean covered, String detail) {
        checks.add(new PlaytestAcceptanceAnalyzer.Check(key, covered && evidence.seen > 0 && evidence.invalid == 0,
                detail + " | observed=" + evidence.seen + " invalid=" + evidence.invalid));
    }

    private static final class Evidence {
        int seen;
        int invalid;

        void observe(boolean valid) {
            seen++;
            if (!valid)
                invalid++;
        }
    }

    public record Result(boolean complete, List<PlaytestAcceptanceAnalyzer.Check> checks) {}
}
