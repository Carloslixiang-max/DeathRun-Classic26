package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pl.mrstudios.commons.bukkit.item.ItemBuilder;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.api.arena.event.user.UserArenaCheckpointEvent;
import pl.mrstudios.deathrun.api.arena.event.user.UserArenaFinishedEvent;
import pl.mrstudios.deathrun.api.arena.user.IUser;
import pl.mrstudios.deathrun.arena.Arena;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.arena.checkpoint.Checkpoint;
import pl.mrstudios.deathrun.arena.win.WinMapManager;
import pl.mrstudios.deathrun.config.Configuration;
import pl.mrstudios.deathrun.config.impl.MapConfiguration;
import pl.mrstudios.deathrun.plugin.Entrypoint;
import pl.mrstudios.deathrun.reward.RewardService;
import pl.mrstudios.deathrun.classic.checkpoint.SegmentAabb;

import java.awt.image.BufferedImage;
import java.util.Objects;

import static java.lang.String.valueOf;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static java.util.Optional.ofNullable;
import static net.kyori.adventure.text.minimessage.MiniMessage.miniMessage;
import static net.kyori.adventure.title.Title.Times.times;
import static net.kyori.adventure.title.Title.title;
import static org.bukkit.GameMode.ADVENTURE;
import static org.bukkit.Material.NETHER_PORTAL;
import static org.bukkit.Material.RED_BED;
import static org.bukkit.event.EventPriority.MONITOR;
import static org.bukkit.inventory.ItemFlag.values;
import static pl.mrstudios.deathrun.api.arena.enums.GameState.PLAYING;
import static pl.mrstudios.deathrun.api.arena.user.enums.Role.RUNNER;
import static pl.mrstudios.deathrun.api.arena.user.enums.Role.SPECTATOR;

public class ArenaCheckpointReachedListener implements Listener {

    private final ArenaManager arenaManager;
    private final Plugin plugin;
    private final Server server;
    private final Configuration configuration;
        private final WinMapManager winMapManager;
        private final RewardService rewardService;

    @Inject
    public ArenaCheckpointReachedListener(
            @NotNull ArenaManager arenaManager,
            @NotNull Plugin plugin,
            @NotNull Server server,
                        @NotNull Configuration configuration,
                        @NotNull WinMapManager winMapManager,
                        @NotNull RewardService rewardService
    ) {
        this.arenaManager = arenaManager;
        this.plugin = plugin;
        this.server = server;
        this.configuration = configuration;
                this.winMapManager = winMapManager;
                this.rewardService = rewardService;
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = MONITOR)
    public void onPlayerMove(
            @NotNull PlayerMoveEvent event
    ) {
        if (event.getTo() == null || event.getFrom() == null)
            return;

                if (event.getFrom().getWorld() != null && event.getTo().getWorld() != null
                                && !event.getFrom().getWorld().getUID().equals(event.getTo().getWorld().getUID()))
                        return;

        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()
                && event.getTo().getBlock().getType() != NETHER_PORTAL)
            return;

