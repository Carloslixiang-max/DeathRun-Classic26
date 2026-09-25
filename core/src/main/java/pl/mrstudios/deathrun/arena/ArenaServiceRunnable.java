package pl.mrstudios.deathrun.arena;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.bukkit.item.ItemBuilder;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.api.arena.enums.GameState;
import pl.mrstudios.deathrun.api.arena.event.arena.ArenaGameStateChangeEvent;
import pl.mrstudios.deathrun.api.arena.event.arena.ArenaShutdownStartedEvent;
import pl.mrstudios.deathrun.api.arena.event.user.UserArenaRoleAssignedEvent;
import pl.mrstudios.deathrun.api.arena.user.IUser;
import pl.mrstudios.deathrun.api.arena.user.enums.Role;
import pl.mrstudios.deathrun.arena.win.WinMapManager;
import pl.mrstudios.deathrun.config.Configuration;
import pl.mrstudios.deathrun.config.impl.MapConfiguration;
import pl.mrstudios.deathrun.reward.RewardService;
import pl.mrstudios.deathrun.classic.strafe.ClassicStrafeService;
import pl.mrstudios.deathrun.classic.death.DeathNavigatorService;
import pl.mrstudios.deathrun.classic.role.ClassicRoleAllocation;
import pl.mrstudios.deathrun.classic.score.ClassicScoring;
import pl.mrstudios.deathrun.classic.trap.TrapActivationService;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.String.valueOf;
import static java.time.Duration.ofMillis;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.IntStream.range;
import static me.catcoder.sidebar.ProtocolSidebar.newAdventureSidebar;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.minimessage.MiniMessage.miniMessage;
import static net.kyori.adventure.title.Title.Times.times;
import static net.kyori.adventure.title.Title.title;
import static org.bukkit.Material.AIR;
import static org.bukkit.Material.RED_BED;
import static org.bukkit.inventory.ItemFlag.values;
import static pl.mrstudios.deathrun.api.arena.enums.GameState.*;
import static pl.mrstudios.deathrun.api.arena.user.enums.Role.DEATH;
import static pl.mrstudios.deathrun.api.arena.user.enums.Role.RUNNER;
import static pl.mrstudios.deathrun.api.arena.user.enums.Role.UNKNOWN;

public class ArenaServiceRunnable extends BukkitRunnable {

    private final Arena arena;
        private final MapConfiguration.MapDefinition map;
        private final ArenaManager arenaManager;
                private final WinMapManager winMapManager;
    private final RewardService rewardService;
    private final Plugin plugin;
    private final Server server;
    private final Configuration configuration;

    private BukkitTask sidebarTask;
        private boolean backgroundSongWarningLogged;
        private boolean forceStartRequested;
        private boolean timerExpired;
        private boolean deathVictory;

    @Inject
    public ArenaServiceRunnable(
            @NotNull Arena arena,
            @NotNull MapConfiguration.MapDefinition map,
            @NotNull ArenaManager arenaManager,
            @NotNull WinMapManager winMapManager,
            @NotNull RewardService rewardService,
            @NotNull Plugin plugin,
            @NotNull Server server,
            @NotNull Configuration configuration
    ) {

        this.arena = arena;
        this.map = map;
        this.arenaManager = arenaManager;
        this.winMapManager = winMapManager;
        this.rewardService = rewardService;
        this.server = server;
        this.plugin = plugin;
        this.configuration = configuration;
        this.setState(WAITING);

        this.resetRoundState();

    }

    @Override
    public void run() {
        try {
            switch (this.arena.getGameState()) {
                case WAITING -> this.waiting();
                case STARTING -> this.starting();
                case PLAYING -> this.playing();
                case ENDING -> this.ending();
            }
        } catch (Throwable throwable) {
            this.plugin.getLogger().log(
                    java.util.logging.Level.SEVERE,
                    "[DR-START] Fatal arena tick failure map=" + this.resolvedMapId()
                            + " state=" + this.arena.getGameState()
                            + " users=" + this.arena.getUsers().size()
                            + ". Aborting this DeathRun match without stopping the server.",
                    throwable
            );
            this.arenaManager.emergencyAbortMap(this.resolvedMapId(), throwable);
        }
    }

