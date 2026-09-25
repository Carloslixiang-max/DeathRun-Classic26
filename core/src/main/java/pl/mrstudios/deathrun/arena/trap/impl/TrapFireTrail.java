package pl.mrstudios.deathrun.arena.trap.impl;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pl.mrstudios.deathrun.arena.trap.Trap;

import java.time.Duration;
import java.util.List;

import static java.time.Duration.ofSeconds;

public final class TrapFireTrail extends Trap {

    @Override
    public void start() {
        if (this.abortIfAnyNullWorldLocation("start"))
            return;

        for (Location location : this.locations) {
            Location center = location.clone().toCenterLocation().add(0, 0.2, 0);
            center.getWorld().spawnParticle(Particle.FLAME, center, 16, 0.25, 0.15, 0.25, 0.01);
            center.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, center, 8, 0.2, 0.1, 0.2, 0.01);
        }

        Location first = this.locations.get(0);
        first.getWorld().playSound(first, Sound.ITEM_FIRECHARGE_USE, 1.0f, 1.0f);
    }

    @Override public void end() {}
    @Override public void setExtra(@Nullable Object... objects) {}

    @Override
    public @NotNull List<Location> filter(@NotNull List<Location> list, @Nullable Object... objects) {
        return list.stream()
                .filter(location -> location != null && location.getWorld() != null)
                .toList();
    }

    @Override public @NotNull Duration getDuration() { return ofSeconds(3); }
}
