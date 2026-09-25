package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.arena.ArenaManager;

import static org.bukkit.event.EventPriority.MONITOR;

public class ArenaWorldListener implements Listener {

    private final ArenaManager arenaManager;

    @Inject
    public ArenaWorldListener(@NotNull ArenaManager arenaManager) {
        this.arenaManager = arenaManager;
    }

    @EventHandler(priority = MONITOR)
    public void onArenaWorldLoad(@NotNull WorldLoadEvent event) {
        if (this.arenaManager.isDeathRunWorld(event.getWorld()))
            event.getWorld().setAutoSave(false);
    }

    @EventHandler(priority = MONITOR)
    public void onWorldUnload(@NotNull WorldUnloadEvent event) {
        if (this.arenaManager.isDeathRunWorld(event.getWorld()))
            event.getWorld().setAutoSave(false);
    }

    @EventHandler(priority = MONITOR)
    public void onWorldSave(@NotNull WorldSaveEvent event) {
        if (this.arenaManager.isDeathRunWorld(event.getWorld()))
            event.getWorld().setAutoSave(false);
    }
}