    /* Waiting */
    protected void waiting() {

                this.arenaManager.applyQueuedPlayersForMapStart(this.resolvedMapId());

                if (this.forceStartRequested && this.totalPlayersReadyToStart() > 0) {
                        this.setState(STARTING);
                        return;
                }

                                if (this.totalPlayersReadyToStart() >= this.requiredPlayersToStart())
            this.setState(STARTING);

    }

    protected void stateSwitchToWaiting() {
        TrapActivationService.resetMap(this.resolvedMapId());
        this.resetRoundState();
        for (int i = 0; i < this.map.arenaStartBarrierBlocks.size(); i++) {
            Location location = this.map.arenaStartBarrierBlocks.get(i);
                        if (location == null || location.getWorld() == null) {
                                this.plugin.getLogger().warning("Skipping barrier restore for map " + this.resolvedMapId() + " because location world is null.");
                                continue;
                        }

            org.bukkit.Material restoreMaterial = i < this.map.arenaStartBarrierRestoreMaterials.size()
                    ? this.map.arenaStartBarrierRestoreMaterials.get(i)
                    : org.bukkit.Material.BARRIER;

            location.getBlock().setType(restoreMaterial);
        }

        this.arena.getUsers().stream()
                .map(IUser::asBukkit)
                .filter(Objects::nonNull)
                .forEach((player) -> {
                                        this.winMapManager.reclaimMap(player);
                    player.getInventory().clear();
                    player.getInventory().setArmorContents(new ItemStack[4]);
                    player.getInventory().setItemInOffHand(null);
                    player.setAllowFlight(false);
                    player.teleport(this.map.arenaWaitingLobbyLocation);
                });

        this.arena.getUsers().forEach((user) -> {
            user.setDeaths(0);
            user.setLives(0);
            user.setRoundPoints(0);
            user.setEliminated(false);
            user.setRole(UNKNOWN);
            if (!this.map.arenaCheckpoints.isEmpty())
                user.setCheckpoint(this.map.arenaCheckpoints.get(0));
        });
    }

    /* Starting */
    private int startingTimer;

    protected void starting() {

                this.arenaManager.applyQueuedPlayersForMapStart(this.resolvedMapId());

                if (this.totalPlayersReadyToStart() == 0) {
                        this.setState(WAITING);
                        return;
                }

                                if (!this.forceStartRequested && this.totalPlayersReadyToStart() < this.requiredPlayersToStart()) {
            this.setState(WAITING);
            return;
        }

        this.startingTimer--;
        if (stream(messageTimes).anyMatch((i) -> this.startingTimer == i))
            this.arena.getUsers().stream()
                    .map(IUser::asBukkit).filter(Objects::nonNull)
                    .forEach((player) -> {
                        player.showTitle(title(
                                miniMessage().deserialize(
                                        this.configuration.language().arenaPreStartingTitle
                                                .replace("<timer>", valueOf(this.startingTimer))
                                ),
                                miniMessage().deserialize(
                                        this.configuration.language().arenaPreStartingSubtitle
                                                .replace("<timer>", valueOf(this.startingTimer))
                                ),
                                times(ofMillis(250), ofMillis(1000), ofMillis(250))
                        ));
                        player.sendMessage(miniMessage().deserialize(
                                this.configuration.language().chatMessageArenaStartingTimer
                                        .replace("<timer>", valueOf(this.startingTimer))
                        ));
                        player.playSound(player.getLocation(), this.configuration.plugin().arenaSoundPreStarting, 1.0f, 1.0f);
                    });

        if (this.startingTimer > 0)
            return;

        this.setState(PLAYING);

    }

