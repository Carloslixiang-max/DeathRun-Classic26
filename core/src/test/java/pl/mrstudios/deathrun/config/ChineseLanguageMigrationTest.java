package pl.mrstudios.deathrun.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.mrstudios.deathrun.config.impl.LanguageConfiguration;
import java.nio.file.Path;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

class ChineseLanguageMigrationTest {
    @TempDir Path directory;
    @Test void existingEnglishTextIsBackedUpAndChinesePersistsWithoutResettingOptions() throws Exception {
        Path file = directory.resolve("language.yml");
        Files.writeString(file, "arena-roles-runner-name: Runner\n");
        LanguageConfiguration language = new LanguageConfiguration();
        language.arenaRolesRunnerName = "Runner";
        language.arenaScoreboardEnabled = false;
        language.arenaScoreboardUpdateTicks = 42;
        assertTrue(ConfigurationFactory.upgradeChineseText(language, file));
        assertEquals("arena-roles-runner-name: Runner\n", Files.readString(directory.resolve("language.yml.before-chinese-v3.bak")));
        assertTrue(language.arenaRolesRunnerName.contains("跑酷者"));
        assertFalse(language.arenaScoreboardEnabled);
        assertEquals(42, language.arenaScoreboardUpdateTicks);
        assertEquals(3, language.chineseTextRevision);
        language.arenaRolesRunnerName = "自定义跑酷者";
        assertFalse(ConfigurationFactory.upgradeChineseText(language, file));
        assertEquals("自定义跑酷者", language.arenaRolesRunnerName);
    }
}
