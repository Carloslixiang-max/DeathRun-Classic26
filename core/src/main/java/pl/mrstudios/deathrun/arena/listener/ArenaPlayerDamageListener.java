package pl.mrstudios.deathrun.arena.listener;

import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.arena.Arena;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.arena.win.WinMapManager;
import pl.mrstudios.deathrun.classic.death.DeathRunDeathCause;
import pl.mrstudios.deathrun.classic.death.DeathRunDeathService;
import pl.mrstudios.deathrun.config.Configuration;

import static org.bukkit.Material.LAVA;
import static org.bukkit.Material.WATER;
import static org.bukkit.event.EventPriority.MONITOR;
import static org.bukkit.event.entity.EntityDamageEvent.DamageCause.*;
import static pl.mrstudios.deathrun.api.arena.enums.GameState.PLAYING;

public class ArenaPlayerDamageListener implements Listener {

    private final ArenaManager arenaManager;
    private final Configuration configuration;
    private final DeathRunDeathService deathService;

    @Inject
    public ArenaPlayerDamageListener(
            @NotNull ArenaManager arenaManager,
            @NotNull Plugin plugin,
            @NotNull Server server,
            @NotNull BukkitAudiences audiences,
            @NotNull Configuration configuration,
            @NotNull WinMapManager winMapManager
    ) {
        this.arenaManager = arenaManager;
        this.configuration = configuration;
        this.deathService = new DeathRunDeathService(arenaManager, plugin, server, audiences, configuration, winMapManager);
    }

    @EventHandler(priority = MONITOR)
    public void onDamage(@NotNull EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player))
            return;
        Arena arena = this.arenaManager.arenaForPlayer(player);
        if (arena == null)
            return;

        if (event.getCause() == FIRE || event.getCause() == FIRE_TICK)
            event.setCancelled(true);
        if (event.getCause() == FALL && player.getFallDistance() <= this.configuration.plugin().arenaMaxFallDistance)
            event.setCancelled(true);
        if (event.getCause() == ENTITY_ATTACK || event.getCause() == ENTITY_SWEEP_ATTACK)
            event.setCancelled(true);
        if (arena.getGameState() != PLAYING)
            event.setCancelled(true);
        if (event.isCancelled())
            return;

        this.deathService.killRunner(player, switch (event.getCause()) {
            case FALL -> DeathRunDeathCause.FALL;
            case FIRE, FIRE_TICK, HOT_FLOOR -> DeathRunDeathCause.FIRE;
            case VOID -> DeathRunDeathCause.VOID;
            default -> DeathRunDeathCause.DIRECT_DAMAGE;
        });
        event.setCancelled(true);
    }

    @EventHandler(priority = MONITOR)
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        if (event.getTo() == null)
            return;
        Arena arena = this.arenaManager.arenaForPlayer(event.getPlayer());
        if (arena == null || arena.getGameState() != PLAYING)
            return;
        if (event.getTo().getBlock().getType() == WATER)
            this.deathService.killRunner(event.getPlayer(), DeathRunDeathCause.WATER);
        else if (event.getTo().getBlock().getType() == LAVA)
            this.deathService.killRunner(event.getPlayer(), DeathRunDeathCause.LAVA);
    }

    @EventHandler(priority = MONITOR)
    public void onEntityExplode(@NotNull EntityExplodeEvent event) {
        if (!this.arenaManager.isDeathRunWorld(event.getLocation().getWorld()))
            return;
        event.getLocation().getNearbyEntitiesByType(Player.class, 3f).forEach(player -> {
            Arena arena = this.arenaManager.arenaForPlayer(player);
            if (arena != null && arena.getGameState() == PLAYING)
                this.deathService.killRunner(player, DeathRunDeathCause.TRAP);
        });
    }

    @EventHandler(priority = MONITOR)
    public void onCombust(@NotNull EntityCombustEvent event) {
        if (event.getEntity() instanceof Player player && this.arenaManager.runtimeForPlayer(player) != null)
            event.setCancelled(true);
    }
}
