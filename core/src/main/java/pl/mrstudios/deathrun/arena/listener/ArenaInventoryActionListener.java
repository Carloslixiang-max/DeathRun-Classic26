package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.entity.Player;
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
    public ArenaInventoryActionListener(@NotNull ArenaManager arenaManager, @NotNull Plugin plugin) {
        this.arenaManager = arenaManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = MONITOR)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;
        if (!this.inDeathRun(player) || this.hasInventoryBypass(player))
            return;
        if (event.getClickedInventory() == null || event.getClickedInventory().getType() != PLAYER)
            return;
        event.setCancelled(true);
    }

    @EventHandler(priority = MONITOR)
    public void onCreativeInventory(@NotNull InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;
        if (!this.inDeathRun(player) || this.hasInventoryBypass(player))
            return;
        event.setCancelled(true);
    }

    @EventHandler(priority = MONITOR)
    public void onItemDrop(@NotNull PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!this.inDeathRun(player) || this.hasInventoryBypass(player) || player.isDead())
            return;
        event.setCancelled(true);
        this.plugin.getLogger().fine("[DeathRun] Prevented arena item drop for " + player.getName());
    }

    @EventHandler(priority = MONITOR)
    public void onPlayerItemSwap(@NotNull PlayerSwapHandItemsEvent event) {
        if (!this.inDeathRun(event.getPlayer()) || this.hasInventoryBypass(event.getPlayer()))
            return;
        event.setCancelled(true);
    }

    @EventHandler(priority = MONITOR)
    public void onPlayerArrowPickup(@NotNull PlayerPickupArrowEvent event) {
        if (!this.inDeathRun(event.getPlayer()) || this.hasInventoryBypass(event.getPlayer()))
            return;
        event.setCancelled(true);
    }

    @EventHandler(priority = MONITOR)
    public void onPlayerItemPickup(@NotNull PlayerAttemptPickupItemEvent event) {
        if (!this.inDeathRun(event.getPlayer()) || this.hasInventoryBypass(event.getPlayer()))
            return;
        event.setCancelled(true);
    }

    private boolean inDeathRun(@NotNull Player player) {
        return this.arenaManager.runtimeForPlayer(player) != null;
    }

    private boolean hasInventoryBypass(@NotNull org.bukkit.command.CommandSender commandSender) {
        return commandSender.isOp() || commandSender.hasPermission(INVENTORY_BYPASS_PERMISSION);
    }
}
