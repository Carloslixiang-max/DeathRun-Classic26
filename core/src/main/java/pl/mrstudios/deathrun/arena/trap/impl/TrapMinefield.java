package pl.mrstudios.deathrun.arena.trap.impl;

import org.bukkit.Location;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pl.mrstudios.deathrun.arena.trap.Trap;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.time.Duration.ofSeconds;

public final class TrapMinefield extends Trap {

    private static final int MAX_MINES = 12;

    @Override
    public void start() {
        if (this.abortIfAnyNullWorldLocation("start"))
            return;

        List<Location> shuffled = new ArrayList<>(this.locations);
        Collections.shuffle(shuffled);
        shuffled.stream().limit(MAX_MINES).forEach(location ->
                location.getWorld().spawn(location.toCenterLocation(), TNTPrimed.class, entity -> {
                    entity.setFuseTicks(8);
                    entity.setYield(0.0f);
                    entity.setVelocity(new Vector(0, 0.08, 0));
                })
        );
    }

    @Override public void end() {}

    @Override public void setExtra(@Nullable Object... objects) {}

    @Override
    public @NotNull List<Location> filter(@NotNull List<Location> list, @Nullable Object... objects) {
        return list.stream()
                .filter(location -> location != null && location.getWorld() != null)
                .toList();
    }

    @Override public @NotNull Duration getDuration() { return ofSeconds(2); }
}
