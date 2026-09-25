package pl.mrstudios.deathrun.arena;

import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pl.mrstudios.commons.bukkit.item.ItemBuilder;
import pl.mrstudios.deathrun.api.arena.enums.GameState;
import pl.mrstudios.deathrun.api.arena.event.arena.ArenaUserJoinedEvent;
import pl.mrstudios.deathrun.api.arena.event.arena.ArenaUserLeftEvent;
import pl.mrstudios.deathrun.api.arena.user.IUser;
import pl.mrstudios.deathrun.arena.user.User;
import pl.mrstudios.deathrun.arena.pad.TeleportPad;
import pl.mrstudios.deathrun.arena.sign.SignManager;
import pl.mrstudios.deathrun.arena.win.WinMapManager;
import pl.mrstudios.deathrun.config.Configuration;
import pl.mrstudios.deathrun.config.impl.MapConfiguration;
import pl.mrstudios.deathrun.reward.RewardService;

import java.util.*;

import static java.lang.Integer.MAX_VALUE;
import static java.lang.String.valueOf;
import static net.kyori.adventure.text.minimessage.MiniMessage.miniMessage;
import static org.bukkit.GameMode.ADVENTURE;
import static org.bukkit.Material.RED_BED;
import static org.bukkit.inventory.ItemFlag.values;
import static org.bukkit.potion.PotionEffectType.NIGHT_VISION;
import static org.bukkit.potion.PotionEffectType.SATURATION;
import static pl.mrstudios.deathrun.api.arena.enums.GameState.STARTING;
import static pl.mrstudios.deathrun.api.arena.enums.GameState.WAITING;
import static pl.mrstudios.deathrun.api.arena.enums.GameState.PLAYING;
import static pl.mrstudios.deathrun.api.arena.enums.GameState.ENDING;

public class ArenaManager {

    private final Plugin plugin;
    private final Server server;
    private final BukkitAudiences audiences;
    private final Configuration configuration;
    private final WinMapManager winMapManager;
    private final RewardService rewardService;
    private SignManager signManager;

    private final Map<String, ArenaRuntime> runtimesByMapId = new LinkedHashMap<>();
    private final Map<UUID, String> playerMapIndex = new HashMap<>();
    private final Set<String> editLockedMaps = new HashSet<>();

    public ArenaManager(
            @NotNull Plugin plugin,
            @NotNull Server server,
            @NotNull BukkitAudiences audiences,
            @NotNull Configuration configuration,
            @NotNull WinMapManager winMapManager,
            @NotNull RewardService rewardService
    ) {
        this.plugin = plugin;
        this.server = server;
        this.audiences = audiences;
        this.configuration = configuration;
        this.winMapManager = winMapManager;
        this.rewardService = rewardService;
    }

    public void initialize() {
        new ArrayList<>(this.server.getOnlinePlayers()).forEach((player) -> this.leaveCurrentMap(player, false));
        this.playerMapIndex.clear();

        this.runtimesByMapId.values().forEach((runtime) -> runtime.service().cancel());
        this.runtimesByMapId.clear();

        for (MapConfiguration.MapDefinition map : this.configuration.map().resolvedMaps()) {
            this.ensureMapWorldBindings(map);
            String mapId = this.mapId(map);
            Arena arena = new Arena(this.mapName(map));
            ArenaServiceRunnable service = new ArenaServiceRunnable(arena, map, this, this.winMapManager, this.rewardService, this.plugin, this.server, this.audiences, this.configuration);
            service.runTaskTimer(this.plugin, 0, 20);
            this.runtimesByMapId.put(mapId, new ArenaRuntime(mapId, map, arena, service));
        }
    }

    public void setSignManager(
            @NotNull SignManager signManager
    ) {
        this.signManager = signManager;
    }

    public @NotNull Collection<ArenaRuntime> runtimes() {
        return this.runtimesByMapId.values();
    }

    public @Nullable ArenaRuntime runtimeForPlayer(
            @NotNull Player player
    ) {
        String mapId = this.playerMapIndex.get(player.getUniqueId());
        if (mapId == null)
            return null;

        return this.runtimesByMapId.get(mapId);
    }

