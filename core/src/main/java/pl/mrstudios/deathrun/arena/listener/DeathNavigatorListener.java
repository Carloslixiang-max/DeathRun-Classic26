package pl.mrstudios.deathrun.arena.listener;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Server;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.api.arena.user.IUser;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.classic.death.DeathNavigatorService;
import pl.mrstudios.deathrun.classic.playtest.PlaytestTraceService;
import pl.mrstudios.deathrun.classic.trap.TrapActivationService;
import pl.mrstudios.deathrun.config.Configuration;

import static pl.mrstudios.deathrun.api.arena.enums.GameState.PLAYING;
import static pl.mrstudios.deathrun.api.arena.user.enums.Role.DEATH;

public final class DeathNavigatorListener implements Listener {

    private final ArenaManager arenaManager;
    private final DeathNavigatorService navigator;
    private final TrapActivationService activationService;
    private final PlaytestTraceService trace;

    @Inject
    public DeathNavigatorListener(
            @NotNull ArenaManager arenaManager,
            @NotNull Plugin plugin,
            @NotNull Server server,
            @NotNull Configuration configuration,
            @NotNull PlaytestTraceService trace
    ) {
        this.arenaManager = arenaManager;
        this.navigator = new DeathNavigatorService(plugin);
        this.activationService = new TrapActivationService(plugin, server, configuration);
        this.trace = trace;
    }

    @EventHandler
    public void onInteract(@NotNull PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        ArenaManager.ArenaRuntime runtime = this.arenaManager.runtimeForPlayer(event.getPlayer());
        if (runtime == null || runtime.arena().getGameState() != PLAYING)
            return;

        IUser user = runtime.arena().getUser(event.getPlayer());
        if (user == null || user.getRole() != DEATH)
            return;

        DeathNavigatorService.Action action = this.navigator.actionOf(event.getItem());
        if (action == null)
            return;

        event.setCancelled(true);
        switch (action) {
            case PREVIOUS -> {
                this.navigator.previous(event.getPlayer(), runtime);
                this.showSelection(event, runtime);
                this.trace.record(runtime.mapId(), "DEATH_NAV",
                        "player=" + event.getPlayer().getName()
                                + " action=PREVIOUS selected=" + (this.navigator.selectedIndex(event.getPlayer(), runtime) + 1));
            }
            case NEXT -> {
                this.navigator.next(event.getPlayer(), runtime);
                this.showSelection(event, runtime);
                this.trace.record(runtime.mapId(), "DEATH_NAV",
                        "player=" + event.getPlayer().getName()
                                + " action=NEXT selected=" + (this.navigator.selectedIndex(event.getPlayer(), runtime) + 1));
            }
            case JUMP -> {
                boolean jumped = this.navigator.jump(event.getPlayer(), runtime);
                this.showSelection(event, runtime);
                this.trace.record(runtime.mapId(), "DEATH_NAV",
                        "player=" + event.getPlayer().getName()
                                + " action=JUMP success=" + jumped
                                + " selected=" + (this.navigator.selectedIndex(event.getPlayer(), runtime) + 1));
                if (!jumped)
                    event.getPlayer().sendActionBar(MiniMessage.miniMessage().deserialize("<red>Trap Jumper unavailable"));
            }
            case ACTIVATE -> {
                int index = this.navigator.selectedIndex(event.getPlayer(), runtime);
                if (index < 0) {
                    event.getPlayer().sendActionBar(MiniMessage.miniMessage().deserialize("<red>No trap selected"));
                    return;
                }

                TrapActivationService.ActivationResult result = this.activationService.activate(
                        event.getPlayer(),
                        runtime,
                        index
                );
                this.trace.record(runtime.mapId(), "DEATH_NAV",
                        "player=" + event.getPlayer().getName()
                                + " action=ACTIVATE result=" + result
                                + " selected=" + (index + 1));

                switch (result) {
                    case ACTIVATED -> event.getPlayer().sendActionBar(MiniMessage.miniMessage().deserialize(
                            "<green>Activated</green> <gray>·</gray> <white>"
                                    + this.navigator.selectionLabel(event.getPlayer(), runtime)
                                    + "</white>"
                    ));
                    case COOLDOWN -> {
                        long seconds = (this.activationService.cooldownRemainingMillis(runtime.mapId(), index) + 999L) / 1000L;
                        event.getPlayer().sendActionBar(MiniMessage.miniMessage().deserialize(
                                "<red>Trap cooling down</red> <gray>·</gray> <white>" + seconds + "s</white>"
                        ));
                    }
                    case FAILED -> event.getPlayer().sendActionBar(MiniMessage.miniMessage().deserialize(
                            "<red>Trap failed safely. Check server log.</red>"
                    ));
                    default -> event.getPlayer().sendActionBar(MiniMessage.miniMessage().deserialize(
                            "<red>Trap unavailable: " + result.name() + "</red>"
                    ));
                }
            }
        }
    }

    private void showSelection(@NotNull PlayerInteractEvent event, @NotNull ArenaManager.ArenaRuntime runtime) {
        String label = this.navigator.selectionLabel(event.getPlayer(), runtime)
                .replace("<", "")
                .replace(">", "");
        event.getPlayer().sendActionBar(MiniMessage.miniMessage().deserialize(
                "<aqua>" + label + "</aqua>"
        ));
    }
}
