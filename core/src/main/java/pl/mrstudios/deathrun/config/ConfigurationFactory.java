package pl.mrstudios.deathrun.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;
import eu.okaeri.configs.yaml.bukkit.serdes.SerdesBukkit;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.deathrun.config.serdes.PluginSerdes;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Files;
import pl.mrstudios.deathrun.config.impl.LanguageConfiguration;

import static eu.okaeri.configs.ConfigManager.create;

public record ConfigurationFactory(
        @NotNull Path directory
) {

    public <CONFIG extends OkaeriConfig> CONFIG produce(
            @NotNull Class<CONFIG> clazz,
            @NotNull String file
    ) {
        return produce(clazz, this.directory.resolve(file).toFile());
    }

    public <CONFIG extends OkaeriConfig> CONFIG produce(
            @NotNull Class<CONFIG> clazz,
            @NotNull File file
    ) {
        CONFIG config = create(clazz, (initializer) ->
                initializer.withConfigurer(new YamlBukkitConfigurer(), new SerdesBukkit(), new PluginSerdes())
                        .withBindFile(file)
                        .saveDefaults()
                        .load(true)
        );
        if (config instanceof LanguageConfiguration language && upgradeChineseText(language, file.toPath()))
            config.save();
        return config;
    }

    public static boolean upgradeChineseText(LanguageConfiguration language, Path file) {
        if (language.chineseTextRevision >= 3)
            return false;
        try {
            Path backup = file.resolveSibling(file.getFileName() + ".before-chinese-v3.bak");
            if (Files.exists(file) && !Files.exists(backup))
                Files.copy(file, backup);
            LanguageConfiguration defaults = new LanguageConfiguration();
            for (var field : LanguageConfiguration.class.getFields()) {
                if (field.getType() == String.class || field.getType() == java.util.List.class)
                    field.set(language, field.get(defaults));
            }
            language.chineseTextRevision = 3;
            return true;
        } catch (java.io.IOException | ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot upgrade Chinese text safely", exception);
        }
    }


}
