package pl.mrstudios.deathrun.config.impl;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.Header;
import eu.okaeri.configs.annotation.Names;

import java.util.List;

import static eu.okaeri.configs.annotation.NameModifier.TO_LOWER_CASE;
import static eu.okaeri.configs.annotation.NameStrategy.HYPHEN_CASE;
import static java.util.Arrays.asList;

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
public class LanguageConfiguration extends OkaeriConfig {

    @Comment({
            "",
            "------------------------------------------------------------------------",
            "                                 GENERAL",
            "------------------------------------------------------------------------",
            ""
    })
    public String chatMessageNoPermissions = "<red>You don't have permissions to this command.";
    public String chatMessageInvalidCommandUsage = "<red>Invalid command usage, correct usage is <dark_red><usage><red>.";
    public String chatMessageArenaPlayerJoined = "<gray><player> <yellow>has joined. <aqua>(<currentPlayers>/<maxPlayers>)";
    public String chatMessageArenaPlayerLeft = "<gray><player> <yellow>has quit.";
    public String chatMessageArenaStartingTimer = "<yellow>Game starts in <gold><timer> seconds<yellow>.";
        public String chatMessageArenaCheckpointReached = "<gold>Checkpoint reached: <yellow><checkpointName>";
    public String chatMessageArenaPlayerFinished = "<reset> <white><b>FINISH ></b> <gray>Player <gold><player> <gray>has finished game in <white><seconds> seconds<gray>. <dark_gray>(#<finishPosition>)";

