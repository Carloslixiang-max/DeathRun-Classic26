package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.api.arena.IArena;
import pl.mrstudios.deathrun.api.arena.enums.GameState;
import pl.mrstudios.deathrun.api.arena.event.arena.ArenaGameStateChangeEvent;
import pl.mrstudios.deathrun.api.arena.event.arena.ArenaUserLeftEvent;
import pl.mrstudios.deathrun.api.arena.event.user.UserArenaFinishedEvent;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.player.PlayerStatisticsService;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.bukkit.event.EventPriority.MONITOR;

public class ArenaPlayerStatsListener implements Listener {

    private final ArenaManager arenaManager;
    private final PlayerStatisticsService playerStatisticsService;
    private final Map<String, Set<UUID>> winnersByMapId = new ConcurrentHashMap<>();

    @Inject
    public ArenaPlayerStatsListener(
            @NotNull ArenaManager arenaManager,
            @NotNull PlayerStatisticsService playerStatisticsService
    ) {
        this.arenaManager = arenaManager;
        this.playerStatisticsService = playerStatisticsService;
    }

    @EventHandler(priority = MONITOR)
    public void onRunnerFinished(
            @NotNull UserArenaFinishedEvent event
    ) {
        if (event.getPosition() != 1)
            return;

        UUID uniqueId = event.getUser().getUniqueId();
        this.playerStatisticsService.incrementWins(uniqueId);

        Player player = event.getUser().asBukkit();
        if (player == null)
            return;

        ArenaManager.ArenaRuntime runtime = this.arenaManager.runtimeForPlayer(player);
        if (runtime == null)
            return;

        this.winnersByMapId
                .computeIfAbsent(runtime.mapId(), (ignored) -> ConcurrentHashMap.newKeySet())
                .add(uniqueId);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArenaUserLeft(
            @NotNull ArenaUserLeftEvent event
    ) {
        if (event.getArena().getGameState() != GameState.ENDING)
            return;

        String mapId = this.resolveMapId(event.getArena());
        if (mapId == null)
            return;

        Set<UUID> winners = this.winnersByMapId.getOrDefault(mapId, Set.of());
        UUID uniqueId = event.getUser().getUniqueId();
        if (winners.contains(uniqueId))
            return;

        this.playerStatisticsService.incrementLosses(uniqueId);
    }

    @EventHandler(priority = MONITOR)
    public void onGameStateChange(
            @NotNull ArenaGameStateChangeEvent event
    ) {
        if (event.getGameState() != GameState.WAITING)
            return;

        String mapId = this.resolveMapId(event.getArena());
        if (mapId == null)
            return;

        this.winnersByMapId.remove(mapId);
    }

    private String resolveMapId(
            @NotNull IArena arena
    ) {
        return this.arenaManager.runtimes().stream()
                .filter((runtime) -> runtime.arena() == arena)
                .map(ArenaManager.ArenaRuntime::mapId)
                .findFirst()
                .orElse(null);
    }
}
