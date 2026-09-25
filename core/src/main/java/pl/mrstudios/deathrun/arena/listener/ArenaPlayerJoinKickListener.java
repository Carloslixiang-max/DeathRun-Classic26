package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;

import static org.bukkit.event.EventPriority.MONITOR;
import static org.bukkit.event.player.PlayerLoginEvent.Result.KICK_FULL;

public class ArenaPlayerJoinKickListener implements Listener {

    @Inject
    public ArenaPlayerJoinKickListener() {}

    @EventHandler(priority = MONITOR)
    public void onPlayerKick(
            @NotNull PlayerLoginEvent event
    ) {

        if (event.getResult() == KICK_FULL)
            if (event.getPlayer().hasPermission("mrstudios.deathrun.admin"))
                event.allow();

    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = MONITOR)
    public void onPlayerLogin(
            @NotNull PlayerLoginEvent event
    ) {
        if (event.getPlayer().hasPermission("mrstudios.deathrun.admin"))
            event.allow();

    }

}
