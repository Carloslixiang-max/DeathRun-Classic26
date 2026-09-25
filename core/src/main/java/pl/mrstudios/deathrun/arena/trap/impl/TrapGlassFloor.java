package pl.mrstudios.deathrun.arena.trap.impl;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

import static java.time.Duration.ofSeconds;
import static org.bukkit.Material.AIR;

public final class TrapGlassFloor extends ClassicBlockSwapTrap {

    @Override
    public void start() {
        if (!this.locations.isEmpty() && this.locations.get(0).getWorld() != null) {
            Location first = this.locations.get(0);
            first.getWorld().playSound(first, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.9f);
            for (Location location : this.locations) {
                if (location != null && location.getWorld() != null)
                    location.getWorld().spawnParticle(
                            Particle.BLOCK,
                            location.clone().toCenterLocation(),
                            5,
                            0.25, 0.15, 0.25,
                            Material.GLASS.createBlockData()
                    );
            }
        }
        super.start();
    }

    @Override protected @NotNull Material replacement() { return AIR; }
    @Override protected @NotNull Duration activeDuration() { return ofSeconds(3); }
}