    protected void stateSwitchToStarting() {
        String mapName = this.map.name == null || this.map.name.isBlank()
                ? this.resolvedMapId()
                : this.map.name;
        String creator = this.map.creator == null || this.map.creator.isBlank()
                ? "Unknown"
                : this.map.creator;

        mapName = mapName.replace("<", "").replace(">", "");
        creator = creator.replace("<", "").replace(">", "");

        String finalMapName = mapName;
        String finalCreator = creator;
        this.arena.getUsers().stream()
                .map(IUser::asBukkit)
                .filter(Objects::nonNull)
                .forEach(player -> player.showTitle(title(
                        miniMessage().deserialize(
                                this.configuration.language().classicPreshowTitle
                                        .replace("<map>", finalMapName)
                        ),
                        miniMessage().deserialize(
                                this.configuration.language().classicPreshowSubtitle
                                        .replace("<creator>", finalCreator)
                        ),
                        times(ofMillis(250), ofMillis(2000), ofMillis(250))
                )));
    }

    /* Playing */
    private int barrierTimer;

    protected void playing() {

                boolean hasActiveRunner = this.arena.getRunners().stream()
                        .anyMatch(user -> !user.isEliminated());
                if (!hasActiveRunner) {
                        this.deathVictory = this.arena.getFinishedRuns() == 0;
                        this.setState(ENDING);
                        return;
                }

        if (this.barrierTimer != -1) {

            if (this.barrierTimer >= 0)
                this.barrierTimer--;

            if (stream(messageTimes).anyMatch((i) -> this.barrierTimer == i))
                this.arena.getUsers()
                        .stream().map(IUser::asBukkit)
                        .filter(Objects::nonNull).forEach((player) -> {

                            player.playSound(player.getLocation(), this.configuration.plugin().arenaSoundStarting, 1.0f, 1.0f);
                            player.showTitle(title(
                                    miniMessage().deserialize(
                                            this.configuration.language().arenaStartingTitle
                                                    .replace("<timer>", valueOf(this.barrierTimer))),
                                    miniMessage().deserialize(
                                            this.configuration.language().arenaStartingSubtitle
                                                    .replace("<timer>", valueOf(this.barrierTimer))
                                    ),
                                    times(ofMillis(250), ofMillis(1000), ofMillis(250))
                            ));

                        });

            if (this.barrierTimer == 0) {

                this.map.arenaStartBarrierBlocks
                        .stream()
                        .map(Location::getBlock)
                        .forEach((block) -> block.setType(AIR));

                this.startBackgroundSong();

                this.arena.getUsers()
                        .stream()
                        .map(IUser::asBukkit)
                        .filter(Objects::nonNull)
                        .forEach((player) -> player.playSound(player.getLocation(), this.configuration.plugin().arenaSoundStarted, 1.0f, 1.0f));

            }

            if (this.barrierTimer > 0)
                return;

        }

        this.arena.setElapsedTime(this.arena.getElapsedTime() + 1);
        this.arena.setRemainingTime(this.arena.getRemainingTime() - 1);

        if (this.arena.getRemainingTime() <= 0) {
            this.timerExpired = true;
            this.setState(ENDING);
        }

    }

