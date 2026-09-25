package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.api.arena.user.IUser;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.config.Configuration;

import static org.bukkit.event.EventPriority.MONITOR;
import static pl.mrstudios.deathrun.api.arena.enums.GameState.PLAYING;
import static pl.mrstudios.deathrun.api.arena.user.enums.Role.RUNNER;

public class ArenaBlockEffectListener implements Listener {

    private final ArenaManager arenaManager;
    private final Configuration configuration;

    @Inject
    public ArenaBlockEffectListener(
            @NotNull ArenaManager arenaManager,
            @NotNull Configuration configuration
    ) {
        this.arenaManager = arenaManager;
        this.configuration = configuration;
    }

    @EventHandler(priority = MONITOR)
    public void onStepOnBlockEffect(@NotNull PlayerMoveEvent event) {
        if (event.getTo() == null)
            return;

        var runtime = this.arenaManager.runtimeForPlayer(event.getPlayer());
        if (runtime == null || runtime.arena().getGameState() != PLAYING)
            return;

        IUser user = runtime.arena().getUser(event.getPlayer());
        if (user == null || user.getRole() != RUNNER || user.isEliminated())
            return;

        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ())
            return;

        this.configuration.plugin().blockEffects.stream()
                .filter(effect -> effect.blockType() == event.getTo().clone().add(0, -1, 0).getBlock().getType())
                .findFirst()
                .ifPresent(effect -> event.getPlayer().addPotionEffect(
                        new PotionEffect(
                                effect.effectType(),
                                (int) (20 * effect.duration()),
                                effect.amplifier(),
                                false,
                                false,
                                true
                        )
                ));
    }
}
