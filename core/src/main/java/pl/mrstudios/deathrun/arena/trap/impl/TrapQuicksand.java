package pl.mrstudios.deathrun.arena.trap.impl;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import java.time.Duration;
import static java.time.Duration.ofSeconds;
import static org.bukkit.Material.SOUL_SAND;

public final class TrapQuicksand extends ClassicBlockSwapTrap {
    @Override protected @NotNull Material replacement() { return SOUL_SAND; }
    @Override protected @NotNull Duration activeDuration() { return ofSeconds(4); }
}
