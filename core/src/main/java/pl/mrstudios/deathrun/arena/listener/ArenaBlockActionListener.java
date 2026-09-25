package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.arena.ArenaManager;

import static org.bukkit.GameMode.CREATIVE;

import static java.util.Arrays.stream;
import static org.bukkit.Material.*;
import static org.bukkit.event.EventPriority.MONITOR;
import static org.bukkit.event.block.Action.PHYSICAL;
import static org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK;

public class ArenaBlockActionListener implements Listener {

    private static final String BUILD_BYPASS_PERMISSION = "deathrun.build.bypass";

    private final ArenaManager arenaManager;

    @Inject
    public ArenaBlockActionListener(
            @NotNull ArenaManager arenaManager
    ) {
        this.arenaManager = arenaManager;
    }

    @EventHandler(priority = MONITOR)
    public void onBlockBreak(
            @NotNull BlockBreakEvent event
    ) {
        if (this.shouldRestrict(event.getPlayer()))
            event.setCancelled(true);
    }

    @EventHandler(priority = MONITOR)
    public void onBlockPlace(
            @NotNull BlockPlaceEvent event
    ) {
        if (this.shouldRestrict(event.getPlayer()))
            event.setCancelled(true);
    }

    @EventHandler(priority = MONITOR)
    public void onPlayerInteract(
            @NotNull PlayerInteractEvent event
    ) {
        if (!this.shouldRestrict(event.getPlayer()))
            return;

        if (event.getAction() == RIGHT_CLICK_BLOCK)
            if (event.getClickedBlock() != null)
                if (stream(containerMaterials).anyMatch((material) -> material == event.getClickedBlock().getType()))
                    event.setCancelled(true);

        if (event.getAction() == PHYSICAL)
            event.setCancelled(true);

    }

    private boolean shouldRestrict(
            @NotNull Player player
    ) {
        if (player.isOp() || player.hasPermission(BUILD_BYPASS_PERMISSION) || player.getGameMode() == CREATIVE)
            return false;

        return this.arenaManager.runtimeForPlayer(player) != null;
    }

    protected static final Material[] containerMaterials = {
            CHEST, DISPENSER, DROPPER, FURNACE,
            HOPPER, BREWING_STAND, BEACON, ANVIL,
            CHIPPED_ANVIL, DAMAGED_ANVIL, ENCHANTING_TABLE,
            ENDER_CHEST, BARREL, BLAST_FURNACE, SMOKER
    };

}