    @Comment({
            "",
            "------------------------------------------------------------------------",
            "                                COMMANDS",
            "------------------------------------------------------------------------",
            ""
    })
    public List<String> commandHelpMainLines = asList(
            "<reset>",
            "<reset>    <gold>DeathRun <dark_gray>(v<version>) <gray>by <white>MrStudios Industries",
            "<reset>",
            "<reset> <b>*</b> <white>/dr join <map>",
            "<reset> <b>*</b> <white>/dr join lobby",
            "<reset> <b>*</b> <white>/dr start (map)",
            "<reset> <b>*</b> <white>/dr stop (map)",
            "<reset> <b>*</b> <white>/dr reload",
            "<reset> <b>*</b> <white>/dr maps",
            "<reset> <b>*</b> <white>/dr help (page)",
            "<reset> <b>*</b> <white>/dr leave",
            "<reset> <b>*</b> <white>/dr map list",
            "<reset>"
    );
    public List<String> commandHelpSetupLines = asList(
            "<reset>",
            "<reset>    <gold>DeathRun <dark_gray>(v<version>) <gray>by <white>MrStudios Industries",
            "<reset>",
            "<reset> <b>*</b> <white>/dr map list",
            "<reset> <b>*</b> <white>/dr map edit <id>",
            "<reset> <b>*</b> <white>/dr map create <id> <world>",
            "<reset> <b>*</b> <white>/dr map delete <id>",
            "<reset> <b>*</b> <white>/dr map enable <id>",
            "<reset> <b>*</b> <white>/dr map disable <id>",
            "<reset> <b>*</b> <white>/dr map restore <id>",
            "<reset> <b>*</b> <white>/dr map check (id)",
            "<reset> <b>*</b> <white>/dr map status (id)",
            "<reset> <b>*</b> <white>/dr map fixbarrier <id>",
            "<reset> <b>*</b> <white>/dr map backup <id>",
            "<reset> <b>*</b> <white>/dr map autofix <id>",
            "<reset> <b>*</b> <white>/dr edit create <name>",
            "<reset> <b>*</b> <white>/dr setlobby",
            "<reset> <b>*</b> <white>/dr setbarrier (material)",
            "<reset> <b>*</b> <white>/dr addspawn <death/runner>",
            "<reset> <b>*</b> <white>/dr trap add <type> (objects)",
            "<reset> <b>*</b> <white>/dr cp add",
            "<reset> <b>*</b> <white>/dr cp list",
            "<reset> <b>*</b> <white>/dr cp tp <id>",
            "<reset> <b>*</b> <white>/dr cp tp <map> <id>",
            "<reset> <b>*</b> <white>/dr cp setorder <id> <position>",
            "<reset> <b>*</b> <white>/dr cp setname <id> <name>",
            "<reset> <b>*</b> <white>/dr cp setfinish <id>",
            "<reset> <b>*</b> <white>/dr cp move <id>",
            "<reset> <b>*</b> <white>/dr cp delete <id>",
            "<reset> <b>*</b> <white>/dr sethub",
            "<reset> <b>*</b> <white>/dr addteleport",
            "<reset> <b>*</b> <white>/dr cancel",
            "<reset> <b>*</b> <white>/dr save",
            "<reset>"
    );
    public String commandMessageSetupDisabled = "<dark_red><b>*</b> <red>You can't use that command while setup is disabled.";
        public String commandMessageCheckpointAdded = "<reset> <dark_green><b>*</b> <green>Checkpoint <dark_green>#<checkpoint><green> added for map <dark_green><map><green>.";
        public String commandMessageCheckpointAreaInfo = "<reset> <gray>Checkpoint area size: <white><blocks> blocks<gray>.";
        public String commandMessageCheckpointAreaEmpty = "<reset> <dark_red><b>*</b> <red>Checkpoint area is empty. Select a WorldEdit region first.";
                public String commandMessageCheckpointNotFound = "<reset> <dark_red><b>*</b> <red>Checkpoint <dark_red>#<checkpoint> <red>was not found on map <dark_red><map><red>.";
                public String commandMessageCheckpointDeleted = "<reset> <dark_green><b>*</b> <green>Deleted checkpoint <dark_green>#<checkpoint> <green>from map <dark_green><map><green>.";
                public String commandMessageCheckpointOrderUpdated = "<reset> <dark_green><b>*</b> <green>Moved checkpoint <dark_green>#<checkpoint> <green>to position <dark_green><position><green>.";
                public String commandMessageCheckpointNameSet = "<reset> <dark_green><b>*</b> <green>Renamed checkpoint <dark_green>#<checkpoint> <green>to <dark_green><name><green>.";
                public String commandMessageCheckpointFinishSet = "<reset> <dark_green><b>*</b> <green>Checkpoint <dark_green>#<checkpoint> <green>is now the finish checkpoint.";
                public String commandMessageCheckpointMoved = "<reset> <dark_green><b>*</b> <green>Moved checkpoint <dark_green>#<checkpoint> <green>spawn to your current location.";
    public String commandMessageRoleInvalid = "<reset> <dark_red><b>*</b> <red>You must select <dark_red>RUNNER <red>or <dark_red>DEATH <red>role.";
    public String commandMessageRoleSpawnAdded = "<reset> <dark_green><b>*</b> <green>Added <dark_green><role> <green>role spawn.";
    public String commandMessageArenaNameSet = "<reset> <dark_green><b>*</b> <green>Arena name has been set to <dark_green><name><green>.";
    public String commandMessageStartBarrierSet = "<reset> <dark_green><b>*</b> <green>Arena start barrier has been set.";
    public String commandMessageWaitingLobbySet = "<reset> <dark_green><b>*</b> <green>Arena waiting lobby has been set.";
    public String commandMessageTeleportPadAdded = "<reset> <dark_green><b>*</b> <green>Added arena teleport pad.";
        public String commandMessageSaveSuccess = "<reset> <dark_green><b>*</b> <green>Arena configuration saved successfully.";
    public String commandMessageTrapLookAtButton = "<reset> <dark_red><b>*</b> <red>You must look at button that is activating trap.";
    public String commandMessageTrapNotExists = "<reset> <dark_red><b>*</b> <red>Trap <dark_red><type> <red>is not exists.";
    public String commandMessageTrapAdded = "<reset> <dark_green><b>*</b> <green>Added trap <dark_green><type> <green>to arena.";
    public String commandMessageNoMapsConfigured = "<dark_red><b>*</b> <red>No maps are configured yet.";
                public String commandMessageStartSuccess = "<dark_green><b>*</b> <green>Force-start scheduled for map <dark_green><map><green>.";
                public String commandMessageStartMapUnavailable = "<dark_red><b>*</b> <red>Map <dark_red><map> <red>is not available.";
                public String commandMessageStartNoPlayers = "<dark_red><b>*</b> <red>Map <dark_red><map> <red>has no queued players to start.";
                public String commandMessageStartAlreadyRunning = "<dark_red><b>*</b> <red>Map <dark_red><map> <red>is already running.";
                public String commandMessageStartNoCurrentMap = "<dark_red><b>*</b> <red>You are not queued in any map. Use <white>/dr start <map><red>.";
                public String commandMessageStopSuccess = "<dark_green><b>*</b> <green>Map <dark_green><map> <green>has been stopped and reset to waiting.";
                public String commandMessageStopMapUnavailable = "<dark_red><b>*</b> <red>Map <dark_red><map> <red>is not available.";
                public String commandMessageStopAlreadyWaiting = "<gold><b>*</b> <yellow>Map <gold><map> <yellow>is already in waiting state.";
                public String commandMessageStopMovedToHub = "<dark_red><b>*</b> <red>The match has been stopped by an administrator.";
                public String commandMessageStopNoCurrentMap = "<dark_red><b>*</b> <red>You are not queued in any map. Use <white>/dr stop <map><red>.";
                public String commandMessageReloadSuccess = "<dark_green><b>*</b> <green>DeathRun configuration and runtimes reloaded.";
                public String commandMessageReloadFailed = "<dark_red><b>*</b> <red>Reload failed: <dark_red><reason>";
                public String commandMessageJoinForcedActor = "<dark_green><b>*</b> <green>Executed join for <dark_green><player><green> on map <dark_green><map><green>.";
                public String commandMessageJoinForcedLobbyActor = "<dark_green><b>*</b> <green>Sent <dark_green><player> <green>to lobby.";
        public String commandMessageSetupMapSelected = "<reset> <dark_green><b>*</b> <green>Selected setup map <dark_green><map><green>.";
        public String commandMessageSetupMapCreated = "<reset> <dark_green><b>*</b> <green>Created map <dark_green><map><green> in world <dark_green><world><green>.";
        public String commandMessageSetupMapDeleted = "<reset> <dark_green><b>*</b> <green>Deleted map <dark_green><map><green>.";
        public String commandMessageSetupMapEnabled = "<reset> <dark_green><b>*</b> <green>Enabled setup mode for map <dark_green><map><green>.";
        public String commandMessageSetupMapDisabled = "<reset> <dark_green><b>*</b> <green>Disabled setup mode for map <dark_green><map><green>.";
        public String commandMessageSetupMapRestoreSuccess = "<reset> <dark_green><b>*</b> <green>Restored world <dark_green><world> <green>for map <dark_green><map><green>.";
        public String commandMessageSetupMapRestoreMissingBackup = "<reset> <dark_red><b>*</b> <red>Missing backup zip for world <dark_red><world><red>.";
        public String commandMessageSetupMapRestoreWorldMissing = "<reset> <dark_red><b>*</b> <red>Map world <dark_red><world> <red>is not set.";
        public String commandMessageSetupMapRestorePlayersPresent = "<reset> <dark_red><b>*</b> <red>Cannot restore map while players are queued or in-game.";
        public String commandMessageSetupMapRestoreUnloadFailed = "<reset> <dark_red><b>*</b> <red>Could not unload world <dark_red><world><red>.";
        public String commandMessageSetupMapRestoreLoadFailed = "<reset> <dark_red><b>*</b> <red>Could not load restored world <dark_red><world><red>.";
        public String commandMessageSetupMapRestoreFailed = "<reset> <dark_red><b>*</b> <red>Map restore failed: <dark_red><reason>";
        public String commandMessageSetupMapCheckHeader = "<gold>[DR]</gold> <gray>Map health check:";
        public String commandMessageSetupMapCheckEntryOk = "<reset> <dark_green><b>*</b> <green><map> <gray>- healthy";
        public String commandMessageSetupMapCheckEntryIssues = "<reset> <dark_red><b>*</b> <red><map> <gray>- issues: <white><issues>";
        public String commandMessageSetupMapCheckNoIssues = "<reset> <dark_green><b>*</b> <green>No issues detected.";
        public String commandMessageSetupMapStatusHeader = "<gold>[DR]</gold> <gray>Map status:";
        public String commandMessageSetupMapStatusLine = "<reset> <gray>- <white><map> <dark_gray>| state: <white><state> <dark_gray>| players: <white><players>/<maxPlayers> <dark_gray>| setup: <white><setup> <dark_gray>| health: <white><health>";
        public String commandMessageSetupMapStatusIssues = "<reset>   <dark_gray>issues: <white><issues>";
        public String commandMessageSetupMapFixBarrierSuccess = "<reset> <dark_green><b>*</b> <green>Rebuilt barrier restore snapshot for <dark_green><map><green>.";
        public String commandMessageSetupMapFixBarrierNoBarrier = "<reset> <dark_red><b>*</b> <red>Map <dark_red><map> <red>has no configured start barrier blocks.";
        public String commandMessageSetupMapBackupSuccess = "<reset> <dark_green><b>*</b> <green>Backup refreshed for map <dark_green><map> <green>(world <dark_green><world><green>).";
        public String commandMessageSetupMapBackupWorldMissing = "<reset> <dark_red><b>*</b> <red>World <dark_red><world> <red>is not loaded.";
        public String commandMessageSetupMapBackupFailed = "<reset> <dark_red><b>*</b> <red>Backup failed: <dark_red><reason>";
        public String commandMessageSetupMapAutofixApplied = "<reset> <dark_green><b>*</b> <green>Autofix applied for <dark_green><map><green>: <white><actions>";
        public String commandMessageSetupMapAutofixNoChanges = "<reset> <gold><b>*</b> <yellow>No autofix actions applied for <gold><map><yellow>.";
        public String commandMessageSetupMapPreflightFailed = "<reset> <dark_red><b>*</b> <red>Cannot finalize map <dark_red><map><red>. Fix: <white><issues>";
        public String commandMessageSetupMapPreflightPassed = "<reset> <dark_green><b>*</b> <green>Preflight checks passed for <dark_green><map><green>.";
        public String commandMessageSetupMapMissing = "<reset> <dark_red><b>*</b> <red>Map <dark_red><map> <red>does not exist.";
        public String commandMessageSetupMapAlreadyExists = "<reset> <dark_red><b>*</b> <red>Map <dark_red><map> <red>already exists.";
        public String commandMessageSetupMapInvalidWorld = "<reset> <dark_red><b>*</b> <red>World <dark_red><world> <red>is not loaded.";
        public String commandMessageSetupMapWorldUnavailable = "<reset> <dark_red><b>*</b> <red>The world for this map could not be found. Please ensure the map's world is loaded.";
        public String commandMessageSetupMapNoSelection = "<reset> <dark_red><b>*</b> <red>Select setup map first using <white>/dr map edit <id><red>.";
        public String commandMessageSetupMapLocked = "<reset> <dark_red><b>*</b> <red>Selected map setup is disabled. Re-enable it in map.yml or create a new map.";
        public String commandMessageSetupEditModeRequired = "<reset> <dark_red><b>*</b> <red>You must be in edit mode to use this command. Use <white>/dr map edit <mapname> <red>first.";
        public String commandMessageSetupEditModeAlreadyActive = "<reset> <gold><b>*</b> <yellow>You are already in edit mode. Please finish editing your current map first.";
        public String commandMessageSetupEditModeEntered = "<reset> <dark_green><b>*</b> <green>Edit mode enabled for map <dark_green><map><green>.";
        public String commandMessageSetupEditModeSaved = "<reset> <dark_green><b>*</b> <green>Editing complete; all changes have been saved.";
        public String commandMessageSetupEditModeCancelled = "<reset> <gold><b>*</b> <yellow>Edit mode cancelled for map <gold><map><yellow>.";
        public String commandMessageSetupMapListLine = "<reset> <gray>- <white><id> <dark_gray>| <white><name> <dark_gray>| <white><world> <dark_gray>| <white><state>";
        public String commandMessageSetupMapListEmpty = "<reset> <dark_red><b>*</b> <red>No setup maps available.";
        public String commandMessageSetupMapDeleteLastBlocked = "<reset> <dark_red><b>*</b> <red>You cannot delete the last map.";
        public String commandMessageSetupMapStateEnabled = "<green>setup-enabled";
        public String commandMessageSetupMapStateDisabled = "<red>setup-disabled";

