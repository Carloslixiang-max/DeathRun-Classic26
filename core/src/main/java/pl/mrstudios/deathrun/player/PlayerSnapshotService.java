package pl.mrstudios.deathrun.player;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scoreboard.Scoreboard;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerSnapshotService {

    private static final int FORMAT_VERSION = 1;

    private final Plugin plugin;
    private final File recoveryDirectory;
    private final Map<UUID, Scoreboard> liveScoreboards = new ConcurrentHashMap<>();

    public PlayerSnapshotService(@NotNull Plugin plugin) {
        this.plugin = plugin;
        this.recoveryDirectory = new File(plugin.getDataFolder(), "recovery");
        if (!this.recoveryDirectory.exists() && !this.recoveryDirectory.mkdirs())
            plugin.getLogger().warning("[DeathRun] Could not create recovery directory: " + this.recoveryDirectory);
    }

    public boolean hasPending(@NotNull UUID playerId) {
        return this.fileFor(playerId).isFile();
    }

    public boolean capture(@NotNull Player player) {
        UUID playerId = player.getUniqueId();
        File target = this.fileFor(playerId);
        if (target.isFile()) {
            this.plugin.getLogger().warning("[DeathRun] Refusing to overwrite pending recovery for " + player.getName());
            return false;
        }

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
        yaml.set("scoreboard.was-main", Bukkit.getScoreboardManager() != null
                && player.getScoreboard() == Bukkit.getScoreboardManager().getMainScoreboard());

        File temp = new File(this.recoveryDirectory, playerId + ".yml.tmp");
        try {
            yaml.save(temp);
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception exception) {
            this.liveScoreboards.remove(playerId);
            try { Files.deleteIfExists(temp.toPath()); } catch (IOException ignored) {}
            this.plugin.getLogger().severe("[DeathRun] Player snapshot capture failed for " + player.getName() + ": " + exception.getMessage());
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

        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            if (yaml.getInt("format-version", -1) != FORMAT_VERSION)
                throw new IllegalStateException("unsupported recovery format");
            if (!playerId.toString().equalsIgnoreCase(yaml.getString("uuid", "")))
                throw new IllegalStateException("recovery UUID mismatch");

            Location target = this.readLocation(yaml);
            if (target == null || target.getWorld() == null)
                throw new IllegalStateException("snapshot world is unavailable");

            player.getActivePotionEffects().stream()
                    .map(PotionEffect::getType)
                    .forEach(player::removePotionEffect);
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);
            player.getInventory().setItemInOffHand(null);

            player.getInventory().setStorageContents(this.readItemArray(yaml, "inventory.storage", 36));
            player.getInventory().setArmorContents(this.readItemArray(yaml, "inventory.armor", 4));
            ItemStack offhand = yaml.getItemStack("inventory.offhand");
            player.getInventory().setItemInOffHand(offhand);
            player.getInventory().setHeldItemSlot(clamp(yaml.getInt("inventory.held-slot", 0), 0, 8));

            player.setGameMode(GameMode.valueOf(yaml.getString("gamemode", GameMode.SURVIVAL.name())));
            player.setAllowFlight(yaml.getBoolean("allow-flight", false));
            if (player.getAllowFlight())
                player.setFlying(yaml.getBoolean("flying", false));
            player.setLevel(Math.max(0, yaml.getInt("level", 0)));
            player.setExp((float) clamp(yaml.getDouble("exp", 0.0), 0.0, 1.0));
            player.setTotalExperience(Math.max(0, yaml.getInt("total-experience", 0)));
            player.setHealth(Math.max(0.01, Math.min(yaml.getDouble("health", player.getHealth()), player.getMaxHealth())));
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

            if (!player.teleport(target))
                throw new IllegalStateException("teleport to saved location was rejected");

            Scoreboard originalScoreboard = this.liveScoreboards.remove(playerId);
            if (originalScoreboard != null)
                player.setScoreboard(originalScoreboard);

            Files.delete(file.toPath());
            return true;
        } catch (Exception exception) {
            this.plugin.getLogger().severe("[DeathRun] Player snapshot restore failed for " + player.getName()
                    + "; recovery file kept: " + exception.getMessage());
            return false;
        }
    }

    private @NotNull File fileFor(@NotNull UUID playerId) {
        return new File(this.recoveryDirectory, playerId + ".yml");
    }

    private Location readLocation(YamlConfiguration yaml) {
        World world = null;
        String uuid = yaml.getString("location.world-uuid");
        if (uuid != null) {
            try { world = Bukkit.getWorld(UUID.fromString(uuid)); } catch (IllegalArgumentException ignored) {}
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

    private ItemStack[] readItemArray(YamlConfiguration yaml, String path, int size) {
        ItemStack[] output = new ItemStack[size];
        List<?> values = yaml.getList(path, List.of());
        int limit = Math.min(size, values.size());
        for (int i = 0; i < limit; i++) {
            Object value = values.get(i);
            if (value instanceof ItemStack item)
                output[i] = item;
        }
        return output;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
