package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.arena.ArenaManager;

import static org.bukkit.event.EventPriority.MONITOR;

public class ArenaProjectileListener implements Listener {

    private final ArenaManager arenaManager;

    @Inject
    public ArenaProjectileListener(@NotNull ArenaManager arenaManager) {
        this.arenaManager = arenaManager;
    }

    @EventHandler(priority = MONITOR)
    public void onProjectileLand(@NotNull ProjectileHitEvent event) {
        if (!this.arenaManager.isDeathRunWorld(event.getEntity().getWorld()))
            return;
        event.getEntity().remove();
    }
}
