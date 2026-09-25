package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.arena.sign.SignManager;

import static org.bukkit.event.EventPriority.MONITOR;

public class ArenaSignCreateListener implements Listener {

    private final Plugin plugin;
    private final SignManager signManager;

    @Inject
    public ArenaSignCreateListener(
            @NotNull Plugin plugin,
            @NotNull SignManager signManager
    ) {
        this.plugin = plugin;
        this.signManager = signManager;
    }

    @EventHandler(priority = MONITOR)
    public void onSignChange(
            @NotNull SignChangeEvent event
    ) {
        if (!"[dr]".equalsIgnoreCase(this.normalize(event.getLine(0))))
            return;

        if (!event.getPlayer().hasPermission("deathrun.signs.create")) {
            event.getPlayer().sendMessage(ChatColor.RED + "You don't have permission to create DeathRun signs.");
            return;
        }

        String action = this.normalize(event.getLine(1));
        switch (action.toLowerCase()) {

            case "join" -> {
                String mapId = this.normalize(event.getLine(2));
                if (mapId.isBlank() || !this.signManager.mapExists(mapId)) {
                    event.getPlayer().sendMessage(ChatColor.RED + "Invalid map id for join sign.");
                    return;
                }

                this.signManager.createJoinSign(event.getBlock().getLocation(), mapId);
                event.getPlayer().sendMessage(ChatColor.GREEN + "Created DeathRun join sign for map: " + mapId);
            }

            case "autojoin" -> {
                this.signManager.createAutoJoinSign(event.getBlock().getLocation());
                event.getPlayer().sendMessage(ChatColor.GREEN + "Created DeathRun auto-join sign.");
            }

            case "leave" -> {
                this.signManager.createLeaveSign(event.getBlock().getLocation());
                event.getPlayer().sendMessage(ChatColor.GREEN + "Created DeathRun leave sign.");
            }

            default -> {
                event.getPlayer().sendMessage(ChatColor.RED + "Invalid DeathRun sign action. Use join, autojoin or leave.");
                return;
            }

        }

        this.plugin.getServer().getScheduler().runTask(this.plugin, this.signManager::updateAllSigns);
    }

    private @NotNull String normalize(
            String content
    ) {
        if (content == null)
            return "";

        String stripped = ChatColor.stripColor(content);
        return stripped == null ? "" : stripped.trim();
    }

}
