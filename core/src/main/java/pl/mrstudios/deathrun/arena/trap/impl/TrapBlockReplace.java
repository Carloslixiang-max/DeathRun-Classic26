package pl.mrstudios.deathrun.arena.trap.impl;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pl.mrstudios.deathrun.api.arena.trap.annotations.Serializable;
import static org.bukkit.Material.COBBLESTONE;

public final class TrapBlockReplace extends ClassicBlockSwapTrap {
    @Serializable private Material material = COBBLESTONE;
    @Override protected @NotNull Material replacement() { return this.material == null ? COBBLESTONE : this.material; }
    @Override public void setExtra(@Nullable Object... objects) {
        if (objects != null && objects.length > 0 && objects[0] instanceof Material selected)
            this.material = selected;
    }
}