                this.processCheckpoint(event.getPlayer(), event.getFrom(), event.getTo(), "move");

    }

    @EventHandler(priority = MONITOR)
    public void onPlayerPortal(
            @NotNull PlayerPortalEvent event
    ) {
                this.processCheckpoint(event.getPlayer(), event.getFrom(), event.getFrom(), "portal");
        }

        @EventHandler(priority = MONITOR)
        public void onPlayerTeleport(
                        @NotNull PlayerTeleportEvent event
        ) {
                if (event.getTo() == null)
                        return;

                // Teleports must not sweep the whole path: an internal respawn/start
                // teleport can cross later checkpoint boxes geometrically. Only test the
                // destination point; high-speed normal movement is handled by PlayerMoveEvent.
                this.processCheckpoint(event.getPlayer(), event.getTo(), event.getTo(), "teleport");
    }

    private void processCheckpoint(
            @NotNull org.bukkit.entity.Player player,
            @NotNull Location from,
            @NotNull Location to,
            @NotNull String source
    ) {
        Arena arena = this.arenaManager.arenaForPlayer(player);
        ArenaManager.ArenaRuntime runtime = this.arenaManager.runtimeForPlayer(player);
        MapConfiguration.MapDefinition map = this.arenaManager.mapForPlayer(player);
                if (arena == null || map == null || runtime == null)
            return;

                                if (arena.getGameState() != PLAYING)
            return;

        if (map.arenaCheckpoints.isEmpty())
            return;

        IUser user = arena.getUser(player);
                                if (user == null)
            return;

                                if (user.getRole() != RUNNER || user.isEliminated())
            return;

                int touchedIndex = -1;
                for (int i = 0; i < map.arenaCheckpoints.size(); i++) {
                        if (!this.sweptTouchesCheckpoint(map.arenaCheckpoints.get(i), from, to))
                                continue;

                        touchedIndex = i;
                        break;
                }

                                if (touchedIndex < 0)
                        return;

                int lastCompletedIndex = -1;
                if (user.getCheckpoint() != null) {
                        for (int i = 0; i < map.arenaCheckpoints.size(); i++) {
                                if (map.arenaCheckpoints.get(i).id().equals(user.getCheckpoint().id())) {
                                        lastCompletedIndex = i;
                                        break;
                                }
                        }
                }

                int expectedIndex = lastCompletedIndex + 1;
                if (touchedIndex != expectedIndex)
                        return;

                Checkpoint checkpoint = map.arenaCheckpoints.get(touchedIndex);

        UserArenaCheckpointEvent userArenaCheckpointEvent = new UserArenaCheckpointEvent(user, checkpoint);
        this.server.getPluginManager().callEvent(userArenaCheckpointEvent);

        user.setCheckpoint(checkpoint);

        Checkpoint finishCheckpoint = this.finishCheckpoint(map);
        int finishIndex = finishCheckpoint == null ? -1 : map.arenaCheckpoints.indexOf(finishCheckpoint);
        boolean reachedFinishCheckpoint = finishIndex >= 0 && touchedIndex == finishIndex;

        user.setRoundPoints(user.getRoundPoints() + this.checkpointPoints(map, touchedIndex));
        if (!reachedFinishCheckpoint)
            user.setLives(user.getLives() + 2);

        player.showTitle(
                title(
                        miniMessage().deserialize(
                                this.configuration.language().arenaCheckpointTitle
                                        .replace("<checkpoint>", valueOf(checkpoint.id()))
                        ),
                        miniMessage().deserialize(
                                this.configuration.language().arenaCheckpointSubtitle
                                        .replace("<checkpoint>", valueOf(checkpoint.id()))
                        ),
                        times(ofMillis(250), ofSeconds(3), ofMillis(250))
                )
        );

        player.playSound(
                player.getLocation(),
                this.configuration.plugin().arenaSoundCheckpointReached,
                this.configuration.plugin().arenaSoundCheckpointReachedVolume,
                this.configuration.plugin().arenaSoundCheckpointReachedPitch
        );
        player.sendMessage(miniMessage().deserialize(
                this.configuration.language().chatMessageArenaCheckpointReached
                        .replace("<checkpoint>", valueOf(checkpoint.id()))
                        .replace("<checkpointName>", this.displayCheckpointName(checkpoint))
        ));

        boolean completedFullSequence = touchedIndex == map.arenaCheckpoints.size() - 1;

        if (!completedFullSequence || !reachedFinishCheckpoint)
            return;

        user.setRoundPoints(user.getRoundPoints() + user.getLives());

        arena.setFinishedRuns(arena.getFinishedRuns() + 1);

        int position = arena.getFinishedRuns(), time = arena.getElapsedTime();
        this.server.getPluginManager().callEvent(
                new UserArenaFinishedEvent(user, time, position)
        );

        this.rewardService.rewardRunnerFinish(player, runtime.mapId(), position);

        if (position == 1)
            if (arena.getRemainingTime() >= 60)
                arena.setRemainingTime(60);

        player.showTitle(
                title(
                        miniMessage().deserialize(
                                this.configuration.language().arenaFinishTitle
                                        .replace("<position>", valueOf(position))
                                        .replace("<seconds>", valueOf(time))
                        ),
                        miniMessage().deserialize(
                                this.configuration.language().arenaFinishSubtitle
                                        .replace("<position>", valueOf(position))
                                        .replace("<seconds>", valueOf(time))
                        ),
                        times(ofMillis(250), ofSeconds(3), ofMillis(250))
                )
        );

        player.playSound(
                player.getLocation(),
                this.configuration.plugin().arenaSoundPlayerFinished,
                this.configuration.plugin().arenaSoundPlayerFinishedVolume,
                this.configuration.plugin().arenaSoundPlayerFinishedPitch
        );

        user.setRole(SPECTATOR);
        ofNullable(this.arenaManager.runtimeForPlayer(player))
                .ifPresent((runtimeHandle) -> runtimeHandle.service().removeBackgroundSongPlayer(player));

        player.teleport(map.arenaCheckpoints.get(0).spawn());
        arena.getUsers().stream()
                .map(IUser::asBukkit)
                .filter(Objects::nonNull)
                .forEach((target) -> target.sendMessage(miniMessage().deserialize(
                        this.configuration.language().chatMessageArenaPlayerFinished
                                .replace("<player>", this.safePlayerName(player))
                                .replace("<seconds>", valueOf(time))
                                .replace("<finishPosition>", valueOf(position))
                )));

        this.configuration.language().chatMessageGameEndSpectator.stream()
                .map(miniMessage()::deserialize)
                .forEach((component) -> player.sendMessage(component));

        player.setAllowFlight(true);
        player.setGameMode(ADVENTURE);
        arena.getUsers()
                .stream().map(IUser::asBukkit)
                .filter(Objects::nonNull).forEach(
                        (target) -> target.hidePlayer(this.plugin, player)
                );

        player.getInventory().clear();
        player.getInventory().setItem(
                8, new ItemBuilder(RED_BED)
                        .name(miniMessage().deserialize(this.configuration.language().arenaItemLeaveName))
                        .itemFlags(values())
                        .build()
        );

        BufferedImage parchmentImage = this.plugin instanceof Entrypoint entrypoint
                ? entrypoint.getWinParchmentImage()
                : null;
        this.plugin.getLogger().info("[DR-DBG] Runner finished: player=" + player.getName()
                + " map=" + map.id
                + " position=" + position
                + " timeSeconds=" + time
                + " parchmentLoaded=" + (parchmentImage != null));
        this.winMapManager.giveWinMap(player, parchmentImage, position, time);

    }

        private @Nullable Checkpoint finishCheckpoint(
                        @NotNull MapConfiguration.MapDefinition map
        ) {
                if (map.arenaCheckpoints.isEmpty())
                        return null;

                if (map.arenaFinishCheckpointId == null)
                        return map.arenaCheckpoints.get(map.arenaCheckpoints.size() - 1);

                return map.arenaCheckpoints.stream()
                                .filter((checkpoint) -> checkpoint.id().equals(map.arenaFinishCheckpointId))
                                .findFirst()
                                .orElseGet(() -> map.arenaCheckpoints.get(map.arenaCheckpoints.size() - 1));
        }

    private boolean sweptTouchesCheckpoint(
            @NotNull Checkpoint checkpoint,
            @NotNull Location from,
            @NotNull Location to
    ) {
        if (checkpoint.locations().isEmpty())
            return false;

        if (from.getWorld() == null || to.getWorld() == null || checkpoint.locations().get(0).getWorld() == null)
            return false;

        if (!from.getWorld().getUID().equals(to.getWorld().getUID())
                || !checkpoint.locations().get(0).getWorld().getUID().equals(from.getWorld().getUID()))
            return false;

        int blockMinX = checkpoint.locations().stream().mapToInt(Location::getBlockX).min().orElseThrow();
        int blockMaxX = checkpoint.locations().stream().mapToInt(Location::getBlockX).max().orElseThrow();
        int blockMinY = checkpoint.locations().stream().mapToInt(Location::getBlockY).min().orElseThrow();
        int blockMaxY = checkpoint.locations().stream().mapToInt(Location::getBlockY).max().orElseThrow();
        int blockMinZ = checkpoint.locations().stream().mapToInt(Location::getBlockZ).min().orElseThrow();
        int blockMaxZ = checkpoint.locations().stream().mapToInt(Location::getBlockZ).max().orElseThrow();

        // Sweep the player's feet point against the checkpoint volume expanded by
        // a normal player hitbox (0.6 wide, 1.8 high). This prevents tunnelling
        // at Strafe velocity without granting checkpoints from blocks away.
        final double halfWidth = 0.30;
        final double height = 1.80;

        return SegmentAabb.intersects(
                from.getX(), from.getY(), from.getZ(),
                to.getX(), to.getY(), to.getZ(),
                blockMinX - halfWidth,
                blockMinY - height,
                blockMinZ - halfWidth,
                blockMaxX + 1.0 + halfWidth,
                blockMaxY + 1.0,
                blockMaxZ + 1.0 + halfWidth
        );
    }

    private int checkpointPoints(@NotNull MapConfiguration.MapDefinition map, int index) {
        if (index < 0 || index >= map.arenaCheckpointPoints.size())
            return 0;
        Integer points = map.arenaCheckpointPoints.get(index);
        return points == null ? 0 : Math.max(0, points);
    }

    private @NotNull String safePlayerName(
            @NotNull org.bukkit.entity.Player player
    ) {
        String stripped = ChatColor.stripColor(player.getDisplayName());
        return stripped == null || stripped.isBlank() ? player.getName() : stripped;
    }

        private @NotNull String displayCheckpointName(
                        @NotNull Checkpoint checkpoint
        ) {
                if (checkpoint.name() == null || checkpoint.name().isBlank())
                        return "#" + checkpoint.id();

                return checkpoint.name();
        }

}
