package pl.mrstudios.deathrun.config.impl;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.Header;
import eu.okaeri.configs.annotation.Names;
import org.bukkit.Location;
import org.bukkit.Sound;
import pl.mrstudios.deathrun.arena.booster.Booster;
import pl.mrstudios.deathrun.arena.booster.BoosterItem;
import pl.mrstudios.deathrun.arena.effect.BlockEffect;

import java.util.List;

import static eu.okaeri.configs.annotation.NameModifier.TO_LOWER_CASE;
import static eu.okaeri.configs.annotation.NameStrategy.HYPHEN_CASE;
import static java.util.List.of;
import static org.bukkit.Material.*;
import static org.bukkit.Sound.*;
import static org.bukkit.potion.PotionEffectType.JUMP_BOOST;
import static org.bukkit.potion.PotionEffectType.SPEED;
import static pl.mrstudios.deathrun.api.arena.booster.enums.Direction.FORWARD;

@Header({
        " ",
        "------------------------------------------------------------------------",
        "                              INFORMATION",
        "------------------------------------------------------------------------",
        " ",
        " This is configuration file for DeathRun plugin, if you found any issue ",
        " contact with us through Discord or create issue on GitHub. If you need",
        " help with configuration visit https://github.com/CIlie23/DeathRun/wiki.",
        " "
}) @SuppressWarnings("deprecation")
@Names(strategy = HYPHEN_CASE, modifier = TO_LOWER_CASE)
public class PluginConfiguration extends OkaeriConfig {

    @Comment({
            "",
            "------------------------------------------------------------------------",
            "                               GENERAL",
            "------------------------------------------------------------------------",
            ""
    })
        @Comment({ "", "Main hub location used when leaving queue/game. If null, first world spawn is used." })
        public Location mainHubLocation;

                @Comment({ "", "Required players threshold to start a round." })
        public int arenaRequiredPlayersToStart = 5;

        @Comment({ "", "Max players in one map. 0 or less means auto based on configured spawn points; values above 0 are used directly and spawn points are reused." })
        public int arenaMaxPlayers = 15;

    @Comment({ "", "Amount of players with 'DEATH' role on arena." })
    public int arenaDeathsAmount = 1;

    @Comment({ "", "Amount of time that runners have to complete run. (in seconds)" })
    public int arenaGameTime = 600;

    @Comment({ "", "Amount of time that is needed to game start." })
    public int arenaPreStartingTime = 30;

    @Comment({ "", "Amount of time before start barrier will be removed." })
    public int arenaStartingTime = 10;

    @Comment({ "", "Amount of time before players on server will be moved to lobby." })
    public int arenaEndDelay = 10;

    @Comment({ "", "Amount of time before trap can be used again." })
    public int arenaTrapDelay = 20;

    @Comment({ "", "Max ,,survivable`` distance that player can fall." })
    public int arenaMaxFallDistance = 8;

    @Comment({ "", "Speed Amplifier for Death role." })
    public int arenaDeathSpeedAmplifier = 10;

    @Comment({
            "",
            "------------------------------------------------------------------------",
            "                                EFFECTS",
            "------------------------------------------------------------------------",
            ""
    })

    public List<BlockEffect> blockEffects = of(
            new BlockEffect(EMERALD_BLOCK, JUMP_BOOST, 7, 1.5f),
            new BlockEffect(REDSTONE_BLOCK, SPEED, 5, 1.5f)
    );

    @Comment({
            "",
            "------------------------------------------------------------------------",
            "                               BOOSTERS",
            "------------------------------------------------------------------------",
            ""
    })
    public List<Booster> boosters = of(
            new Booster(
                    0,
                    2.5f,
                    10,
                    new BoosterItem(
                            "<green>Booster <gray>(Right Click)",
                            FEATHER,
                            null
                    ),
                    new BoosterItem(
                            "<red>Booster <gray>(<delay> seconds)",
                            FEATHER,
                            null
                    ),
                    FORWARD,
                    ENTITY_BLAZE_AMBIENT
            )
    );

    @Comment({
            "",
            "------------------------------------------------------------------------",
            "                               SOUNDS",
            "------------------------------------------------------------------------",
            ""
    })
    public Sound arenaSoundPreStarting = BLOCK_NOTE_BLOCK_PLING;
    public Sound arenaSoundStarting = ENTITY_EXPERIENCE_ORB_PICKUP;
    public Sound arenaSoundStarted = ENTITY_ENDER_DRAGON_GROWL;
    public Sound arenaSoundCheckpointReached = ENTITY_EXPERIENCE_ORB_PICKUP;
        public float arenaSoundCheckpointReachedVolume = 1.0f;
        public float arenaSoundCheckpointReachedPitch = 1.0f;
        public Sound arenaSoundPlayerFinished = UI_TOAST_CHALLENGE_COMPLETE;
        public float arenaSoundPlayerFinishedVolume = 1.0f;
        public float arenaSoundPlayerFinishedPitch = 1.0f;
        public boolean arenaBackgroundSongEnabled = false;
        public String arenaBackgroundSongFileName = "lobby_theme.nbs";
        public boolean arenaBackgroundSongLoop = true;
    public Sound arenaSoundTrapDelay = ENTITY_VILLAGER_NO;
    public Sound arenaSoundPlayerDeath = ENTITY_SKELETON_DEATH;

    @Comment({
            "",
            "------------------------------------------------------------------------",
            "                               REWARDS",
            "------------------------------------------------------------------------",
            ""
    })
    public RewardConfiguration rewards = new RewardConfiguration();

    @Names(strategy = HYPHEN_CASE, modifier = TO_LOWER_CASE)
    public static class RewardConfiguration extends OkaeriConfig {

        public List<String> runnerFirstPlace = of(
                "give %player% diamond 5"
        );

        public List<String> runnerSecondPlace = of(
                "give %player% diamond 3"
        );

        public List<String> runnerThirdPlace = of(
                "give %player% diamond 2"
        );

        public List<String> runnerParticipation = of(
                "give %player% diamond 1"
        );

        public List<String> deathWin = of(
                "give %player% gold_ingot 3"
        );

    }

}