    public @Nullable ArenaRuntime runtimeByMapId(
            @NotNull String mapId
    ) {
        return this.runtimesByMapId.get(mapId.toLowerCase(Locale.ROOT));
    }

    public boolean isMapLockedForEditing(
            @NotNull String mapId
    ) {
        return this.editLockedMaps.contains(mapId.toLowerCase(Locale.ROOT));
    }

    public boolean isMapLockedForEditing(
            @NotNull MapConfiguration.MapDefinition map
    ) {
        return this.isMapLockedForEditing(this.mapId(map));
    }

    public void setMapEditLocked(
            @NotNull String mapId,
            boolean locked
    ) {
        String normalizedMapId = mapId.toLowerCase(Locale.ROOT);
        if (!locked) {
            this.editLockedMaps.remove(normalizedMapId);
            return;
        }

        this.editLockedMaps.add(normalizedMapId);

        if (this.signManager != null)
            this.signManager.clearQueueForMap(normalizedMapId);

        ArenaRuntime runtime = this.runtimeByMapId(normalizedMapId);
        if (runtime == null)
            return;

        List<Player> players = runtime.arena().getUsers().stream()
                .map(IUser::asBukkit)
                .filter(Objects::nonNull)
                .toList();

        for (Player player : players) {
            this.leaveCurrentMap(player, true);
            this.returnPlayerToHub(player);
            this.audiences.player(player).sendMessage(miniMessage().deserialize(this.configuration.language().mapSelectorMapEditing));
        }
    }

    public @Nullable Location resolveMapLocation(
            @Nullable Location location,
            @NotNull MapConfiguration.MapDefinition map
    ) {
        if (location == null)
            return null;

        if (location.getWorld() != null)
            return location;

        if (map.world == null || map.world.isBlank())
            return location;

        World world = this.server.getWorld(map.world);
        if (world == null)
            return location;

        Location clone = location.clone();
        clone.setWorld(world);
        return clone;
    }

    public @Nullable MapConfiguration.MapDefinition mapForPlayer(
            @NotNull Player player
    ) {
        ArenaRuntime runtime = this.runtimeForPlayer(player);
        return runtime == null ? null : runtime.map();
    }

    public @Nullable Arena arenaForPlayer(
            @NotNull Player player
    ) {
        ArenaRuntime runtime = this.runtimeForPlayer(player);
        return runtime == null ? null : runtime.arena();
    }

    public int playersInMap(
            @NotNull String mapId
    ) {
        ArenaRuntime runtime = this.runtimeByMapId(mapId);
        return runtime == null ? 0 : runtime.arena().getUsers().size();
    }

    public void reloadRuntime(
            @NotNull String mapId
    ) {
        String normalizedMapId = mapId.toLowerCase(Locale.ROOT);
        ArenaRuntime previous = this.runtimesByMapId.remove(normalizedMapId);
        if (previous != null)
            previous.service().cancel();

        MapConfiguration.MapDefinition map = this.configuration.map().getMapById(normalizedMapId);
        if (map == null)
            return;

        this.ensureMapWorldBindings(map);

        Arena arena = new Arena(this.mapName(map));
        ArenaServiceRunnable service = new ArenaServiceRunnable(arena, map, this, this.winMapManager, this.rewardService, this.plugin, this.server, this.audiences, this.configuration);
        service.runTaskTimer(this.plugin, 0, 20);
        this.runtimesByMapId.put(normalizedMapId, new ArenaRuntime(normalizedMapId, map, arena, service));
    }

