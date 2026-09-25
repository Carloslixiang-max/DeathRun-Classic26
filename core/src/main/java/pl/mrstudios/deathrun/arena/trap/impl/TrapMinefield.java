package pl.mrstudios.deathrun.arena.trap.impl;

import org.jetbrains.annotations.NotNull;
import java.time.Duration;
import static java.time.Duration.ofSeconds;

public final class TrapMinefield extends TrapTNT {
    @Override public @NotNull Duration getDuration() { return ofSeconds(2); }
}
