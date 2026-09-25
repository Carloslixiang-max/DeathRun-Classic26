package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.api.arena.user.IUser;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.classic.strafe.ClassicStrafeService;

import static pl.mrstudios.deathrun.api.arena.enums.GameState.PLAYING;
import static pl.mrstudios.deathrun.api.arena.user.enums.Role.RUNNER;

public final class ClassicStrafeListener implements Listener {

    private final ArenaManager arenaManager;
    private final ClassicStrafeService strafeService;

    @Inject
    public ClassicStrafeListener(@NotNull ArenaManager arenaManager, @NotNull Plugin plugin) {
        this.arenaManager = arenaManager;
        this.strafeService = new ClassicStrafeService(plugin);
    }

    @EventHandler
    public void onInteract(@NotNull PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        Player player = event.getPlayer();
        ArenaManager.ArenaRuntime runtime = this.arenaManager.runtimeForPlayer(player);
        if (runtime == null || runtime.arena().getGameState() != PLAYING)
            return;
        IUser user = runtime.arena().getUser(player);
        if (user == null || user.getRole() != RUNNER || user.isEliminated())
            return;
        ClassicStrafeService.Direction direction = this.strafeService.directionOf(event.getItem());
        if (direction == null)
            return;
        event.setCancelled(true);
        boolean activated = this.strafeService.activate(player, direction);
        if (activated) {
            player.sendActionBar(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                    "<aqua>" + direction.label() + "</aqua> <gray>used · <white>60s</white>"
            ));
            return;
        }

        long seconds = (this.strafeService.remainingMillis(player, direction) + 999L) / 1000L;
        player.sendActionBar(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                "<red>" + direction.label() + "</red> <gray>ready in <white>" + seconds + "s</white>"
        ));
    }
}