    public @NotNull JoinResult joinMap(
            @NotNull Player player,
            @NotNull String mapId
    ) {
        if (this.signManager != null)
            this.signManager.leaveQueue(player);

        ArenaRuntime runtime = this.runtimeByMapId(mapId);
        if (runtime == null)
            return JoinResult.MAP_UNAVAILABLE;

        if (this.isMapLockedForEditing(runtime.mapId()))
            return JoinResult.MAP_EDITING;

        if (!this.isMapConfigured(runtime.map()))
            return JoinResult.MAP_NOT_READY;

        if (runtime.arena().getGameState() != WAITING && runtime.arena().getGameState() != STARTING)
            return JoinResult.MATCH_IN_PROGRESS;

        int maxPlayers = this.maxPlayers(runtime.map());
        if (runtime.arena().getUsers().size() >= maxPlayers)
            return JoinResult.MAP_FULL;

        ArenaRuntime previousRuntime = this.runtimeForPlayer(player);
        if (previousRuntime != null && previousRuntime.mapId().equalsIgnoreCase(runtime.mapId()))
            return JoinResult.ALREADY_IN_MAP;

        this.leaveCurrentMap(player, true);

        User user = new User(player);
        runtime.arena().getUsers().add(user);
        this.playerMapIndex.put(player.getUniqueId(), runtime.mapId());

        this.preparePlayerForWaiting(player, runtime.map());

        if (runtime.arena().getSidebar() != null)
            runtime.arena().getSidebar().addViewer(player);

        runtime.arena().getUsers().stream()
                .map(IUser::asBukkit)
                .filter(Objects::nonNull)
                .forEach((target) -> this.audiences.player(target).sendMessage(miniMessage().deserialize(
                        this.configuration.language().chatMessageArenaPlayerJoined
                    .replace("<player>", this.safePlayerName(player))
                                .replace("<currentPlayers>", valueOf(runtime.arena().getUsers().size()))
                                .replace("<maxPlayers>", valueOf(maxPlayers))
                )));

        this.server.getPluginManager().callEvent(new ArenaUserJoinedEvent(user, runtime.arena()));
        return JoinResult.JOINED;
    }

    public @NotNull ForceStartResult forceStartMap(
            @NotNull String mapId
    ) {
        ArenaRuntime runtime = this.runtimeByMapId(mapId);
        if (runtime == null)
            return ForceStartResult.MAP_UNAVAILABLE;

        if (runtime.arena().getGameState() == PLAYING || runtime.arena().getGameState() == ENDING)
            return ForceStartResult.MATCH_ALREADY_RUNNING;

        if (runtime.arena().getUsers().isEmpty())
            return ForceStartResult.NO_PLAYERS;

        return runtime.service().requestForceStart()
                ? ForceStartResult.STARTED
                : ForceStartResult.MATCH_ALREADY_RUNNING;
    }

    public @NotNull ForceStopResult forceStopMap(
            @NotNull String mapId
    ) {
        ArenaRuntime runtime = this.runtimeByMapId(mapId);
        if (runtime == null)
            return ForceStopResult.MAP_UNAVAILABLE;

        boolean stopped = runtime.service().requestStop();
        if (!stopped)
            return ForceStopResult.ALREADY_WAITING;

        List<Player> activePlayers = runtime.arena().getUsers().stream()
                .map(IUser::asBukkit)
                .filter(Objects::nonNull)
                .toList();

        for (Player player : activePlayers) {
            this.leaveCurrentMap(player, false);
            this.returnPlayerToHub(player);
            this.audiences.player(player).sendMessage(miniMessage().deserialize(this.configuration.language().commandMessageStopMovedToHub));
        }

        if (this.signManager != null) {
            for (Player queuedPlayer : this.signManager.drainQueuedPlayers(runtime.mapId(), Integer.MAX_VALUE)) {
                this.returnPlayerToHub(queuedPlayer);
                this.audiences.player(queuedPlayer).sendMessage(miniMessage().deserialize(this.configuration.language().commandMessageStopMovedToHub));
            }
        }

        return ForceStopResult.STOPPED;
    }

