package pl.mrstudios.deathrun.arena.sign;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pl.mrstudios.deathrun.api.arena.enums.GameState;
import pl.mrstudios.deathrun.arena.ArenaManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.File;

public class SignManager {

    private final Plugin plugin;
    private final ArenaManager arenaManager;
    private final File storageFile;

    private final Map<Location, QueueSign> signs = new ConcurrentHashMap<>();
    private final Map<String, LinkedHashSet<UUID>> queuedPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerQueue = new ConcurrentHashMap<>();
    private final AtomicInteger signIdSequence = new AtomicInteger(1);

    public SignManager(
            @NotNull Plugin plugin,
            @NotNull ArenaManager arenaManager
    ) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.storageFile = new File(this.plugin.getDataFolder(), "signs.yml");

        this.loadSigns();

        this.plugin.getServer().getScheduler().runTaskTimer(
                this.plugin,
                this::updateAllSigns,
                20L,
                20L
        );
    }

    public boolean mapExists(
            @NotNull String mapId
    ) {
        return this.resolveMapIdOrNull(mapId) != null;
    }

    public @Nullable QueueSign signAt(
            @NotNull Location location
    ) {
        return this.signs.get(location);
    }

    public @Nullable QueueSign signAt(
            @NotNull Block block
    ) {
        return this.signAt(block.getLocation());
    }

    public @NotNull QueueSign createJoinSign(
            @NotNull Location location,
            @NotNull String mapId
    ) {
        QueueSign sign = new QueueSign(this.signIdSequence.getAndIncrement(), QueueSignType.JOIN, this.normalizeMapId(mapId), location);
        this.signs.put(location, sign);
        this.saveSigns();
        return sign;
    }

    public @NotNull QueueSign createAutoJoinSign(
            @NotNull Location location
    ) {
        QueueSign sign = new QueueSign(this.signIdSequence.getAndIncrement(), QueueSignType.AUTOJOIN, null, location);
        this.signs.put(location, sign);
        this.saveSigns();
        return sign;
    }

    public @NotNull QueueSign createLeaveSign(
            @NotNull Location location
    ) {
        QueueSign sign = new QueueSign(this.signIdSequence.getAndIncrement(), QueueSignType.LEAVE, null, location);
        this.signs.put(location, sign);
        this.saveSigns();
        return sign;
    }

    public boolean removeSign(
            @NotNull Location location
    ) {
        boolean removed = this.signs.remove(location) != null;
        if (removed)
            this.saveSigns();

        return removed;
    }

    public void shutdown() {
        this.saveSigns();
    }

    public boolean queuePlayerToMap(
            @NotNull Player player,
            @NotNull String mapId
    ) {
        String normalizedMapId = this.resolveMapIdOrNull(mapId);
        if (normalizedMapId == null)
            return false;

        if (this.arenaManager.isMapLockedForEditing(normalizedMapId)) {
            player.sendMessage(ChatColor.RED + "This map is currently unavailable as it is being edited.");
            return false;
        }

        ArenaManager.ArenaRuntime runtime = this.arenaManager.runtimeByMapId(normalizedMapId);
        if (runtime == null)
            return false;

        this.arenaManager.ensureMapWorldBindings(runtime.map());

        if (!this.arenaManager.isMapConfigured(runtime.map()))
            return false;

        GameState gameState = runtime.arena().getGameState();
        if (gameState != GameState.WAITING && gameState != GameState.STARTING)
            return false;

        int maxPlayers = this.arenaManager.maxPlayers(runtime.map());
        int current = runtime.arena().getUsers().size() + this.queuedPlayersCount(normalizedMapId);
        if (current >= maxPlayers)
            return false;

        this.leaveQueue(player);

        this.queuedPlayers
                .computeIfAbsent(normalizedMapId, (ignored) -> new LinkedHashSet<>())
                .add(player.getUniqueId());
        this.playerQueue.put(player.getUniqueId(), normalizedMapId);

        if (runtime.map().arenaWaitingLobbyLocation != null) {
            Location waitingLobby = runtime.map().arenaWaitingLobbyLocation;
            if (waitingLobby.getWorld() == null) {
                String worldName = runtime.map().world;
                World mapWorld = worldName == null || worldName.isBlank() ? null : this.plugin.getServer().getWorld(worldName);
                if (mapWorld == null) {
                    this.plugin.getLogger().severe("[DeathRun] Queue teleport cancelled for player " + player.getName()
                            + " on map " + normalizedMapId
                            + " because waiting lobby location has null world and configured world is unavailable.");
                    player.sendMessage(ChatColor.RED + "This map is currently misconfigured (waiting lobby world missing). Please contact staff.");
                    return true;
                }

                waitingLobby = waitingLobby.clone();
                waitingLobby.setWorld(mapWorld);
                runtime.map().arenaWaitingLobbyLocation = waitingLobby;
            }

            player.teleport(waitingLobby);
        }

        return true;
    }

    public boolean queuePlayerToBestMap(
            @NotNull Player player
    ) {
        Optional<String> bestMap = this.bestJoinableMap();
        if (bestMap.isEmpty())
            return false;

        return this.queuePlayerToMap(player, bestMap.get());
    }

    public boolean leaveQueue(
            @NotNull Player player
    ) {
        String mapId = this.playerQueue.remove(player.getUniqueId());
        if (mapId == null)
            return false;

        LinkedHashSet<UUID> players = this.queuedPlayers.get(mapId);
        if (players == null)
            return false;

        boolean removed = players.remove(player.getUniqueId());
        if (players.isEmpty())
            this.queuedPlayers.remove(mapId);

        return removed;
    }

    public int queuedPlayersCount(
            @NotNull String mapId
    ) {
        LinkedHashSet<UUID> players = this.queuedPlayers.get(this.normalizeMapId(mapId));
        return players == null ? 0 : players.size();
    }

    public void clearQueueForMap(
            @NotNull String mapId
    ) {
        String normalizedMapId = this.normalizeMapId(mapId);
        LinkedHashSet<UUID> players = this.queuedPlayers.remove(normalizedMapId);
        if (players == null || players.isEmpty())
            return;

        for (UUID uniqueId : players)
            this.playerQueue.remove(uniqueId);
    }

    public @NotNull List<Player> drainQueuedPlayers(
            @NotNull String mapId,
            int maxCount
    ) {
        String normalizedMapId = this.normalizeMapId(mapId);
        LinkedHashSet<UUID> players = this.queuedPlayers.get(normalizedMapId);
        if (players == null || players.isEmpty() || maxCount <= 0)
            return List.of();

        List<Player> drainedPlayers = new ArrayList<>();
        Iterator<UUID> iterator = players.iterator();
        while (iterator.hasNext() && drainedPlayers.size() < maxCount) {
            UUID uniqueId = iterator.next();
            iterator.remove();
            this.playerQueue.remove(uniqueId);

            Player player = this.plugin.getServer().getPlayer(uniqueId);
            if (player == null || !player.isOnline())
                continue;

            drainedPlayers.add(player);
        }

        if (players.isEmpty())
            this.queuedPlayers.remove(normalizedMapId);

        return drainedPlayers;
    }

    public @NotNull Optional<String> bestJoinableMap() {
        Collection<ArenaManager.ArenaRuntime> runtimes = this.arenaManager.runtimes();
        return runtimes.stream()
                .filter((runtime) -> this.arenaManager.isMapConfigured(runtime.map()))
            .filter((runtime) -> !this.arenaManager.isMapLockedForEditing(runtime.mapId()))
                .filter((runtime) -> runtime.arena().getGameState() == GameState.WAITING || runtime.arena().getGameState() == GameState.STARTING)
                .filter((runtime) -> runtime.arena().getUsers().size() + this.queuedPlayersCount(runtime.mapId()) < this.arenaManager.maxPlayers(runtime.map()))
                .max(Comparator.comparingInt((runtime) -> runtime.arena().getUsers().size() + this.queuedPlayersCount(runtime.mapId())))
                .map(ArenaManager.ArenaRuntime::mapId);
    }

    public void updateAllSigns() {
        Iterator<Map.Entry<Location, QueueSign>> iterator = this.signs.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Location, QueueSign> entry = iterator.next();
            QueueSign queueSign = entry.getValue();
            boolean autoJoinSign = queueSign.type() == QueueSignType.AUTOJOIN;

            Block block = entry.getKey().getBlock();
            if (!(block.getState() instanceof Sign sign)) {
                iterator.remove();
                continue;
            }

            if (queueSign.type() == QueueSignType.LEAVE) {
                this.setLine(sign, 0, "&c[DR]");
                this.setLine(sign, 1, "&fLeave Queue");
                this.setLine(sign, 2, "");
                this.setLine(sign, 3, "&8Right Click");
                sign.update();
                continue;
            }

            String mapId = queueSign.type() == QueueSignType.JOIN
                    ? Objects.requireNonNullElse(queueSign.mapId(), "")
                    : this.bestJoinableMap().orElse("");

            ArenaManager.ArenaRuntime runtime = this.arenaManager.runtimeByMapId(mapId);
            if (runtime == null) {
                this.setLine(sign, 0, autoJoinSign ? "&8[Autojoin]" : "&8[NotJoinable]");
                this.setLine(sign, 1, autoJoinSign ? "&7No Maps" : "&7Unknown");
                this.setLine(sign, 2, "");
                this.setLine(sign, 3, "");
                //this.setLine(sign, 2, "&7- / -");
                //this.setLine(sign, 3, "&7• In Game •");
                sign.update();
                continue;
            }

            int max = this.arenaManager.maxPlayers(runtime.map());
            int current = Math.min(max, runtime.arena().getUsers().size() + this.queuedPlayersCount(runtime.mapId()));
            String mapName = runtime.map().name == null || runtime.map().name.isBlank() ? runtime.mapId() : runtime.map().name;

            if (runtime.arena().getGameState() == GameState.ENDING) {
                this.setLine(sign, 0, "&4█ █ █ █ █");
                this.setLine(sign, 1, autoJoinSign ? "&4[Autojoin]" : "&4[Restarting]");
                this.setLine(sign, 2, autoJoinSign ? "&4Restarting" : mapName);
                this.setLine(sign, 3, "&4█ █ █ █ █");
                sign.update();
                continue;
            }

            if (runtime.arena().getGameState() == GameState.PLAYING) {
                this.setLine(sign, 0, autoJoinSign ? "&8[Autojoin]" : "&8[NotJoinable]");
                this.setLine(sign, 1, autoJoinSign ? "&7Random Map" : mapName);
                this.setLine(sign, 2, "&f" + current + "/" + max);
                this.setLine(sign, 3, "&7• In Game •");
                sign.update();
                continue;
            }

            if (current >= max) {
                this.setLine(sign, 0, autoJoinSign ? "&4[Auto Full]" : "&4[Full-" + queueSign.id() + "]");
                this.setLine(sign, 1, autoJoinSign ? "&7Random Map" : mapName);
                this.setLine(sign, 2, "&f" + max + "/" + max);
                this.setLine(sign, 3, "&5● Lobby ●");
                sign.update();
                continue;
            }

            this.setLine(sign, 0, autoJoinSign ? "&1[Autojoin]" : "&1[Join-" + queueSign.id() + "]");
            this.setLine(sign, 1, autoJoinSign ? "&7Random Map" : mapName);
            this.setLine(sign, 2, "&8" + current + "/" + max);
            this.setLine(sign, 3, "&5● Lobby ●");
            sign.update();
        }
    }

    private void setLine(
            @NotNull Sign sign,
            int line,
            @NotNull String content
    ) {
        String colored = ChatColor.translateAlternateColorCodes('&', content);
        if (colored.length() > 15)
            colored = colored.substring(0, 15);

        sign.setLine(line, colored);
    }

    private @NotNull String normalizeMapId(
            @NotNull String mapId
    ) {
        return mapId.toLowerCase(Locale.ROOT).replace(" ", "-");
    }

    private @Nullable String resolveMapIdOrNull(
            @NotNull String source
    ) {
        String normalized = this.normalizeMapId(source);

        ArenaManager.ArenaRuntime direct = this.arenaManager.runtimeByMapId(normalized);
        if (direct != null)
            return direct.mapId();

        return this.arenaManager.runtimes().stream()
                .filter((runtime) -> {
                    String mapName = runtime.map().name;
                    if (mapName == null || mapName.isBlank())
                        return false;

                    return this.normalizeMapId(mapName).equals(normalized);
                })
                .findFirst()
                .map(ArenaManager.ArenaRuntime::mapId)
                .orElse(null);
    }

    public enum QueueSignType {
        JOIN,
        AUTOJOIN,
        LEAVE
    }

    public record QueueSign(
            int id,
            @NotNull QueueSignType type,
            @Nullable String mapId,
            @NotNull Location location
    ) {}

    private void loadSigns() {
        this.signs.clear();

        if (!this.storageFile.exists())
            return;

        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.storageFile);
            ConfigurationSection section = yaml.getConfigurationSection("signs");
            if (section == null)
                return;

            int maxId = 0;
            for (String key : section.getKeys(false)) {
                ConfigurationSection signSection = section.getConfigurationSection(key);
                if (signSection == null)
                    continue;

                String typeRaw = signSection.getString("type", "");
                QueueSignType type;
                try {
                    type = QueueSignType.valueOf(typeRaw);
                } catch (Exception ignored) {
                    continue;
                }

                String worldName = signSection.getString("world", "");
                World world = this.plugin.getServer().getWorld(worldName);
                if (world == null)
                    continue;

                int x = signSection.getInt("x");
                int y = signSection.getInt("y");
                int z = signSection.getInt("z");
                int id = signSection.getInt("id", 0);
                String mapId = signSection.getString("map-id");

                Location location = new Location(world, x, y, z);
                this.signs.put(location, new QueueSign(id, type, mapId, location));
                maxId = Math.max(maxId, id);
            }

            this.signIdSequence.set(Math.max(1, maxId + 1));
        } catch (Exception exception) {
            this.plugin.getLogger().warning("Failed to load signs.yml: " + exception.getMessage());
        }
    }

    private void saveSigns() {
        try {
            if (!this.plugin.getDataFolder().exists() && !this.plugin.getDataFolder().mkdirs()) {
                this.plugin.getLogger().warning("Failed to create plugin data folder for sign storage.");
                return;
            }

            YamlConfiguration yaml = new YamlConfiguration();
            int index = 0;
            for (QueueSign sign : this.signs.values()) {
                if (sign.location().getWorld() == null)
                    continue;

                String base = "signs." + index++;
                yaml.set(base + ".id", sign.id());
                yaml.set(base + ".type", sign.type().name());
                yaml.set(base + ".map-id", sign.mapId());
                yaml.set(base + ".world", sign.location().getWorld().getName());
                yaml.set(base + ".x", sign.location().getBlockX());
                yaml.set(base + ".y", sign.location().getBlockY());
                yaml.set(base + ".z", sign.location().getBlockZ());
            }

            yaml.save(this.storageFile);
        } catch (Exception exception) {
            this.plugin.getLogger().warning("Failed to save signs.yml: " + exception.getMessage());
        }
    }

}