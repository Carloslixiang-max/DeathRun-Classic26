package pl.mrstudios.deathrun.classic.tobee;

import java.util.List;

/** Reviewed archive control platforms, separate from trap floors and roof buttons. */
public final class ToBeeSpawnGeometry {
    private ToBeeSpawnGeometry() {}
    public static final float RUNNER_YAW = 90f;
    public static final float DEATH_YAW = 90f;
    public static boolean runnerRegion(int x, int y, int z) {
        return x >= 85 && x <= 92 && y == 25 && z >= 79 && z <= 85;
    }
    public static final List<Pos> DEATH_STARTS = List.of(new Pos(75, 25, 56), new Pos(76, 25, 57));
    public static final List<ControlLanding> CONTROLS = List.of(
            new ControlLanding("pair-011", new Pos(15, 35, 62), new Pos(10, 27, 68)),
            new ControlLanding("pair-007", new Pos(-24, 35, 6), new Pos(-24, 29, 10)),
            new ControlLanding("pair-005", new Pos(-34, 35, -15), new Pos(-36, 29, -19)),
            new ControlLanding("pair-001-melt-ice", new Pos(-41, 35, 3), new Pos(-45, 29, 6)),
            new ControlLanding("pair-015-coal-fire", new Pos(31, 48, 14), new Pos(29, 44, 15)),
            new ControlLanding("pair-021-red-D", new Pos(81, 25, 54), new Pos(88, 25, 65)),
            new ControlLanding("action-021-sea-lantern", new Pos(74, 25, 54), new Pos(71, 25, 76)),
            new ControlLanding("pair-006-red-A", new Pos(-21, 35, -15), new Pos(-22, 29, -19)),
            new ControlLanding("pair-012-red-B", new Pos(13, 35, -15), new Pos(12, 29, -19)),
            new ControlLanding("pair-018-red-C", new Pos(15, 35, 60), new Pos(11, 29, 60)),
            new ControlLanding("pair-004-flood-A", new Pos(-39, 35, 6), new Pos(-42, 29, 10)),
            new ControlLanding("pair-002-dark-wood-A", new Pos(-41, 35, -14), new Pos(-45, 29, -19)),
            new ControlLanding("pair-016-floor-fall-A", new Pos(24, 35, -15), new Pos(27, 29, -17)),
            new ControlLanding("pair-009-minefield", new Pos(15, 35, 39), new Pos(11, 29, 39)),
            new ControlLanding("pair-017-flood-B", new Pos(26, 48, 23), new Pos(26, 44, 21)),
            new ControlLanding("pair-019-floor-fall-B", new Pos(79, 25, 54), new Pos(51, 25, 56)),
            new ControlLanding("pair-010-fire-snake-A", new Pos(15, 35, 55), new Pos(11, 29, 54)),
            new ControlLanding("pair-020-fire-snake-B", new Pos(76, 25, 47), new Pos(75, 25, 56)),
            new ControlLanding("pair-014-dark-wood-B", new Pos(15, 35, 57), new Pos(15, 25, 76)),
            new ControlLanding("action-017-drop-TNT", new Pos(28, 48, 7), new Pos(28, 44, 9)),
            new ControlLanding("pair-003-random-wall-A", new Pos(-41, 35, 1), new Pos(-45, 29, 1)),
            new ControlLanding("pair-008-random-wall-B", new Pos(-6, 35, 6), new Pos(-6, 29, 10)),
            new ControlLanding("pair-013-random-wall-C", new Pos(22, 35, -15), new Pos(22, 29, -19))
    );
    public record Pos(int x, int y, int z) {}
    public record ControlLanding(String id, Pos button, Pos landing) {}
}
