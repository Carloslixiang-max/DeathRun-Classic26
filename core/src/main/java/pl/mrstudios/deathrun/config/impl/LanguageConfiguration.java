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
    public int chineseTextRevision = 0;
    public List<String> chatMessageArenaRules = asList(
            "<gold><b>━━━━ 死神跑酷 · 玩法与规则 ━━━━",
            "<gray>跑酷者依次经过检查点并到达终点；死神使用机关阻止通关。",
            "<gray>本局限时 <white><gameSeconds> 秒</white>；首位跑酷者通关后，剩余时间最多 <white>60 秒</white>。",
            "<gray>跑酷者生命耗尽即淘汰，死亡后回到最近检查点；退出或结束后恢复入场前状态。"
    );


    @Comment({
            "",
            "------------------------------------------------------------------------",
            "                                 GENERAL",
            "------------------------------------------------------------------------",
            ""
    })
    public String chatMessageNoPermissions = "<red>你没有权限使用此指令。";
    public String chatMessageInvalidCommandUsage = "<red>指令用法错误，正确用法：<white><usage>";
    public String chatMessageArenaPlayerJoined = "<gray><player> <yellow>加入了游戏。<aqua>(<currentPlayers>/<maxPlayers>)";
    public String chatMessageArenaPlayerLeft = "<gray><player> <yellow>退出了游戏。";
    public String chatMessageArenaStartingTimer = "<yellow>游戏将在 <gold><timer> <yellow>秒后开始。";
        public String chatMessageArenaCheckpointReached = "<gold>已到达：<yellow><checkpointName>";
    public String chatMessageArenaPlayerFinished = "<white><b>通关 ></b> <gold><player> <gray>用时 <white><seconds> 秒<gray>，第 <white><finishPosition> <gray>名。";

    @Comment({
            "",
            "------------------------------------------------------------------------",
            "                                COMMANDS",
            "------------------------------------------------------------------------",
            ""
    })
    public List<String> commandHelpMainLines = asList(
            "<reset>",
            "<reset>    <gold>DeathRun <dark_gray>(v<version>) <gray>作者：<white>MrStudios Industries",
            "<reset>",
            "<reset> <b>*</b> <white>/dr join <map>",
            "<reset> <b>*</b> <white>/dr vote",
            "<reset> <b>*</b> <white>/dr join lobby",
            "<reset> <b>*</b> <white>/dr start (map)",
            "<reset> <b>*</b> <white>/dr stop (map)",
            "<reset> <b>*</b> <white>/dr reload",
            "<reset> <b>*</b> <white>/dr maps",
            "<reset> <b>*</b> <white>/dr help (page)",
            "<reset> <b>*</b> <white>/dr leave",
            "<reset> <b>*</b> <white>/dr map list",
            "<reset> <b>*</b> <white>/dr map manifest <id>",
            "<reset> <b>*</b> <white>/dr map legacyscan",
            "<reset> <b>*</b> <white>/dr map legacyfilescan <id>",
            "<reset> <b>*</b> <white>/dr map session <id>",
            "<reset> <b>*</b> <white>/dr map trace start <id>",
            "<reset> <b>*</b> <white>/dr map trace status <id>",
            "<reset> <b>*</b> <white>/dr map trace acceptance <id>",
            "<reset> <b>*</b> <white>/dr map trace stop <id>",
            "<reset> <b>*</b> <white>/dr map trace clear <id>",
            "<reset>"
    );
    public List<String> commandHelpSetupLines = asList(
            "<reset>",
            "<reset>    <gold>DeathRun <dark_gray>(v<version>) <gray>作者：<white>MrStudios Industries",
            "<reset>",
            "<reset> <b>*</b> <white>/dr map list",
            "<reset> <b>*</b> <white>/dr map profile classic <id>",
            "<reset> <b>*</b> <white>/dr map profile interstellar <id>",
            "<reset> <b>*</b> <white>/dr map creator <id> <creator>",
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
            "<reset> <b>*</b> <white>/dr cp points <id> <points>",
            "<reset> <b>*</b> <white>/dr cp move <id>",
            "<reset> <b>*</b> <white>/dr cp delete <id>",
            "<reset> <b>*</b> <white>/dr spawn add <runner/death>",
            "<reset> <b>*</b> <white>/dr spawn list <runner/death>",
            "<reset> <b>*</b> <white>/dr spawn tp <runner/death> <index>",
            "<reset> <b>*</b> <white>/dr spawn move <runner/death> <index>",
            "<reset> <b>*</b> <white>/dr spawn delete <runner/death> <index>",
            "<reset> <b>*</b> <white>/dr spawn clear <runner/death>",
            "<reset> <b>*</b> <white>/dr sethub",
            "<reset> <b>*</b> <white>/dr addteleport",
            "<reset> <b>*</b> <white>/dr cancel",
            "<reset> <b>*</b> <white>/dr save",
            "<reset> <b>*</b> <white>/dr playtest create",
            "<reset> <b>*</b> <white>/dr playtest verify",
            "<reset>"
    );
    public String commandMessageSetupDisabled = "<red>当前未启用地图编辑模式。";
        public String commandMessageCheckpointAdded = "<green>已为 <map> 添加检查点 #<checkpoint>。";
        public String commandMessageCheckpointAreaInfo = "<gray>检查点区域：<white><blocks> 个方块。";
        public String commandMessageCheckpointAreaEmpty = "<red>检查点区域为空，请先用 WorldEdit 选择区域。";
                public String commandMessageCheckpointNotFound = "<red><map> 中没有检查点 #<checkpoint>。";
                public String commandMessageCheckpointDeleted = "<green>已删除 <map> 的检查点 #<checkpoint>。";
                public String commandMessageCheckpointOrderUpdated = "<green>检查点 #<checkpoint> 已移到第 <position> 位。";
                public String commandMessageCheckpointNameSet = "<green>检查点 #<checkpoint> 已改名为 <name>。";
                public String commandMessageCheckpointFinishSet = "<green>检查点 #<checkpoint> 已设为终点。";
                public String commandMessageCheckpointMoved = "<green>检查点 #<checkpoint> 的复活点已移到你的位置。";
                public String commandMessageCheckpointPointsSet = "<green>检查点 #<checkpoint> 的分数已设为 <points>。";
    public String commandMessageRoleInvalid = "<red>请选择跑酷者（runner）或死神（death）。";
    public String commandMessageRoleSpawnAdded = "<green>已添加 <role> 出生点。";
    public String commandMessageRoleSpawnListHeader = "<gold>[死神跑酷]</gold> <map> 的 <role> 出生点：<count> 个";
    public String commandMessageRoleSpawnListLine = "<reset> <gray>- <white>#<index> <dark_gray>| <white><world> <dark_gray>| <white><x>, <y>, <z>";
    public String commandMessageRoleSpawnListEmpty = "<yellow>没有配置 <role> 出生点。";
    public String commandMessageRoleSpawnNotFound = "<red>没有 <role> 出生点 #<index>。";
    public String commandMessageRoleSpawnTeleported = "<green>已传送至 <role> 出生点 #<index>。";
    public String commandMessageRoleSpawnMoved = "<green>已移动 <role> 出生点 #<index>。";
    public String commandMessageRoleSpawnDeleted = "<green>已删除 <role> 出生点 #<index>。";
    public String commandMessageRoleSpawnCleared = "<green>已清空 <count> 个 <role> 出生点。";
    public String commandMessageRoleSpawnWrongWorld = "<red>请先进入 <map> 对应的世界 <world>。";
    public String commandMessageArenaNameSet = "<green>地图名称已设为 <name>。";
    public String commandMessageStartBarrierSet = "<green>起跑屏障已设置。";
    public String commandMessageWaitingLobbySet = "<green>等待位置已设置。";
    public String commandMessageTeleportPadAdded = "<green>已添加传送台。";
        public String commandMessageSaveSuccess = "<green>地图配置已保存。";
    public String commandMessageTrapLookAtButton = "<red>请看向机关的激活按钮。";
    public String commandMessageTrapNotExists = "<red>机关 <type> 不存在。";
    public String commandMessageTrapAdded = "<green>已添加机关 <type>。";
    public String commandMessageNoMapsConfigured = "<red>尚未配置地图。";
                public String commandMessageStartSuccess = "<green>已安排开始地图 <map>。";
                public String commandMessageStartMapUnavailable = "<red>地图 <map> 不可用。";
                public String commandMessageStartNoPlayers = "<red>地图 <map> 没有等待中的玩家。";
                public String commandMessageStartAlreadyRunning = "<red>地图 <map> 已在比赛中。";
                public String commandMessageStartNoCurrentMap = "<red>你没有加入地图，请使用 <white>/dr start <map>。";
                public String commandMessageStopSuccess = "<green>地图 <map> 已停止并复位至等待状态。";
                public String commandMessageStopMapUnavailable = "<red>地图 <map> 不可用。";
                public String commandMessageStopAlreadyWaiting = "<yellow>地图 <map> 已处于等待状态。";
                public String commandMessageStopMovedToHub = "<red>管理员已结束本局比赛。";
                public String commandMessageStopNoCurrentMap = "<red>你没有加入地图，请使用 <white>/dr stop <map>。";
                public String commandMessageReloadSuccess = "<green>死神跑酷配置和地图已重载。";
                public String commandMessageReloadFailed = "<red>重载失败：<reason>";
    public String commandMessageRecoveryLocked = "<red>状态尚待恢复，暂时限制物品转移和其他指令。请检查原世界与传送限制后使用 <white>/dr recover</white>。";
    public String commandMessageRespawnFailed = "<red>复活传送失败，已退出比赛并尝试恢复入场前状态；如仍待恢复，请使用 <white>/dr recover</white>。";
    public String chatMessageLeavePending = "<red>已退出比赛，状态尚待恢复。请检查原世界与传送限制后使用 <white>/dr recover</white>。";
    public String chatMessageLeaveRestored = "<yellow>已退出死神跑酷，并恢复入场前状态。";
    public String chatMessageLeaveQueue = "<yellow>已退出死神跑酷队列。";
    public String chatMessageNoQueue = "<gray>你没有加入死神跑酷队列。";
    public String chatMessageQueueSaveFailed = "<red>无法安全保存你的状态，已取消加入队列；如有待恢复状态，请先使用 <white>/dr recover</white>。";
    public String chatMessageQueueTeleportFailed = "<red>等待区传送失败，已取消排队并恢复入场前状态。";
    public String chatMessageQueueNeedsLeave = "<red>请先退出当前比赛，再加入队列。";
    public String chatMessageQueueWorldMissing = "<red>等待区世界不可用，请联系管理员。";
    public String chatMessageQueueJoinFailed = "<red>未能加入队列，地图可能未就绪或已满。";
    public String chatMessageAutoJoinUnavailable = "<red>没有可自动加入的地图。";
    public String chatMessageSignNoPermission = "<red>你没有使用此死神跑酷告示牌的权限。";
    public String commandMessageJoinStateSaveFailed = "<red>无法安全保存你的状态，已取消加入；如有待恢复状态，请先使用 <white>/dr recover</white>。";
    public String commandMessageJoinTeleportFailed = "<red>等待区传送失败，已取消加入并尝试恢复入场前状态。";
                public String commandMessageRecoverSuccess = "<green>已恢复你进入游戏前的状态。";
                public String commandMessageRecoverFailed = "<red>暂时无法恢复，恢复记录已保留，请检查原世界后重试。";
                public String commandMessageRecoverNone = "<yellow>你没有待恢复的游戏状态。";
                public String commandMessageRecoverInMatch = "<red>请先退出本局比赛，再执行恢复。";
                public String commandMessageJoinForcedActor = "<green>已让 <player> 加入地图 <map>。";
                public String commandMessageJoinForcedLobbyActor = "<green>已让 <player> 返回大厅。";
        public String commandMessageSetupMapSelected = "<green>已选中地图 <map>。";
        public String commandMessageMapCreatorSet = "<green>地图 <map> 的作者已设为 <creator>。";
        public String commandMessageSetupMapCreated = "<green>已在世界 <world> 创建地图 <map>。";
        public String commandMessageSetupMapDeleted = "<green>已删除地图 <map>。";
        public String commandMessageSetupMapEnabled = "<green>已启用地图 <map> 的编辑模式。";
        public String commandMessageSetupMapDisabled = "<green>已关闭地图 <map> 的编辑模式。";
        public String commandMessageSetupMapRestoreSuccess = "<green>已从备份恢复地图 <map> 的世界 <world>。";
        public String commandMessageSetupMapRestoreMissingBackup = "<red>世界 <world> 缺少备份压缩包。";
        public String commandMessageSetupMapRestoreWorldMissing = "<red>未设置地图世界 <world>。";
        public String commandMessageSetupMapRestorePlayersPresent = "<red>玩家等待或比赛期间不能恢复地图。";
        public String commandMessageSetupMapRestoreUnloadFailed = "<red>无法卸载世界 <world>。";
        public String commandMessageSetupMapRestoreLoadFailed = "<red>无法加载恢复后的世界 <world>。";
        public String commandMessageSetupMapRestoreFailed = "<red>地图恢复失败：<reason>";
        public String commandMessageSetupMapCheckHeader = "<gold>[死神跑酷]</gold> <gray>地图检查：";
        public String commandMessageSetupMapCheckEntryOk = "<green><map> <gray>— 正常";
        public String commandMessageSetupMapCheckEntryIssues = "<red><map> <gray>— 问题：<white><issues>";
        public String commandMessageSetupMapCheckNoIssues = "<green>未发现问题。";
        public String commandMessageSetupMapStatusHeader = "<gold>[死神跑酷]</gold> <gray>地图状态：";
        public String commandMessageSetupMapStatusLine = "<gray><map> | 状态：<state> | 玩家：<players>/<maxPlayers> | 编辑：<setup> | 健康：<health>";
        public String commandMessageSetupMapStatusIssues = "<gray>问题：<white><issues>";
        public String commandMessageSetupMapFixBarrierSuccess = "<green>已重建 <map> 的起跑屏障恢复记录。";
        public String commandMessageSetupMapFixBarrierNoBarrier = "<red>地图 <map> 没有配置起跑屏障。";
        public String commandMessageSetupMapBackupSuccess = "<green>地图 <map> 的备份已更新（世界 <world>）。";
        public String commandMessageSetupMapBackupWorldMissing = "<red>世界 <world> 未加载。";
        public String commandMessageSetupMapBackupPlayersPresent = "<red>地图内有人时不能备份或自动修复。";
        public String commandMessageSetupMapBackupFailed = "<red>备份失败：<reason>";
        public String commandMessageSetupMapAutofixApplied = "<green>已修复 <map>：<actions>";
        public String commandMessageSetupMapAutofixNoChanges = "<yellow>地图 <map> 无需自动修复。";
        public String commandMessageSetupMapPreflightFailed = "<red>地图 <map> 无法开放，请修复：<issues>";
        public String commandMessageSetupMapPreflightPassed = "<green>地图 <map> 已通过开放检查。";
        public String commandMessageSetupMapMissing = "<red>地图 <map> 不存在。";
        public String commandMessageSetupMapAlreadyExists = "<red>地图 <map> 已存在。";
        public String commandMessageSetupMapInvalidWorld = "<red>世界 <world> 未加载，且未找到世界文件。";
        public String commandMessageSetupMapWorldUnavailable = "<red>找不到地图世界，请确认世界已加载。";
        public String commandMessageSetupMapNoSelection = "<red>请先使用 <white>/dr map edit <id> <red>选择地图。";
        public String commandMessageSetupMapLocked = "<red>地图编辑已关闭，请先重新启用。";
        public String commandMessageSetupEditModeRequired = "<red>请先使用 <white>/dr map edit <mapname> <red>进入编辑模式。";
        public String commandMessageSetupEditModeAlreadyActive = "<yellow>你已在编辑地图，请先结束当前编辑。";
        public String commandMessageSetupEditModeEntered = "<green>已进入 <map> 的编辑模式。";
        public String commandMessageSetupEditModeSaved = "<green>编辑完成，全部修改已保存。";
        public String commandMessageSetupEditModeCancelled = "<yellow>已取消编辑地图 <map>。";
        public String commandMessageSetupMapListLine = "<reset> <gray>- <white><id> <dark_gray>| <white><name> <dark_gray>| <white><world> <dark_gray>| <white><state>";
        public String commandMessageSetupMapListEmpty = "<red>没有可编辑的地图。";
        public String commandMessageSetupMapDeleteLastBlocked = "<red>不能删除最后一张地图。";
        public String commandMessageSetupMapStateEnabled = "<yellow>编辑中";
        public String commandMessageSetupMapStateDisabled = "<green>可游玩";

        public String commandMessageClassicProfileApplied = "<green>已为 <map> 应用经典规则 <profile>。";
        public String commandMessageClassicProfileCheckpointCount = "<red>规则 <profile> 需要 <required> 个检查点，当前为 <actual> 个。";

    public String mapSelectorTitle = "死神跑酷地图";
    public String mapSelectorMapName = "<gold><name>";
    public List<String> mapSelectorMapLore = asList(
            "<gray>作者： <white><creator>",
            "<gray>世界： <white><world>",
            "<gray>玩家： <white><players>/<maxPlayers>",
            "<gray>状态： <white><status>",
            "<yellow>点击加入"
    );
    public String mapSelectorStatusAvailable = "<green>可加入";
    public String mapSelectorStatusDisabled = "<red>未开放";
    public String mapSelectorStatusMissingLobby = "<gold>缺少等待点";
        public String mapSelectorStatusNotReady = "<gold>未就绪";
                public String mapSelectorStatusEditing = "<gold>编辑中";
        public String mapSelectorStatusInProgress = "<red>比赛中";
        public String mapSelectorMapSelected = "<green>已加入地图 <map>。";
    public String mapSelectorMapUnavailable = "<red>地图暂不可用。";
                public String mapSelectorMapEditing = "<red>地图正在编辑，暂不可加入。";
        public String mapSelectorMapNotReady = "<red>地图尚未配置完成。";
        public String mapSelectorMapFull = "<red>地图人数已满。";
        public String mapSelectorMapInProgress = "<red>本局比赛已经开始。";
        public String mapSelectorAlreadyJoined = "<yellow>你已在这张地图的等待队列中。";

    public String classicVoteTitle = "死神跑酷地图投票";
    public String classicVoteRandomName = "<light_purple><b>随机地图";
    public List<String> classicVoteMapLore = asList(
            "<gray>作者： <white><creator>",
            "<gray>票数： <white><votes>",
            "<yellow>点击投票"
    );
    public List<String> classicVoteRandomLore = asList(
            "<gray>票数： <white><votes>",
            "<gray>随机选项获胜时，将从候选地图中随机选择。",
            "<yellow>点击投票"
    );
    public String classicVoteRecorded = "<green>你的投票：<white><choice>";
    public String classicVoteCountdown = "<gold>投票将在 <white><seconds> 秒后结束";
    public String classicVoteWinner = "<gold>已选地图：<white><map> <gray>作者：<white><creator>";
    public String classicVoteNoMaps = "<red>没有可投票的地图。";
    public String classicVoteInMatch = "<red>比赛中不能参与地图投票。";
    public String classicVoteLeft = "<yellow>你已退出地图投票。";
    public String classicPreshowTitle = "<gold><b><map>";
    public String classicPreshowSubtitle = "<gray>作者：<white><creator>";

    public List<String> chatMessageArenaGameStartRunner = asList(
            "<gold><b>你是跑酷者！",
            "<gray>沿赛道依次通过检查点，到达终点；避开死神机关。",
            "<gray>初始 <green>2 条生命</green>，普通检查点 <green>+2</green>，死亡 <red>−1</red>；耗尽后淘汰。",
            "<gray>快捷栏 <aqua>4 / 5 / 6</aqua> 格羽毛右键：左 / 后 / 右冲刺，每个方向独立冷却 <white>60 秒</white>。",
            "<gray>起跑屏障打开后才能冲刺；退出使用 <white>/dr leave</white>。"
    );

    public List<String> chatMessageArenaGameStartDeath = asList(
            "<gold><b>你是死神！",
            "<gray>观察跑酷者，选择时机右键触发机关。",
            "<gray><yellow>1 / 9</yellow> 格：上 / 下一个机关；<aqua>5</aqua> 格：传送到所选机关控制通道。",
            "<gray><red>2–4 / 6–8</red> 格：激活所选机关；机关冷却时无法重复触发。",
            "<gray>退出使用 <white>/dr leave</white>。"
    );

    public List<String> chatMessageGameEndSpectator = asList(
            "<gray>你已成为观战者，可以观看其他玩家。",
            "<gray>右键退出道具或使用 <white>/dr leave</white> 离开。"
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

    public String arenaDeathTitle = "<red>你死了！";
    public String arenaDeathSubtitle = "<yellow>别放弃，继续挑战！";

    public String arenaCheckpointTitle = "<yellow>到达检查点！";
    public String arenaCheckpointSubtitle = "<gold>已到达第 <yellow><checkpoint> <gold>个检查点。";

    public String arenaFinishTitle = "<dark_aqua><b>成功通关！";
    public String arenaFinishSubtitle = "<gray>你的名次：第 <white><position> <gray>名。";

    public String arenaGameEndTitle = "<red><b>比赛结束！";
    public String arenaGameEndSubtitle = "<reset>";

    public String arenaMoveServerTitle = "<aqua>等待返回…";
    public String arenaMoveServerSubtitle = "<gray><white><endTimer> <gray>秒后返回大厅。";
        public String arenaMoveServerChat = "<gray>比赛结束，正在返回大厅…";

    @Comment({
            "",
            "------------------------------------------------------------------------",
            "                              SCOREBOARD",
            "------------------------------------------------------------------------",
            ""
    })
    public boolean arenaScoreboardEnabled = true;
    public int arenaScoreboardUpdateTicks = 20;
    public String arenaScoreboardTitle = "<yellow><b>死神跑酷";

    public List<String> arenaScoreboardLinesWaiting = asList(
            "<reset>",
            "<white>地图： <green><map>",
            "<white>作者： <gray><creator>",
            "<white>玩家： <green><currentPlayers>/<maxPlayers>",
            "<reset>",
            "<white>等待玩家…"
    );

    public List<String> arenaScoreboardLinesStarting = asList(
            "<reset>",
            "<white>地图： <green><map>",
            "<white>作者： <gray><creator>",
            "<white>玩家： <green><currentPlayers>/<maxPlayers>",
            "<reset>",
            "<white><green><timer> 秒后开始"
    );

    public List<String> arenaScoreboardLinesPlaying = asList(
            "<reset>",
            "<white>剩余时间： <green><timeFormatted>",
            "<white>角色： <green><role>",
            "<white>生命： <red><lives>",
            "<white>分数： <gold><roundPoints>",
            "<white>检查点： <aqua><checkpoint>",
            "<reset>",
            "<white>跑酷者： <green><runners>",
            "<white>死神： <red><deathPlayers>",
            "<reset>",
            "<white>地图： <green><map>",
            "<white>作者： <gray><creator>"
    );

    @Comment({
            "",
            "------------------------------------------------------------------------",
            "                              HOLOGRAMS",
            "------------------------------------------------------------------------",
            ""
    })
    public String arenaHologramTrapDelayed = "<red><delay> 秒";

    @Comment({
            "",
            "------------------------------------------------------------------------",
            "                                ROLES",
            "------------------------------------------------------------------------",
            ""
    })
    public String arenaRolesRunnerName = "<green>跑酷者";
    public String arenaRolesDeathName = "<red>死神";
    public String arenaRolesSpectatorName = "<gray>观战者";

    @Comment({
            "",
            "------------------------------------------------------------------------",
            "                                ITEMS",
            "------------------------------------------------------------------------",
            ""
    })
        public String arenaItemMapSelectorName = "<yellow>选择地图 <gray>（右键）";
    public String arenaItemLeaveName = "<red>退出游戏 <gray>（右键）";

}
