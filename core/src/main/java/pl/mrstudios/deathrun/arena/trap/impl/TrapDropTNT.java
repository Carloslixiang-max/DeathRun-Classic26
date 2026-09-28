package pl.mrstudios.deathrun.arena.trap.impl;

import org.bukkit.Location;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

import static java.time.Duration.ofSeconds;

/**
 * Classic26 reconstruction for the archived To Bee "Drop TNT" action.
 *
 * Locations are preserved TNT anchor blocks. Primed TNT is spawned one block
 * below each anchor so the original map block is never destroyed or replaced.
 */
public final class TrapDropTNT extends TrapTNT {

    @Override
    public void start() {
        if (this.abortIfAnyNullWorldLocation("start"))
            return;

        for (Location anchor : this.locations) {
            Location spawn = anchor.clone().add(0, -1, 0).toCenterLocation();
            anchor.getWorld().spawn(spawn, TNTPrimed.class, entity -> {
                entity.setFuseTicks(25);
                entity.setYield(0.0f);
                entity.addScoreboardTag(
                        pl.mrstudios.deathrun.classic.trap.DeathRunEntityTags.TRAP_EXPLOSIVE
                );
                entity.setVelocity(new Vector(0, -0.12, 0));
            });
        }
    }

    @Override
    public @NotNull Duration getDuration() {
        return ofSeconds(3);
    }
}
