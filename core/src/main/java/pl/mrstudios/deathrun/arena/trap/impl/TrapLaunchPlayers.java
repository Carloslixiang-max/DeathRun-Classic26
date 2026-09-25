package pl.mrstudios.deathrun.arena.trap.impl;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pl.mrstudios.deathrun.arena.trap.Trap;
import java.time.Duration;
import java.util.List;
import static java.time.Duration.ofSeconds;

public final class TrapLaunchPlayers extends Trap {
    @Override public void start() {}
    @Override public void end() {}
    @Override public void setExtra(@Nullable Object... objects) {}
    @Override public @NotNull List<Location> filter(@NotNull List<Location> list, @Nullable Object... objects) {
        return list.stream().filter(location -> location != null && location.getWorld() != null).toList();
    }
    @Override public @NotNull Duration getDuration() { return ofSeconds(3); }
}
