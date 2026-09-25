package pl.mrstudios.deathrun.classic.trap;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

public final class DeathRunEntityTags {

    public static final String TRAP_PROJECTILE = "deathrun_classic26_trap_projectile";
    public static final String TRAP_EXPLOSIVE = "deathrun_classic26_trap_explosive";

    private DeathRunEntityTags() {}

    public static boolean isTrapProjectile(@NotNull Entity entity) {
        return entity.getScoreboardTags().contains(TRAP_PROJECTILE);
    }

    public static boolean isTrapExplosive(@NotNull Entity entity) {
        return entity.getScoreboardTags().contains(TRAP_EXPLOSIVE);
    }
}
