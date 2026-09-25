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
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.api.arena.event.user.UserArenaDeathEvent;
import pl.mrstudios.deathrun.api.arena.user.IUser;
import pl.mrstudios.deathrun.arena.Arena;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.arena.win.WinMapManager;
import pl.mrstudios.deathrun.config.Configuration;
import pl.mrstudios.deathrun.plugin.Entrypoint;

import java.awt.image.BufferedImage;
import java.util.List;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static java.util.Optional.ofNullable;
import static net.kyori.adventure.text.minimessage.MiniMessage.miniMessage;
import static net.kyori.adventure.title.Title.Times.times;
import static net.kyori.adventure.title.Title.title;
import static org.bukkit.Material.LAVA;
import static org.bukkit.Material.WATER;
import static org.bukkit.event.EventPriority.MONITOR;
import static org.bukkit.event.entity.EntityDamageEvent.DamageCause.*;
import static org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE;
import static pl.mrstudios.deathrun.api.arena.enums.GameState.PLAYING;
import static pl.mrstudios.deathrun.api.arena.user.enums.Role.RUNNER;

public class ArenaPlayerDamageListener implements Listener {

    private final ArenaManager arenaManager;
    private final Plugin plugin;
    private final Server server;
    private final BukkitAudiences audiences;
    private final Configuration configuration;
    private final WinMapManager winMapManager;

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
        this.plugin = plugin;
        this.server = server;
        this.audiences = audiences;
        this.configuration = configuration;
        this.winMapManager = winMapManager;
    }

    @EventHandler(priority = MONITOR)
    public void onDamage(
            @NotNull EntityDamageEvent event
    ) {

        if (!(event.getEntity() instanceof Player player))
            return;

        if (event.getCause() == FIRE || event.getCause() == FIRE_TICK)
            event.setCancelled(true);

        if (event.getCause() == FALL && player.getFallDistance() <= this.configuration.plugin().arenaMaxFallDistance)
            event.setCancelled(true);

        if (event.getCause() == ENTITY_ATTACK || event.getCause() == ENTITY_SWEEP_ATTACK)
            event.setCancelled(true);

        Arena arena = this.arenaManager.arenaForPlayer(player);
        if (arena == null) {
            event.setCancelled(true);
            return;
        }

        if (arena.getGameState() != PLAYING)
            event.setCancelled(true);

        if (event.isCancelled())
            return;

        ofNullable(arena.getUser(player))
            .filter((user) -> user.getRole() == RUNNER)
            .ifPresent((user) -> this.callPlayerDeath(user, player, arena));
        event.setCancelled(true);

    }

    @EventHandler(priority = MONITOR)
    public void onPlayerMove(
            @NotNull PlayerMoveEvent event
    ) {

        if (
                event.getFrom().getBlockX() == event.getTo().getBlockX()
                        && event.getFrom().getBlockY() == event.getTo().getBlockY()
                        && event.getFrom().getBlockZ() == event.getTo().getBlockZ()
                        && event.getFrom().getPitch() != event.getTo().getPitch()
                        && event.getFrom().getYaw() != event.getTo().getYaw()
        ) return;

        Arena arena = this.arenaManager.arenaForPlayer(event.getPlayer());
        if (arena == null || arena.getGameState() != PLAYING)
            return;

        if (event.getTo().getBlock().getType() != WATER && event.getTo().getBlock().getType() != LAVA)
            return;

        ofNullable(arena.getUser(event.getPlayer()))
                .filter((user) -> user.getRole() == RUNNER)
            .ifPresent((user) -> this.callPlayerDeath(user, event.getPlayer(), arena));

    }

    @EventHandler(priority = MONITOR)
    public void onEntityExplode(
            @NotNull EntityExplodeEvent event
    ) {

        event.getLocation().getNearbyEntitiesByType(Player.class, 3f)
                .forEach((player) -> {
                    Arena arena = this.arenaManager.arenaForPlayer(player);
                    if (arena != null && arena.getGameState() == PLAYING)
                        player.damage(1);
                });

    }

    @EventHandler(priority = MONITOR)
    public void onCombust(
            @NotNull EntityCombustEvent event
    ) {
        event.setCancelled(true);
    }

    protected void callPlayerDeath(
            @NotNull IUser user,
            @NotNull Player player,
            @NotNull Arena arena
    ) {

        user.setDeaths(user.getDeaths() + 1);
        player.teleport(this.respawnLocation(user.getCheckpoint()));
        player.playSound(player.getLocation(), this.configuration.plugin().arenaSoundPlayerDeath, 1.0f, 1.0f);
        player.addPotionEffect(FIRE_RESISTANCE_EFFECT);
        player.setFireTicks(0);

        BufferedImage parchmentImage = this.plugin instanceof Entrypoint entrypoint
            ? entrypoint.getLoseParchmentImage()
            : null;
        this.plugin.getLogger().info("[DR-DBG] Runner died: player=" + player.getName()
            + " map=" + arena.getName()
            + " deaths=" + user.getDeaths()
            + " parchmentLoaded=" + (parchmentImage != null));
        this.winMapManager.giveLoseMap(player, parchmentImage, user.getDeaths());

        this.server.getPluginManager().callEvent(new UserArenaDeathEvent(user, arena));
        this.audiences.player(player).showTitle(
                title(
                        miniMessage().deserialize(this.configuration.language().arenaDeathTitle),
                        miniMessage().deserialize(this.configuration.language().arenaDeathSubtitle),
                        times(ofMillis(250), ofSeconds(2), ofMillis(250))
                )
        );

    }

    protected static final PotionEffect FIRE_RESISTANCE_EFFECT = new PotionEffect(FIRE_RESISTANCE, 20, 1, false, false, false);

    private @NotNull org.bukkit.Location respawnLocation(
            @NotNull pl.mrstudios.deathrun.api.arena.checkpoint.ICheckpoint checkpoint
    ) {
        List<org.bukkit.Location> area = checkpoint.locations();
        if (area.isEmpty() || area.get(0).getWorld() == null)
            return checkpoint.spawn();

        int minX = area.stream().mapToInt(org.bukkit.Location::getBlockX).min().orElse(checkpoint.spawn().getBlockX());
        int maxX = area.stream().mapToInt(org.bukkit.Location::getBlockX).max().orElse(checkpoint.spawn().getBlockX());
        int minY = area.stream().mapToInt(org.bukkit.Location::getBlockY).min().orElse(checkpoint.spawn().getBlockY());
        int maxY = area.stream().mapToInt(org.bukkit.Location::getBlockY).max().orElse(checkpoint.spawn().getBlockY());
        int minZ = area.stream().mapToInt(org.bukkit.Location::getBlockZ).min().orElse(checkpoint.spawn().getBlockZ());
        int maxZ = area.stream().mapToInt(org.bukkit.Location::getBlockZ).max().orElse(checkpoint.spawn().getBlockZ());

        double centerX = (minX + maxX) / 2.0 + 0.5;
        double centerY = Math.max(minY + 1, maxY + 1);
        double centerZ = (minZ + maxZ) / 2.0 + 0.5;

        org.bukkit.Location safe = new org.bukkit.Location(area.get(0).getWorld(), centerX, centerY, centerZ);
        safe.setYaw(checkpoint.spawn().getYaw());
        safe.setPitch(checkpoint.spawn().getPitch());
        return safe;
    }

}
