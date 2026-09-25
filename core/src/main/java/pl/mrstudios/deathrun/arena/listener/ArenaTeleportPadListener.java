package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.config.impl.MapConfiguration;

import static java.util.Arrays.stream;
import static org.bukkit.Material.*;
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

        if (stream(pressurePlates).noneMatch((material) -> material == event.getClickedBlock().getType()))
            return;

        MapConfiguration.MapDefinition map = this.arenaManager.mapForPlayer(event.getPlayer());
        if (map == null)
            return;

        map.teleportPads
                .stream().filter(
                        (teleportPad) -> teleportPad.padLocation().getBlockX() == event.getClickedBlock().getX()
                                && teleportPad.padLocation().getBlockY() == event.getClickedBlock().getY()
                                && teleportPad.padLocation().getBlockZ() == event.getClickedBlock().getZ()
                ).findFirst().ifPresent(
                        (teleportPad) -> event.getPlayer().teleport(teleportPad.teleportLocation())
                );

    }

    protected static final Material[] pressurePlates = {
            POLISHED_BLACKSTONE_PRESSURE_PLATE, ACACIA_PRESSURE_PLATE,
            BAMBOO_PRESSURE_PLATE,
            BIRCH_PRESSURE_PLATE, CRIMSON_PRESSURE_PLATE,
            CHERRY_PRESSURE_PLATE,
            DARK_OAK_PRESSURE_PLATE, HEAVY_WEIGHTED_PRESSURE_PLATE,
            JUNGLE_PRESSURE_PLATE, LIGHT_WEIGHTED_PRESSURE_PLATE,
            MANGROVE_PRESSURE_PLATE,
            OAK_PRESSURE_PLATE, SPRUCE_PRESSURE_PLATE,
            STONE_PRESSURE_PLATE, WARPED_PRESSURE_PLATE
    };

}
