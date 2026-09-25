package pl.mrstudios.deathrun.arena.trap.impl;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pl.mrstudios.deathrun.arena.trap.Trap;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.time.Duration.ofSeconds;

public abstract class ClassicBlockSwapTrap extends Trap {
    private final Map<Location, BlockData> backup = new HashMap<>();

    protected abstract @NotNull Material replacement();
    protected @NotNull Duration activeDuration() { return ofSeconds(3); }

    @Override public void start() {
        if (this.abortIfAnyNullWorldLocation("start")) return;
        this.backup.clear();
        for (Location location : this.locations) {
            this.backup.put(location, location.getBlock().getBlockData());
            location.getBlock().setType(this.replacement(), false);
        }
    }

    @Override public void end() {
        this.backup.forEach((location, blockData) -> location.getBlock().setBlockData(blockData, false));
        this.backup.clear();
    }

    @Override public void setExtra(@Nullable Object... objects) {}

    @Override public @NotNull List<Location> filter(@NotNull List<Location> list, @Nullable Object... objects) {
        return list.stream().filter(location -> location != null && location.getWorld() != null).toList();
    }

    @Override public @NotNull Duration getDuration() { return this.activeDuration(); }
}
