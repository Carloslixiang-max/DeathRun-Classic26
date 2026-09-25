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

public class LoseScreenRenderer extends MapRenderer {

    private final BufferedImage background;
    private final String playerName;
    private final int deaths;
    private final Set<UUID> renderedPlayers = ConcurrentHashMap.newKeySet();

    public LoseScreenRenderer(
            @NotNull BufferedImage background,
            @NotNull String playerName,
            int deaths
    ) {
        super(false);
        this.background = background;
        this.playerName = playerName;
        this.deaths = Math.max(0, deaths);
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
        canvas.drawText(10, 14, font, "DeathRun Defeat");
        canvas.drawText(10, 38, font, "Player: " + this.playerName);
        canvas.drawText(10, 54, font, "Status: You Died");
        canvas.drawText(10, 70, font, "Deaths: " + this.deaths);
    }

}
