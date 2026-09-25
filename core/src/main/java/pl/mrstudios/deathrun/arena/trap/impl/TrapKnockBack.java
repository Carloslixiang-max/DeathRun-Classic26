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

public final class TrapKnockBack extends Trap {

    @Override
    public void start() {
        if (this.abortIfAnyNullWorldLocation("start"))
            return;

        for (Location location : this.locations)
            location.getWorld().spawnParticle(
                    Particle.GUST,
                    location.clone().toCenterLocation(),
                    4,
                    0.3, 0.15, 0.3,
                    0.02
            );

        if (!this.locations.isEmpty())
            this.locations.get(0).getWorld().playSound(
                    this.locations.get(0),
                    Sound.ENTITY_BREEZE_WIND_BURST,
                    1.0f,
                    0.9f
            );
    }

    @Override public void end() {}
    @Override public void setExtra(@Nullable Object... objects) {}

    @Override
    public @NotNull List<Location> filter(@NotNull List<Location> list, @Nullable Object... objects) {
        return list.stream().filter(location -> location != null && location.getWorld() != null).toList();
    }

    @Override public @NotNull Duration getDuration() { return ofSeconds(3); }
}
