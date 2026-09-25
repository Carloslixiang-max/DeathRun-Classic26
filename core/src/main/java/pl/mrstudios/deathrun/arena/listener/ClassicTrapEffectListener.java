package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.api.arena.trap.ITrap;
import pl.mrstudios.deathrun.api.arena.user.IUser;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.arena.trap.impl.*;
import pl.mrstudios.deathrun.arena.win.WinMapManager;
import pl.mrstudios.deathrun.classic.death.DeathRunDeathCause;
import pl.mrstudios.deathrun.classic.death.DeathRunDeathService;
import pl.mrstudios.deathrun.classic.trap.TrapActivationContext;
import pl.mrstudios.deathrun.classic.trap.TrapActivationService;
import pl.mrstudios.deathrun.config.Configuration;

import static pl.mrstudios.deathrun.api.arena.enums.GameState.PLAYING;
import static pl.mrstudios.deathrun.api.arena.user.enums.Role.RUNNER;

public final class ClassicTrapEffectListener implements Listener {

    private final ArenaManager arenaManager;
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
        this.activationService = new TrapActivationService(plugin, server, configuration);
        this.deathService = new DeathRunDeathService(arenaManager, plugin, server, configuration, winMapManager);
    }

    @EventHandler
    public void onMove(@NotNull PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        Player player = event.getPlayer();
        ArenaManager.ArenaRuntime runtime = this.arenaManager.runtimeForPlayer(player);
        if (runtime == null || runtime.arena().getGameState() != PLAYING) return;

        IUser user = runtime.arena().getUser(player);
        if (user == null || user.getRole() != RUNNER || user.isEliminated()) return;

        for (TrapActivationContext context : this.activationService.activeAttributions().values()) {
            if (!context.mapId().equalsIgnoreCase(runtime.mapId())) continue;
            if (!this.inside(context.trap(), event.getTo())) continue;

            boolean firstContact = context.victims().add(player.getUniqueId());
            this.activationService.markTrapContact(player, context.mapId(), context.trapIndex());

            if (context.trap() instanceof TrapFireFloor || context.trap() instanceof TrapFireTrail) {
                this.deathService.killRunner(player, DeathRunDeathCause.TRAP);
                return;
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

            if (context.trap() instanceof TrapGiant) {
                this.deathService.killRunner(player, DeathRunDeathCause.TRAP);
                return;
            }
        }
    }

    private boolean inside(@NotNull ITrap trap, @NotNull Location location) {
        if (trap.getLocations().isEmpty() || location.getWorld() == null) return false;
        Location first = trap.getLocations().get(0);
        if (first == null || first.getWorld() == null || !first.getWorld().getUID().equals(location.getWorld().getUID()))
            return false;

        int minX = trap.getLocations().stream().filter(java.util.Objects::nonNull).mapToInt(Location::getBlockX).min().orElse(0);
        int maxX = trap.getLocations().stream().filter(java.util.Objects::nonNull).mapToInt(Location::getBlockX).max().orElse(0);
        int minY = trap.getLocations().stream().filter(java.util.Objects::nonNull).mapToInt(Location::getBlockY).min().orElse(0);
        int maxY = trap.getLocations().stream().filter(java.util.Objects::nonNull).mapToInt(Location::getBlockY).max().orElse(0);
        int minZ = trap.getLocations().stream().filter(java.util.Objects::nonNull).mapToInt(Location::getBlockZ).min().orElse(0);
        int maxZ = trap.getLocations().stream().filter(java.util.Objects::nonNull).mapToInt(Location::getBlockZ).max().orElse(0);

        return location.getX() >= minX - 0.5 && location.getX() <= maxX + 1.5
                && location.getY() >= minY - 1.0 && location.getY() <= maxY + 3.0
                && location.getZ() >= minZ - 0.5 && location.getZ() <= maxZ + 1.5;
    }
}
