package pl.mrstudios.deathrun.reward;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.deathrun.config.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RewardService {

    private final Plugin plugin;
    private final Configuration configuration;

    private final Map<String, List<UUID>> finishingOrderByMapId = new ConcurrentHashMap<>();
    private final Map<String, Integer> startingRunnerCountByMapId = new ConcurrentHashMap<>();

    public RewardService(
            @NotNull Plugin plugin,
            @NotNull Configuration configuration
    ) {
        this.plugin = plugin;
        this.configuration = configuration;
    }

    public void resetMatch(
            @NotNull String mapId,
            int runnerCount
    ) {
        this.finishingOrderByMapId.put(mapId, new ArrayList<>());
        this.startingRunnerCountByMapId.put(mapId, Math.max(0, runnerCount));
    }

    public void rewardRunnerFinish(
            @NotNull Player player,
            @NotNull String mapId,
            int position
    ) {
        List<UUID> finished = this.finishingOrderByMapId.computeIfAbsent(mapId, (ignored) -> new ArrayList<>());
        UUID uniqueId = player.getUniqueId();
        if (finished.contains(uniqueId))
            return;

        finished.add(uniqueId);

        List<String> commands = this.commandsForPlacement(position);
        if (commands.isEmpty())
            return;

        String commandPlayer = player.getName();
        for (String command : commands) {
            String consoleCommand = this.replacePlayer(command, commandPlayer);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), consoleCommand);
        }
    }

    public void rewardDeathWin(
            @NotNull String mapId,
            @NotNull List<Player> deathPlayers
    ) {
        int startedRunners = this.startingRunnerCountByMapId.getOrDefault(mapId, 0);
        int finishedRunners = this.finishingOrderByMapId.getOrDefault(mapId, List.of()).size();
        if (startedRunners <= finishedRunners)
            return;

        List<String> commands = this.configuration.plugin().rewards.deathWin;
        if (commands.isEmpty())
            return;

        for (Player player : deathPlayers) {
            if (player == null)
                continue;

            String commandPlayer = player.getName();
            for (String command : commands) {
                String consoleCommand = this.replacePlayer(command, commandPlayer);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), consoleCommand);
            }
        }
    }

    private List<String> commandsForPlacement(int position) {
        return switch (position) {
            case 1 -> this.configuration.plugin().rewards.runnerFirstPlace;
            case 2 -> this.configuration.plugin().rewards.runnerSecondPlace;
            case 3 -> this.configuration.plugin().rewards.runnerThirdPlace;
            default -> this.configuration.plugin().rewards.runnerParticipation;
        };
    }

    private @NotNull String replacePlayer(
            @NotNull String command,
            @NotNull String playerName
    ) {
        return command.replace("%player%", playerName);
    }

}
