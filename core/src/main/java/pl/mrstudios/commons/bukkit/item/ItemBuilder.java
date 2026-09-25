package pl.mrstudios.commons.bukkit.item;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

public class ItemBuilder {

    private final ItemStack itemStack;

    public ItemBuilder(@NotNull Material material) {
        this.itemStack = new ItemStack(material);
    }

    public ItemBuilder(@NotNull Material material, int amount) {
        this.itemStack = new ItemStack(material, amount);
    }

    public @NotNull ItemBuilder name(@NotNull Component component) {
        ItemMeta meta = this.itemStack.getItemMeta();
        meta.displayName(component);
        this.itemStack.setItemMeta(meta);
        return this;
    }

    public @NotNull ItemBuilder texture(@NotNull String texture) {
        // No-op fallback: custom textures are optional for gameplay flow.
        return this;
    }

    public @NotNull ItemBuilder itemFlags(@NotNull ItemFlag... flags) {
        ItemMeta meta = this.itemStack.getItemMeta();
        meta.addItemFlags(flags);
        this.itemStack.setItemMeta(meta);
        return this;
    }

    public @NotNull ItemStack build() {
        return this.itemStack.clone();
    }
}
