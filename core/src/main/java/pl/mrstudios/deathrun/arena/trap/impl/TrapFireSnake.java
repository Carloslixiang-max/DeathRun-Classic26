package pl.mrstudios.deathrun.arena.trap.impl;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pl.mrstudios.deathrun.api.arena.trap.annotations.Serializable;
import pl.mrstudios.deathrun.arena.trap.Trap;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static java.time.Duration.ofSeconds;

/**
 * Moving Classic-style fire wave. Locations are route support blocks. The
 * longer horizontal axis becomes the travel axis; slices ignite in ascending
 * coordinate order and accelerate as the wave advances.
 */
public final class TrapFireSnake extends Trap {

    private final Map<Location, BlockData> backup = new LinkedHashMap<>();
    private final List<BukkitTask> tasks = new ArrayList<>();

    @Serializable
    private boolean reverse;

    @Override
    public void start() {
        if (this.abortIfAnyNullWorldLocation("start"))
            return;

        this.cancelTasks();
        this.backup.clear();
        for (Location location : this.locations)
            this.backup.put(location.clone(), location.getBlock().getBlockData());

        List<List<Location>> frames = frames(this.locations, this.reverse);
        if (frames.isEmpty())
            return;

        Plugin plugin = JavaPlugin.getProvidingPlugin(TrapFireSnake.class);
        long delay = 0L;
        for (int index = 0; index < frames.size(); index++) {
            int frameIndex = index;
            int stepTicks = Math.max(1, 4 - (index / 5));
            delay += stepTicks;
            this.tasks.add(Bukkit.getScheduler().runTaskLater(
                    plugin,
                    () -> this.igniteFrame(frames, frameIndex),
                    delay
            ));
        }
    }

    private void igniteFrame(@NotNull List<List<Location>> frames, int index) {
        if (index >= 2)
            this.restoreFrame(frames.get(index - 2));

        List<Location> frame = frames.get(index);
        for (Location location : frame) {
            if (location.getWorld() == null)
                continue;
            location.getBlock().setType(Material.MAGMA_BLOCK, false);
            Location effect = location.clone().toCenterLocation().add(0, 0.65, 0);
            location.getWorld().spawnParticle(Particle.FLAME, effect, 6, 0.25, 0.15, 0.25, 0.01);
            location.getWorld().spawnParticle(Particle.SMOKE, effect, 2, 0.2, 0.1, 0.2, 0.01);
        }

        Location first = frame.stream().findFirst().orElse(null);
        if (first != null && first.getWorld() != null)
            first.getWorld().playSound(first, Sound.ITEM_FIRECHARGE_USE, 0.65f, 1.15f);
    }

    private void restoreFrame(@NotNull List<Location> frame) {
        for (Location location : frame) {
            BlockData original = this.backup.get(location);
            if (original != null && location.getWorld() != null)
                location.getBlock().setBlockData(original, false);
        }
    }

    @Override
    public void end() {
        this.cancelTasks();
        this.backup.forEach((location, blockData) -> {
            if (location.getWorld() != null)
                location.getBlock().setBlockData(blockData, false);
        });
        this.backup.clear();
    }

    private void cancelTasks() {
        this.tasks.forEach(BukkitTask::cancel);
        this.tasks.clear();
    }

    private static @NotNull List<List<Location>> frames(@NotNull List<Location> locations, boolean reverse) {
        if (locations.isEmpty())
            return List.of();

        int minX = locations.stream().mapToInt(Location::getBlockX).min().orElse(0);
        int maxX = locations.stream().mapToInt(Location::getBlockX).max().orElse(0);
        int minZ = locations.stream().mapToInt(Location::getBlockZ).min().orElse(0);
        int maxZ = locations.stream().mapToInt(Location::getBlockZ).max().orElse(0);
        boolean travelZ = (maxZ - minZ) >= (maxX - minX);

        TreeMap<Integer, List<Location>> grouped = new TreeMap<>();
        for (Location location : locations) {
            int key = travelZ ? location.getBlockZ() : location.getBlockX();
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(location);
        }
        var ordered = reverse ? grouped.descendingMap().values() : grouped.values();
        return ordered.stream().map(List::copyOf).toList();
    }

    public void setReverse(boolean reverse) {
        this.reverse = reverse;
    }

    public boolean isReverse() {
        return this.reverse;
    }

    @Override
    public void setExtra(@Nullable Object... objects) {}

    @Override
    public @NotNull List<Location> filter(@NotNull List<Location> list, @Nullable Object... objects) {
        return list.stream()
                .filter(location -> location != null && location.getWorld() != null)
                .toList();
    }

    @Override
    public @NotNull Duration getDuration() {
        return ofSeconds(3);
    }
}
