package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.config.Configuration;
import pl.mrstudios.deathrun.player.RecoveryCommands;

import static org.bukkit.event.EventPriority.HIGHEST;

/** Pending recovery owns the inventory even after the player has left the arena. */
public final class PlayerRecoveryGuardListener implements Listener {
    private final ArenaManager manager;
    private final Configuration configuration;

    @Inject
    public PlayerRecoveryGuardListener(ArenaManager manager, Configuration configuration) {
        this.manager = manager;
        this.configuration = configuration;
    }

    @EventHandler(priority = HIGHEST) public void click(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && manager.isPendingStateProtected(p)) e.setCancelled(true);
    }
    @EventHandler(priority = HIGHEST) public void drag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && manager.isPendingStateProtected(p)) e.setCancelled(true);
    }
    @EventHandler(priority = HIGHEST) public void open(InventoryOpenEvent e) {
        if (e.getPlayer() instanceof Player p && manager.isPendingStateProtected(p)) {
            boolean queueMenu = manager.isQueuedSnapshot(p) && !manager.isRecoveryBlocked(p)
                    && (pl.mrstudios.deathrun.classic.vote.ClassicVoteService.isClassicVoteInventory(e.getInventory())
                    || pl.mrstudios.deathrun.arena.selector.MapSelectorService.isMapSelectorInventory(e.getInventory()));
            if (!queueMenu) e.setCancelled(true);
        }
    }
    @EventHandler(priority = HIGHEST) public void drop(PlayerDropItemEvent e) {
        if (manager.isPendingStateProtected(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(priority = HIGHEST) public void pickup(PlayerAttemptPickupItemEvent e) {
        if (manager.isPendingStateProtected(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(priority = HIGHEST) public void arrow(PlayerPickupArrowEvent e) {
        if (manager.isPendingStateProtected(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(priority = HIGHEST) public void swap(PlayerSwapHandItemsEvent e) {
        if (manager.isPendingStateProtected(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(priority = HIGHEST) public void interact(PlayerInteractEvent e) {
        if (manager.isPendingStateProtected(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(priority = HIGHEST) public void entity(PlayerInteractEntityEvent e) {
        if (manager.isPendingStateProtected(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(priority = HIGHEST) public void entityAt(PlayerInteractAtEntityEvent e) { entity(e); }
    @EventHandler(priority = HIGHEST) public void stand(PlayerArmorStandManipulateEvent e) {
        if (manager.isPendingStateProtected(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(priority = HIGHEST) public void creative(org.bukkit.event.inventory.InventoryCreativeEvent e) {
        click(e);
    }
    @EventHandler(priority = HIGHEST) public void breakBlock(BlockBreakEvent e) {
        if (manager.isPendingStateProtected(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(priority = HIGHEST) public void placeBlock(BlockPlaceEvent e) {
        if (manager.isPendingStateProtected(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(priority = HIGHEST) public void damage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && manager.isPendingStateProtected(p)) e.setCancelled(true);
        if (e instanceof org.bukkit.event.entity.EntityDamageByEntityEvent hit
                && hit.getDamager() instanceof Player p && manager.isPendingStateProtected(p)) e.setCancelled(true);
    }
    @EventHandler(priority = HIGHEST) public void death(PlayerDeathEvent e) {
        if (!manager.isPendingStateProtected(e.getEntity())) return;
        e.getDrops().clear();
        e.setKeepInventory(true);
        e.setKeepLevel(true);
        e.setDroppedExp(0);
    }
    @EventHandler(priority = HIGHEST) public void command(PlayerCommandPreprocessEvent e) {
        if (!manager.isPendingStateProtected(e.getPlayer())) return;
        boolean blocked = manager.isRecoveryBlocked(e.getPlayer());
        if (blocked ? RecoveryCommands.allowed(e.getMessage()) : RecoveryCommands.allowedWhileQueued(e.getMessage())) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize(blocked ? configuration.language().commandMessageRecoveryLocked
                        : configuration.language().chatMessageQueueProtected));
    }
    @EventHandler(priority = HIGHEST, ignoreCancelled = true) public void teleport(PlayerTeleportEvent e) {
        if (manager.isRecoveryBlocked(e.getPlayer()) && !manager.isSnapshotRestoring(e.getPlayer()))
            e.setCancelled(true);
    }
    @EventHandler(priority = HIGHEST, ignoreCancelled = true) public void portal(PlayerPortalEvent e) {
        if (manager.isQueuedSnapshot(e.getPlayer())) e.setCancelled(true);
        else teleport(e);
    }
    @EventHandler(priority = HIGHEST, ignoreCancelled = true) public void move(PlayerMoveEvent e) {
        if (e instanceof PlayerTeleportEvent || e.getTo() == null
                || !manager.isRecoveryBlocked(e.getPlayer()) || manager.isSnapshotRestoring(e.getPlayer())) return;
        if (e.getFrom().getX() != e.getTo().getX() || e.getFrom().getY() != e.getTo().getY()
                || e.getFrom().getZ() != e.getTo().getZ()) e.setCancelled(true);
    }
}