    public String mapSelectorTitle = "DeathRun Maps";
    public String mapSelectorMapName = "<gold><name>";
    public List<String> mapSelectorMapLore = asList(
            "<gray>World: <white><world>",
            "<gray>Players: <white><players>/<maxPlayers>",
            "<gray>Status: <white><status>",
            "<yellow>Click to join"
    );
    public String mapSelectorStatusAvailable = "<green>Available";
    public String mapSelectorStatusDisabled = "<red>Disabled";
    public String mapSelectorStatusMissingLobby = "<gold>Missing lobby";
        public String mapSelectorStatusNotReady = "<gold>Not ready";
                public String mapSelectorStatusEditing = "<gold>Being edited";
        public String mapSelectorStatusInProgress = "<red>In progress";
        public String mapSelectorMapSelected = "<dark_green><b>*</b> <green>Joined map <dark_green><map><green>.";
    public String mapSelectorMapUnavailable = "<dark_red><b>*</b> <red>This map is not available yet.";
                public String mapSelectorMapEditing = "<dark_red><b>*</b> <red>This map is currently unavailable as it is being edited.";
        public String mapSelectorMapNotReady = "<dark_red><b>*</b> <red>This map is not fully configured yet.";
        public String mapSelectorMapFull = "<dark_red><b>*</b> <red>This map is full right now.";
        public String mapSelectorMapInProgress = "<dark_red><b>*</b> <red>This match is already in progress.";
        public String mapSelectorAlreadyJoined = "<gold><b>*</b> <yellow>You are already queued on this map.";

