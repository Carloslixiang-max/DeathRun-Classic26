package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.arena.ArenaManager;

public class ArenaPlayerRespawnListener implements Listener {

    private final ArenaManager arenaManager;

    @Inject
    public ArenaPlayerRespawnListener(
            @NotNull ArenaManager arenaManager
    ) {
        this.arenaManager = arenaManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(
            @NotNull PlayerRespawnEvent event
    ) {
        if (!this.arenaManager.shouldReturnToHubOnJoinOrRespawn(event.getPlayer()))
            return;

        Location hub = this.arenaManager.resolveHubLocation();
        if (hub != null && hub.getWorld() != null)
            event.setRespawnLocation(hub);
    }
}
