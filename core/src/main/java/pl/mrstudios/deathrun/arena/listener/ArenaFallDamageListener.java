package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.arena.ArenaManager;

import static org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL;
import static pl.mrstudios.deathrun.api.arena.enums.GameState.PLAYING;

public class ArenaFallDamageListener implements Listener {

    private final ArenaManager arenaManager;

    @Inject
    public ArenaFallDamageListener(
            @NotNull ArenaManager arenaManager
    ) {
        this.arenaManager = arenaManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFallDamage(
            @NotNull EntityDamageEvent event
    ) {
        if (event.getCause() != FALL)
            return;

        if (!(event.getEntity() instanceof Player player))
            return;

        ArenaManager.ArenaRuntime runtime = this.arenaManager.runtimeForPlayer(player);
        if (runtime == null)
            return;

        if (runtime.arena().getGameState() != PLAYING)
            return;

        event.setCancelled(true);
    }
}
