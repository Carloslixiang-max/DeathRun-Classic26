package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.arena.ArenaManager;

public class ArenaPlayerRespawnListener implements Listener {

    private final ArenaManager arenaManager;
    private final Plugin plugin;

    @Inject
    public ArenaPlayerRespawnListener(
            @NotNull ArenaManager arenaManager,
            @NotNull Plugin plugin
    ) {
        this.arenaManager = arenaManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUnexpectedVanillaDeath(@NotNull PlayerDeathEvent event) {
        if (this.arenaManager.runtimeForPlayer(event.getEntity()) == null)
            return;

        // Normal Classic deaths are intercepted before vanilla death. If another
        // plugin or an unforeseen damage source still kills a participant, never
        // leak DeathRun hotbar items into the world.
        event.getDrops().clear();
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.setDroppedExp(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(
            @NotNull PlayerRespawnEvent event
    ) {
        if (this.arenaManager.runtimeForPlayer(event.getPlayer()) != null) {
            Location hub = this.arenaManager.resolveHubLocation();
            if (hub != null && hub.getWorld() != null)
                event.setRespawnLocation(hub);

            // Restore the durable pre-DeathRun snapshot after Bukkit has fully
            // completed respawn. This is a safety fallback, not normal life loss.
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                if (this.arenaManager.runtimeForPlayer(event.getPlayer()) != null) {
                    this.arenaManager.leaveCurrentMap(event.getPlayer(), true);
                    if (this.arenaManager.hasPendingSnapshot(event.getPlayer()))
                        event.getPlayer().sendMessage(org.bukkit.ChatColor.RED
                                + "[DeathRun] Unexpected vanilla death detected; recovery is still pending. Use /dr recover.");
                    else
                        event.getPlayer().sendMessage(org.bukkit.ChatColor.YELLOW
                                + "[DeathRun] Unexpected vanilla death detected; your pre-game state was restored.");
                }
            });
            return;
        }

        if (!this.arenaManager.shouldReturnToHubOnJoinOrRespawn(event.getPlayer()))
            return;

        Location hub = this.arenaManager.resolveHubLocation();
        if (hub != null && hub.getWorld() != null)
            event.setRespawnLocation(hub);
    }
}
