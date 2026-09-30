package pl.mrstudios.deathrun.classic.tobee;

import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.deathrun.api.arena.enums.GameState;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.classic.trap.DeathRunEntityTags;
import pl.mrstudios.deathrun.classic.trap.TrapActivationService;

import java.util.ArrayList;
import java.util.List;

public final class ToBeeReadinessService {

    private final ArenaManager arenaManager;

    public ToBeeReadinessService(@NotNull ArenaManager arenaManager) {
        this.arenaManager = arenaManager;
    }

    public @NotNull Result check(@NotNull World world) {
        List<String> issues = new ArrayList<>();

        ArenaManager.ArenaRuntime runtime = this.arenaManager.runtimeByMapId(
                ToBeeCandidateProfileService.MAP_ID
        );
        if (runtime == null)
            return new Result(false, List.of("runtime-unavailable"), 0, 0);

        if (runtime.map().world == null || !runtime.map().world.equalsIgnoreCase(world.getName()))
            issues.add("world-mismatch:" + runtime.map().world);
        if (runtime.map().arenaSetupEnabled)
            issues.add("setup-still-enabled");
        if (runtime.arena().getGameState() != GameState.WAITING)
            issues.add("state:" + runtime.arena().getGameState());
        if (!runtime.arena().getUsers().isEmpty())
            issues.add("active-players:" + runtime.arena().getUsers().size());
        if (runtime.map().arenaRunnerSpawnLocations.size() != 20)
            issues.add("runner-spawns:" + runtime.map().arenaRunnerSpawnLocations.size() + "/20");
        if (runtime.map().arenaDeathSpawnLocations.size() != 2)
            issues.add("death-spawns:" + runtime.map().arenaDeathSpawnLocations.size() + "/2");
        if (runtime.map().arenaCheckpoints.size() != 6)
            issues.add("checkpoints:" + runtime.map().arenaCheckpoints.size() + "/6");
        if (runtime.map().arenaTraps.size() != ToBeeCandidateProfileService.implementedTrapCount())
            issues.add("traps:" + runtime.map().arenaTraps.size() + "/"
                    + ToBeeCandidateProfileService.implementedTrapCount());
        if (!"Timmetatsch".equals(runtime.map().creator))
            issues.add("creator:" + runtime.map().creator);

        issues.addAll(ToBeeCandidateProfileService.spawnIssues(world, runtime.map()));

        int barriers = 0;
        for (var location : runtime.map().arenaStartBarrierBlocks) {
            if (location == null || location.getWorld() == null) {
                issues.add("barrier-null-world");
                continue;
            }
            if (!location.getWorld().getUID().equals(world.getUID())) {
                issues.add("barrier-wrong-world:"
                        + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
                continue;
            }
            if (location.getBlock().getType() != Material.BARRIER) {
                issues.add("barrier-not-restored:"
                        + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ()
                        + ":" + location.getBlock().getType().name());
                continue;
            }
            barriers++;
        }
        if (barriers != ToBeeCandidateProfileService.startBarrierPositions().size())
            issues.add("barriers:" + barriers + "/"
                    + ToBeeCandidateProfileService.startBarrierPositions().size());

        long trapEntities = world.getEntities().stream()
                .filter(DeathRunEntityTags::isTrapEntity)
                .count();
        if (trapEntities != 0L)
            issues.add("trap-entities:" + trapEntities);

        TrapActivationService.MapRuntimeState trapState =
                TrapActivationService.mapRuntimeState(runtime.mapId());
        if (!trapState.clean())
            issues.add("trap-runtime-state:" + trapState.compact());

        return new Result(issues.isEmpty(), List.copyOf(issues), barriers, trapEntities);
    }

    public record Result(
            boolean success,
            @NotNull List<String> issues,
            int restoredBarriers,
            long trapEntities
    ) {}
}
