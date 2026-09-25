package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.classic.vote.ClassicVoteService;

public final class ClassicVoteListener implements Listener {

    private final ClassicVoteService voteService;

    @Inject
    public ClassicVoteListener(@NotNull ClassicVoteService voteService) {
        this.voteService = voteService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(@NotNull InventoryClickEvent event) {
        if (!this.voteService.isVoteInventory(event.getView().getTitle()))
            return;

        event.setCancelled(true);
        if (event.getWhoClicked() instanceof org.bukkit.entity.Player player)
            this.voteService.handleClick(player, event.getCurrentItem());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrag(@NotNull InventoryDragEvent event) {
        if (this.voteService.isVoteInventory(event.getView().getTitle()))
            event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        this.voteService.leave(event.getPlayer(), false);
    }
}
