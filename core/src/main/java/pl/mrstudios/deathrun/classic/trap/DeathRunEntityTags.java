package pl.mrstudios.deathrun.classic.trap;

import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

public final class DeathRunEntityTags {

    public static final String TRAP_PROJECTILE = "deathrun_classic26_trap_projectile";
    public static final String TRAP_EXPLOSIVE = "deathrun_classic26_trap_explosive";
    private static final String MAP_TAG_PREFIX = "deathrun_classic26_map_";

    private DeathRunEntityTags() {}

    public static boolean isTrapProjectile(@NotNull Entity entity) {
        return entity.getScoreboardTags().contains(TRAP_PROJECTILE);
    }

    public static boolean isTrapExplosive(@NotNull Entity entity) {
        return entity.getScoreboardTags().contains(TRAP_EXPLOSIVE);
    }

    public static boolean isTrapEntity(@NotNull Entity entity) {
        return isTrapProjectile(entity) || isTrapExplosive(entity);
    }

    public static @NotNull String mapTag(@NotNull String mapId) {
        return MAP_TAG_PREFIX + mapId.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9_-]", "_");
    }

    public static void tagForMap(@NotNull Entity entity, @NotNull String mapId) {
        if (isTrapEntity(entity))
            entity.addScoreboardTag(mapTag(mapId));
    }

    public static boolean belongsToMap(@NotNull Entity entity, @NotNull String mapId) {
        return isTrapEntity(entity) && entity.getScoreboardTags().contains(mapTag(mapId));
    }

    public static int cleanupMapEntities(@NotNull World world, @NotNull String mapId) {
        int removed = 0;
        for (Entity entity : java.util.List.copyOf(world.getEntities())) {
            if (!belongsToMap(entity, mapId))
                continue;
            entity.remove();
            removed++;
        }
        return removed;
    }

    public static int cleanupAllTrapEntities(@NotNull World world) {
        int removed = 0;
        for (Entity entity : java.util.List.copyOf(world.getEntities())) {
            if (!isTrapEntity(entity))
                continue;
            entity.remove();
            removed++;
        }
        return removed;
    }
}
