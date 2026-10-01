package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.api.arena.event.arena.ArenaTrapActivateEvent;
import pl.mrstudios.deathrun.api.arena.trap.ITrap;
import pl.mrstudios.deathrun.api.arena.user.IUser;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.arena.trap.impl.*;
import pl.mrstudios.deathrun.arena.win.WinMapManager;
import pl.mrstudios.deathrun.classic.checkpoint.SegmentAabb;
import pl.mrstudios.deathrun.classic.death.DeathRunDeathCause;
import pl.mrstudios.deathrun.classic.death.DeathRunDeathService;
import pl.mrstudios.deathrun.classic.trap.TrapActivationContext;
import pl.mrstudios.deathrun.classic.trap.TrapActivationService;
import pl.mrstudios.deathrun.config.Configuration;

import static pl.mrstudios.deathrun.api.arena.enums.GameState.PLAYING;
import static pl.mrstudios.deathrun.api.arena.user.enums.Role.RUNNER;

public final class ClassicTrapEffectListener implements Listener {

    private final pl.mrstudios.deathrun.classic.checkpoint.LocationBoundsCache bounds = new pl.mrstudios.deathrun.classic.checkpoint.LocationBoundsCache();
    private final ArenaManager arenaManager;
    private final Plugin plugin;
    private final TrapActivationService activationService;
    private final DeathRunDeathService deathService;

    @Inject
    public ClassicTrapEffectListener(
            @NotNull ArenaManager arenaManager,
            @NotNull Plugin plugin,
            @NotNull Server server,
            @NotNull Configuration configuration,
            @NotNull WinMapManager winMapManager
    ) {
        this.arenaManager = arenaManager;
        this.plugin = plugin;
        this.activationService = new TrapActivationService(plugin, server, configuration);
        this.deathService = new DeathRunDeathService(arenaManager, plugin, server, configuration, winMapManager);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(@NotNull PlayerMoveEvent event) {
        if (event instanceof org.bukkit.event.player.PlayerTeleportEvent || event.getTo() == null
                || (event.getFrom().getX() == event.getTo().getX() && event.getFrom().getY() == event.getTo().getY()
                && event.getFrom().getZ() == event.getTo().getZ()))
            return;

        Player player = event.getPlayer();
        ArenaManager.ArenaRuntime runtime = this.arenaManager.runtimeForPlayer(player);
        if (!this.isActiveRunner(player, runtime))
            return;

        this.activationService.visitActive(runtime.mapId(), context ->
                this.touches(context.trap(), event.getFrom(), event.getTo()) && this.applyContact(player, context));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrapActivated(@NotNull ArenaTrapActivateEvent event) {
        // The activation event is fired immediately before TrapActivationService
        // publishes the active context. Run one tick later so players already
        // standing still inside a trap are affected just like moving players.
        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            ArenaManager.ArenaRuntime runtime = this.arenaManager.runtimes().stream()
                    .filter(candidate -> candidate.arena() == event.getArena())
                    .findFirst()
                    .orElse(null);
            if (runtime == null || runtime.arena().getGameState() != PLAYING)
                return;

            TrapActivationContext context = this.activationService.activeForTrap(runtime.mapId(), event.getTrap());
            if (context == null)
                return;

            runtime.arena().getRunners().stream()
                    .map(IUser::asBukkit)
                    .filter(java.util.Objects::nonNull)
                    .filter(player -> this.isActiveRunner(player, runtime))
                    .filter(player -> this.inside(context.trap(), player.getLocation()))
                    .forEach(player -> this.applyContact(player, context));
        });
    }

    private boolean isActiveRunner(
            @NotNull Player player,
            ArenaManager.ArenaRuntime runtime
    ) {
        if (runtime == null || runtime.arena().getGameState() != PLAYING)
            return false;

        IUser user = runtime.arena().getUser(player);
        return user != null && user.getRole() == RUNNER && !user.isEliminated();
    }

    private boolean applyContact(
            @NotNull Player player,
            @NotNull TrapActivationContext context
    ) {
        boolean firstContact = context.victims().add(player.getUniqueId());
        this.activationService.markTrapContact(player, context.mapId(), context.trapIndex());

        if (context.trap() instanceof TrapFireFloor || context.trap() instanceof TrapFireTrail) {
            this.deathService.killRunner(player, DeathRunDeathCause.TRAP);
            return true;
        }

        if (context.trap() instanceof TrapQuicksand)
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 4, false, false, false));

        if (firstContact && context.trap() instanceof TrapLaunchPlayers) {
            Vector velocity = player.getVelocity().clone();
            velocity.setY(1.15);
            player.setVelocity(velocity);
        }

        if (firstContact && context.trap() instanceof TrapKnockBack) {
            Vector push = player.getLocation().toVector().subtract(context.trap().getButton().toVector()).setY(0);
            if (push.lengthSquared() < 0.0001)
                push = player.getLocation().getDirection().multiply(-1).setY(0);
            push.normalize().multiply(1.65).setY(0.45);
            player.setVelocity(push);
        }

        if (context.trap() instanceof TrapGiant || context.trap() instanceof TrapParticles) {
            this.deathService.killRunner(player, DeathRunDeathCause.TRAP);
            return true;
        }

        return false;
    }

    private boolean inside(@NotNull ITrap trap, @NotNull Location location) {
        return this.touches(trap, location, location);
    }

    private boolean touches(
            @NotNull ITrap trap,
            @NotNull Location from,
            @NotNull Location to
    ) {
        return this.bounds.touches(trap, trap.getLocations(), from, to);
    }
}
