package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.Server;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.api.arena.user.IUser;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.classic.trap.TrapActivationService;
import pl.mrstudios.deathrun.config.Configuration;

import static org.bukkit.event.EventPriority.MONITOR;
import static org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK;
import static pl.mrstudios.deathrun.api.arena.user.enums.Role.DEATH;

public class ArenaButtonClickListener implements Listener {

    private final ArenaManager arenaManager;
    private final Configuration configuration;
    private final TrapActivationService activationService;

    @Inject
    public ArenaButtonClickListener(
            @NotNull ArenaManager arenaManager,
            @NotNull Plugin plugin,
            @NotNull Server server,
            @NotNull Configuration configuration
    ) {
        this.arenaManager = arenaManager;
        this.configuration = configuration;
        this.activationService = new TrapActivationService(plugin, server, configuration);
    }

    @EventHandler(priority = MONITOR)
    public void onArenaButtonClick(@NotNull PlayerInteractEvent event) {
        if (event.isCancelled())
            return;
        if (event.getClickedBlock() == null || event.getAction() != RIGHT_CLICK_BLOCK)
            return;
        if (!event.getClickedBlock().getType().name().endsWith("_BUTTON"))
            return;

        ArenaManager.ArenaRuntime runtime = this.arenaManager.runtimeForPlayer(event.getPlayer());
        if (runtime == null)
            return;
        IUser user = runtime.arena().getUser(event.getPlayer());
        if (user == null || user.getRole() != DEATH)
            return;

        this.arenaManager.ensureMapWorldBindings(runtime.map());
        int trapIndex = -1;
        for (int i = 0; i < runtime.map().arenaTraps.size(); i++) {
            var button = runtime.map().arenaTraps.get(i).getButton();
            if (button.getWorld() == null || !button.getWorld().getUID().equals(event.getClickedBlock().getWorld().getUID()))
                continue;
            if (button.getBlockX() == event.getClickedBlock().getX()
                    && button.getBlockY() == event.getClickedBlock().getY()
                    && button.getBlockZ() == event.getClickedBlock().getZ()) {
                trapIndex = i;
                break;
            }
        }
        if (trapIndex < 0)
            return;

        event.setCancelled(true);
        TrapActivationService.ActivationResult result = this.activationService.activate(event.getPlayer(), runtime, trapIndex);
        if (result == TrapActivationService.ActivationResult.COOLDOWN)
            event.getPlayer().playSound(event.getPlayer().getLocation(), this.configuration.plugin().arenaSoundTrapDelay, 1.0f, 1.0f);
    }


}
