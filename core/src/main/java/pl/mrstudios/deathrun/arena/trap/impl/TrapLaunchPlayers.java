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

public final class TrapLaunchPlayers extends Trap {

    @Override
    public void start() {
        if (this.abortIfAnyNullWorldLocation("start"))
            return;

        for (Location location : this.locations)
            location.getWorld().spawnParticle(
                    Particle.CLOUD,
                    location.clone().toCenterLocation(),
                    10,
                    0.25, 0.05, 0.25,
                    0.04
            );

        if (!this.locations.isEmpty())
            this.locations.get(0).getWorld().playSound(
                    this.locations.get(0),
                    Sound.ENTITY_ENDER_DRAGON_FLAP,
                    1.0f,
                    1.35f
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
