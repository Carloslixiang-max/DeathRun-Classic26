package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import pl.mrstudios.deathrun.arena.world.ArenaMobProtection;
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
        if (this.arenaManager.isDeathRunWorld(event.getWorld())) {
            event.getWorld().setAutoSave(false);
            ArenaMobProtection.protect(event.getWorld());
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(@NotNull CreatureSpawnEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Mob
                && this.arenaManager.isDeathRunWorld(event.getLocation().getWorld())
                && ArenaMobProtection.blocks(event.getSpawnReason()))
            event.setCancelled(true);
    }

    @EventHandler(priority = MONITOR)
    public void onEntitiesLoad(@NotNull EntitiesLoadEvent event) {
        if (this.arenaManager.isDeathRunWorld(event.getWorld()))
            event.getEntities().forEach(ArenaMobProtection::removeAmbient);
    }

    @EventHandler(priority = MONITOR)
    public void onWorldUnload(@NotNull WorldUnloadEvent event) {
        if (this.arenaManager.isDeathRunWorld(event.getWorld()))
            event.getWorld().setAutoSave(true);
    }

    // Do not change auto-save from WorldSaveEvent. During plugin shutdown or a
    // runtime reload ArenaManager deliberately enables auto-save and calls
    // world.save(); flipping it back off from that save event would leave the
    // world owned by a disabled plugin.
}
