package pl.mrstudios.deathrun.classic.checkpoint;

/** Immutable block bounds; player hitbox expansion is shared by traps and checkpoints. */
public record MovementBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    public boolean touches(double x0, double y0, double z0, double x1, double y1, double z1) {
        return touches(x0, y0, z0, x1, y1, z1, 1.01);
    }
    public boolean touches(double x0, double y0, double z0, double x1, double y1, double z1, double topPadding) {
        return SegmentAabb.intersects(x0, y0, z0, x1, y1, z1,
                minX - .30, minY - 1.80, minZ - .30,
                maxX + 1.30, maxY + topPadding, maxZ + 1.30);
    }
}
