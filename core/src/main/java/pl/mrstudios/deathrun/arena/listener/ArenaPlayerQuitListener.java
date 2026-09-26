package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.classic.playtest.PlaytestTraceService;

import static org.bukkit.event.EventPriority.MONITOR;

public class ArenaPlayerQuitListener implements Listener {

    private final ArenaManager arenaManager;
    private final PlaytestTraceService trace;

    @Inject
    public ArenaPlayerQuitListener(
            @NotNull ArenaManager arenaManager,
            @NotNull PlaytestTraceService trace
    ) {
        this.arenaManager = arenaManager;
        this.trace = trace;
    }

    @EventHandler(priority = MONITOR)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        this.arenaManager.leaveQueue(event.getPlayer());
        ArenaManager.ArenaRuntime runtime = this.arenaManager.runtimeForPlayer(event.getPlayer());
        if (runtime != null) {
            this.trace.rememberDisconnect(event.getPlayer().getUniqueId(), runtime.mapId());
            this.trace.record(runtime.mapId(), "DISCONNECT",
                    "player=" + event.getPlayer().getName()
                            + " state=" + runtime.arena().getGameState()
                            + " snapshotPending=" + this.arenaManager.hasPendingSnapshot(event.getPlayer()));
            this.arenaManager.leaveCurrentMap(event.getPlayer(), true);
        }
    }
}
