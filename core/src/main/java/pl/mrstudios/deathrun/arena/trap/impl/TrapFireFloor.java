package pl.mrstudios.deathrun.arena.trap.impl;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import java.time.Duration;
import static java.time.Duration.ofSeconds;
import static org.bukkit.Material.MAGMA_BLOCK;

public final class TrapFireFloor extends ClassicBlockSwapTrap {
    @Override protected @NotNull Material replacement() { return MAGMA_BLOCK; }
    @Override protected @NotNull Duration activeDuration() { return ofSeconds(3); }
}
