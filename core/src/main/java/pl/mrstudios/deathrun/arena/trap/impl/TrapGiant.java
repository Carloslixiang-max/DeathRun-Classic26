package pl.mrstudios.deathrun.arena.trap.impl;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Giant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pl.mrstudios.deathrun.arena.trap.Trap;

import java.time.Duration;
import java.util.List;

import static java.time.Duration.ofSeconds;

public final class TrapGiant extends Trap {

    private Giant spawned;

    @Override
    public void start() {
        if (this.abortIfAnyNullWorldLocation("start") || this.locations.isEmpty())
            return;

        Location center = this.center();
        this.spawned = center.getWorld().spawn(center, Giant.class, giant -> {
            giant.setRemoveWhenFarAway(false);
            giant.setAI(false);
            giant.setInvulnerable(true);
            giant.setSilent(true);
            giant.setPersistent(false);
        });

        center.getWorld().spawnParticle(Particle.EXPLOSION, center.clone().add(0, 1, 0), 4, 1.0, 0.5, 1.0, 0.0);
        center.getWorld().playSound(center, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.2f, 0.65f);
    }

    @Override
    public void end() {
        if (this.spawned != null) {
            this.spawned.remove();
            this.spawned = null;
        }
    }

    @Override public void setExtra(@Nullable Object... objects) {}

    @Override
    public @NotNull List<Location> filter(@NotNull List<Location> list, @Nullable Object... objects) {
        return list.stream()
                .filter(location -> location != null && location.getWorld() != null)
                .toList();
    }

    @Override public @NotNull Duration getDuration() { return ofSeconds(5); }

    private @NotNull Location center() {
        Location first = this.locations.get(0);
        double x = this.locations.stream().mapToDouble(Location::getX).average().orElse(first.getX());
        double y = this.locations.stream().mapToDouble(Location::getY).average().orElse(first.getY());
        double z = this.locations.stream().mapToDouble(Location::getZ).average().orElse(first.getZ());
        return new Location(first.getWorld(), x + 0.5, y, z + 0.5);
    }
}
