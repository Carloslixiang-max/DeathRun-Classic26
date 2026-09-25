package pl.mrstudios.deathrun.config.serializer;

import eu.okaeri.configs.schema.GenericsDeclaration;
import eu.okaeri.configs.serdes.DeserializationData;
import eu.okaeri.configs.serdes.ObjectSerializer;
import eu.okaeri.configs.serdes.SerializationData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.deathrun.util.LocationResolveUtil;

public class LocationSerializer implements ObjectSerializer<Location> {

    @Override
    public void serialize(
            @NotNull Location object,
            @NotNull SerializationData data,
            @NotNull GenericsDeclaration generics
    ) {
        if (object.getWorld() != null) {
            data.add("world", object.getWorld().getName());
            data.add("worldUuid", object.getWorld().getUID().toString());
        }

        data.add("x", object.getX());
        data.add("y", object.getY());
        data.add("z", object.getZ());
        data.add("yaw", object.getYaw());
        data.add("pitch", object.getPitch());
    }

    @Override
    public @NotNull Location deserialize(
            @NotNull DeserializationData data,
            @NotNull GenericsDeclaration generics
    ) {
        World world = this.resolveWorld(data);

        double x = data.get("x", Double.class);
        double y = data.get("y", Double.class);
        double z = data.get("z", Double.class);
        float yaw = data.containsKey("yaw") ? data.get("yaw", Float.class) : 0.0f;
        float pitch = data.containsKey("pitch") ? data.get("pitch", Float.class) : 0.0f;

        return new Location(world, x, y, z, yaw, pitch);
    }

    @Override
    public boolean supports(
            @NotNull Class<? super Location> type
    ) {
        return Location.class.isAssignableFrom(type);
    }

    private World resolveWorld(
            @NotNull DeserializationData data
    ) {
        String worldName = data.containsKey("world") ? data.get("world", String.class) : null;
        String worldUuid = data.containsKey("worldUuid") ? data.get("worldUuid", String.class) : null;
        String legacyWorldName = data.containsKey("world-name") ? data.get("world-name", String.class) : null;

        World resolved = LocationResolveUtil.resolveWorld(worldName, worldUuid, legacyWorldName);
        if (resolved == null && (worldName != null || worldUuid != null || legacyWorldName != null)) {
            Bukkit.getLogger().warning("[DeathRun] Location deserialized with unresolved world reference: "
                    + LocationResolveUtil.summarizeWorldReference(worldName, worldUuid, legacyWorldName)
                    + ". The location will remain world-null until runtime rebinding.");
        }

        return resolved;
    }
}