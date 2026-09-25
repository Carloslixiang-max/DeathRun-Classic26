package pl.mrstudios.deathrun.player;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerStatisticsService {

    private final File statsFile;
    private final Map<UUID, StatsEntry> statsByPlayer = new ConcurrentHashMap<>();

    public PlayerStatisticsService(
            @NotNull Plugin plugin
    ) {
        this.statsFile = new File(plugin.getDataFolder(), "player-stats.yml");
        this.load();
    }

    public int getWins(
            @NotNull UUID uniqueId
    ) {
        return this.statsByPlayer.getOrDefault(uniqueId, StatsEntry.EMPTY).wins();
    }

    public int getLosses(
            @NotNull UUID uniqueId
    ) {
        return this.statsByPlayer.getOrDefault(uniqueId, StatsEntry.EMPTY).losses();
    }

    public void incrementWins(
            @NotNull UUID uniqueId
    ) {
        StatsEntry current = this.statsByPlayer.getOrDefault(uniqueId, StatsEntry.EMPTY);
        this.statsByPlayer.put(uniqueId, new StatsEntry(current.wins() + 1, current.losses()));
        this.save();
    }

    public void incrementLosses(
            @NotNull UUID uniqueId
    ) {
        StatsEntry current = this.statsByPlayer.getOrDefault(uniqueId, StatsEntry.EMPTY);
        this.statsByPlayer.put(uniqueId, new StatsEntry(current.wins(), current.losses() + 1));
        this.save();
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        this.statsByPlayer.forEach((uniqueId, stats) -> {
            String path = "players." + uniqueId;
            yaml.set(path + ".wins", stats.wins());
            yaml.set(path + ".losses", stats.losses());
        });

        try {
            yaml.save(this.statsFile);
        } catch (Exception ignored) {
        }
    }

    private void load() {
        this.statsByPlayer.clear();
        if (!this.statsFile.exists())
            return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.statsFile);
        if (!yaml.isConfigurationSection("players"))
            return;

        for (String key : yaml.getConfigurationSection("players").getKeys(false)) {
            UUID uniqueId;
            try {
                uniqueId = UUID.fromString(key);
            } catch (IllegalArgumentException exception) {
                continue;
            }

            int wins = Math.max(0, yaml.getInt("players." + key + ".wins", 0));
            int losses = Math.max(0, yaml.getInt("players." + key + ".losses", 0));
            this.statsByPlayer.put(uniqueId, new StatsEntry(wins, losses));
        }
    }

    private record StatsEntry(int wins, int losses) {
        private static final StatsEntry EMPTY = new StatsEntry(0, 0);
    }
}
