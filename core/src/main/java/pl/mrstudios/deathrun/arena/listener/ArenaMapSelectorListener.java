package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.arena.selector.MapSelectorService;

public class ArenaMapSelectorListener implements Listener {

    private final MapSelectorService mapSelectorService;

    @Inject
    public ArenaMapSelectorListener(
            @NotNull MapSelectorService mapSelectorService
    ) {
        this.mapSelectorService = mapSelectorService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(
            @NotNull InventoryClickEvent event
    ) {
        if (!this.mapSelectorService.isSelectorInventory(event.getView().getTitle()))
            return;

        event.setCancelled(true);
        if (event.getWhoClicked() instanceof org.bukkit.entity.Player player)
            this.mapSelectorService.handleClick(player, event.getCurrentItem());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryDrag(
            @NotNull InventoryDragEvent event
    ) {
        if (!this.mapSelectorService.isSelectorInventory(event.getView().getTitle()))
            return;

        event.setCancelled(true);
    }

}
