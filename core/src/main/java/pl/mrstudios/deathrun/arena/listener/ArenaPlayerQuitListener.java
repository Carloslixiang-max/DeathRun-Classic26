package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.arena.ArenaManager;

import static org.bukkit.event.EventPriority.MONITOR;

public class ArenaPlayerQuitListener implements Listener {

    private final ArenaManager arenaManager;

    @Inject
    public ArenaPlayerQuitListener(@NotNull ArenaManager arenaManager) {
        this.arenaManager = arenaManager;
    }

    @EventHandler(priority = MONITOR)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        this.arenaManager.leaveQueue(event.getPlayer());
        if (this.arenaManager.runtimeForPlayer(event.getPlayer()) != null)
            this.arenaManager.leaveCurrentMap(event.getPlayer(), true);
    }
}
