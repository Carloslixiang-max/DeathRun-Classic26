package pl.mrstudios.deathrun.arena.listener;

import io.papermc.paper.event.player.PlayerOpenSignEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.arena.sign.SignManager;
import pl.mrstudios.deathrun.arena.sign.SignManager.QueueSign;

import static org.bukkit.event.EventPriority.HIGHEST;

public class ArenaSignInteractListener implements Listener {

    private static final String SIGN_USE_PERMISSION = "deathrun.signs.use";
    private static final String LEGACY_SIGN_USE_PERMISSION = "deathrun.sign.use";

    private final ArenaManager arenaManager;
    private final SignManager signManager;

    @Inject
    public ArenaSignInteractListener(
            @NotNull ArenaManager arenaManager,
            @NotNull SignManager signManager
    ) {
        this.arenaManager = arenaManager;
        this.signManager = signManager;
    }

    @EventHandler(priority = HIGHEST)
    public void onPlayerOpenSign(
            @NotNull PlayerOpenSignEvent event
    ) {
        if (!(event.getSign().getLocation().getBlock().getState() instanceof Sign))
            return;

        QueueSign queueSign = this.signManager.signAt(event.getSign().getLocation().getBlock());
        if (queueSign == null)
            return;

        if (!event.getPlayer().hasPermission(SIGN_USE_PERMISSION)
            && !event.getPlayer().hasPermission(LEGACY_SIGN_USE_PERMISSION)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You don't have permission to use DeathRun signs.");
            return;
        }

        event.setCancelled(true);

        switch (queueSign.type()) {

            case JOIN -> {
                if (queueSign.mapId() != null && this.arenaManager.isMapLockedForEditing(queueSign.mapId())) {
                    event.getPlayer().sendMessage(ChatColor.RED + "This map is currently unavailable as it is being edited.");
                    return;
                }

                if (queueSign.mapId() == null || queueSign.mapId().isBlank()) {
                    event.getPlayer().sendMessage(ChatColor.RED + "This map is currently not joinable.");
                    return;
                }

                String consoleCommand = "dr join " + event.getPlayer().getName() + " " + queueSign.mapId();
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), consoleCommand);
            }

            case AUTOJOIN -> {
                String bestMapId = this.signManager.bestJoinableMap().orElse(null);
                if (bestMapId == null) {
                    event.getPlayer().sendMessage(ChatColor.RED + "No map available for auto-join.");
                    return;
                }

                String consoleCommand = "dr join " + event.getPlayer().getName() + " " + bestMapId;
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), consoleCommand);
            }

            case LEAVE -> {
                boolean leftQueue = this.signManager.leaveQueue(event.getPlayer());
                boolean leftMap = this.arenaManager.leaveCurrentMap(event.getPlayer(), true);
                if (!leftQueue && !leftMap) {
                    event.getPlayer().sendMessage(ChatColor.GRAY + "You're not in any DeathRun queue.");
                    return;
                }

                this.arenaManager.returnPlayerToHub(event.getPlayer());
                event.getPlayer().sendMessage(ChatColor.YELLOW + "You have left the match and returned to the Hub.");
            }

        }
    }

}
