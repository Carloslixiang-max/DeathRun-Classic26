package pl.mrstudios.deathrun.classic.checkpoint;

/** Pure-math slab intersection used by swept checkpoint detection. */
public final class SegmentAabb {
    private SegmentAabb() {}

    public static boolean intersects(
            double x0, double y0, double z0,
            double x1, double y1, double z1,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ
    ) {
        double[] t = {0.0, 1.0};
        return axis(x0, x1 - x0, minX, maxX, t)
                && axis(y0, y1 - y0, minY, maxY, t)
                && axis(z0, z1 - z0, minZ, maxZ, t);
    }

    private static boolean axis(double origin, double delta, double min, double max, double[] t) {
        final double epsilon = 1.0e-12;
        if (Math.abs(delta) < epsilon)
            return origin >= min && origin <= max;

        double inv = 1.0 / delta;
        double a = (min - origin) * inv;
        double b = (max - origin) * inv;
        if (a > b) {
            double swap = a;
            a = b;
            b = swap;
        }
        t[0] = Math.max(t[0], a);
        t[1] = Math.min(t[1], b);
        return t[0] <= t[1];
    }
}
