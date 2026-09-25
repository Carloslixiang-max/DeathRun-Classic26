package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.arena.sign.SignManager;

import static org.bukkit.event.EventPriority.HIGHEST;

public class ArenaSignBreakListener implements Listener {

    private final SignManager signManager;

    @Inject
    public ArenaSignBreakListener(
            @NotNull SignManager signManager
    ) {
        this.signManager = signManager;
    }

    @EventHandler(priority = HIGHEST)
    public void onBlockBreak(
            @NotNull BlockBreakEvent event
    ) {
        if (this.signManager.signAt(event.getBlock()) == null)
            return;

        if (!event.getPlayer().hasPermission("deathrun.signs.break")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You don't have permission to break DeathRun signs.");
            return;
        }

        this.signManager.removeSign(event.getBlock().getLocation());
        event.getPlayer().sendMessage(ChatColor.GREEN + "DeathRun sign removed.");
    }

}
