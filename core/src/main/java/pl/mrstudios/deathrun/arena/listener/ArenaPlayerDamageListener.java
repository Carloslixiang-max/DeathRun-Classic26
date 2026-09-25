package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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
import pl.mrstudios.deathrun.classic.trap.TrapActivationContext;
import pl.mrstudios.deathrun.classic.trap.TrapActivationService;
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
    private final TrapActivationService trapActivationService;

    @Inject
    public ArenaPlayerDamageListener(
            @NotNull ArenaManager arenaManager,
            @NotNull Plugin plugin,
            @NotNull Server server,
            @NotNull Configuration configuration,
            @NotNull WinMapManager winMapManager
    ) {
        this.arenaManager = arenaManager;
        this.configuration = configuration;
        this.deathService = new DeathRunDeathService(arenaManager, plugin, server, configuration, winMapManager);
        this.trapActivationService = new TrapActivationService(plugin, server, configuration);
    }

    @EventHandler(priority = MONITOR)
    public void onDamage(@NotNull EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player))
            return;
        ArenaManager.ArenaRuntime runtime = this.arenaManager.runtimeForPlayer(player);
        if (runtime == null) {
            if (event instanceof EntityDamageByEntityEvent damageByEntity) {
                var damager = damageByEntity.getDamager();
                if (pl.mrstudios.deathrun.classic.trap.DeathRunEntityTags.isTrapProjectile(damager)
                        || pl.mrstudios.deathrun.classic.trap.DeathRunEntityTags.isTrapExplosive(damager))
                    event.setCancelled(true);
            }
            return;
        }
        Arena arena = runtime.arena();

        if (event.getCause() == FIRE || event.getCause() == FIRE_TICK)
            event.setCancelled(true);
        if (event.getCause() == FALL && player.getFallDistance() <= this.configuration.plugin().arenaMaxFallDistance)
            event.setCancelled(true);
        if (event.getCause() == ENTITY_ATTACK || event.getCause() == ENTITY_SWEEP_ATTACK)
            event.setCancelled(true);

        if (event.getCause() == PROJECTILE) {
            if (!(event instanceof EntityDamageByEntityEvent damageByEntity)
                    || !(damageByEntity.getDamager() instanceof Projectile projectile)
                    || !pl.mrstudios.deathrun.classic.trap.DeathRunEntityTags.isTrapProjectile(projectile)) {
                event.setCancelled(true);
            } else {
                TrapActivationContext attribution = this.trapActivationService.attributionForProjectile(projectile.getLocation());
                if (attribution == null || !runtime.mapId().equalsIgnoreCase(attribution.mapId())) {
                    event.setCancelled(true);
                } else {
                    this.trapActivationService.markTrapContact(player, attribution);
                }
            }
        }

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
        if (!pl.mrstudios.deathrun.classic.trap.DeathRunEntityTags.isTrapExplosive(event.getEntity()))
            return;

        TrapActivationContext attribution = this.trapActivationService.attributionForExplosion(event.getLocation());
        if (attribution == null)
            return;

        event.getLocation().getNearbyEntitiesByType(Player.class, 3f).forEach(player -> {
            ArenaManager.ArenaRuntime runtime = this.arenaManager.runtimeForPlayer(player);
            if (runtime == null
                    || runtime.arena().getGameState() != PLAYING
                    || !runtime.mapId().equalsIgnoreCase(attribution.mapId()))
                return;

            this.trapActivationService.markTrapContact(player, attribution);
            this.deathService.killRunner(player, DeathRunDeathCause.TRAP);
        });
    }

    @EventHandler(priority = MONITOR)
    public void onCombust(@NotNull EntityCombustEvent event) {
        if (event.getEntity() instanceof Player player && this.arenaManager.runtimeForPlayer(player) != null)
            event.setCancelled(true);
    }
}
