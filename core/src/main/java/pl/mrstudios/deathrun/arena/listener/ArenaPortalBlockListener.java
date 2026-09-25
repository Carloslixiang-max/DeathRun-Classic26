package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.arena.ArenaManager;

public class ArenaPortalBlockListener implements Listener {

    private final ArenaManager arenaManager;

    @Inject
    public ArenaPortalBlockListener(
            @NotNull ArenaManager arenaManager
    ) {
        this.arenaManager = arenaManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPortal(
            @NotNull PlayerPortalEvent event
    ) {
        Player player = event.getPlayer();
        if (this.arenaManager.runtimeForPlayer(player) == null)
            return;

        event.setCancelled(true);
    }

}