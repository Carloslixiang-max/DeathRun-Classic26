package pl.mrstudios.deathrun.arena.listener;

import io.papermc.paper.event.player.PlayerOpenSignEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.arena.sign.SignManager;
import pl.mrstudios.deathrun.arena.sign.SignManager.QueueSign;

import static org.bukkit.event.EventPriority.HIGHEST;
import static org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK;

public class ArenaSignInteractListener implements Listener {

    private static final String SIGN_USE_PERMISSION = "deathrun.signs.use";
    private static final String LEGACY_SIGN_USE_PERMISSION = "deathrun.sign.use";

    private final pl.mrstudios.deathrun.config.Configuration configuration;
    private final ArenaManager arenaManager;
    private final SignManager signManager;

    @Inject
    public ArenaSignInteractListener(
            @NotNull ArenaManager arenaManager,
            @NotNull SignManager signManager,
            @NotNull pl.mrstudios.deathrun.config.Configuration configuration
    ) {
        this.arenaManager = arenaManager;
        this.configuration = configuration;
        this.signManager = signManager;
    }

    @EventHandler(priority = HIGHEST)
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        if (event.getAction() != RIGHT_CLICK_BLOCK || event.getClickedBlock() == null)
            return;
        if (!(event.getClickedBlock().getState() instanceof Sign))
            return;

        QueueSign queueSign = this.signManager.signAt(event.getClickedBlock());
        if (queueSign == null)
            return;

        event.setCancelled(true);
        this.handle(event.getPlayer(), queueSign);
    }

    @EventHandler(priority = HIGHEST)
    public void onPlayerOpenSign(@NotNull PlayerOpenSignEvent event) {
        QueueSign queueSign = this.signManager.signAt(event.getSign().getLocation().getBlock());
        if (queueSign != null)
            event.setCancelled(true);
    }

    private void handle(@NotNull Player player, @NotNull QueueSign queueSign) {
        if (!player.hasPermission(SIGN_USE_PERMISSION)
                && !player.hasPermission(LEGACY_SIGN_USE_PERMISSION)) {
            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(this.configuration.language().chatMessageSignNoPermission));
            return;
        }

        switch (queueSign.type()) {
            case JOIN -> {
                if (queueSign.mapId() != null && this.arenaManager.isMapLockedForEditing(queueSign.mapId())) {
                    player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(this.configuration.language().mapSelectorMapEditing));
                    return;
                }

                if (queueSign.mapId() == null || queueSign.mapId().isBlank()) {
                    player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(this.configuration.language().mapSelectorMapNotReady));
                    return;
                }

                Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(),
                        "dr join " + player.getName() + " " + queueSign.mapId()
                );
            }

            case AUTOJOIN -> {
                String bestMapId = this.signManager.bestJoinableMap().orElse(null);
                if (bestMapId == null) {
                    player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(this.configuration.language().chatMessageAutoJoinUnavailable));
                    return;
                }

                Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(),
                        "dr join " + player.getName() + " " + bestMapId
                );
            }

            case LEAVE -> {
                boolean leftQueue = this.signManager.leaveQueue(player);
                boolean leftMap = this.arenaManager.leaveCurrentMap(player, true);
                if (!leftQueue && !leftMap) {
                    player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(this.configuration.language().chatMessageNoQueue));
                    return;
                }

                if (leftMap) {
                    if (this.arenaManager.hasPendingSnapshot(player))
                        player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(this.configuration.language().chatMessageLeavePending));
                    else
                        player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(this.configuration.language().chatMessageLeaveRestored));
                    return;
                }

                if (this.arenaManager.hasPendingSnapshot(player)) {
                    if (!this.arenaManager.restorePendingSnapshot(player)) {
                        player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(this.configuration.language().commandMessageRecoverFailed));
                        return;
                    }
                } else {
                    this.arenaManager.returnPlayerToHub(player);
                }
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(this.configuration.language().chatMessageLeaveQueue));
            }
        }
    }
}
