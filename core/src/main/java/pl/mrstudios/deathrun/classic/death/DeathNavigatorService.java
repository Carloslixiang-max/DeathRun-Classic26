package pl.mrstudios.deathrun.classic.death;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.deathrun.arena.ArenaManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DeathNavigatorService {

    private static final Map<UUID, Integer> SELECTED_TRAP = new ConcurrentHashMap<>();
    private final NamespacedKey actionKey;

    public DeathNavigatorService(@NotNull Plugin plugin) {
        this.actionKey = new NamespacedKey(plugin, "death_control");
    }

    public void prepareDeath(@NotNull Player player) {
        player.getInventory().setItem(0, item(Material.ARROW, "<yellow>Previous Trap</yellow>", Action.PREVIOUS));
        player.getInventory().setItem(1, item(Material.BLAZE_POWDER, "<red>Activate</red>", Action.ACTIVATE));
        player.getInventory().setItem(2, item(Material.BLAZE_POWDER, "<red>Activate</red>", Action.ACTIVATE));
        player.getInventory().setItem(3, item(Material.BLAZE_POWDER, "<red>Activate</red>", Action.ACTIVATE));
        player.getInventory().setItem(4, item(Material.ENDER_PEARL, "<aqua>Trap Jumper</aqua>", Action.JUMP));
        player.getInventory().setItem(5, item(Material.BLAZE_POWDER, "<red>Activate</red>", Action.ACTIVATE));
        player.getInventory().setItem(6, item(Material.BLAZE_POWDER, "<red>Activate</red>", Action.ACTIVATE));
        player.getInventory().setItem(7, item(Material.BLAZE_POWDER, "<red>Activate</red>", Action.ACTIVATE));
        player.getInventory().setItem(8, item(Material.ARROW, "<yellow>Next Trap</yellow>", Action.NEXT));
        SELECTED_TRAP.put(player.getUniqueId(), 0);
    }

    public Action actionOf(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return null;
        String raw = item.getItemMeta().getPersistentDataContainer().get(this.actionKey, PersistentDataType.STRING);
        if (raw == null)
            return null;
        try { return Action.valueOf(raw); } catch (IllegalArgumentException ignored) { return null; }
    }

    public int selectedIndex(@NotNull Player player, @NotNull ArenaManager.ArenaRuntime runtime) {
        int size = runtime.map().arenaTraps.size();
        if (size <= 0)
            return -1;
        return Math.floorMod(SELECTED_TRAP.getOrDefault(player.getUniqueId(), 0), size);
    }

    public int previous(@NotNull Player player, @NotNull ArenaManager.ArenaRuntime runtime) {
        int current = selectedIndex(player, runtime);
        if (current < 0) return -1;
        int next = Math.floorMod(current - 1, runtime.map().arenaTraps.size());
        SELECTED_TRAP.put(player.getUniqueId(), next);
        return next;
    }

    public int next(@NotNull Player player, @NotNull ArenaManager.ArenaRuntime runtime) {
        int current = selectedIndex(player, runtime);
        if (current < 0) return -1;
        int next = Math.floorMod(current + 1, runtime.map().arenaTraps.size());
        SELECTED_TRAP.put(player.getUniqueId(), next);
        return next;
    }

    public boolean jump(@NotNull Player player, @NotNull ArenaManager.ArenaRuntime runtime) {
        int index = selectedIndex(player, runtime);
        if (index < 0)
            return false;

        Location button = runtime.map().arenaTraps.get(index).getButton();
        if (button == null || button.getWorld() == null)
            return false;

        Location target = this.safeLandingNear(button, player.getLocation().getYaw(), player.getLocation().getPitch());
        return target != null && player.teleport(target);
    }

    private Location safeLandingNear(@NotNull Location button, float yaw, float pitch) {
        int baseX = button.getBlockX();
        int baseY = button.getBlockY();
        int baseZ = button.getBlockZ();

        int[][] offsets = {
                {0, 1, 0},
                {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
                {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1},
                {1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1},
                {0, 2, 0}
        };

        for (int[] offset : offsets) {
            int x = baseX + offset[0];
            int y = baseY + offset[1];
            int z = baseZ + offset[2];

            var feet = button.getWorld().getBlockAt(x, y, z);
            var head = button.getWorld().getBlockAt(x, y + 1, z);
            var floor = button.getWorld().getBlockAt(x, y - 1, z);

            if (!feet.isPassable() || !head.isPassable() || !floor.getType().isSolid())
                continue;

            Location target = new Location(button.getWorld(), x + 0.5, y, z + 0.5, yaw, pitch);
            return target;
        }

        return null;
    }

    public @NotNull String selectionLabel(@NotNull Player player, @NotNull ArenaManager.ArenaRuntime runtime) {
        int index = this.selectedIndex(player, runtime);
        if (index < 0)
            return "No traps";

        var trap = runtime.map().arenaTraps.get(index);
        String type = trap.getClass().getSimpleName();
        if (type.startsWith("Trap"))
            type = type.substring(4);
        type = type.replaceAll("([a-z])([A-Z])", "$1 $2");
        return "Trap " + (index + 1) + "/" + runtime.map().arenaTraps.size() + " · " + type;
    }

    public static void clearPlayer(@NotNull UUID playerId) {
        SELECTED_TRAP.remove(playerId);
    }

    private ItemStack item(Material material, String name, Action action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize(name));
        meta.getPersistentDataContainer().set(this.actionKey, PersistentDataType.STRING, action.name());
        item.setItemMeta(meta);
        return item;
    }

    public enum Action { PREVIOUS, NEXT, JUMP, ACTIVATE }
}
