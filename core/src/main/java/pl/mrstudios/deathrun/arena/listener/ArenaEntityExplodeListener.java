package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.arena.ArenaManager;

public class ArenaEntityExplodeListener implements Listener {

    private final ArenaManager arenaManager;

    @Inject
    public ArenaEntityExplodeListener(@NotNull ArenaManager arenaManager) {
        this.arenaManager = arenaManager;
    }

    @EventHandler
    public void onEntityExplode(@NotNull EntityExplodeEvent event) {
        if (!this.arenaManager.isDeathRunWorld(event.getLocation().getWorld()))
            return;
        if (!pl.mrstudios.deathrun.classic.trap.DeathRunEntityTags.isTrapExplosive(event.getEntity()))
            return;
        event.blockList().clear();
    }
}
