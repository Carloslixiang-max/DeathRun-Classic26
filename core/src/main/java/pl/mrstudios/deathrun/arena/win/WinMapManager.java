package pl.mrstudios.deathrun.arena.win;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public class WinMapManager {

    private Plugin plugin;
    private final Deque<MapView> pooledViews = new ArrayDeque<>();
    private final Map<UUID, MapView> activeViewsByPlayer = new HashMap<>();
    private final Map<UUID, ItemStack> replacedMainHandByPlayer = new HashMap<>();

    public void initialize(
            @NotNull Plugin plugin,
            int poolSize
    ) {
        this.plugin = plugin;
        this.pooledViews.clear();
        this.activeViewsByPlayer.clear();
        this.replacedMainHandByPlayer.clear();

        List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) {
            this.plugin.getLogger().warning("Win map pool initialization skipped: no loaded worlds.");
            return;
        }

        World world = worlds.get(0);
        int targetSize = Math.max(1, poolSize);
        for (int i = 0; i < targetSize; i++)
            this.pooledViews.add(this.createView(world));

        this.plugin.getLogger().info("[DR-DBG] Win map pool initialized: size="
            + this.pooledViews.size() + " world=" + world.getName());
    }

    public void giveWinMap(
            @NotNull Player player,
            @Nullable BufferedImage background,
            int position,
            int timeSeconds
    ) {
        this.giveResultMap(
                player,
                background,
                (image) -> new WinScreenRenderer(image, player.getName(), position, timeSeconds),
                "win",
                "position=" + position + " timeSeconds=" + timeSeconds
        );
    }

    public void giveLoseMap(
            @NotNull Player player,
            @Nullable BufferedImage background,
            int deaths
    ) {
        this.giveResultMap(
                player,
                background,
                (image) -> new LoseScreenRenderer(image, player.getName(), deaths),
                "lose",
                "deaths=" + deaths
        );
    }

    public void reclaimMap(
            @NotNull Player player
    ) {
        UUID playerId = player.getUniqueId();
        MapView view = this.activeViewsByPlayer.remove(playerId);
        ItemStack replacedMainHand = this.replacedMainHandByPlayer.remove(playerId);
        if (view == null)
            return;

        this.clearRenderers(view);
        this.pooledViews.addLast(view);

        if (this.plugin != null)
            this.plugin.getLogger().info("[DR-DBG] Win map reclaimed from " + player.getName()
                + " viewId=" + view.getId()
                + " poolAvailable=" + this.pooledViews.size());

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand.getType() != Material.FILLED_MAP)
            return;

        if (!(mainHand.getItemMeta() instanceof MapMeta mapMeta))
            return;

        MapView handView = mapMeta.getMapView();
        if (handView != null && handView.getId() == view.getId())
            player.getInventory().setItemInMainHand(replacedMainHand == null ? new ItemStack(Material.AIR) : replacedMainHand);
    }

    public void shutdown() {
        if (this.plugin == null)
            return;

        this.plugin.getLogger().info("[DR-DBG] Win map manager shutdown: active="
                + this.activeViewsByPlayer.size() + " pooled=" + this.pooledViews.size());

        List<UUID> active = new ArrayList<>(this.activeViewsByPlayer.keySet());
        for (UUID uuid : active) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null)
                continue;

            this.reclaimMap(player);
        }

        this.activeViewsByPlayer.clear();
        this.pooledViews.clear();
        this.replacedMainHandByPlayer.clear();
    }

    private @NotNull MapView createView(
            @NotNull World world
    ) {
        MapView view = Bukkit.createMap(world);
        this.clearRenderers(view);
        return view;
    }

    private void clearRenderers(
            @NotNull MapView view
    ) {
        List<MapRenderer> renderers = new ArrayList<>(view.getRenderers());
        renderers.forEach(view::removeRenderer);
    }

    private void giveResultMap(
            @NotNull Player player,
            @Nullable BufferedImage background,
            @NotNull Function<BufferedImage, MapRenderer> rendererFactory,
            @NotNull String type,
            @NotNull String details
    ) {
        if (background == null) {
            if (this.plugin != null)
                this.plugin.getLogger().warning("[DR-DBG] Skipping " + type + " map for " + player.getName() + ": background image is null.");
            return;
        }

        this.reclaimMap(player);

        if (this.plugin == null)
            return;

        MapView view = this.pooledViews.pollFirst();
        if (view == null) {
            List<World> worlds = Bukkit.getWorlds();
            if (worlds.isEmpty())
                return;

            this.plugin.getLogger().warning("Win map pool exhausted; creating fallback map view.");
            view = this.createView(worlds.get(0));
        }

        this.clearRenderers(view);
        view.addRenderer(rendererFactory.apply(background));

        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        if (mapItem.getItemMeta() instanceof MapMeta mapMeta) {
            mapMeta.setMapView(view);
            mapItem.setItemMeta(mapMeta);
        }

        ItemStack mainHandBeforeMap = player.getInventory().getItemInMainHand();
        if (mainHandBeforeMap.getType() == Material.AIR)
            this.replacedMainHandByPlayer.put(player.getUniqueId(), null);
        else
            this.replacedMainHandByPlayer.put(player.getUniqueId(), mainHandBeforeMap.clone());

        player.getInventory().setItemInMainHand(mapItem);
        this.activeViewsByPlayer.put(player.getUniqueId(), view);
        this.plugin.getLogger().info("[DR-DBG] " + type + " map assigned to " + player.getName()
                + " viewId=" + view.getId()
                + " " + details
                + " poolRemaining=" + this.pooledViews.size());
    }

}
