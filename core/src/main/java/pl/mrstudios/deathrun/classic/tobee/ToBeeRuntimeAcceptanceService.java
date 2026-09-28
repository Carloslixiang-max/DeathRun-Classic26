package pl.mrstudios.deathrun.classic.tobee;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.deathrun.api.arena.enums.GameState;
import pl.mrstudios.deathrun.api.arena.trap.ITrap;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.arena.trap.impl.ClassicBlockSwapTrap;
import pl.mrstudios.deathrun.arena.trap.impl.TrapArrows;
import pl.mrstudios.deathrun.arena.trap.impl.TrapDisappearingBlocks;
import pl.mrstudios.deathrun.arena.trap.impl.TrapFireSnake;
import pl.mrstudios.deathrun.arena.trap.impl.TrapMinefield;
import pl.mrstudios.deathrun.arena.trap.impl.TrapTNT;
import pl.mrstudios.deathrun.classic.trap.DeathRunEntityTags;
import pl.mrstudios.deathrun.classic.trap.TrapActivationService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Live Paper acceptance for the reconstructed To Bee trap catalog.
 *
 * The map is edit-locked for the duration of the check. Every trap is activated
 * on the real imported world, observed while active, ended, and then compared
 * against a pre-activation BlockData envelope. The envelope is restored even
 * after a failure so a diagnostic run cannot intentionally leave the course
 * mutated.
 */
public final class ToBeeRuntimeAcceptanceService {

    private static final Set<String> RUNNING = ConcurrentHashMap.newKeySet();
    private static final int NORMAL_PADDING = 4;
    private static final int FLOOD_PADDING = 12;
    private static final long ACTIVE_OBSERVE_TICKS = 5L;
    private static final long POST_END_SETTLE_TICKS = 5L;

    private final Plugin plugin;
    private final ArenaManager arenaManager;

