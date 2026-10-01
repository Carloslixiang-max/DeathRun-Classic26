package pl.mrstudios.deathrun.classic.tobee;

import java.util.List;

public final class ToBeeCheckpointRepairPlan {
    private static final List<Integer> GATES = List.of(7, 2, 1, 4, 5, 3);
    private ToBeeCheckpointRepairPlan() {}
    public static boolean valid(List<Integer> ids, Integer finishId) {
        return ids.equals(List.of(1, 2, 3, 4, 5, 6)) && Integer.valueOf(6).equals(finishId);
    }
    public static int gateForCheckpoint(int id) {
        if (id < 1 || id > GATES.size()) throw new IllegalArgumentException("Unreviewed checkpoint ID: " + id);
        return GATES.get(id - 1);
    }
}
