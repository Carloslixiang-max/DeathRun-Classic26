package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.arena.ArenaManager;

public class ArenaFoodLevelListener implements Listener {

    private final ArenaManager arenaManager;

    @Inject
    public ArenaFoodLevelListener(
            @NotNull ArenaManager arenaManager
    ) {
        this.arenaManager = arenaManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFoodLevelChange(
            @NotNull FoodLevelChangeEvent event
    ) {
        if (!(event.getEntity() instanceof Player player))
            return;

        if (this.arenaManager.runtimeForPlayer(player) == null)
            return;

        event.setCancelled(true);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
    }

}