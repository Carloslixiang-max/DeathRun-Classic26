package pl.mrstudios.deathrun.player;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerSnapshotService {

    private static final int FORMAT_VERSION = 2;

    private final Plugin plugin;
    private final File recoveryDirectory;
    private final Map<UUID, Scoreboard> liveScoreboards = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> restoring = ConcurrentHashMap.newKeySet();
    private final java.util.Set<UUID> failedRecoveries = ConcurrentHashMap.newKeySet();

    public PlayerSnapshotService(@NotNull Plugin plugin) {
        this.plugin = plugin;
        this.recoveryDirectory = new File(plugin.getDataFolder(), "recovery");
        if (!this.recoveryDirectory.exists() && !this.recoveryDirectory.mkdirs())
            plugin.getLogger().warning("[DeathRun] Could not create recovery directory: " + this.recoveryDirectory);

        this.recoverInterruptedJournalMoves();
    }

    public boolean hasPending(@NotNull UUID playerId) {
        return this.fileFor(playerId).isFile();
    }

    /**
     * A pending file can either belong to the current queue/match session or be
     * an orphan from a previous crash/restart. Only current-session snapshots
     * may be reused when a player transitions from queue -> arena.
     */
    public boolean isCurrentSessionSnapshot(@NotNull UUID playerId) {
        return this.liveScoreboards.containsKey(playerId) && this.hasPending(playerId)
                && !this.failedRecoveries.contains(playerId) && !this.restoring.contains(playerId);
    }

    public boolean isRestoring(UUID playerId) { return this.restoring.contains(playerId); }

    public boolean capture(@NotNull Player player) {
        UUID playerId = player.getUniqueId();
        File target = this.fileFor(playerId);
        if (target.isFile()) {
            this.plugin.getLogger().warning("[DeathRun] Refusing to overwrite pending recovery for " + player.getName());
            return false;
        }

        File temp = new File(this.recoveryDirectory, playerId + ".yml.tmp");
        try {
            this.liveScoreboards.put(playerId, player.getScoreboard());

            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("format-version", FORMAT_VERSION);
            yaml.set("uuid", playerId.toString());
            yaml.set("name", player.getName());

            yaml.set("inventory.storage", Arrays.asList(player.getInventory().getStorageContents()));
            yaml.set("inventory.armor", Arrays.asList(player.getInventory().getArmorContents()));
            yaml.set("inventory.offhand", player.getInventory().getItemInOffHand());
            yaml.set("inventory.held-slot", player.getInventory().getHeldItemSlot());

            Location location = player.getLocation();
            World world = location.getWorld();
            if (world == null) throw new IllegalStateException("player world is unavailable");
            yaml.set("location.world-name", world == null ? null : world.getName());
            yaml.set("location.world-uuid", world == null ? null : world.getUID().toString());
            yaml.set("location.x", location.getX());
            yaml.set("location.y", location.getY());
            yaml.set("location.z", location.getZ());
            yaml.set("location.yaw", location.getYaw());
            yaml.set("location.pitch", location.getPitch());

            yaml.set("gamemode", player.getGameMode().name());
            yaml.set("allow-flight", player.getAllowFlight());
            yaml.set("flying", player.isFlying());
            yaml.set("level", player.getLevel());
            yaml.set("exp", player.getExp());
            yaml.set("total-experience", player.getTotalExperience());
            yaml.set("health", player.getHealth());
            yaml.set("food", player.getFoodLevel());
            yaml.set("saturation", player.getSaturation());
            yaml.set("exhaustion", player.getExhaustion());
            yaml.set("potion-effects", new ArrayList<>(player.getActivePotionEffects()));
            yaml.set("fire-ticks", player.getFireTicks());
            yaml.set("fall-distance", player.getFallDistance());
            yaml.set("walk-speed", player.getWalkSpeed());
            yaml.set("fly-speed", player.getFlySpeed());

            this.writeScoreboard(yaml, player.getScoreboard());
            this.validatePayload(yaml);

            yaml.save(temp);
            try (FileChannel channel = FileChannel.open(temp.toPath(), StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception exception) {
            this.liveScoreboards.remove(playerId);
            try {
                Files.deleteIfExists(temp.toPath());
            } catch (IOException ignored) {
            }
            this.plugin.getLogger().severe(
                    "[DeathRun] Player snapshot capture failed for " + player.getName() + ": " + exception.getMessage()
            );
            return false;
        }
    }

    public boolean restorePending(@NotNull Player player) {
        return !this.hasPending(player.getUniqueId()) || this.restore(player);
    }

    public boolean restore(@NotNull Player player) {
        UUID playerId = player.getUniqueId();
        File file = this.fileFor(playerId);
        if (!file.isFile())
            return true;
        if (player.isDead() || !this.restoring.add(playerId))
            return false;
        this.failedRecoveries.add(playerId);

        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            int formatVersion = yaml.getInt("format-version", -1);
            if (formatVersion < 1 || formatVersion > FORMAT_VERSION)
                throw new IllegalStateException("unsupported recovery format " + formatVersion);
            if (!playerId.toString().equalsIgnoreCase(yaml.getString("uuid", "")))
                throw new IllegalStateException("recovery UUID mismatch");

            RecoveryTransaction.Phase phase = RecoveryTransaction.Phase.valueOf(yaml.getString("recovery-phase", "PENDING"));
            if (phase == RecoveryTransaction.Phase.APPLIED) {
                // Player data was saved before this phase was committed. Only retire the journal.
                Files.delete(file.toPath());
                this.liveScoreboards.remove(playerId);
                this.failedRecoveries.remove(playerId);
                return true;
            }

            this.validatePayload(yaml);
            Location target = this.readLocation(yaml);
            if (target == null || target.getWorld() == null)
                throw new IllegalStateException("snapshot world is unavailable");

            ItemStack[] storage = this.readItemArray(yaml, "inventory.storage", 36);
            ItemStack[] armor = this.readItemArray(yaml, "inventory.armor", 4);
            ItemStack offhand = yaml.getItemStack("inventory.offhand");
            GameMode savedMode = this.readGameMode(yaml);

            RecoveryTransaction.restore(phase, () -> {
                player.closeInventory();
                if (!player.teleport(target))
                    throw new IllegalStateException("teleport to saved location was rejected");
            }, () -> this.savePhase(yaml, file, RecoveryTransaction.Phase.RESTORING), () -> {

                player.getActivePotionEffects().stream()
                        .map(PotionEffect::getType)
                        .toList()
                        .forEach(player::removePotionEffect);

                player.getInventory().clear();
                player.getInventory().setArmorContents(new ItemStack[4]);
                player.getInventory().setItemInOffHand(null);

                player.getInventory().setStorageContents(storage);
                player.getInventory().setArmorContents(armor);
                player.getInventory().setItemInOffHand(offhand);
                player.getInventory().setHeldItemSlot(clamp(yaml.getInt("inventory.held-slot", 0), 0, 8));

                player.setGameMode(savedMode);
                player.setFlying(false);
                boolean allowFlight = yaml.getBoolean("allow-flight", false);
                player.setAllowFlight(allowFlight);
                if (allowFlight && yaml.getBoolean("flying", false))
                    player.setFlying(true);

                player.setLevel(Math.max(0, yaml.getInt("level", 0)));
                player.setExp((float) clamp(yaml.getDouble("exp", 0.0), 0.0, 1.0));
                player.setTotalExperience(Math.max(0, yaml.getInt("total-experience", 0)));
                player.setHealth(Math.max(
                        0.01,
                        Math.min(yaml.getDouble("health", player.getHealth()), player.getMaxHealth())
                ));
                player.setFoodLevel(clamp(yaml.getInt("food", 20), 0, 20));
                player.setSaturation((float) Math.max(0.0, yaml.getDouble("saturation", 5.0)));
                player.setExhaustion((float) Math.max(0.0, yaml.getDouble("exhaustion", 0.0)));
                player.setFireTicks(yaml.getInt("fire-ticks", 0));
                player.setFallDistance((float) Math.max(0.0, yaml.getDouble("fall-distance", 0.0)));
                player.setWalkSpeed((float) clamp(yaml.getDouble("walk-speed", 0.2), -1.0, 1.0));
                player.setFlySpeed((float) clamp(yaml.getDouble("fly-speed", 0.1), -1.0, 1.0));

                for (Object value : yaml.getList("potion-effects", List.of())) {
                    if (value instanceof PotionEffect effect)
                        player.addPotionEffect(effect, true);
                }

                Scoreboard exactLiveScoreboard = this.liveScoreboards.get(playerId);
                if (exactLiveScoreboard != null) {
                    player.setScoreboard(exactLiveScoreboard);
                } else {
                    Scoreboard durableScoreboard = this.readScoreboard(yaml);
                    if (durableScoreboard != null)
                        player.setScoreboard(durableScoreboard);
                }

                player.saveData();
            }, () -> this.savePhase(yaml, file, RecoveryTransaction.Phase.APPLIED),
                    () -> Files.delete(file.toPath()));
            this.liveScoreboards.remove(playerId);
            this.failedRecoveries.remove(playerId);
            this.plugin.getLogger().info("[DeathRun] Restored pre-game state for " + player.getName());
            return true;
        } catch (Exception exception) {
            this.plugin.getLogger().severe(
                    "[DeathRun] Player snapshot restore failed for " + player.getName()
                            + "; recovery file kept: " + exception.getMessage()
            );
            return false;
        } finally {
            this.restoring.remove(playerId);
        }
    }

    private void savePhase(YamlConfiguration yaml, File target, RecoveryTransaction.Phase phase) throws IOException {
        yaml.set("recovery-phase", phase.name());
        File temp = new File(this.recoveryDirectory, target.getName() + ".tmp");
        yaml.save(temp);
        try (FileChannel channel = FileChannel.open(temp.toPath(), StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void writeScoreboard(@NotNull YamlConfiguration yaml, @NotNull Scoreboard scoreboard) {
        if (Bukkit.getScoreboardManager() == null)
            return;

        boolean wasMain = scoreboard == Bukkit.getScoreboardManager().getMainScoreboard();
        yaml.set("scoreboard.was-main", wasMain);
        if (wasMain)
            return;

        List<Map<String, Object>> objectives = new ArrayList<>();
        for (Objective objective : scoreboard.getObjectives()) {
            Map<String, Object> serialized = new LinkedHashMap<>();
            serialized.put("name", objective.getName());
            serialized.put("criteria", objective.getCriteria());
            serialized.put("display-name", objective.getDisplayName());
            serialized.put("display-slot", objective.getDisplaySlot() == null ? null : objective.getDisplaySlot().name());

            List<Map<String, Object>> scores = new ArrayList<>();
            for (String entry : scoreboard.getEntries()) {
                Score score = objective.getScore(entry);
                if (!score.isScoreSet())
                    continue;

                Map<String, Object> serializedScore = new LinkedHashMap<>();
                serializedScore.put("entry", entry);
                serializedScore.put("value", score.getScore());
                scores.add(serializedScore);
            }
            serialized.put("scores", scores);
            objectives.add(serialized);
        }
        yaml.set("scoreboard.objectives", objectives);

        List<Map<String, Object>> teams = new ArrayList<>();
        for (Team team : scoreboard.getTeams()) {
            Map<String, Object> serialized = new LinkedHashMap<>();
            serialized.put("name", team.getName());
            serialized.put("prefix", team.getPrefix());
            serialized.put("suffix", team.getSuffix());
            serialized.put("color", team.getColor() == null ? null : team.getColor().name());
            serialized.put("friendly-fire", team.allowFriendlyFire());
            serialized.put("see-invisible", team.canSeeFriendlyInvisibles());
            serialized.put("entries", new ArrayList<>(team.getEntries()));

            Map<String, String> options = new LinkedHashMap<>();
            for (Team.Option option : Team.Option.values())
                options.put(option.name(), team.getOption(option).name());
            serialized.put("options", options);
            teams.add(serialized);
        }
        yaml.set("scoreboard.teams", teams);
    }

    private @Nullable Scoreboard readScoreboard(@NotNull YamlConfiguration yaml) {
        if (Bukkit.getScoreboardManager() == null)
            return null;

        if (yaml.getBoolean("scoreboard.was-main", true))
            return Bukkit.getScoreboardManager().getMainScoreboard();

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();

        for (Map<?, ?> raw : this.mapList(yaml, "scoreboard.objectives")) {
            String name = this.string(raw.get("name"), "");
            String criteria = this.string(raw.get("criteria"), "dummy");
            String displayName = this.string(raw.get("display-name"), name);
            if (name.isBlank())
                continue;

            try {
                Objective objective = scoreboard.registerNewObjective(name, criteria, displayName);
                String displaySlot = this.string(raw.get("display-slot"), "");
                if (!displaySlot.isBlank()) {
                    try {
                        objective.setDisplaySlot(DisplaySlot.valueOf(displaySlot));
                    } catch (IllegalArgumentException ignored) {
                    }
                }

                Object scoresObject = raw.get("scores");
                if (scoresObject instanceof List<?> scores) {
                    for (Object value : scores) {
                        if (!(value instanceof Map<?, ?> scoreMap))
                            continue;

                        String entry = this.string(scoreMap.get("entry"), "");
                        if (entry.isBlank())
                            continue;

                        int scoreValue = this.integer(scoreMap.get("value"), 0);
                        objective.getScore(entry).setScore(scoreValue);
                    }
                }
            } catch (Exception objectiveFailure) {
                this.plugin.getLogger().warning(
                        "[DeathRun] Could not reconstruct scoreboard objective " + name + ": "
                                + objectiveFailure.getMessage()
                );
            }
        }

        for (Map<?, ?> raw : this.mapList(yaml, "scoreboard.teams")) {
            String name = this.string(raw.get("name"), "");
            if (name.isBlank())
                continue;

            try {
                Team team = scoreboard.registerNewTeam(name);
                team.setPrefix(this.string(raw.get("prefix"), ""));
                team.setSuffix(this.string(raw.get("suffix"), ""));
                team.setAllowFriendlyFire(this.bool(raw.get("friendly-fire"), true));
                team.setCanSeeFriendlyInvisibles(this.bool(raw.get("see-invisible"), false));

                String color = this.string(raw.get("color"), "");
                if (!color.isBlank()) {
                    try {
                        team.setColor(ChatColor.valueOf(color));
                    } catch (IllegalArgumentException ignored) {
                    }
                }

                Object entriesObject = raw.get("entries");
                if (entriesObject instanceof List<?> entries)
                    for (Object entry : entries)
                        if (entry != null)
                            team.addEntry(String.valueOf(entry));

                Object optionsObject = raw.get("options");
                if (optionsObject instanceof Map<?, ?> options) {
                    for (Map.Entry<?, ?> entry : options.entrySet()) {
                        try {
                            Team.Option option = Team.Option.valueOf(String.valueOf(entry.getKey()));
                            Team.OptionStatus status = Team.OptionStatus.valueOf(String.valueOf(entry.getValue()));
                            team.setOption(option, status);
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
            } catch (Exception teamFailure) {
                this.plugin.getLogger().warning(
                        "[DeathRun] Could not reconstruct scoreboard team " + name + ": "
                                + teamFailure.getMessage()
                );
            }
        }

        return scoreboard;
    }

    private @NotNull List<Map<?, ?>> mapList(@NotNull YamlConfiguration yaml, @NotNull String path) {
        List<Map<?, ?>> result = new ArrayList<>();
        for (Object value : yaml.getList(path, List.of())) {
            if (value instanceof Map<?, ?> map)
                result.add(map);
        }
        return result;
    }

    private @NotNull GameMode readGameMode(@NotNull YamlConfiguration yaml) {
        try {
            return GameMode.valueOf(yaml.getString("gamemode", GameMode.SURVIVAL.name()));
        } catch (IllegalArgumentException invalidMode) {
            throw new IllegalStateException("invalid saved game mode", invalidMode);
        }
    }

    private void recoverInterruptedJournalMoves() {
        File[] tempFiles = this.recoveryDirectory.listFiles(
                (directory, name) -> name.endsWith(".yml.tmp")
        );
        if (tempFiles == null || tempFiles.length == 0)
            return;

        for (File temp : tempFiles) {
            String name = temp.getName();
            String playerIdText = name.substring(0, name.length() - ".yml.tmp".length());

            UUID playerId;
            try {
                playerId = UUID.fromString(playerIdText);
            } catch (IllegalArgumentException invalidName) {
                this.plugin.getLogger().warning(
                        "[DeathRun] Ignoring malformed recovery temp journal: " + name
                );
                continue;
            }

            File target = this.fileFor(playerId);
            if (target.isFile()) {
                try {
                    Files.deleteIfExists(temp.toPath());
                } catch (IOException ignored) {
                }
                continue;
            }

            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(temp);
                if (!playerId.toString().equalsIgnoreCase(yaml.getString("uuid", "")))
                    throw new IllegalStateException("recovery UUID mismatch");

                int formatVersion = yaml.getInt("format-version", -1);
                if (formatVersion < 1 || formatVersion > FORMAT_VERSION)
                    throw new IllegalStateException("unsupported recovery format " + formatVersion);

                RecoveryTransaction.Phase phase = RecoveryTransaction.Phase.valueOf(yaml.getString("recovery-phase", "PENDING"));
                if (phase != RecoveryTransaction.Phase.APPLIED) this.validatePayload(yaml);

                try {
                    Files.move(
                            temp.toPath(),
                            target.toPath(),
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING
                    );
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }

                this.plugin.getLogger().warning(
                        "[DeathRun] Recovered interrupted snapshot journal for " + playerId
                );
            } catch (Exception exception) {
                this.plugin.getLogger().severe(
                        "[DeathRun] Could not recover temp snapshot journal " + name
                                + ": " + exception.getMessage()
                );
            }
        }
    }

    private @NotNull File fileFor(@NotNull UUID playerId) {
        return new File(this.recoveryDirectory, playerId + ".yml");
    }

    private void validatePayload(YamlConfiguration yaml) {
        this.readItemArray(yaml, "inventory.storage", 36);
        this.readItemArray(yaml, "inventory.armor", 4);
        Object offhand = yaml.get("inventory.offhand");
        if (offhand != null && !(offhand instanceof ItemStack))
            throw new IllegalStateException("invalid recovery offhand item");
        for (String field : List.of("x", "y", "z", "yaw", "pitch")) {
            Object value = yaml.get("location." + field);
            if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue()))
                throw new IllegalStateException("missing or invalid recovery location." + field);
        }
        for (String field : List.of("health", "exp", "saturation", "exhaustion", "fall-distance", "walk-speed", "fly-speed")) {
            Object value = yaml.get(field);
            if (value != null && (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())))
                throw new IllegalStateException("invalid recovery " + field);
        }
        this.readGameMode(yaml);
    }

    private @Nullable Location readLocation(@NotNull YamlConfiguration yaml) {
        World world = null;
        String uuid = yaml.getString("location.world-uuid");
        if (uuid != null) {
            try {
                world = Bukkit.getWorld(UUID.fromString(uuid));
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (world == null) {
            String name = yaml.getString("location.world-name");
            if (name != null)
                world = Bukkit.getWorld(name);
        }

        if (world == null)
            return null;

        return new Location(
                world,
                yaml.getDouble("location.x"),
                yaml.getDouble("location.y"),
                yaml.getDouble("location.z"),
                (float) yaml.getDouble("location.yaw"),
                (float) yaml.getDouble("location.pitch")
        );
    }

    private ItemStack[] readItemArray(@NotNull YamlConfiguration yaml, @NotNull String path, int size) {
        ItemStack[] output = new ItemStack[size];
        List<?> values = yaml.getList(path);
        if (values == null || values.size() != size)
            throw new IllegalStateException("incomplete recovery " + path + ": expected " + size + " slots");
        for (int i = 0; i < size; i++) {
            Object value = values.get(i);
            if (value instanceof ItemStack item)
                output[i] = item;
            else if (value != null)
                throw new IllegalStateException("invalid recovery item at " + path + "[" + i + "]");
        }
        return output;
    }

    private @NotNull String string(@Nullable Object value, @NotNull String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private int integer(@Nullable Object value, int fallback) {
        if (value instanceof Number number)
            return number.intValue();
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private boolean bool(@Nullable Object value, boolean fallback) {
        if (value instanceof Boolean bool)
            return bool;
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
