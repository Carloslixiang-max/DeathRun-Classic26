package pl.mrstudios.deathrun.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pl.mrstudios.deathrun.api.arena.checkpoint.ICheckpoint;
import pl.mrstudios.deathrun.api.arena.enums.GameState;
import pl.mrstudios.deathrun.api.arena.user.IUser;
import pl.mrstudios.deathrun.api.arena.user.enums.Role;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.config.impl.MapConfiguration;
import pl.mrstudios.deathrun.player.PlayerStatisticsService;
import pl.mrstudios.deathrun.plugin.Entrypoint;

public class DeathRunPlaceholderExpansion extends PlaceholderExpansion {

    private final Entrypoint plugin;
    private final ArenaManager arenaManager;
    private final PlayerStatisticsService playerStatisticsService;

    public DeathRunPlaceholderExpansion(
            @NotNull Entrypoint plugin
    ) {
        this.plugin = plugin;
        this.arenaManager = plugin.getArenaManager();
        this.playerStatisticsService = plugin.getPlayerStatisticsService();
    }

    @Override
    public @NotNull String getIdentifier() {
        return "deathrun";
    }

    @Override
    public @NotNull String getAuthor() {
        return this.plugin.getDescription().getAuthors().isEmpty()
                ? "MrStudios"
                : String.join(", ", this.plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return this.plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @NotNull String onPlaceholderRequest(
            @Nullable Player player,
            @NotNull String identifier
    ) {
        if (identifier.startsWith("map_players_")) {
            String mapId = identifier.substring("map_players_".length());
            return String.valueOf(this.mapPlayers(mapId));
        }

        if (identifier.startsWith("map_status_")) {
            String mapId = identifier.substring("map_status_".length());
            return this.mapStatus(mapId);
        }

        if (identifier.startsWith("map_max_players_")) {
            String mapId = identifier.substring("map_max_players_".length());
            return String.valueOf(this.mapMaxPlayers(mapId));
        }

        if (player == null) {
            return switch (identifier) {
                case "state" -> "NONE";
                case "map" -> "";
                case "checkpoint", "deaths", "wins", "losses" -> "0";
                case "in_game" -> "false";
                default -> "";
            };
        }

        ArenaManager.ArenaRuntime runtime = this.arenaManager.runtimeForPlayer(player);
        IUser user = runtime == null ? null : runtime.arena().getUser(player);

        return switch (identifier) {
            case "state" -> this.playerState(runtime, user);
            case "map" -> runtime == null ? "" : this.mapName(runtime.map());
            case "checkpoint" -> String.valueOf(this.checkpointIndex(user));
            case "deaths" -> String.valueOf(user == null ? 0 : user.getDeaths());
            case "wins" -> String.valueOf(this.playerStatisticsService.getWins(player.getUniqueId()));
            case "losses" -> String.valueOf(this.playerStatisticsService.getLosses(player.getUniqueId()));
            case "in_game" -> String.valueOf(this.isInActiveMatch(runtime));
            default -> "";
        };
    }

    private int mapPlayers(
            @NotNull String mapId
    ) {
        MapConfiguration.MapDefinition map = this.findMap(mapId);
        if (map == null)
            return 0;

        String normalizedMapId = this.normalizedMapId(map);
        int players = this.arenaManager.playersInMap(normalizedMapId);
        int queued = this.arenaManager.queuedPlayersForMap(normalizedMapId);
        return players + queued;
    }

    private int mapMaxPlayers(
            @NotNull String mapId
    ) {
        MapConfiguration.MapDefinition map = this.findMap(mapId);
        if (map == null)
            return 0;

        int configuredMax = map.arenaMaxPlayers != null
                ? map.arenaMaxPlayers
                : this.plugin.getConfiguration().plugin().arenaMaxPlayers;

        if (configuredMax > 0)
            return configuredMax;

        int spawnCapacity = map.arenaRunnerSpawnLocations.size() + map.arenaDeathSpawnLocations.size();
        return Math.max(1, spawnCapacity);
    }

    private @NotNull String mapStatus(
            @NotNull String mapId
    ) {
        MapConfiguration.MapDefinition map = this.findMap(mapId);
        if (map == null)
            return "UNKNOWN";

        if (map.arenaSetupEnabled)
            return "DISABLED";

        ArenaManager.ArenaRuntime runtime = this.arenaManager.runtimeByMapId(mapId);
        if (runtime == null)
            return "UNKNOWN";

        GameState state = runtime.arena().getGameState();
        if (state == GameState.PLAYING || state == GameState.ENDING)
            return "IN_PROGRESS";

        if (state == GameState.WAITING || state == GameState.STARTING)
            return "WAITING";

        return state.name();
    }

    private @Nullable MapConfiguration.MapDefinition findMap(
            @NotNull String mapId
    ) {
        String normalizedInput = this.plugin.getConfiguration().map().normalizedMapId(mapId);
        for (MapConfiguration.MapDefinition map : this.plugin.getConfiguration().map().resolvedMaps()) {
            if (map.id != null && this.plugin.getConfiguration().map().normalizedMapId(map.id).equalsIgnoreCase(normalizedInput))
                return map;

            if (map.name != null && this.plugin.getConfiguration().map().normalizedMapId(map.name).equalsIgnoreCase(normalizedInput))
                return map;
        }

        return null;
    }

    private @NotNull String normalizedMapId(
            @NotNull MapConfiguration.MapDefinition map
    ) {
        if (map.id != null && !map.id.isBlank())
            return this.plugin.getConfiguration().map().normalizedMapId(map.id);

        if (map.name != null && !map.name.isBlank())
            return this.plugin.getConfiguration().map().normalizedMapId(map.name);

        return "default";
    }

    private @NotNull String playerState(
            @Nullable ArenaManager.ArenaRuntime runtime,
            @Nullable IUser user
    ) {
        if (runtime == null || user == null)
            return "NONE";

        if (user.getRole() == Role.SPECTATOR)
            return "SPECTATING";

        if (user.getRole() == Role.DEATH)
            return "DEAD";

        GameState state = runtime.arena().getGameState();
        if (state == GameState.PLAYING || state == GameState.ENDING)
            return "RUNNING";

        return "WAITING";
    }

    private int checkpointIndex(
            @Nullable IUser user
    ) {
        if (user == null)
            return 0;

        ICheckpoint checkpoint;
        try {
            checkpoint = user.getCheckpoint();
        } catch (Exception exception) {
            return 0;
        }

        if (checkpoint == null || checkpoint.id() == null)
            return 0;

        return Math.max(0, checkpoint.id());
    }

    private boolean isInActiveMatch(
            @Nullable ArenaManager.ArenaRuntime runtime
    ) {
        if (runtime == null)
            return false;

        GameState state = runtime.arena().getGameState();
        return state == GameState.STARTING || state == GameState.PLAYING || state == GameState.ENDING;
    }

    private @NotNull String mapName(
            @NotNull MapConfiguration.MapDefinition map
    ) {
        if (map.name != null && !map.name.isBlank())
            return map.name;

        return map.id == null ? "" : map.id;
    }
}
