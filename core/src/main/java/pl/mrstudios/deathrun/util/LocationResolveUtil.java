package pl.mrstudios.deathrun.util;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class LocationResolveUtil {

    private LocationResolveUtil() {
    }

    public static @Nullable World resolveWorld(
            @Nullable String worldName,
            @Nullable String worldUuid,
            @Nullable String legacyWorldName
    ) {
        if (worldName != null && !worldName.isBlank()) {
            World byName = Bukkit.getWorld(worldName);
            if (byName != null)
                return byName;
        }

        if (worldUuid != null && !worldUuid.isBlank()) {
            try {
                World byUuid = Bukkit.getWorld(UUID.fromString(worldUuid));
                if (byUuid != null)
                    return byUuid;
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (legacyWorldName != null && !legacyWorldName.isBlank()) {
            World legacyWorld = Bukkit.getWorld(legacyWorldName);
            if (legacyWorld != null)
                return legacyWorld;
        }

        return null;
    }

    public static @NotNull String summarizeWorldReference(
            @Nullable String worldName,
            @Nullable String worldUuid,
            @Nullable String legacyWorldName
    ) {
        return "world=" + (worldName == null || worldName.isBlank() ? "-" : worldName)
                + ", uuid=" + (worldUuid == null || worldUuid.isBlank() ? "-" : worldUuid)
                + ", legacy=" + (legacyWorldName == null || legacyWorldName.isBlank() ? "-" : legacyWorldName);
    }
}
