package pl.mrstudios.deathrun.classic.trap;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pl.mrstudios.deathrun.api.arena.event.arena.ArenaTrapActivateEvent;
import pl.mrstudios.deathrun.api.arena.trap.ITrap;
import pl.mrstudios.deathrun.api.arena.user.IUser;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.arena.trap.impl.TrapArrows;
import pl.mrstudios.deathrun.arena.trap.impl.TrapMinefield;
import pl.mrstudios.deathrun.arena.trap.impl.TrapTNT;
import pl.mrstudios.deathrun.config.Configuration;

import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import static org.bukkit.ChatColor.translateAlternateColorCodes;
import static pl.mrstudios.deathrun.api.arena.enums.GameState.PLAYING;
import static pl.mrstudios.deathrun.api.arena.user.enums.Role.DEATH;

public final class TrapActivationService {

    private static final long RECENT_CONTACT_MILLIS = 4_000L;
    private static final String HOLOGRAM_TAG = "deathrun_classic26_trap_hologram";
    private static final Map<TrapKey, Long> COOLDOWN_UNTIL = new ConcurrentHashMap<>();
    private static final Map<TrapKey, TrapActivationContext> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<TrapKey, ArmorStand> HOLOGRAMS = new ConcurrentHashMap<>();
    private static final Map<TrapKey, BukkitTask> END_TASKS = new ConcurrentHashMap<>();
    private static final Map<TrapKey, BukkitTask> HOLOGRAM_TASKS = new ConcurrentHashMap<>();
    private static final Map<UUID, RecentContact> RECENT_CONTACT = new ConcurrentHashMap<>();

    private final Plugin plugin;
    private final Server server;
    private final Configuration configuration;

    public TrapActivationService(@NotNull Plugin plugin, @NotNull Server server, @NotNull Configuration configuration) {
        this.plugin = plugin;
        this.server = server;
        this.configuration = configuration;
    }

    public ActivationResult activate(@NotNull Player death, @NotNull ArenaManager.ArenaRuntime runtime, int trapIndex) {
        if (runtime.arena().getGameState() != PLAYING)
            return ActivationResult.NOT_PLAYING;

        IUser user = runtime.arena().getUser(death);
        if (user == null || user.getRole() != DEATH)
            return ActivationResult.NOT_DEATH;
        if (trapIndex < 0 || trapIndex >= runtime.map().arenaTraps.size())
            return ActivationResult.INVALID_TRAP;

        TrapKey key = new TrapKey(runtime.mapId(), trapIndex);
        long now = System.currentTimeMillis();
        if (COOLDOWN_UNTIL.getOrDefault(key, 0L) > now)
            return ActivationResult.COOLDOWN;

        ITrap trap = runtime.map().arenaTraps.get(trapIndex);
        ArenaTrapActivateEvent event = new ArenaTrapActivateEvent(trap, runtime.arena());
        this.server.getPluginManager().callEvent(event);
        if (event.isCancelled())
            return ActivationResult.CANCELLED;

        long cooldownMillis = Duration.ofSeconds(Math.max(0, this.configuration.plugin().arenaTrapDelay)).toMillis();
        long durationMillis = Math.max(50L, trap.getDuration().toMillis());
        long cooldownEnd = now + cooldownMillis;
        long activeEnd = now + durationMillis;

        Set<UUID> victims = ConcurrentHashMap.newKeySet();
        TrapActivationContext context = new TrapActivationContext(
                runtime.mapId(),
                trapIndex,
                trap.getClass().getSimpleName(),
                death.getUniqueId(),
                now,
                activeEnd,
                trap,
                victims
        );

        ACTIVE.put(key, context);
        try {
            trap.start();
        } catch (Throwable throwable) {
            ACTIVE.remove(key, context);
            try {
                trap.end();
            } catch (Throwable ignored) {
                // Best-effort rollback.
            }
            this.plugin.getLogger().log(
                    Level.SEVERE,
                    "[DeathRun] Trap activation failed map=" + runtime.mapId()
                            + " trap=" + trapIndex
                            + " type=" + trap.getClass().getSimpleName(),
                    throwable
            );
            return ActivationResult.FAILED;
        }

        COOLDOWN_UNTIL.put(key, cooldownEnd);
        BukkitTask endTask = this.server.getScheduler().runTaskLater(this.plugin, () -> {
            try {
                trap.end();
            } catch (Throwable throwable) {
                this.plugin.getLogger().log(
                        Level.SEVERE,
                        "[DeathRun] Trap rollback failed map=" + runtime.mapId()
                                + " trap=" + trapIndex
                                + " type=" + trap.getClass().getSimpleName(),
                        throwable
                );
            } finally {
                END_TASKS.remove(key);
                ACTIVE.remove(key, context);
            }
        }, Math.max(1L, (durationMillis + 49L) / 50L));
        END_TASKS.put(key, endTask);

        this.startCooldownHologram(trap, key, cooldownEnd);
        return ActivationResult.ACTIVATED;
    }

