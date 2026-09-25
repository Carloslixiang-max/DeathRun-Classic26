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
import pl.mrstudios.deathrun.api.arena.booster.IBooster;
import pl.mrstudios.deathrun.api.arena.booster.IBoosterItem;
import pl.mrstudios.deathrun.api.arena.booster.enums.Direction;
import pl.mrstudios.deathrun.arena.booster.Booster;

public class BoosterSerializer implements ObjectSerializer<IBooster> {

    @Override
    public void serialize(
            @NotNull IBooster object,
            @NotNull SerializationData data,
            @NotNull GenericsDeclaration generics
    ) {
        data.add("slot", object.slot());
        data.add("power", object.power());
        data.add("delay", object.delay());
        data.add("item", object.item());
        data.add("delayItem", object.delayItem());
        data.add("direction", object.direction());

        if (object.sound() instanceof Keyed keyed) {
            data.add("sound", keyed.getKey().toString());
        }
        else {
            data.add("sound", object.sound().toString());
        }
    }

    @Override
    public @NotNull IBooster deserialize(
            @NotNull DeserializationData data,
            @NotNull GenericsDeclaration generics
    ) {

        String soundName = data.get("sound", String.class);
        Sound sound = resolveSound(soundName);

        return new Booster(
                data.get("slot", Integer.class),
                data.get("power", Float.class),
                data.get("delay", Integer.class),
                data.get("item", IBoosterItem.class),
                data.get("delayItem", IBoosterItem.class),
                data.get("direction", Direction.class),
                sound
        );
    }

    @Override
    public boolean supports(
            @NotNull Class<? super IBooster> type
    ) {
        return IBooster.class.isAssignableFrom(type);
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