    public List<String> chatMessageArenaGameStartRunner = asList(
            "<reset>",
            "<reset>   <gold><b>*</b> <gray>You are <green>Runner<gray>.",
            "<reset>   <white><b>*</b> <gray>Your task is complete run in shortest possible time, during this task interfering player will trigger various traps.",
            "<reset>"
    );

    public List<String> chatMessageArenaGameStartDeath = asList(
            "<reset>",
            "<reset>   <gold><b>*</b> <gray>You are <red>Death<gray>.",
            "<reset>   <white><b>*</b> <gray>Your task is to disturb runners by launching traps.",
            "<reset>"
    );

    public List<String> chatMessageGameEndSpectator = asList(
            "<reset>",
            "<reset>   <gold><b>*</b> <gray>You are <dark_gray>Spectator<gray>.",
            "<reset>   <white><b>*</b> <gray>Now you can follow other players.",
            "<reset>"
    );

    @Comment({
            "",
            "------------------------------------------------------------------------",
            "                                 TITLES",
            "------------------------------------------------------------------------",
            ""
    })
    public String arenaPreStartingTitle = "<red><timer>";
    public String arenaPreStartingSubtitle = "<reset>";

    public String arenaStartingTitle = "<red><timer>";
    public String arenaStartingSubtitle = "<reset>";

