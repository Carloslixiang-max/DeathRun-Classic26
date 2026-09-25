package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.arena.sign.SignManager;

import static org.bukkit.Material.AIR;
import static org.bukkit.event.block.Action.RIGHT_CLICK_AIR;
import static org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK;

public class ArenaClickItemListener implements Listener {

    private final ArenaManager arenaManager;
    private final SignManager signManager;

    @Inject
    public ArenaClickItemListener(
            @NotNull ArenaManager arenaManager,
            @NotNull SignManager signManager
    ) {
        this.arenaManager = arenaManager;
        this.signManager = signManager;
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onStepOnBlockEffect(
            @NotNull PlayerInteractEvent event
    ) {

        if (event.getAction() != RIGHT_CLICK_BLOCK && event.getAction() != RIGHT_CLICK_AIR)
            return;

        ItemStack usedItem = this.resolveUsedItem(event);
        if (usedItem == null || usedItem.getType() == AIR)
            return;

        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            ItemStack mainHand = event.getPlayer().getInventory().getItemInMainHand();
            if (mainHand != null && mainHand.getType() != AIR)
                return;
        }

        Material usedType = usedItem.getType();

        if (!usedType.name().endsWith("_BED"))
            return;

        event.setCancelled(true);

        boolean leftMap = this.arenaManager.leaveCurrentMap(event.getPlayer(), true);
        boolean leftQueue = this.signManager.leaveQueue(event.getPlayer());
        if (!leftMap && !leftQueue)
            return;

        this.arenaManager.returnPlayerToHub(event.getPlayer());
        event.getPlayer().sendMessage(org.bukkit.ChatColor.YELLOW + "You have left the match and returned to the Hub.");

    }

    private ItemStack resolveUsedItem(
            @NotNull PlayerInteractEvent event
    ) {
        if (event.getItem() != null)
            return event.getItem();

        if (event.getHand() == EquipmentSlot.OFF_HAND)
            return event.getPlayer().getInventory().getItemInOffHand();

        return event.getPlayer().getInventory().getItemInMainHand();
    }

}