    public static void shutdownAll(@NotNull Server server) {
        for (TrapActivationContext context : java.util.List.copyOf(ACTIVE.values())) {
            try {
                context.trap().end();
            } catch (Exception ignored) {
                // Best-effort rollback during plugin disable.
            }
        }

        END_TASKS.values().forEach(BukkitTask::cancel);
        HOLOGRAM_TASKS.values().forEach(BukkitTask::cancel);
        END_TASKS.clear();
        HOLOGRAM_TASKS.clear();

        ACTIVE.clear();
        COOLDOWN_UNTIL.clear();
        RECENT_CONTACT.clear();
        HOLOGRAMS.values().forEach(ArmorStand::remove);
        HOLOGRAMS.clear();

        server.getWorlds().forEach(world ->
                world.getEntitiesByClass(ArmorStand.class).stream()
                        .filter(stand -> stand.getScoreboardTags().contains(HOLOGRAM_TAG))
                        .forEach(ArmorStand::remove)
        );
    }

    public static void clearPlayer(@NotNull UUID playerId) {
        RECENT_CONTACT.remove(playerId);
        ACTIVE.values().forEach(context -> context.victims().remove(playerId));
    }

    public static void resetMap(@NotNull String mapId) {
        String normalized = mapId.toLowerCase(java.util.Locale.ROOT);

        java.util.List<Map.Entry<TrapKey, TrapActivationContext>> active = ACTIVE.entrySet().stream()
                .filter(entry -> entry.getKey().mapId().equalsIgnoreCase(normalized))
                .toList();

        for (Map.Entry<TrapKey, TrapActivationContext> entry : active) {
            try {
                entry.getValue().trap().end();
            } catch (Throwable ignored) {
                // Best-effort map reset.
            }
            ACTIVE.remove(entry.getKey(), entry.getValue());
        }

        java.util.List<TrapKey> scheduledEndKeys = END_TASKS.keySet().stream()
                .filter(key -> key.mapId().equalsIgnoreCase(normalized))
                .toList();
        for (TrapKey key : scheduledEndKeys) {
            BukkitTask task = END_TASKS.remove(key);
            if (task != null)
                task.cancel();
        }

        java.util.List<TrapKey> scheduledHologramKeys = HOLOGRAM_TASKS.keySet().stream()
                .filter(key -> key.mapId().equalsIgnoreCase(normalized))
                .toList();
        for (TrapKey key : scheduledHologramKeys) {
            BukkitTask task = HOLOGRAM_TASKS.remove(key);
            if (task != null)
                task.cancel();
        }

        COOLDOWN_UNTIL.keySet().removeIf(key -> key.mapId().equalsIgnoreCase(normalized));

        java.util.List<TrapKey> hologramKeys = HOLOGRAMS.keySet().stream()
                .filter(key -> key.mapId().equalsIgnoreCase(normalized))
                .toList();
        for (TrapKey key : hologramKeys) {
            ArmorStand stand = HOLOGRAMS.remove(key);
            if (stand != null)
                stand.remove();
        }

        RECENT_CONTACT.entrySet().removeIf(entry ->
                entry.getValue().context().mapId().equalsIgnoreCase(normalized)
        );
    }

    public long cooldownRemainingMillis(@NotNull String mapId, int trapIndex) {
        return Math.max(0L, COOLDOWN_UNTIL.getOrDefault(new TrapKey(mapId, trapIndex), 0L) - System.currentTimeMillis());
    }

    public void markTrapContact(@NotNull Player victim, @NotNull String mapId, int trapIndex) {
        TrapActivationContext context = ACTIVE.get(new TrapKey(mapId, trapIndex));
        if (context != null)
            this.markTrapContact(victim, context);
    }

    public void markTrapContact(@NotNull Player victim, @NotNull TrapActivationContext context) {
        if (System.currentTimeMillis() > context.expiresAt())
            return;

        context.victims().add(victim.getUniqueId());
        RECENT_CONTACT.put(
                victim.getUniqueId(),
                new RecentContact(context, System.currentTimeMillis() + RECENT_CONTACT_MILLIS)
        );
    }

    public @Nullable TrapActivationContext recentAttribution(@NotNull UUID victim) {
        RecentContact contact = RECENT_CONTACT.get(victim);
        if (contact == null)
            return null;

        if (System.currentTimeMillis() > contact.expiresAt()) {
            RECENT_CONTACT.remove(victim, contact);
            return null;
        }

        return contact.context();
    }

    public @Nullable TrapActivationContext attributionForExplosion(@NotNull Location explosion) {
        if (explosion.getWorld() == null)
            return null;

        long now = System.currentTimeMillis();
        return ACTIVE.values().stream()
                .filter(context -> now <= context.expiresAt())
                .filter(context -> context.trap() instanceof TrapTNT || context.trap() instanceof TrapMinefield)
                .filter(context -> context.trap().getLocations().stream().anyMatch(location ->
                        location != null
                                && location.getWorld() != null
                                && location.getWorld().getUID().equals(explosion.getWorld().getUID())
                ))
                .min(Comparator.comparingDouble(context -> this.minDistanceSquared(context.trap(), explosion)))
                .filter(context -> this.minDistanceSquared(context.trap(), explosion) <= 36.0)
                .orElse(null);
    }

