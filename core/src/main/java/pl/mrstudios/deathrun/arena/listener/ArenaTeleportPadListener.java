package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.config.impl.MapConfiguration;

import static org.bukkit.event.EventPriority.MONITOR;
import static org.bukkit.event.block.Action.PHYSICAL;

public class ArenaTeleportPadListener implements Listener {

    private final ArenaManager arenaManager;

    @Inject
    public ArenaTeleportPadListener(
            @NotNull ArenaManager arenaManager
    ) {
        this.arenaManager = arenaManager;
    }

    @EventHandler(priority = MONITOR)
    public void onPlayerEnterPlate(
            @NotNull PlayerInteractEvent event
    ) {

        if (event.getAction() != PHYSICAL)
            return;

        if (event.getClickedBlock() == null)
            return;

        if (!event.getClickedBlock().getType().name().endsWith("_PRESSURE_PLATE"))
            return;

        MapConfiguration.MapDefinition map = this.arenaManager.mapForPlayer(event.getPlayer());
        if (map == null)
            return;

        map.teleportPads
                .stream()
                .filter(teleportPad -> teleportPad.padLocation() != null
                        && teleportPad.padLocation().getWorld() != null
                        && teleportPad.teleportLocation() != null
                        && teleportPad.teleportLocation().getWorld() != null)
                .filter(teleportPad -> teleportPad.padLocation().getWorld().getUID()
                        .equals(event.getClickedBlock().getWorld().getUID()))
                .filter(teleportPad -> teleportPad.padLocation().getBlockX() == event.getClickedBlock().getX()
                        && teleportPad.padLocation().getBlockY() == event.getClickedBlock().getY()
                        && teleportPad.padLocation().getBlockZ() == event.getClickedBlock().getZ())
                .findFirst()
                .ifPresent(teleportPad -> event.getPlayer().teleport(teleportPad.teleportLocation()));

    }

}
