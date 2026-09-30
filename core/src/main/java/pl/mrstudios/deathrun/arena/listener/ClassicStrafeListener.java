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
import pl.mrstudios.deathrun.classic.playtest.PlaytestTraceService;

import static pl.mrstudios.deathrun.api.arena.enums.GameState.PLAYING;
import static pl.mrstudios.deathrun.api.arena.user.enums.Role.RUNNER;

public final class ClassicStrafeListener implements Listener {

    private final ArenaManager arenaManager;
    private final ClassicStrafeService strafeService;
    private final PlaytestTraceService trace;

    @Inject
    public ClassicStrafeListener(@NotNull ArenaManager arenaManager, @NotNull Plugin plugin, @NotNull PlaytestTraceService trace) {
        this.arenaManager = arenaManager;
        this.strafeService = new ClassicStrafeService(plugin);
        this.trace = trace;
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
        long sampleStarted = System.currentTimeMillis();
        long beforeLeft = this.strafeService.remainingMillis(player, ClassicStrafeService.Direction.LEFT);
        long beforeBack = this.strafeService.remainingMillis(player, ClassicStrafeService.Direction.BACK);
        long beforeRight = this.strafeService.remainingMillis(player, ClassicStrafeService.Direction.RIGHT);
        boolean activated = this.strafeService.activate(player, direction);
        if (activated) {
            var velocity = player.getVelocity();
            long afterLeft = this.strafeService.remainingMillis(player, ClassicStrafeService.Direction.LEFT);
            long afterBack = this.strafeService.remainingMillis(player, ClassicStrafeService.Direction.BACK);
            long afterRight = this.strafeService.remainingMillis(player, ClassicStrafeService.Direction.RIGHT);
            long sampleElapsedMillis = Math.max(0, System.currentTimeMillis() - sampleStarted);
            this.trace.record(runtime.mapId(), "STRAFE", "player=" + player.getName()
                    + " direction=" + direction.name() + " cooldown=60"
                    + " horizontal=" + Math.hypot(velocity.getX(), velocity.getZ())
                    + " vertical=" + velocity.getY()
                    + " beforeLeft=" + beforeLeft + " beforeBack=" + beforeBack + " beforeRight=" + beforeRight
                    + " afterLeft=" + afterLeft + " afterBack=" + afterBack + " afterRight=" + afterRight
                    + " sampleElapsedMillis=" + sampleElapsedMillis);
            player.sendActionBar(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                    "<aqua>" + direction.label() + "</aqua> <gray>used · <white>60s</white>"
            ));
            return;
        }

        long remainingMillis = this.strafeService.remainingMillis(player, direction);
        this.trace.record(runtime.mapId(), "STRAFE_BLOCKED", "player=" + player.getName()
                + " direction=" + direction.name() + " remainingMillis=" + remainingMillis);
        long seconds = (remainingMillis + 999L) / 1000L;
        player.sendActionBar(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                "<red>" + direction.label() + "</red> <gray>ready in <white>" + seconds + "s</white>"
        ));
    }
}
