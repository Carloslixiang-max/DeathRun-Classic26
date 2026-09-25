package pl.mrstudios.deathrun.config.serializer;

import eu.okaeri.configs.schema.GenericsDeclaration;
import eu.okaeri.configs.serdes.DeserializationData;
import eu.okaeri.configs.serdes.ObjectSerializer;
import eu.okaeri.configs.serdes.SerializationData;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.jetbrains.annotations.NotNull;

public class SoundSerializer implements ObjectSerializer<Sound> {

    @Override
    public void serialize(
            @NotNull Sound object,
            @NotNull SerializationData data,
            @NotNull GenericsDeclaration generics
    ) {
        if (object instanceof Keyed keyed) {
            data.add("key", keyed.getKey().toString());
            return;
        }

        data.add("key", object.toString());
    }

    @Override
    public @NotNull Sound deserialize(
            @NotNull DeserializationData data,
            @NotNull GenericsDeclaration generics
    ) {
        return resolveSound(data.get("key", String.class));
    }

    @Override
    public boolean supports(
            @NotNull Class<? super Sound> type
    ) {
        return Sound.class.isAssignableFrom(type);
    }

    private static @NotNull Sound resolveSound(@NotNull String value) {
        NamespacedKey namespacedKey = NamespacedKey.fromString(value);
        if (namespacedKey != null) {
            Sound namespaced = Registry.SOUNDS.get(namespacedKey);
            if (namespaced != null) {
                return namespaced;
            }
        }

        try {
            return Sound.valueOf(value.replace("minecraft:", "").toUpperCase().replace('.', '_'));
        }
        catch (IllegalArgumentException exception) {
            return Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        }
    }
}