    public boolean leaveCurrentMap(
            @NotNull Player player,
            boolean notifyArena
    ) {
        boolean removed = false;

        for (ArenaRuntime runtime : this.runtimesByMapId.values()) {
            IUser user = runtime.arena().getUser(player);
            if (user == null)
                continue;

            runtime.arena().getUsers().remove(user);
            runtime.service().removeBackgroundSongPlayer(player);
            this.winMapManager.reclaimMap(player);

            if (runtime.arena().getSidebar() != null)
                runtime.arena().getSidebar().removeViewer(player);

            if (notifyArena && (runtime.arena().getGameState() == WAITING || runtime.arena().getGameState() == STARTING)) {
                int maxPlayers = this.maxPlayers(runtime.map());
                runtime.arena().getUsers().stream()
                        .map(IUser::asBukkit)
                        .filter(Objects::nonNull)
                        .forEach((target) -> this.audiences.player(target).sendMessage(miniMessage().deserialize(
                                this.configuration.language().chatMessageArenaPlayerLeft
                                        .replace("<player>", this.safePlayerName(player))
                                        .replace("<currentPlayers>", valueOf(runtime.arena().getUsers().size()))
                                        .replace("<maxPlayers>", valueOf(maxPlayers))
                        )));
            }

            this.server.getPluginManager().callEvent(new ArenaUserLeftEvent(user, runtime.arena()));
            removed = true;
        }

        this.playerMapIndex.remove(player.getUniqueId());
        this.resetPlayerScoreboard(player);
        return removed;
    }

    public boolean leaveQueue(
            @NotNull Player player
    ) {
        if (this.signManager == null)
            return false;

        return this.signManager.leaveQueue(player);
    }

    public int applyQueuedPlayersForMapStart(
            @NotNull String mapId
    ) {
        if (this.signManager == null)
            return 0;

        ArenaRuntime runtime = this.runtimeByMapId(mapId);
        if (runtime == null)
            return 0;

        if (this.isMapLockedForEditing(runtime.mapId()))
            return 0;

        int freeSlots = Math.max(0, this.maxPlayers(runtime.map()) - runtime.arena().getUsers().size());
        if (freeSlots <= 0)
            return 0;

        int added = 0;
        for (Player player : this.signManager.drainQueuedPlayers(runtime.mapId(), freeSlots)) {
            ArenaRuntime previousRuntime = this.runtimeForPlayer(player);
            if (previousRuntime != null && !previousRuntime.mapId().equalsIgnoreCase(runtime.mapId()))
                this.leaveCurrentMap(player, true);

            if (runtime.arena().getUser(player) != null)
                continue;

            runtime.arena().getUsers().add(new User(player));
            this.playerMapIndex.put(player.getUniqueId(), runtime.mapId());

            this.preparePlayerForWaiting(player, runtime.map());

            if (runtime.arena().getSidebar() != null)
                runtime.arena().getSidebar().addViewer(player);

            added++;
        }

        return added;
    }

    public int queuedPlayersForMap(
            @NotNull String mapId
    ) {
        if (this.signManager == null)
            return 0;

        return this.signManager.queuedPlayersCount(mapId);
    }

    public void preparePlayerForLobbyTools(
            @NotNull Player player
    ) {
        // Map selector compass intentionally disabled; signs are now primary queue flow.
    }

    public @Nullable Location resolveHubLocation() {
        Location target = this.configuration.plugin().mainHubLocation;
        if (target == null || target.getWorld() == null) {
            List<World> worlds = this.server.getWorlds();
            if (!worlds.isEmpty())
                target = worlds.get(0).getSpawnLocation().toCenterLocation();
        }

        return target;
    }

    public boolean shouldReturnToHubOnJoinOrRespawn(
            @NotNull Player player
    ) {
        ArenaRuntime runtime = this.runtimeForPlayer(player);
        if (runtime != null) {
            if (runtime.arena().getUser(player) == null)
                return true;

            GameState state = runtime.arena().getGameState();
            return state == ENDING || state == WAITING;
        }

        World world = player.getWorld();
        if (world == null)
            return false;

        String currentWorld = world.getName();
        return this.runtimesByMapId.values().stream()
                .map((candidate) -> candidate.map().world)
                .filter((name) -> name != null && !name.isBlank())
                .anyMatch((name) -> name.equalsIgnoreCase(currentWorld));
    }