    public ToBeeRuntimeAcceptanceService(
            @NotNull Plugin plugin,
            @NotNull ArenaManager arenaManager
    ) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
    }

    public @NotNull StartResult start(
            @NotNull World world,
            @NotNull Consumer<Result> callback
    ) {
        ArenaManager.ArenaRuntime runtime = this.arenaManager.runtimeByMapId(
                ToBeeCandidateProfileService.MAP_ID
        );
        if (runtime == null)
            return new StartResult(false, "runtime-unavailable");

        if (runtime.map().world == null || !runtime.map().world.equalsIgnoreCase(world.getName()))
            return new StartResult(false, "world-mismatch:" + runtime.map().world);

        if (runtime.arena().getGameState() != GameState.WAITING)
            return new StartResult(false, "map-not-waiting:" + runtime.arena().getGameState());

        if (!runtime.arena().getUsers().isEmpty())
            return new StartResult(false, "active-players:" + runtime.arena().getUsers().size());

        if (runtime.map().arenaTraps.size() != ToBeeCandidateProfileService.implementedTrapCount())
            return new StartResult(false, "trap-count:" + runtime.map().arenaTraps.size());

        String mapId = runtime.mapId().toLowerCase(java.util.Locale.ROOT);
        if (!RUNNING.add(mapId))
            return new StartResult(false, "already-running");

        boolean wasEditLocked = this.arenaManager.isMapLockedForEditing(mapId);
        if (!wasEditLocked)
            this.arenaManager.setMapEditLocked(mapId, true);

        TrapActivationService.resetMap(mapId, world);

        new TestRun(runtime, world, wasEditLocked, callback).next();
        return new StartResult(true, "started");
    }

    private final class TestRun {

        private final ArenaManager.ArenaRuntime runtime;
        private final World world;
        private final boolean wasEditLocked;
        private final Consumer<Result> callback;
        private final List<String> issues = new ArrayList<>();

        private int index;
        private int mutationAssertions;
        private int entityAssertions;

        private TestRun(
                ArenaManager.ArenaRuntime runtime,
                World world,
                boolean wasEditLocked,
                Consumer<Result> callback
        ) {
            this.runtime = runtime;
            this.world = world;
            this.wasEditLocked = wasEditLocked;
            this.callback = callback;
        }

        private void next() {
            if (this.index >= this.runtime.map().arenaTraps.size()) {
                this.finish();
                return;
            }

            int trapIndex = this.index++;
            ITrap trap = this.runtime.map().arenaTraps.get(trapIndex);
            EffectKind kind = effectKind(trap);

            if (kind == EffectKind.UNSUPPORTED) {
                this.issues.add("trap-" + (trapIndex + 1) + "-unsupported:" + trap.getClass().getSimpleName());
                this.next();
                return;
            }

            int padding = trap instanceof pl.mrstudios.deathrun.arena.trap.impl.TrapFlood
                    ? FLOOD_PADDING
                    : NORMAL_PADDING;
            Map<BlockKey, String> envelope = captureEnvelope(this.world, trap, padding);
            Map<BlockKey, String> targetBefore = captureTargets(trap);
            Set<UUID> entitiesBefore = trapEntityIds(this.world);

            try {
                trap.start();
            } catch (Throwable throwable) {
                this.issues.add("trap-" + (trapIndex + 1) + "-start:"
                        + throwable.getClass().getSimpleName());
                safeEnd(trap, trapIndex);
                restoreEnvelope(this.world, envelope);
                cleanupNewEntities(this.world, entitiesBefore);
                this.next();
                return;
            }

            long durationTicks = Math.max(
                    ACTIVE_OBSERVE_TICKS + 1L,
                    (trap.getDuration().toMillis() + 49L) / 50L
            );

            this.plugin.getServer().getScheduler().runTaskLater(
                    this.plugin,
                    () -> this.observeActive(trapIndex, trap, kind, targetBefore, entitiesBefore,
                            envelope, durationTicks),
                    ACTIVE_OBSERVE_TICKS
            );
        }

        private void observeActive(
                int trapIndex,
                ITrap trap,
                EffectKind kind,
                Map<BlockKey, String> targetBefore,
                Set<UUID> entitiesBefore,
                Map<BlockKey, String> envelope,
                long durationTicks
        ) {
            if (kind == EffectKind.BLOCK_MUTATION) {
                this.mutationAssertions++;
                if (!hasTargetMutation(trap, targetBefore))
                    this.issues.add("trap-" + (trapIndex + 1) + "-no-active-block-mutation:"
                            + trap.getClass().getSimpleName());
            } else {
                this.entityAssertions++;
                long spawned = this.world.getEntities().stream()
                        .filter(DeathRunEntityTags::isTrapEntity)
                        .filter(entity -> !entitiesBefore.contains(entity.getUniqueId()))
                        .count();
                if (spawned <= 0L)
                    this.issues.add("trap-" + (trapIndex + 1) + "-no-tagged-entity:"
                            + trap.getClass().getSimpleName());
            }

            long remaining = Math.max(1L, durationTicks - ACTIVE_OBSERVE_TICKS);
            this.plugin.getServer().getScheduler().runTaskLater(
                    this.plugin,
                    () -> this.endAndVerify(trapIndex, trap, entitiesBefore, envelope),
                    remaining
            );
        }

        private void endAndVerify(
                int trapIndex,
                ITrap trap,
                Set<UUID> entitiesBefore,
                Map<BlockKey, String> envelope
        ) {
            safeEnd(trap, trapIndex);
            cleanupNewEntities(this.world, entitiesBefore);

            this.plugin.getServer().getScheduler().runTaskLater(
                    this.plugin,
                    () -> {
                        List<String> residual = changedBlocks(this.world, envelope, 12);
                        if (!residual.isEmpty())
                            this.issues.add("trap-" + (trapIndex + 1) + "-residual:"
                                    + trap.getClass().getSimpleName() + ":" + String.join("|", residual));

                        // Always restore the complete envelope after observation,
                        // including any fluid spread outside the declared target.
                        restoreEnvelope(this.world, envelope);
                        cleanupNewEntities(this.world, entitiesBefore);

                        this.plugin.getLogger().info(
                                "[DR-TOBEE-RUNTIME] trap=" + (trapIndex + 1)
                                        + "/" + this.runtime.map().arenaTraps.size()
                                        + " type=" + trap.getClass().getSimpleName()
                                        + " issues=" + this.issues.size()
                        );
                        this.next();
                    },
                    POST_END_SETTLE_TICKS
            );
        }

        private void safeEnd(ITrap trap, int trapIndex) {
            try {
                trap.end();
            } catch (Throwable throwable) {
                this.issues.add("trap-" + (trapIndex + 1) + "-end:"
                        + throwable.getClass().getSimpleName());
            }
        }

        private void finish() {
            String mapId = this.runtime.mapId().toLowerCase(java.util.Locale.ROOT);
            try {
                TrapActivationService.resetMap(mapId, this.world);
                DeathRunEntityTags.cleanupMapEntities(this.world, mapId);
            } finally {
                if (!this.wasEditLocked)
                    ToBeeRuntimeAcceptanceService.this.arenaManager.setMapEditLocked(mapId, false);
                RUNNING.remove(mapId);
            }

            Result result = new Result(
                    this.issues.isEmpty(),
                    this.runtime.map().arenaTraps.size(),
                    this.mutationAssertions,
                    this.entityAssertions,
                    List.copyOf(this.issues)
            );

            this.plugin.getLogger().info(
                    "[DR-TOBEE-RUNTIME] " + (result.success() ? "PASS" : "FAIL")
                            + " traps=" + result.trapsTested()
                            + " mutationAssertions=" + result.mutationAssertions()
                            + " entityAssertions=" + result.entityAssertions()
                            + " issues=" + result.issues().size()
            );
            this.callback.accept(result);
        }
    }

    private static @NotNull EffectKind effectKind(@NotNull ITrap trap) {
        if (trap instanceof TrapFireSnake
                || trap instanceof TrapDisappearingBlocks
                || trap instanceof ClassicBlockSwapTrap)
            return EffectKind.BLOCK_MUTATION;
        if (trap instanceof TrapArrows)
            return EffectKind.PROJECTILE;
        if (trap instanceof TrapMinefield || trap instanceof TrapTNT)
            return EffectKind.EXPLOSIVE;
        return EffectKind.UNSUPPORTED;
    }

    private static @NotNull Map<BlockKey, String> captureTargets(@NotNull ITrap trap) {
        Map<BlockKey, String> snapshot = new LinkedHashMap<>();
        for (Location location : trap.getLocations()) {
            if (location == null || location.getWorld() == null)
                continue;
            BlockKey key = BlockKey.of(location);
            snapshot.put(key, location.getBlock().getBlockData().getAsString());
        }
        return snapshot;
    }

    private static @NotNull Map<BlockKey, String> captureEnvelope(
            @NotNull World world,
            @NotNull ITrap trap,
            int padding
    ) {
        List<Location> locations = trap.getLocations().stream()
                .filter(java.util.Objects::nonNull)
                .filter(location -> location.getWorld() != null)
                .toList();
        if (locations.isEmpty())
            return Map.of();

        int minX = locations.stream().mapToInt(Location::getBlockX).min().orElse(0) - padding;
        int maxX = locations.stream().mapToInt(Location::getBlockX).max().orElse(0) + padding;
        int minY = Math.max(world.getMinHeight(), locations.stream().mapToInt(Location::getBlockY).min().orElse(0) - padding);
        int maxY = Math.min(world.getMaxHeight() - 1, locations.stream().mapToInt(Location::getBlockY).max().orElse(0) + padding);
        int minZ = locations.stream().mapToInt(Location::getBlockZ).min().orElse(0) - padding;
        int maxZ = locations.stream().mapToInt(Location::getBlockZ).max().orElse(0) + padding;

        Map<BlockKey, String> snapshot = new LinkedHashMap<>();
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++)
                    snapshot.put(new BlockKey(x, y, z), world.getBlockAt(x, y, z).getBlockData().getAsString());
        return snapshot;
    }

    private static boolean hasTargetMutation(
            @NotNull ITrap trap,
            @NotNull Map<BlockKey, String> before
    ) {
        for (Location location : trap.getLocations()) {
            if (location == null || location.getWorld() == null)
                continue;
            String original = before.get(BlockKey.of(location));
            if (original != null && !original.equals(location.getBlock().getBlockData().getAsString()))
                return true;
        }
        return false;
    }

    private static @NotNull List<String> changedBlocks(
            @NotNull World world,
            @NotNull Map<BlockKey, String> before,
            int limit
    ) {
        List<String> changed = new ArrayList<>();
        for (Map.Entry<BlockKey, String> entry : before.entrySet()) {
            BlockKey key = entry.getKey();
            String current = world.getBlockAt(key.x(), key.y(), key.z()).getBlockData().getAsString();
            if (entry.getValue().equals(current))
                continue;
            changed.add(key.compact() + ":" + entry.getValue() + "->" + current);
            if (changed.size() >= limit)
                break;
        }
        return changed;
    }

    private static void restoreEnvelope(
            @NotNull World world,
            @NotNull Map<BlockKey, String> snapshot
    ) {
        snapshot.forEach((key, blockData) -> {
            BlockData data = Bukkit.createBlockData(blockData);
            world.getBlockAt(key.x(), key.y(), key.z()).setBlockData(data, false);
        });
    }

    private static @NotNull Set<UUID> trapEntityIds(@NotNull World world) {
        return world.getEntities().stream()
                .filter(DeathRunEntityTags::isTrapEntity)
                .map(Entity::getUniqueId)
                .collect(Collectors.toSet());
    }

    private static void cleanupNewEntities(
            @NotNull World world,
            @NotNull Set<UUID> before
    ) {
        for (Entity entity : List.copyOf(world.getEntities())) {
            if (DeathRunEntityTags.isTrapEntity(entity) && !before.contains(entity.getUniqueId()))
                entity.remove();
        }
    }

    private enum EffectKind {
        BLOCK_MUTATION,
        PROJECTILE,
        EXPLOSIVE,
        UNSUPPORTED
    }

    private record BlockKey(int x, int y, int z) {
        private static @NotNull BlockKey of(@NotNull Location location) {
            return new BlockKey(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }

        private @NotNull String compact() {
            return this.x + "," + this.y + "," + this.z;
        }
    }

    public record StartResult(boolean started, @NotNull String message) {}

    public record Result(
            boolean success,
            int trapsTested,
            int mutationAssertions,
            int entityAssertions,
            @NotNull List<String> issues
    ) {}
}