    public String arenaDeathTitle = "<red>YOU DIED!";
    public String arenaDeathSubtitle = "<yellow>Don't give up! Try again!";

    public String arenaCheckpointTitle = "<yellow>CHECKPOINT!";
    public String arenaCheckpointSubtitle = "<gold>You reached <yellow>#<checkpoint> checkpoint<gold>.";

    public String arenaFinishTitle = "<dark_aqua><b>FINISH";
    public String arenaFinishSubtitle = "<gray>Your position is <white>#<position><gray>.";

    public String arenaGameEndTitle = "<red><b>GAME END!";
    public String arenaGameEndSubtitle = "<reset>";

    public String arenaMoveServerTitle = "<aqua>Waiting..";
    public String arenaMoveServerSubtitle = "<gray>You will be transferred to lobby in <white><endTimer> seconds<gray>.";
        public String arenaMoveServerChat = "<gray>Game ended, sending you back to lobby...";

    @Comment({
            "",
            "------------------------------------------------------------------------",
            "                              SCOREBOARD",
            "------------------------------------------------------------------------",
            ""
    })
    public boolean arenaScoreboardEnabled = true;
    public int arenaScoreboardUpdateTicks = 20;
    public String arenaScoreboardTitle = "<yellow><b>DEATH RUN";

    public List<String> arenaScoreboardLinesWaiting = asList(
            "<reset>",
            "<white>Map: <green><map>",
            "<white>Players: <green><currentPlayers>/<maxPlayers>",
            "<reset>",
            "<white>Waiting..",
            "<reset>",
            "<yellow>www.example.com"
    );

    public List<String> arenaScoreboardLinesStarting = asList(
            "<reset>",
            "<white>Map: <green><map>",
            "<white>Players: <green><currentPlayers>/<maxPlayers>",
            "<reset>",
            "<white>Start in <green><timer> seconds",
            "<reset>",
            "<yellow>www.example.com"
    );

    public List<String> arenaScoreboardLinesPlaying = asList(
            "<reset>",
            "<white>Time: <green><timeFormatted>",
            "<white>Role: <green><role>",
            "<reset>",
            "<white>Runners: <green><runners>",
            "<white>Deaths: <red><deaths>",
            "<reset>",
            "<white>Map: <green><map>",
            "<reset>",
            "<yellow>www.example.com"
    );

    @Comment({
            "",
            "------------------------------------------------------------------------",
            "                              HOLOGRAMS",
            "------------------------------------------------------------------------",
            ""
    })
    public String arenaHologramTrapDelayed = "<red><delay> seconds";

    @Comment({
            "",
            "------------------------------------------------------------------------",
            "                                ROLES",
            "------------------------------------------------------------------------",
            ""
    })
    public String arenaRolesRunnerName = "<green>Runner";
    public String arenaRolesDeathName = "<red>Death";
    public String arenaRolesSpectatorName = "<gray>Spectator";

    @Comment({
            "",
            "------------------------------------------------------------------------",
            "                                ITEMS",
            "------------------------------------------------------------------------",
            ""
    })
        public String arenaItemMapSelectorName = "<yellow>Map Selector <gray>(Right Click)";
    public String arenaItemLeaveName = "<red>Leave <gray>(Right Click)";

}
