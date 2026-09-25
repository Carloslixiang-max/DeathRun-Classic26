package pl.mrstudios.deathrun.classic.death;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pl.mrstudios.deathrun.api.arena.event.user.UserArenaDeathEvent;
import pl.mrstudios.deathrun.api.arena.user.IUser;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.arena.win.WinMapManager;
import pl.mrstudios.deathrun.classic.trap.TrapActivationContext;
import pl.mrstudios.deathrun.classic.trap.TrapActivationService;
import pl.mrstudios.deathrun.config.Configuration;
import pl.mrstudios.deathrun.plugin.Entrypoint;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static net.kyori.adventure.text.minimessage.MiniMessage.miniMessage;
import static net.kyori.adventure.title.Title.Times.times;
import static net.kyori.adventure.title.Title.title;
import static org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE;
import static pl.mrstudios.deathrun.api.arena.enums.GameState.PLAYING;
import static pl.mrstudios.deathrun.api.arena.user.enums.Role.RUNNER;

public final class DeathRunDeathService {

    private static final PotionEffect FIRE_RESISTANCE_EFFECT = new PotionEffect(FIRE_RESISTANCE, 20, 1, false, false, false);
    private static final long DEATH_DEBOUNCE_MILLIS = 750L;
    private static final Map<UUID, Long> LAST_DEATH_AT = new ConcurrentHashMap<>();

    private final ArenaManager arenaManager;
    private final Plugin plugin;
    private final Server server;
    private final Configuration configuration;
    private final WinMapManager winMapManager;
    private final TrapActivationService trapActivationService;

    public DeathRunDeathService(
            @NotNull ArenaManager arenaManager,
            @NotNull Plugin plugin,
            @NotNull Server server,
            @NotNull Configuration configuration,
            @NotNull WinMapManager winMapManager
    ) {
        this.arenaManager = arenaManager;
        this.plugin = plugin;
        this.server = server;
        this.configuration = configuration;
        this.winMapManager = winMapManager;
        this.trapActivationService = new TrapActivationService(plugin, server, configuration);
    }

    public @Nullable DeathResult killRunner(@NotNull Player player, @NotNull DeathRunDeathCause cause) {
        ArenaManager.ArenaRuntime runtime = this.arenaManager.runtimeForPlayer(player);
        if (runtime == null || runtime.arena().getGameState() != PLAYING)
            return null;
        IUser user = runtime.arena().getUser(player);
        if (user == null || user.getRole() != RUNNER || user.isEliminated())
            return null;
        if (user.getCheckpoint() == null)
            return null;

        long now = System.currentTimeMillis();
        long lastDeath = LAST_DEATH_AT.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastDeath < DEATH_DEBOUNCE_MILLIS)
            return null;
        LAST_DEATH_AT.put(player.getUniqueId(), now);

        TrapActivationContext attribution = this.trapActivationService.recentAttribution(player.getUniqueId());
        user.setDeaths(user.getDeaths() + 1);
        user.setLives(user.getLives() - 1);
        if (user.getLives() <= 0) {
            user.setLives(0);
            user.setEliminated(true);
            if (this.configuration.plugin().classicZeroLivesSpectator) {
                user.setRole(pl.mrstudios.deathrun.api.arena.user.enums.Role.SPECTATOR);
                player.setAllowFlight(true);
                player.setFlying(true);
                player.getInventory().clear();

                ItemStack leave = new ItemStack(Material.RED_BED);
                ItemMeta leaveMeta = leave.getItemMeta();
                leaveMeta.displayName(miniMessage().deserialize(this.configuration.language().arenaItemLeaveName));
                leave.setItemMeta(leaveMeta);
                player.getInventory().setItem(8, leave);

                runtime.arena().getUsers().stream()
                        .map(IUser::asBukkit)
                        .filter(java.util.Objects::nonNull)
                        .filter(other -> other != player)
                        .forEach(other -> other.hidePlayer(this.plugin, player));

                this.configuration.language().chatMessageGameEndSpectator.stream()
                        .map(miniMessage()::deserialize)
                        .forEach(player::sendMessage);
            }
        }

        player.teleport(this.respawnLocation(user));
        player.playSound(player.getLocation(), this.configuration.plugin().arenaSoundPlayerDeath, 1.0f, 1.0f);
        player.addPotionEffect(FIRE_RESISTANCE_EFFECT);
        player.setFireTicks(0);

        BufferedImage parchmentImage = this.plugin instanceof Entrypoint entrypoint
                ? entrypoint.getLoseParchmentImage()
                : null;
        this.winMapManager.giveLoseMap(player, parchmentImage, user.getDeaths());
        this.server.getPluginManager().callEvent(new UserArenaDeathEvent(user, runtime.arena()));
        player.showTitle(title(
                miniMessage().deserialize(this.configuration.language().arenaDeathTitle),
                miniMessage().deserialize(this.configuration.language().arenaDeathSubtitle),
                times(ofMillis(250), ofSeconds(2), ofMillis(250))
        ));

        return new DeathResult(cause, attribution, user.getLives(), user.isEliminated());
    }

    private org.bukkit.Location respawnLocation(IUser user) {
        var checkpoint = user.getCheckpoint();
        List<org.bukkit.Location> area = checkpoint.locations();
        if (area.isEmpty() || area.get(0).getWorld() == null)
            return checkpoint.spawn();
        int minX = area.stream().mapToInt(org.bukkit.Location::getBlockX).min().orElse(checkpoint.spawn().getBlockX());
        int maxX = area.stream().mapToInt(org.bukkit.Location::getBlockX).max().orElse(checkpoint.spawn().getBlockX());
        int minY = area.stream().mapToInt(org.bukkit.Location::getBlockY).min().orElse(checkpoint.spawn().getBlockY());
        int maxY = area.stream().mapToInt(org.bukkit.Location::getBlockY).max().orElse(checkpoint.spawn().getBlockY());
        int minZ = area.stream().mapToInt(org.bukkit.Location::getBlockZ).min().orElse(checkpoint.spawn().getBlockZ());
        int maxZ = area.stream().mapToInt(org.bukkit.Location::getBlockZ).max().orElse(checkpoint.spawn().getBlockZ());
        org.bukkit.Location safe = new org.bukkit.Location(
                area.get(0).getWorld(),
                (minX + maxX) / 2.0 + 0.5,
                Math.max(minY + 1, maxY + 1),
                (minZ + maxZ) / 2.0 + 0.5
        );
        safe.setYaw(checkpoint.spawn().getYaw());
        safe.setPitch(checkpoint.spawn().getPitch());
        return safe;
    }

    public record DeathResult(
            DeathRunDeathCause cause,
            @Nullable TrapActivationContext trapAttribution,
            int remainingLives,
            boolean eliminated
    ) {}
}
