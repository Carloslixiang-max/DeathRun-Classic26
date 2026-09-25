package pl.mrstudios.deathrun.arena.listener;

import net.kyori.adventure.platform.bukkit.BukkitAudiences;
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

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    private final BukkitAudiences audiences;
    private final Configuration configuration;
        private final WinMapManager winMapManager;
        private final RewardService rewardService;
        private final Map<UUID, Integer> progressionByPlayer = new ConcurrentHashMap<>();

    @Inject
    public ArenaCheckpointReachedListener(
            @NotNull ArenaManager arenaManager,
            @NotNull Plugin plugin,
            @NotNull Server server,
            @NotNull BukkitAudiences audiences,
                        @NotNull Configuration configuration,
                        @NotNull WinMapManager winMapManager,
                        @NotNull RewardService rewardService
    ) {
        this.arenaManager = arenaManager;
        this.plugin = plugin;
        this.server = server;
        this.audiences = audiences;
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

                this.processCheckpoint(event.getPlayer(), event.getTo(), "move");

    }

    @EventHandler(priority = MONITOR)
    public void onPlayerPortal(
            @NotNull PlayerPortalEvent event
    ) {
                this.processCheckpoint(event.getPlayer(), event.getFrom(), "portal");
        }

        @EventHandler(priority = MONITOR)
        public void onPlayerTeleport(
                        @NotNull PlayerTeleportEvent event
        ) {
                if (event.getTo() == null)
                        return;

                this.processCheckpoint(event.getPlayer(), event.getTo(), "teleport");
    }

    private void processCheckpoint(
            @NotNull org.bukkit.entity.Player player,
                        @NotNull Location probeLocation,
                        @NotNull String source
    ) {
        Arena arena = this.arenaManager.arenaForPlayer(player);
        ArenaManager.ArenaRuntime runtime = this.arenaManager.runtimeForPlayer(player);
        MapConfiguration.MapDefinition map = this.arenaManager.mapForPlayer(player);
                if (arena == null || map == null || runtime == null) {
                        this.progressionByPlayer.remove(player.getUniqueId());
            return;
                }

                                if (arena.getGameState() != PLAYING) {
                                                this.progressionByPlayer.remove(player.getUniqueId());
                        return;
                                }

        if (map.arenaCheckpoints.isEmpty())
            return;

        IUser user = arena.getUser(player);
                                if (user == null) {
                        this.progressionByPlayer.remove(player.getUniqueId());
            return;
                                }

                                if (user.getRole() != RUNNER) {
                        this.progressionByPlayer.remove(player.getUniqueId());
            return;
                                }

                int touchedIndex = -1;
                for (int i = 0; i < map.arenaCheckpoints.size(); i++) {
                        if (!this.isInsideCheckpointRegion(map.arenaCheckpoints.get(i), probeLocation))
                                continue;

                        touchedIndex = i;
                        break;
                }

                                if (touchedIndex < 0)
                        return;

                int lastCompletedIndex = this.progressionByPlayer.getOrDefault(player.getUniqueId(), -1);
                int expectedIndex = lastCompletedIndex + 1;

                if (touchedIndex != expectedIndex)
                        return;

                Checkpoint checkpoint = map.arenaCheckpoints.get(touchedIndex);
                this.progressionByPlayer.put(player.getUniqueId(), touchedIndex);

        UserArenaCheckpointEvent userArenaCheckpointEvent = new UserArenaCheckpointEvent(user, checkpoint);
        this.server.getPluginManager().callEvent(userArenaCheckpointEvent);

        user.setCheckpoint(checkpoint);
        this.audiences.player(player).showTitle(
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
        this.audiences.player(player).sendMessage(miniMessage().deserialize(
                this.configuration.language().chatMessageArenaCheckpointReached
                        .replace("<checkpoint>", valueOf(checkpoint.id()))
                        .replace("<checkpointName>", this.displayCheckpointName(checkpoint))
        ));

        Checkpoint finishCheckpoint = this.finishCheckpoint(map);
        int finishIndex = finishCheckpoint == null ? -1 : map.arenaCheckpoints.indexOf(finishCheckpoint);
        boolean completedFullSequence = touchedIndex == map.arenaCheckpoints.size() - 1;
        boolean reachedFinishCheckpoint = finishIndex >= 0 && touchedIndex == finishIndex;

        if (!completedFullSequence || !reachedFinishCheckpoint)
            return;

        arena.setFinishedRuns(arena.getFinishedRuns() + 1);

        int position = arena.getFinishedRuns(), time = arena.getElapsedTime();
        this.server.getPluginManager().callEvent(
                new UserArenaFinishedEvent(user, time, position)
        );

        this.rewardService.rewardRunnerFinish(player, runtime.mapId(), position);

        if (position == 1)
            if (arena.getRemainingTime() >= 60)
                arena.setRemainingTime(60);

        this.audiences.player(player).showTitle(
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
                .forEach((target) -> this.audiences.player(target).sendMessage(miniMessage().deserialize(
                        this.configuration.language().chatMessageArenaPlayerFinished
                                .replace("<player>", this.safePlayerName(player))
                                .replace("<seconds>", valueOf(time))
                                .replace("<finishPosition>", valueOf(position))
                )));

        this.configuration.language().chatMessageGameEndSpectator.stream()
                .map(miniMessage()::deserialize)
                .forEach((component) -> this.audiences.player(player).sendMessage(component));

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

    private boolean isInsideCheckpointRegion(
            @NotNull Checkpoint checkpoint,
            @NotNull Location playerLoc
    ) {
        if (checkpoint.locations().isEmpty())
            return false;

        if (playerLoc.getWorld() == null || checkpoint.locations().get(0).getWorld() == null)
            return false;

        if (!checkpoint.locations().get(0).getWorld().getUID().equals(playerLoc.getWorld().getUID()))
            return false;

        int minX = checkpoint.locations().stream().mapToInt(Location::getBlockX).min().orElseThrow() - 1;
        int maxX = checkpoint.locations().stream().mapToInt(Location::getBlockX).max().orElseThrow() + 1;
        int minY = checkpoint.locations().stream().mapToInt(Location::getBlockY).min().orElseThrow() - 1;
        int maxY = checkpoint.locations().stream().mapToInt(Location::getBlockY).max().orElseThrow() + 2;
        int minZ = checkpoint.locations().stream().mapToInt(Location::getBlockZ).min().orElseThrow() - 1;
        int maxZ = checkpoint.locations().stream().mapToInt(Location::getBlockZ).max().orElseThrow() + 1;

        double px = playerLoc.getX();
        double py = playerLoc.getY();
        double pz = playerLoc.getZ();

        return px >= minX && px <= maxX + 1
                && py >= minY && py <= maxY + 1
                && pz >= minZ && pz <= maxZ + 1;
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
