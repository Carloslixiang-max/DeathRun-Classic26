package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.api.arena.event.arena.ArenaGameStateChangeEvent;
import pl.mrstudios.deathrun.api.arena.event.arena.ArenaTrapActivateEvent;
import pl.mrstudios.deathrun.api.arena.event.arena.ArenaUserJoinedEvent;
import pl.mrstudios.deathrun.api.arena.event.arena.ArenaUserLeftEvent;
import pl.mrstudios.deathrun.api.arena.event.user.UserArenaCheckpointEvent;
import pl.mrstudios.deathrun.api.arena.event.user.UserArenaDeathEvent;
import pl.mrstudios.deathrun.api.arena.event.user.UserArenaFinishedEvent;
import pl.mrstudios.deathrun.api.arena.event.user.UserArenaRoleAssignedEvent;
import pl.mrstudios.deathrun.api.arena.event.user.UserArenaUseBoosterEvent;
import pl.mrstudios.deathrun.api.arena.user.IUser;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.classic.playtest.PlaytestTraceService;
import pl.mrstudios.deathrun.classic.trap.TrapActivationContext;
import pl.mrstudios.deathrun.classic.trap.TrapActivationService;
import pl.mrstudios.deathrun.config.Configuration;

import java.util.UUID;

public final class PlaytestTraceListener implements Listener {

    private final PlaytestTraceService trace;
    private final ArenaManager arenaManager;
    private final Plugin plugin;
    private final Server server;
    private final TrapActivationService trapActivationService;

    @Inject
    public PlaytestTraceListener(
            @NotNull PlaytestTraceService trace,
            @NotNull ArenaManager arenaManager,
            @NotNull Plugin plugin,
            @NotNull Server server,
            @NotNull Configuration configuration
    ) {
        this.trace = trace;
        this.arenaManager = arenaManager;
        this.plugin = plugin;
        this.server = server;
        this.trapActivationService = new TrapActivationService(plugin, server, configuration);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onState(@NotNull ArenaGameStateChangeEvent event) {
        this.trace.recordArena(
                event.getArena(),
                "STATE",
                "state=" + event.getGameState()
                        + " users=" + event.getArena().getUsers().size()
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(@NotNull ArenaUserJoinedEvent event) {
        IUser user = event.getUser();
        Player player = user.asBukkit();
        this.trace.recordArena(
                event.getArena(),
                "JOIN",
                "player=" + user.getName()
                        + " role=" + user.getRole()
                        + " snapshotPending=" + (player != null && this.arenaManager.hasPendingSnapshot(player))
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLeft(@NotNull ArenaUserLeftEvent event) {
        String mapId = this.trace.mapIdForArena(event.getArena());
        if (mapId == null)
            return;

        IUser user = event.getUser();
        UUID playerId = user.getUniqueId();
        String playerName = user.getName();
        this.trace.record(
                mapId,
                "LEAVE",
                "player=" + playerName
                        + " role=" + user.getRole()
                        + " lives=" + user.getLives()
                        + " points=" + user.getRoundPoints()
        );

        this.server.getScheduler().runTask(this.plugin, () -> {
            Player player = this.server.getPlayer(playerId);
            this.trace.record(
                    mapId,
                    "RESTORE",
                    "player=" + playerName
                            + " online=" + (player != null)
                            + " snapshotPending=" + (player == null ? "unknown" : this.arenaManager.hasPendingSnapshot(player))
            );
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRole(@NotNull UserArenaRoleAssignedEvent event) {
        this.trace.recordUser(
                event.getUser(),
                "ROLE",
                "player=" + event.getUser().getName()
                        + " role=" + event.getRole()
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCheckpoint(@NotNull UserArenaCheckpointEvent event) {
        String mapId = this.trace.mapIdForUser(event.getUser());
        if (mapId == null)
            return;

        IUser user = event.getUser();
        int checkpointId = event.getCheckpoint().id();
        this.server.getScheduler().runTask(this.plugin, () -> this.trace.record(
                mapId,
                "CHECKPOINT",
                "player=" + user.getName()
                        + " cp=" + checkpointId
                        + " lives=" + user.getLives()
                        + " points=" + user.getRoundPoints()
                        + " eliminated=" + user.isEliminated()
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(@NotNull UserArenaDeathEvent event) {
        IUser user = event.getUser();
        TrapActivationContext attribution = this.trapActivationService.recentAttribution(user.getUniqueId());
        String trap = attribution == null
                ? "-"
                : attribution.trapType() + "#" + (attribution.trapIndex() + 1);

        this.trace.recordArena(
                event.getArena(),
                "DEATH",
                "player=" + user.getName()
                        + " deaths=" + user.getDeaths()
                        + " lives=" + user.getLives()
                        + " eliminated=" + user.isEliminated()
                        + " trap=" + trap
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFinish(@NotNull UserArenaFinishedEvent event) {
        IUser user = event.getUser();
        String mapId = this.trace.mapIdForUser(user);
        if (mapId == null)
            return;

        this.server.getScheduler().runTask(this.plugin, () -> {
            ArenaManager.ArenaRuntime runtime = this.arenaManager.runtimeByMapId(mapId);
            int remaining = runtime == null ? -1 : runtime.arena().getRemainingTime();
            this.trace.record(
                    mapId,
                    "FINISH",
                    "player=" + user.getName()
                            + " position=" + event.getPosition()
                            + " time=" + event.getTime()
                            + " lives=" + user.getLives()
                            + " points=" + user.getRoundPoints()
                            + " remaining=" + remaining
            );
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBooster(@NotNull UserArenaUseBoosterEvent event) {
        this.trace.recordArena(
                event.getArena(),
                "BOOSTER",
                "player=" + event.getUser().getName()
                        + " direction=" + event.getBooster().direction()
                        + " power=" + event.getBooster().power()
                        + " delay=" + event.getBooster().delay()
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrap(@NotNull ArenaTrapActivateEvent event) {
        String mapId = this.trace.mapIdForArena(event.getArena());
        if (mapId == null)
            return;

        ArenaManager.ArenaRuntime runtime = this.arenaManager.runtimeByMapId(mapId);
        if (runtime == null)
            return;

        int trapIndex = runtime.map().arenaTraps.indexOf(event.getTrap());
        if (trapIndex < 0)
            return;

        this.server.getScheduler().runTask(this.plugin, () -> {
            TrapActivationContext context = this.trapActivationService.activeAttributions()
                    .get(mapId + ":" + trapIndex);
            if (context == null)
                return;

            Player death = this.server.getPlayer(context.activatedByDeathUUID());
            this.trace.record(
                    mapId,
                    "TRAP_ACTIVE",
                    "index=" + (trapIndex + 1)
                            + " type=" + context.trapType()
                            + " death=" + (death == null ? context.activatedByDeathUUID() : death.getName())
            );
        });
    }
}
