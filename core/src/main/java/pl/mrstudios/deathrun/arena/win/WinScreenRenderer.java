package pl.mrstudios.deathrun.arena.win;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapFont;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.map.MinecraftFont;
import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WinScreenRenderer extends MapRenderer {

    private final BufferedImage background;
    private final String playerName;
    private final int position;
    private final int timeSeconds;
    private final Set<UUID> renderedPlayers = ConcurrentHashMap.newKeySet();

    public WinScreenRenderer(
            @NotNull BufferedImage background,
            @NotNull String playerName,
            int position,
            int timeSeconds
    ) {
        super(false);
        this.background = background;
        this.playerName = playerName;
        this.position = position;
        this.timeSeconds = Math.max(0, timeSeconds);
    }

    @Override
    public void render(
            @NotNull MapView map,
            @NotNull MapCanvas canvas,
            @NotNull Player player
    ) {
        if (!this.renderedPlayers.add(player.getUniqueId()))
            return;

        canvas.drawImage(0, 0, this.background);

        MapFont font = MinecraftFont.Font;
        canvas.drawText(10, 14, font, "DeathRun Victory");
        canvas.drawText(10, 38, font, "Player: " + this.playerName);
        canvas.drawText(10, 54, font, "Position: #" + this.position);
        canvas.drawText(10, 70, font, "Time: " + this.formatTime(this.timeSeconds));
    }

    private @NotNull String formatTime(
            int secondsTotal
    ) {
        int minutes = secondsTotal / 60;
        int seconds = secondsTotal % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

}
