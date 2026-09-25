package me.catcoder.sidebar;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

public class Sidebar<T> {

    private final Plugin plugin;
    private final Object title;
    private final List<Function<Player, T>> updatableLines = new ArrayList<>();
    private final Set<UUID> viewers = new LinkedHashSet<>();

    public Sidebar(Object title, Plugin plugin) {
        this.title = title;
        this.plugin = plugin;
    }

    public void addViewer(Player player) {
        this.viewers.add(player.getUniqueId());
        this.renderFor(player);
    }

    public void removeViewer(Player player) {
        this.viewers.remove(player.getUniqueId());
        this.resetScoreboard(player);
    }

    public void addUpdatableLine(Function<Player, T> lineSupplier) {
        this.updatableLines.add(lineSupplier);
    }

    public BukkitTask updateLinesPeriodically(long delay, long period) {
        return Bukkit.getScheduler().runTaskTimer(this.plugin, this::updateLinesNow, delay, period);
    }

    public void destroy() {
        for (UUID viewerId : this.viewers) {
            Player player = Bukkit.getPlayer(viewerId);
            if (player == null || !player.isOnline()) {
                continue;
            }

            this.resetScoreboard(player);
        }

        this.viewers.clear();
    }

    private void updateLinesNow() {
        List<UUID> offline = new ArrayList<>();

        for (UUID viewerId : this.viewers) {
            Player player = Bukkit.getPlayer(viewerId);
            if (player == null || !player.isOnline()) {
                offline.add(viewerId);
                continue;
            }

            this.renderFor(player);
        }

        this.viewers.removeAll(offline);
    }

    private void renderFor(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("deathrun", "dummy", this.format(this.title, 32));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        int score = this.updatableLines.size();
        for (int i = 0; i < this.updatableLines.size(); i++) {
            T line = this.updatableLines.get(i).apply(player);
            String base = this.format(line, 40);
            String uniqueLine = this.uniqueLine(base, i);
            objective.getScore(uniqueLine).setScore(score--);
        }

        player.setScoreboard(scoreboard);
    }

    private String format(Object value, int maxLen) {
        String raw = this.stringify(value);
        if (raw.length() <= maxLen) {
            return raw;
        }
        return raw.substring(0, maxLen);
    }

    private String stringify(Object value) {
        if (value instanceof Component component) {
            return LegacyComponentSerializer.legacySection().serialize(component);
        }

        return String.valueOf(value);
    }

    private String uniqueLine(String value, int index) {
        String marker = ChatColor.values()[index % ChatColor.values().length].toString();
        String maxSafe = value;
        if (maxSafe.length() > 38) {
            maxSafe = maxSafe.substring(0, 38);
        }
        return maxSafe + marker;
    }

    private void resetScoreboard(Player player) {
        if (Bukkit.getScoreboardManager() == null) {
            return;
        }

        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }
}