    public void recoverPlayerToHubIfNeeded(
            @NotNull Player player,
            boolean notify
    ) {
        if (!this.shouldReturnToHubOnJoinOrRespawn(player))
            return;

        this.leaveQueue(player);
        this.leaveCurrentMap(player, false);
        this.returnPlayerToHub(player);
        if (notify)
            this.audiences.player(player).sendMessage(miniMessage().deserialize("<gold>[DR]</gold> <gray>Your previous arena session has ended; you were returned to the hub."));
    }

    public void saveLoadedMapWorlds() {
        for (MapConfiguration.MapDefinition map : this.configuration.map().resolvedMaps()) {
            if (map.world == null || map.world.isBlank())
                continue;

            World world = this.server.getWorld(map.world);
            if (world == null)
                continue;

            world.setAutoSave(true);
            world.save();
        }
    }

        public void returnPlayerToHub(
            @NotNull Player player
        ) {
        Location target = this.resolveHubLocation();

        if (target != null && target.getWorld() != null)
            player.teleport(target);

        player.getActivePotionEffects().stream()
            .map(PotionEffect::getType)
            .forEach(player::removePotionEffect);

        player.getInventory().clear();
        player.setGameMode(ADVENTURE);
        player.setAllowFlight(false);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);

        this.server.getOnlinePlayers().forEach((onlinePlayer) -> {
            onlinePlayer.showPlayer(this.plugin, player);
            player.showPlayer(this.plugin, onlinePlayer);
        });
        }

    public @Nullable Arena primaryArena() {
        return this.runtimesByMapId.values().stream()
                .findFirst()
                .map(ArenaRuntime::arena)
                .orElse(null);
    }

    public int maxPlayers(
            @NotNull MapConfiguration.MapDefinition map
    ) {
        int spawnCapacity = map.arenaRunnerSpawnLocations.size() + map.arenaDeathSpawnLocations.size();
        int configuredMax = map.arenaMaxPlayers != null
                ? map.arenaMaxPlayers
                : this.configuration.plugin().arenaMaxPlayers;

        if (configuredMax <= 0)
            return Math.max(1, spawnCapacity);

        return Math.max(1, configuredMax);
    }

    public boolean isMapConfigured(
            @NotNull MapConfiguration.MapDefinition map
    ) {
        return !map.arenaSetupEnabled
                && map.arenaWaitingLobbyLocation != null
                && !map.arenaRunnerSpawnLocations.isEmpty()
                && !map.arenaDeathSpawnLocations.isEmpty()
                && !map.arenaCheckpoints.isEmpty();
    }

    private void preparePlayerForWaiting(
            @NotNull Player player,
            @NotNull MapConfiguration.MapDefinition map
    ) {
        player.getActivePotionEffects().stream()
                .map(PotionEffect::getType)
                .forEach(player::removePotionEffect);

        player.getInventory().clear();
        player.setGameMode(ADVENTURE);
        player.setAllowFlight(false);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);

        if (map.arenaWaitingLobbyLocation != null) {
            Location waitingLobby = map.arenaWaitingLobbyLocation;
            if (waitingLobby.getWorld() == null) {
                World fallbackWorld = (map.world == null || map.world.isBlank()) ? null : this.server.getWorld(map.world);
                if (fallbackWorld == null) {
                    this.plugin.getLogger().severe("[DeathRun] Waiting teleport cancelled for player " + player.getName()
                            + " because map " + this.mapId(map)
                            + " has waiting lobby location with null world and configured world is unavailable.");
                } else {
                    waitingLobby = waitingLobby.clone();
                    waitingLobby.setWorld(fallbackWorld);
                    map.arenaWaitingLobbyLocation = waitingLobby;
                }
            }

            if (waitingLobby.getWorld() != null)
                player.teleport(waitingLobby);
        }

        player.addPotionEffect(new PotionEffect(SATURATION, MAX_VALUE, 1, false, false, false));
        player.addPotionEffect(new PotionEffect(NIGHT_VISION, MAX_VALUE, 1, false, false, false));

        this.preparePlayerForLobbyTools(player);
        player.getInventory().setItem(
                8,
                new ItemBuilder(RED_BED)
                        .name(miniMessage().deserialize(this.configuration.language().arenaItemLeaveName))
                        .itemFlags(values())
                        .build()
        );
    }

    private @NotNull String mapId(
            @NotNull MapConfiguration.MapDefinition map
    ) {
        if (map.id != null && !map.id.isBlank())
            return map.id.toLowerCase(Locale.ROOT);

        if (map.name == null || map.name.isBlank())
            return "default";

        return map.name.toLowerCase(Locale.ROOT).replace(" ", "-");
    }

    private @NotNull String mapName(
            @NotNull MapConfiguration.MapDefinition map
    ) {
        return (map.name == null || map.name.isBlank()) ? this.mapId(map) : map.name;
    }

    private @NotNull String safePlayerName(
            @NotNull Player player
    ) {
        String stripped = ChatColor.stripColor(player.getDisplayName());
        return stripped == null || stripped.isBlank() ? player.getName() : stripped;
    }

    public void ensureMapWorldBindings(
            @NotNull MapConfiguration.MapDefinition map
        ) {
        map.arenaWaitingLobbyLocation = this.resolveMapLocation(map.arenaWaitingLobbyLocation, map);

        map.arenaRunnerSpawnLocations = map.arenaRunnerSpawnLocations.stream()
            .map((location) -> this.resolveMapLocation(location, map))
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        map.arenaDeathSpawnLocations = map.arenaDeathSpawnLocations.stream()
            .map((location) -> this.resolveMapLocation(location, map))
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        map.arenaStartBarrierBlocks = map.arenaStartBarrierBlocks.stream()
            .map((location) -> this.resolveMapLocation(location, map))
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));

        map.arenaCheckpoints = map.arenaCheckpoints.stream()
            .map((checkpoint) -> new pl.mrstudios.deathrun.arena.checkpoint.Checkpoint(
                checkpoint.id(),
                Objects.requireNonNull(this.resolveMapLocation(checkpoint.spawn(), map), "checkpoint spawn"),
                checkpoint.locations().stream()
                    .map((location) -> Objects.requireNonNull(this.resolveMapLocation(location, map), "checkpoint region location"))
                    .toList(),
                checkpoint.name()
            ))
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));

        map.teleportPads = map.teleportPads.stream()
            .map((pad) -> new TeleportPad(
                Objects.requireNonNull(this.resolveMapLocation(pad.padLocation(), map), "teleport pad location"),
                Objects.requireNonNull(this.resolveMapLocation(pad.teleportLocation(), map), "teleport destination location")
            ))
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));

        map.arenaTraps.forEach((trap) -> {
            trap.setButton(Objects.requireNonNull(this.resolveMapLocation(trap.getButton(), map), "trap button"));
            trap.setLocations(trap.getLocations().stream()
                .map((location) -> Objects.requireNonNull(this.resolveMapLocation(location, map), "trap location"))
                .toList());
        });
    }

    private void resetPlayerScoreboard(
            @NotNull Player player
    ) {
        if (Bukkit.getScoreboardManager() == null)
            return;

        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    public enum JoinResult {
        JOINED,
        ALREADY_IN_MAP,
        MAP_UNAVAILABLE,
        MAP_NOT_READY,
        MAP_FULL,
        MATCH_IN_PROGRESS,
        MAP_EDITING
    }

    public enum ForceStartResult {
        STARTED,
        MAP_UNAVAILABLE,
        NO_PLAYERS,
        MATCH_ALREADY_RUNNING
    }

    public enum ForceStopResult {
        STOPPED,
        MAP_UNAVAILABLE,
        ALREADY_WAITING
    }

    public record ArenaRuntime(
            @NotNull String mapId,
            @NotNull MapConfiguration.MapDefinition map,
            @NotNull Arena arena,
            @NotNull ArenaServiceRunnable service
    ) {}

}