    protected void stateSwitchToPlaying() {

                this.forceStartRequested = false;
        this.timerExpired = false;

                this.arenaManager.applyQueuedPlayersForMapStart(this.resolvedMapId());

                this.arena.getUsers().forEach((user) -> user.setRole(UNKNOWN));

                int users = this.arena.getUsers().size();
                int configuredDeaths = Math.max(this.configuration.plugin().arenaDeathsAmount, 0);
                int deathsToAssign = ClassicRoleAllocation.deathCount(users, configuredDeaths);

                if (deathsToAssign > 0) {
                        ArrayList<IUser> shuffled = new ArrayList<>(this.arena.getUsers());
                        Collections.shuffle(shuffled);
                        for (int i = 0; i < deathsToAssign; i++)
                                shuffled.get(i).setRole(DEATH);
                }

        this.arena.getUsers()
                .stream()
                .filter((user) -> user.getRole() != DEATH)
                .forEach((user) -> user.setRole(RUNNER));

        this.arena.getRunners().forEach(user -> {
            user.setLives(ClassicScoring.RUNNER_START_LIVES);
            user.setRoundPoints(0);
            user.setEliminated(false);
        });

        this.arena.getDeaths().forEach(user -> {
            user.setLives(0);
            user.setRoundPoints(0);
            user.setEliminated(false);
        });

        this.rewardService.resetMatch(this.resolvedMapId(), this.arena.getRunners().size());

        range(0, this.arena.getRunners().size())
                .filter((i) -> this.arena.getRunners().get(i).asBukkit() != null)
                .forEach((i) -> {
                    IUser runner = this.arena.getRunners().get(i);
                    Player runnerPlayer = requireNonNull(runner.asBukkit());
                    Location spawn = this.map.arenaRunnerSpawnLocations.get(i % this.map.arenaRunnerSpawnLocations.size());
                    runnerPlayer.teleport(spawn);
                    runner.setCheckpoint(new pl.mrstudios.deathrun.arena.checkpoint.Checkpoint(
                            0, spawn.clone(), java.util.List.of(), "Start"
                    ));
                });

        range(0, this.arena.getDeaths().size())
                .filter((i) -> this.arena.getDeaths().get(i).asBukkit() != null)
                .forEach((i) -> requireNonNull(this.arena.getDeaths().get(i).asBukkit()).teleport(this.map.arenaDeathSpawnLocations.get(i % this.map.arenaDeathSpawnLocations.size())));

        this.arena.getUsers()
                .forEach((user) -> {

                    Player player = user.asBukkit();
                    if (player == null)
                        return;

                    if (user.getRole() == RUNNER)
                        this.configuration.language().chatMessageArenaGameStartRunner.stream()
                                .map(miniMessage()::deserialize)
                                .forEach((component) -> player.sendMessage(component));

                    if (user.getRole() == DEATH)
                        this.configuration.language().chatMessageArenaGameStartDeath.stream()
                                .map(miniMessage()::deserialize)
                                .forEach((component) -> player.sendMessage(component));

                    this.server.getPluginManager().callEvent(new UserArenaRoleAssignedEvent(user.getRole(), user));
                    player.getInventory().clear();
                    player.getInventory().setArmorContents(new ItemStack[4]);
                    player.getInventory().setItemInOffHand(null);
                    player.setFoodLevel(20);
                    player.setSaturation(20.0f);

                    if (user.getRole() == RUNNER) {
                        // Hive Classic movement is provided exclusively by Left/Back/Right Strafe.
                        // Do not inject the old CIlie23 booster item here; its legacy item builder
                        // is not part of the Paper 26.2 Classic runtime.
                        new ClassicStrafeService(this.plugin).prepareRunner(player);
                    }

                    if (user.getRole() == DEATH) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, this.configuration.plugin().arenaDeathSpeedAmplifier, false, false, false));
                        new DeathNavigatorService(this.plugin).prepareDeath(player);
                    }

                });
    }

    /* Ending */
    private int endDelayTimer;

    protected void ending() {

        this.endDelayTimer--;
        if (this.endDelayTimer > this.configuration.plugin().arenaEndDelay)
            return;

        if (this.endDelayTimer > 0)
            this.arena.getUsers()
                    .stream()
                    .map(IUser::asBukkit)
                    .filter(Objects::nonNull)
                    .forEach(
                            (player) ->
                                    player.showTitle(title(
                                            miniMessage().deserialize(
                                                    this.configuration.language().arenaMoveServerTitle
                                                            .replace("<endTimer>", valueOf(this.endDelayTimer))),
                                            miniMessage().deserialize(
                                                    this.configuration.language().arenaMoveServerSubtitle
                                                            .replace("<endTimer>", valueOf(this.endDelayTimer))
                                            ),
                                            times(ofMillis(250), ofMillis(1000), ofMillis(250))
                                    ))
                    );

        if (this.endDelayTimer > 0)
            return;

                ArenaShutdownStartedEvent event = new ArenaShutdownStartedEvent(this.arena, false);
        this.server.getPluginManager().callEvent(event);

        this.arena.getUsers().stream()
                .map(IUser::asBukkit)
                .filter(Objects::nonNull)
                .toList()
                .forEach((player) -> {
                    player.sendMessage(miniMessage().deserialize(this.configuration.language().arenaMoveServerChat));
                    // Exact pre-DeathRun snapshot restoration happens in leaveCurrentMap.
                    this.arenaManager.leaveCurrentMap(player, false);
                });

                this.setState(WAITING);

    }

    protected void stateSwitchToEnding() {

        this.arena.getUsers()
                .stream()
                .map(IUser::asBukkit)
                .filter(Objects::nonNull)
                .forEach((player) -> {
                    player.showTitle(title(
                            miniMessage().deserialize(this.configuration.language().arenaGameEndTitle),
                            miniMessage().deserialize(this.configuration.language().arenaGameEndSubtitle),
                            times(ofMillis(250), ofMillis(2500), ofMillis(250))
                    ));

                    // Give the leave bed during ENDING so remaining DEATH players are never stuck.
                    player.getInventory().setItem(
                            8,
                            new ItemBuilder(RED_BED)
                                    .name(miniMessage().deserialize(this.configuration.language().arenaItemLeaveName))
                                    .itemFlags(values())
                                    .build()
                    );
                });

        if (this.timerExpired || this.deathVictory) {
            this.rewardService.rewardDeathWin(this.resolvedMapId(), this.arena.getDeaths().stream()
                    .map(IUser::asBukkit)
                    .filter(Objects::nonNull)
                    .toList());
            this.timerExpired = false;
            this.deathVictory = false;
        }

    }

    /* Internal */
    protected void setState(
            @NotNull GameState gameState
    ) {

        GameState previousState = this.arena.getGameState();
        this.plugin.getLogger().info(
                "[DR-START] map=" + this.resolvedMapId()
                        + " state=" + previousState + " -> " + gameState
                        + " users=" + this.arena.getUsers().size()
        );

        this.arena.setGameState(gameState);

        switch (gameState) {

            case WAITING ->
                    this.stateSwitchToWaiting();

            case STARTING ->
                    this.stateSwitchToStarting();

            case PLAYING ->
                    this.stateSwitchToPlaying();

            case ENDING ->
                    this.stateSwitchToEnding();

        }

        if (this.sidebarTask != null)
            this.sidebarTask.cancel();

                this.stopBackgroundSong();

        if (this.arena.getSidebar() != null)
            this.arena.getSidebar().destroy();

        this.server.getPluginManager().callEvent(new ArenaGameStateChangeEvent(this.arena, this.arena.getGameState()));
        if (this.arena.getGameState() == ENDING)
            return;

                if (!this.configuration.language().arenaScoreboardEnabled)
            return;

                this.arena.setSidebar(newAdventureSidebar(miniMessage().deserialize(this.configuration.language().arenaScoreboardTitle), this.plugin));

        switch (this.arena.getGameState()) {

            case WAITING ->
                    this.configuration.language().arenaScoreboardLinesWaiting.forEach(this::addLine);

            case STARTING ->
                    this.configuration.language().arenaScoreboardLinesStarting.forEach(this::addLine);

            case PLAYING ->
                    this.configuration.language().arenaScoreboardLinesPlaying.forEach(this::addLine);

            case ENDING -> {
                // No scoreboard lines are rendered for ENDING because sidebar is torn down above.
            }

        }

        this.arena.getUsers()
                .stream()
                .map(IUser::asBukkit)
                .filter(Objects::nonNull)
                .forEach(this.arena.getSidebar()::addViewer);

        int updateTicks = Math.max(1, this.configuration.language().arenaScoreboardUpdateTicks);
        this.sidebarTask = this.arena.getSidebar().updateLinesPeriodically(0, updateTicks);

    }

    protected void addLine(@NotNull String content) {
        this.arena.getSidebar()
                .addUpdatableLine(
                        (player) -> {

                            AtomicReference<Component> component = new AtomicReference<>(empty());

                            ofNullable(this.arena.getUser(player))
                                    .ifPresentOrElse(
                                            (user) -> component.set(miniMessage().deserialize(
                                                    this.applySidebarPlaceholders(player,
                                                            content.replace("<map>", this.displayMapName())
                                                            .replace("<creator>", this.displayMapCreator())
                                                            .replace("<role>", this.rolePrefix(user.getRole()))
                                                            .replace("<currentPlayers>", valueOf(this.currentPlayersForDisplay()))
                                                            .replace("<maxPlayers>", valueOf(this.arenaManager.maxPlayers(this.map)))
                                                            .replace("<timer>", valueOf(this.startingTimer))
                                                            .replace("<time>", valueOf(this.arena.getRemainingTime()))
                                                            .replace("<timeFormatted>", this.formatTime(this.arena.getRemainingTime()))
                                                            .replace("<runners>", valueOf(this.arena.getRunners().size()))
                                                            .replace("<deaths>", valueOf(user.getDeaths()))
                                                            .replace("<lives>", valueOf(user.getLives()))
                                                            .replace("<roundPoints>", valueOf(user.getRoundPoints()))
                                                            .replace("<checkpoint>", user.getCheckpoint() == null ? "-" : valueOf(user.getCheckpoint().id()))
                                                            .replace("<deathPlayers>", valueOf(this.arena.getDeaths().size()))
                                                    )
                                            )),
                                            () -> this.arena.getSidebar().removeViewer(player)
                                    );

                            return component.get();

                        });
    }

        private @NotNull String applySidebarPlaceholders(
                        @NotNull Player player,
                        @NotNull String content
        ) {
                content = this.applyRuntimeMapPlaceholders(content);

                if (this.plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null)
                        return content;

                return PlaceholderAPI.setPlaceholders(player, content);
        }

        private @NotNull String applyRuntimeMapPlaceholders(
                        @NotNull String content
        ) {
                return content
                        .replace("%deathrun_map_players_mapid%", valueOf(this.currentPlayersForDisplay()))
                        .replace("%deathrun_map_max_players_mapid%", valueOf(this.arenaManager.maxPlayers(this.map)))
                        .replace("%deathrun_map_status_mapid%", this.currentMapStatusForDisplay());
        }

        private @NotNull String currentMapStatusForDisplay() {
                return switch (this.arena.getGameState()) {
                        case WAITING, STARTING -> "WAITING";
                        case PLAYING, ENDING -> "IN_PROGRESS";
                };
        }

    protected String rolePrefix(
            @NotNull Role role
    ) {
        return switch (role) {

            case RUNNER ->
                    this.configuration.language().arenaRolesRunnerName;

            case DEATH ->
                    this.configuration.language().arenaRolesDeathName;

            case SPECTATOR ->
                    this.configuration.language().arenaRolesSpectatorName;

            default ->
                    role.name();

        };
    }

    protected String formatTime(int time) {
        int minutes = time / 60, seconds = time % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

        private void startBackgroundSong() {
                if (!this.configuration.plugin().arenaBackgroundSongEnabled)
                        return;

                if (!this.backgroundSongWarningLogged) {
                        this.backgroundSongWarningLogged = true;
                        this.plugin.getLogger().warning(
                                "[DeathRun] NBS background music is disabled in the Classic26 runtime build. "
                                        + "Gameplay is unaffected; use the server resource-pack music system instead."
                        );
                }
        }

        private void stopBackgroundSong() {
                // No-op in the Classic26 runtime build.
        }

        public void removeBackgroundSongPlayer(@NotNull Player player) {
                // No-op in the Classic26 runtime build.
        }

        public void shutdown() {
                if (this.sidebarTask != null) {
                        this.sidebarTask.cancel();
                        this.sidebarTask = null;
                }

                this.stopBackgroundSong();

                if (this.arena.getSidebar() != null)
                        this.arena.getSidebar().destroy();

                try {
                        this.cancel();
                } catch (IllegalStateException ignored) {
                        // Runtime was never scheduled or has already been cancelled.
                }
        }

        private String resolveSongFileName() {
                if (this.map.arenaBackgroundSongFileName != null && !this.map.arenaBackgroundSongFileName.isBlank())
                        return this.map.arenaBackgroundSongFileName;

                return this.configuration.plugin().arenaBackgroundSongFileName;
        }

        private boolean resolveSongLoop() {
                if (this.map.arenaBackgroundSongLoop != null)
                        return this.map.arenaBackgroundSongLoop;

                return this.configuration.plugin().arenaBackgroundSongLoop;
        }

        private int requiredPlayersToStart() {
                int mapCapacity = this.arenaManager.maxPlayers(this.map);
                int configuredRequired = this.map.arenaRequiredPlayersToStart != null
                        ? this.map.arenaRequiredPlayersToStart
                        : this.configuration.plugin().arenaRequiredPlayersToStart;

                if (configuredRequired <= 0)
                        configuredRequired = 1;

                if (mapCapacity <= 0)
                        return max(1, configuredRequired);

                return max(1, min(configuredRequired, mapCapacity));
        }

        public int requiredPlayersToStartForDisplay() {
                return this.requiredPlayersToStart();
        }

        private int totalPlayersReadyToStart() {
                return this.arena.getUsers().size() + this.arenaManager.queuedPlayersForMap(this.resolvedMapId());
        }

        private int currentPlayersForDisplay() {
                int maxPlayers = this.arenaManager.maxPlayers(this.map);
                int totalReady = this.totalPlayersReadyToStart();
                if (maxPlayers <= 0)
                        return Math.max(0, totalReady);

                return min(maxPlayers, Math.max(0, totalReady));
        }

        private @NotNull String resolvedMapId() {
                if (this.map.id != null && !this.map.id.isBlank())
                        return this.map.id.toLowerCase().replace(" ", "-");

                if (this.map.name != null && !this.map.name.isBlank())
                        return this.map.name.toLowerCase().replace(" ", "-");

                return "default";
        }

        private @NotNull String displayMapName() {
                if (this.map.name != null && !this.map.name.isBlank())
                        return this.map.name;

                if (this.map.id != null && !this.map.id.isBlank())
                        return this.map.id;

                return "default";
        }

        private @NotNull String displayMapCreator() {
                if (this.map.creator == null || this.map.creator.isBlank())
                        return "Unknown";

                return this.map.creator.replace("<", "").replace(">", "");
        }

    /* Constants */
    protected static final int[] messageTimes = new int[] { 1, 2, 3, 4, 5, 10, 15, 30, 60, 90, 180, 360 };

        public boolean requestForceStart() {
                if (this.arena.getGameState() == PLAYING || this.arena.getGameState() == ENDING)
                        return false;

                if (this.arena.getUsers().isEmpty())
                        return false;

                this.forceStartRequested = true;
                this.startingTimer = Math.min(this.startingTimer, 4);
                try {
                    if (this.arena.getGameState() == WAITING)
                        this.setState(STARTING);
                    return true;
                } catch (Throwable throwable) {
                    this.plugin.getLogger().log(
                            java.util.logging.Level.SEVERE,
                            "[DR-START] Force-start transition failed for map=" + this.resolvedMapId(),
                            throwable
                    );
                    this.arenaManager.emergencyAbortMap(this.resolvedMapId(), throwable);
                    return false;
                }
        }

        public boolean requestStop() {
                if (this.arena.getGameState() == WAITING)
                        return false;

                this.setState(WAITING);
                return true;
        }

        private void resetRoundState() {
                                this.forceStartRequested = false;
                this.deathVictory = false;
                this.barrierTimer = this.configuration.plugin().arenaStartingTime;
                this.arena.setRemainingTime(this.configuration.plugin().arenaGameTime);
                this.startingTimer = this.configuration.plugin().arenaPreStartingTime + 1;
                this.endDelayTimer = this.configuration.plugin().arenaEndDelay + 5;
                this.arena.setElapsedTime(0);
                this.arena.setFinishedRuns(0);
        }

}
