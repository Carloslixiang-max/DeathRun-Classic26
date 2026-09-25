package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.arena.ArenaManager;

import static org.bukkit.event.EventPriority.MONITOR;

/** Main-server-safe join listener: it never clears or rewrites unrelated player state. */
public class ArenaPlayerJoinListener implements Listener {

    private final ArenaManager arenaManager;

    @Inject
    public ArenaPlayerJoinListener(@NotNull ArenaManager arenaManager) {
        this.arenaManager = arenaManager;
    }

    @EventHandler(priority = MONITOR)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        if (this.arenaManager.hasPendingSnapshot(event.getPlayer())) {
            if (this.arenaManager.restorePendingSnapshot(event.getPlayer()))
                event.getPlayer().sendMessage(org.bukkit.ChatColor.GREEN + "[DeathRun] Your saved pre-game state was recovered.");
            else
                event.getPlayer().sendMessage(org.bukkit.ChatColor.RED + "[DeathRun] Recovery is still pending. Use /dr recover after the saved world is available.");
            return;
        }

        this.arenaManager.recoverPlayerToHubIfNeeded(event.getPlayer(), true);
    }
}
