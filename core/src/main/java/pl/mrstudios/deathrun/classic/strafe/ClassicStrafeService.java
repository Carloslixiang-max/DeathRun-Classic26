package pl.mrstudios.deathrun.classic.strafe;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
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

    private final Plugin plugin;
    private final NamespacedKey strafeKey;
    private static final Map<UUID, EnumMap<Direction, Long>> cooldownUntil = new ConcurrentHashMap<>();
    private static final Map<UUID, BukkitTask> displayTasks = new ConcurrentHashMap<>();

    public ClassicStrafeService(@NotNull Plugin plugin) {
        this.plugin = plugin;
        this.strafeKey = new NamespacedKey(plugin, "classic_strafe");
    }

    public void prepareRunner(@NotNull Player player) {
        UUID playerId = player.getUniqueId();
        clearCooldowns(playerId);

        player.getInventory().setItem(3, item(Direction.LEFT, 0L));
        player.getInventory().setItem(4, item(Direction.BACK, 0L));
        player.getInventory().setItem(5, item(Direction.RIGHT, 0L));

        BukkitTask displayTask = this.plugin.getServer().getScheduler().runTaskTimer(
                this.plugin,
                () -> {
                    if (!player.isOnline()) {
                        clearCooldowns(playerId);
                        return;
                    }
                    this.refreshDisplay(player);
                },
                0L,
                20L
        );
        displayTasks.put(playerId, displayTask);
    }

    public static void clearCooldowns(@NotNull UUID playerId) {
        cooldownUntil.remove(playerId);
        BukkitTask task = displayTasks.remove(playerId);
        if (task != null)
            task.cancel();
    }

    public void clear(@NotNull Player player) {
        clearCooldowns(player.getUniqueId());
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
        ClassicStrafeMath.Horizontal horizontalDirection = ClassicStrafeMath.horizontal(snappedYaw, direction);
        Vector velocity = new Vector(
                horizontalDirection.x() * HORIZONTAL_VELOCITY,
                VERTICAL_VELOCITY,
                horizontalDirection.z() * HORIZONTAL_VELOCITY
        );

        player.setVelocity(velocity);
        this.cooldownUntil
                .computeIfAbsent(player.getUniqueId(), ignored -> new EnumMap<>(Direction.class))
                .put(direction, System.currentTimeMillis() + COOLDOWN_MILLIS);
        this.refreshDisplay(player);
        return true;
    }

    private void refreshDisplay(@NotNull Player player) {
        Direction[] directions = { Direction.LEFT, Direction.BACK, Direction.RIGHT };
        for (int i = 0; i < directions.length; i++) {
            Direction direction = directions[i];
            long remaining = this.remainingMillis(player, direction);
            ItemStack current = player.getInventory().getItem(3 + i);

            // Only rewrite the slot while it is still one of our own Strafe
            // items; this avoids fighting an administrator/debug edit.
            if (current != null && this.directionOf(current) == direction)
                player.getInventory().setItem(3 + i, item(direction, remaining));
        }
    }

    private ItemStack item(Direction direction, long remainingMillis) {
        long seconds = remainingMillis <= 0L ? 0L : (remainingMillis + 999L) / 1000L;
        int amount = seconds <= 0L ? 1 : (int) Math.max(1L, Math.min(64L, seconds));

        ItemStack item = new ItemStack(FEATHER, amount);
        ItemMeta meta = item.getItemMeta();
        if (seconds <= 0L) {
            meta.displayName(miniMessage().deserialize(direction.displayName));
        } else {
            meta.displayName(miniMessage().deserialize(
                    "<red>" + direction.label() + "</red> <gray>(" + seconds + "s)</gray>"
            ));
        }
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
