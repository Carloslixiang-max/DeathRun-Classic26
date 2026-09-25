package pl.mrstudios.deathrun.arena.trap.impl;

import org.bukkit.Location;
import org.bukkit.entity.Giant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pl.mrstudios.deathrun.arena.trap.Trap;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static java.time.Duration.ofSeconds;

public final class TrapGiant extends Trap {
    private final List<Giant> spawned = new ArrayList<>();

    @Override public void start() {
        if (this.abortIfAnyNullWorldLocation("start")) return;
        this.spawned.clear();
        for (Location location : this.locations) {
            Giant giant = location.getWorld().spawn(location.toCenterLocation(), Giant.class);
            giant.setRemoveWhenFarAway(false);
            this.spawned.add(giant);
        }
    }

    @Override public void end() {
        this.spawned.forEach(Giant::remove);
        this.spawned.clear();
    }

    @Override public void setExtra(@Nullable Object... objects) {}
    @Override public @NotNull List<Location> filter(@NotNull List<Location> list, @Nullable Object... objects) {
        return list.stream().filter(location -> location != null && location.getWorld() != null).toList();
    }
    @Override public @NotNull Duration getDuration() { return ofSeconds(5); }
}
