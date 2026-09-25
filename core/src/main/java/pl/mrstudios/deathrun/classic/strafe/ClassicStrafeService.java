package pl.mrstudios.deathrun.classic.strafe;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.kyori.adventure.text.minimessage.MiniMessage.miniMessage;
import static org.bukkit.Material.FEATHER;

public final class ClassicStrafeService {

    public static final double HORIZONTAL_VELOCITY = 1.78;
    public static final double VERTICAL_VELOCITY = 0.30;
    public static final long COOLDOWN_MILLIS = 60_000L;

    private final NamespacedKey strafeKey;
    private static final Map<UUID, EnumMap<Direction, Long>> cooldownUntil = new ConcurrentHashMap<>();

    public ClassicStrafeService(@NotNull Plugin plugin) {
        this.strafeKey = new NamespacedKey(plugin, "classic_strafe");
    }

    public void prepareRunner(@NotNull Player player) {
        player.getInventory().setItem(3, item(Direction.LEFT));
        player.getInventory().setItem(4, item(Direction.BACK));
        player.getInventory().setItem(5, item(Direction.RIGHT));
        this.cooldownUntil.remove(player.getUniqueId());
    }

    public void clear(@NotNull Player player) {
        this.cooldownUntil.remove(player.getUniqueId());
        for (int slot = 3; slot <= 5; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (directionOf(item) != null)
                player.getInventory().setItem(slot, null);
        }
    }

    public Direction directionOf(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return null;
        String raw = item.getItemMeta().getPersistentDataContainer().get(this.strafeKey, PersistentDataType.STRING);
        if (raw == null)
            return null;
        try {
            return Direction.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public long remainingMillis(@NotNull Player player, @NotNull Direction direction) {
        long now = System.currentTimeMillis();
        long until = this.cooldownUntil
                .getOrDefault(player.getUniqueId(), new EnumMap<>(Direction.class))
                .getOrDefault(direction, 0L);
        return Math.max(0L, until - now);
    }

    public boolean activate(@NotNull Player player, @NotNull Direction direction) {
        if (remainingMillis(player, direction) > 0L)
            return false;

        float snappedYaw = Math.round(player.getLocation().getYaw() / 90.0f) * 90.0f;
        double radians = Math.toRadians(snappedYaw);
        Vector forward = new Vector(-Math.sin(radians), 0.0, Math.cos(radians));
        Vector right = new Vector(Math.cos(radians), 0.0, Math.sin(radians));

        Vector horizontal = switch (direction) {
            case LEFT -> right.clone().multiply(-1.0);
            case BACK -> forward.clone().multiply(-1.0);
            case RIGHT -> right;
        };
        horizontal.normalize().multiply(HORIZONTAL_VELOCITY).setY(VERTICAL_VELOCITY);

        player.setVelocity(horizontal);
        this.cooldownUntil
                .computeIfAbsent(player.getUniqueId(), ignored -> new EnumMap<>(Direction.class))
                .put(direction, System.currentTimeMillis() + COOLDOWN_MILLIS);
        return true;
    }

    private ItemStack item(Direction direction) {
        ItemStack item = new ItemStack(FEATHER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(miniMessage().deserialize(direction.displayName));
        meta.getPersistentDataContainer().set(this.strafeKey, PersistentDataType.STRING, direction.name());
        item.setItemMeta(meta);
        return item;
    }

    public enum Direction {
        LEFT("<aqua>Left Strafe</aqua>"),
        BACK("<aqua>Back Strafe</aqua>"),
        RIGHT("<aqua>Right Strafe</aqua>");

        private final String displayName;
        Direction(String displayName) { this.displayName = displayName; }

        public String label() {
            return switch (this) {
                case LEFT -> "Left Strafe";
                case BACK -> "Back Strafe";
                case RIGHT -> "Right Strafe";
            };
        }
    }
}
