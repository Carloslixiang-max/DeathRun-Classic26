package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.arena.ArenaManager;

import static org.bukkit.event.EventPriority.MONITOR;
import static org.bukkit.event.inventory.InventoryType.PLAYER;
public class ArenaInventoryActionListener implements Listener {

    private static final String INVENTORY_BYPASS_PERMISSION = "deathrun.inventory.bypass";

    private final ArenaManager arenaManager;
    private final Plugin plugin;

    @Inject
    public ArenaInventoryActionListener(
            @NotNull ArenaManager arenaManager,
            @NotNull Plugin plugin
    ) {
        this.arenaManager = arenaManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = MONITOR)
    public void onInventoryClick(
            @NotNull InventoryClickEvent event
    ) {
        if (this.hasInventoryBypass(event.getWhoClicked()))
            return;

        if (event.getClickedInventory() == null)
            return;

        if (event.getClickedInventory().getType() != PLAYER)
            return;

        event.setCancelled(true);

    }

    @EventHandler(priority = MONITOR)
    public void onCreativeInventory(
            @NotNull InventoryCreativeEvent event
    ) {
        if (this.hasInventoryBypass(event.getWhoClicked()))
            return;

        event.setCancelled(true);
    }

    @EventHandler(priority = MONITOR)
    public void onItemDrop(
            @NotNull PlayerDropItemEvent event
    ) {
        if (this.hasInventoryBypass(event.getPlayer()))
            return;

        // Paper 1.20.2+ also fires PlayerDropItemEvent on death.
        // Do not cancel in that case, otherwise death drops are swallowed.
        if (event.getPlayer().isDead())
            return;

        var runtime = this.arenaManager.runtimeForPlayer(event.getPlayer());
        if (runtime == null)
            return;

        event.setCancelled(true);

        this.plugin.getLogger().info("[DR-DBG] Prevented item drop for "
            + event.getPlayer().getName()
            + " item=" + event.getItemDrop().getItemStack().getType());
    }

    @EventHandler(priority = MONITOR)
    public void onPlayerItemSwap(
            @NotNull PlayerSwapHandItemsEvent event
    ) {
        if (this.hasInventoryBypass(event.getPlayer()))
            return;

        event.setCancelled(true);
    }

    @EventHandler(priority = MONITOR)
    public void onPlayerArrowPickup(
            @NotNull PlayerPickupArrowEvent event
    ) {
        if (this.hasInventoryBypass(event.getPlayer()))
            return;

        event.setCancelled(true);
    }

    @EventHandler(priority = MONITOR)
    public void onPlayerItemPickup(
            @NotNull PlayerAttemptPickupItemEvent event
    ) {
        if (this.hasInventoryBypass(event.getPlayer()))
            return;

        event.setCancelled(true);
    }

    private boolean hasInventoryBypass(
            @NotNull org.bukkit.command.CommandSender commandSender
    ) {
        return commandSender.isOp() || commandSender.hasPermission(INVENTORY_BYPASS_PERMISSION);
    }

}