    public @Nullable TrapActivationContext attributionForProjectile(@NotNull Location projectileLocation) {
        if (projectileLocation.getWorld() == null)
            return null;

        long now = System.currentTimeMillis();
        return ACTIVE.values().stream()
                .filter(context -> now <= context.expiresAt())
                .filter(context -> context.trap() instanceof TrapArrows)
                .filter(context -> context.trap().getLocations().stream().anyMatch(location ->
                        location != null
                                && location.getWorld() != null
                                && location.getWorld().getUID().equals(projectileLocation.getWorld().getUID())
                ))
                .min(Comparator.comparingDouble(context -> this.minDistanceSquared(context.trap(), projectileLocation)))
                .filter(context -> this.minDistanceSquared(context.trap(), projectileLocation) <= 900.0)
                .orElse(null);
    }

    public Map<String, TrapActivationContext> activeAttributions() {
        long now = System.currentTimeMillis();
        Map<String, TrapActivationContext> copy = new java.util.LinkedHashMap<>();
        ACTIVE.forEach((key, value) -> {
            if (now <= value.expiresAt())
                copy.put(key.mapId() + ":" + key.trapIndex(), value);
        });
        return Collections.unmodifiableMap(copy);
    }

    private double minDistanceSquared(@NotNull ITrap trap, @NotNull Location target) {
        return trap.getLocations().stream()
                .filter(java.util.Objects::nonNull)
                .filter(location -> location.getWorld() != null && target.getWorld() != null)
                .filter(location -> location.getWorld().getUID().equals(target.getWorld().getUID()))
                .mapToDouble(location -> location.distanceSquared(target))
                .min()
                .orElse(Double.MAX_VALUE);
    }

    private void startCooldownHologram(ITrap trap, TrapKey key, long cooldownEnd) {
        if (trap.getButton() == null || trap.getButton().getWorld() == null)
            return;

        ArmorStand stand = trap.getButton().getWorld().spawn(
                trap.getButton().clone().toCenterLocation().add(0, -1, 0),
                ArmorStand.class,
                entity -> {
                    entity.setGravity(false);
                    entity.setInvisible(true);
                    entity.setInvulnerable(true);
                    entity.setCustomNameVisible(true);
                    entity.addScoreboardTag(HOLOGRAM_TAG);
                }
        );

        HOLOGRAMS.put(key, stand);

        BukkitTask hologramTask = new BukkitRunnable() {
            @Override
            public void run() {
                Long currentCooldown = COOLDOWN_UNTIL.get(key);
                if (currentCooldown == null || currentCooldown.longValue() != cooldownEnd || !stand.isValid()) {
                    stand.remove();
                    HOLOGRAMS.remove(key, stand);
                    HOLOGRAM_TASKS.remove(key);
                    cancel();
                    return;
                }

                long remaining = Math.max(0L, cooldownEnd - System.currentTimeMillis());
                if (remaining <= 0L) {
                    stand.remove();
                    HOLOGRAMS.remove(key, stand);
                    HOLOGRAM_TASKS.remove(key);
                    COOLDOWN_UNTIL.remove(key, cooldownEnd);
                    cancel();
                    return;
                }

                long seconds = (remaining + 999L) / 1000L;
                stand.setCustomName(miniMessageToLegacy(
                        configuration.language().arenaHologramTrapDelayed
                                .replace("<delay>", String.valueOf(seconds))
                ));
            }
        }.runTaskTimer(this.plugin, 0L, 20L);
        HOLOGRAM_TASKS.put(key, hologramTask);
    }

    private String miniMessageToLegacy(String message) {
        return translateAlternateColorCodes('&', message
                .replace("<red>", "&c").replace("<green>", "&a").replace("<yellow>", "&e")
                .replace("<blue>", "&9").replace("<white>", "&f").replace("<black>", "&0")
                .replace("<gray>", "&7").replace("<dark_gray>", "&8").replace("<gold>", "&6")
                .replace("<dark_red>", "&4").replace("<dark_green>", "&2").replace("<dark_blue>", "&1")
                .replace("<dark_aqua>", "&3").replace("<dark_purple>", "&5").replace("<aqua>", "&b")
                .replace("<light_purple>", "&d").replace("<bold>", "&l").replace("<italic>", "&o")
                .replace("<strikethrough>", "&m").replace("<underline>", "&n").replace("<reset>", "&r")
                .replace("<magic>", "&k"));
    }

    public enum ActivationResult {
        ACTIVATED,
        COOLDOWN,
        NOT_PLAYING,
        NOT_DEATH,
        INVALID_TRAP,
        CANCELLED,
        FAILED
    }

    private record TrapKey(String mapId, int trapIndex) {}
    private record RecentContact(TrapActivationContext context, long expiresAt) {}
}
