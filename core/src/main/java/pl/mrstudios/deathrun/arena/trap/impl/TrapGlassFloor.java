package pl.mrstudios.deathrun.arena.trap.impl;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import java.time.Duration;
import static java.time.Duration.ofSeconds;
import static org.bukkit.Material.GLASS;

public final class TrapGlassFloor extends ClassicBlockSwapTrap {
    @Override protected @NotNull Material replacement() { return GLASS; }
    @Override protected @NotNull Duration activeDuration() { return ofSeconds(3); }
}
