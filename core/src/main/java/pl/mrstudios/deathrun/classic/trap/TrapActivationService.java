package pl.mrstudios.deathrun.classic.trap;

import org.bukkit.Server;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pl.mrstudios.deathrun.api.arena.event.arena.ArenaTrapActivateEvent;
import pl.mrstudios.deathrun.api.arena.trap.ITrap;
import pl.mrstudios.deathrun.api.arena.user.IUser;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.config.Configuration;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.bukkit.ChatColor.translateAlternateColorCodes;
import static pl.mrstudios.deathrun.api.arena.enums.GameState.PLAYING;
import static pl.mrstudios.deathrun.api.arena.user.enums.Role.DEATH;

public final class TrapActivationService {

    private static final long RECENT_CONTACT_MILLIS = 4_000L;
    private static final String HOLOGRAM_TAG = "deathrun_classic26_trap_hologram";
    private static final Map<TrapKey, Long> COOLDOWN_UNTIL = new ConcurrentHashMap<>();
    private static final Map<TrapKey, TrapActivationContext> ACTIVE = new ConcurrentHashMap<>();
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
        long durationMillis = Math.max(0L, trap.getDuration().toMillis());
        long cooldownEnd = now + cooldownMillis;
        long activeEnd = now + durationMillis;
        COOLDOWN_UNTIL.put(key, cooldownEnd);

        Set<UUID> victims = ConcurrentHashMap.newKeySet();
        TrapActivationContext context = new TrapActivationContext(
                runtime.mapId(), trapIndex, trap.getClass().getSimpleName(), death.getUniqueId(),
                now, activeEnd, trap, victims
        );
        ACTIVE.put(key, context);

        trap.start();
        this.server.getScheduler().runTaskLater(this.plugin, () -> {
            try {
                trap.end();
            } finally {
                ACTIVE.remove(key, context);
            }
        }, Math.max(1L, durationMillis / 50L));

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

        ACTIVE.clear();
        COOLDOWN_UNTIL.clear();
        RECENT_CONTACT.clear();

        server.getWorlds().forEach(world ->
                world.getEntitiesByClass(ArmorStand.class).stream()
                        .filter(stand -> stand.getScoreboardTags().contains(HOLOGRAM_TAG))
                        .forEach(ArmorStand::remove)
        );
    }

    public long cooldownRemainingMillis(@NotNull String mapId, int trapIndex) {
        return Math.max(0L, COOLDOWN_UNTIL.getOrDefault(new TrapKey(mapId, trapIndex), 0L) - System.currentTimeMillis());
    }

    public void markTrapContact(@NotNull Player victim, @NotNull String mapId, int trapIndex) {
        TrapKey key = new TrapKey(mapId, trapIndex);
        TrapActivationContext context = ACTIVE.get(key);
        if (context == null || System.currentTimeMillis() > context.expiresAt())
            return;
        context.victims().add(victim.getUniqueId());
        RECENT_CONTACT.put(victim.getUniqueId(), new RecentContact(context, System.currentTimeMillis() + RECENT_CONTACT_MILLIS));
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

    public Map<String, TrapActivationContext> activeAttributions() {
        Map<String, TrapActivationContext> copy = new java.util.LinkedHashMap<>();
        ACTIVE.forEach((key, value) -> copy.put(key.mapId() + ":" + key.trapIndex(), value));
        return Collections.unmodifiableMap(copy);
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
        new BukkitRunnable() {
            @Override public void run() {
                long remaining = Math.max(0L, cooldownEnd - System.currentTimeMillis());
                if (remaining <= 0L) {
                    stand.remove();
                    COOLDOWN_UNTIL.remove(key, cooldownEnd);
                    cancel();
                    return;
                }
                long seconds = (remaining + 999L) / 1000L;
                stand.setCustomName(miniMessageToLegacy(
                        configuration.language().arenaHologramTrapDelayed.replace("<delay>", String.valueOf(seconds))
                ));
            }
        }.runTaskTimer(this.plugin, 0L, 20L);
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

    public enum ActivationResult { ACTIVATED, COOLDOWN, NOT_PLAYING, NOT_DEATH, INVALID_TRAP, CANCELLED }
    private record TrapKey(String mapId, int trapIndex) {}
    private record RecentContact(TrapActivationContext context, long expiresAt) {}
}
