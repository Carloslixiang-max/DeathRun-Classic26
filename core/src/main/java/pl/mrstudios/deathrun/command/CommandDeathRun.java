package pl.mrstudios.deathrun.command;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.regions.Region;
import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.lingala.zip4j.ZipFile;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.api.arena.trap.ITrap;
import pl.mrstudios.deathrun.api.arena.user.enums.Role;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.arena.checkpoint.Checkpoint;
import pl.mrstudios.deathrun.arena.pad.TeleportPad;
import pl.mrstudios.deathrun.arena.selector.MapSelectorService;
import pl.mrstudios.deathrun.arena.sign.SignManager;
import pl.mrstudios.deathrun.arena.trap.TrapRegistry;
import pl.mrstudios.deathrun.config.Configuration;
import pl.mrstudios.deathrun.config.impl.MapConfiguration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.sk89q.worldedit.bukkit.BukkitAdapter.adapt;
import static java.lang.String.join;
import static java.util.Objects.requireNonNull;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.deleteIfExists;
import static java.nio.file.Files.exists;
import static java.nio.file.Paths.get;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Stream.of;
import static net.kyori.adventure.text.minimessage.MiniMessage.miniMessage;
import static net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.bukkit.Material.*;
import static pl.mrstudios.deathrun.api.arena.user.enums.Role.DEATH;
import static pl.mrstudios.deathrun.api.arena.user.enums.Role.RUNNER;

@Command(
    name = "dr",
    aliases = { "deathrun" }
) @SuppressWarnings("unused")
public class CommandDeathRun {

    private static final String PREFIX = "<gold>[DR]</gold> ";

    private final Plugin plugin;
    private final WorldEdit worldEdit;

    private final BukkitAudiences audiences;

    private final TrapRegistry  trapRegistry;
    private final ArenaManager arenaManager;
    private final MapSelectorService mapSelectorService;
    private final SignManager signManager;
    private final Configuration configuration;
    private final Map<UUID, String> setupMapSelection = new HashMap<>();
    private final Set<UUID> setupEditModePlayers = new HashSet<>();
    private final Map<UUID, Integer> checkpointListTokens = new HashMap<>();
    private final Map<UUID, Integer> trapListTokens = new HashMap<>();

    @Inject
    public CommandDeathRun(
            @NotNull Plugin plugin,
            @NotNull WorldEdit worldEdit,
            @NotNull BukkitAudiences audiences,
            @NotNull TrapRegistry trapRegistry,
            @NotNull ArenaManager arenaManager,
            @NotNull MapSelectorService mapSelectorService,
                @NotNull SignManager signManager,
            @NotNull Configuration configuration
    ) {
        this.plugin = plugin;
        this.worldEdit = worldEdit;
        this.audiences = audiences;
        this.trapRegistry = trapRegistry;
        this.arenaManager = arenaManager;
        this.mapSelectorService = mapSelectorService;
        this.signManager = signManager;
        this.configuration = configuration;
    }

    @Execute
    public void noArguments(
            @Context Player player
    ) {
        String content = java.lang.String.join("<br>", this.configuration.language().commandHelpMainLines)
            .replace("<version>", this.plugin.getDescription().getVersion());
        this.message(player, content);
    }

    @Execute(name = "maps")
        @Permission("mrstudios.command.deathrun.join")
    public void maps(
            @Context Player player
    ) {
        if (this.configuration.map().resolvedMaps().isEmpty()) {
            this.message(player, this.configuration.language().commandMessageNoMapsConfigured);
            return;
        }

        this.mapSelectorService.open(player);
    }

    @Execute(name = "join")
    @Permission("mrstudios.command.deathrun.join")
    public void join(
            @Context Player player,
            @Arg("map") String mapId
    ) {
        this.joinPlayerToMap(player, mapId, null);
    }

    @Execute(name = "join")
    @Permission("mrstudios.command.deathrun.join.others")
    public void join(
            @Context CommandSender sender,
            @Arg("map") String mapId,
            @Arg("player") Player target
    ) {
        this.joinPlayerToMap(target, mapId, sender);
    }

    @Execute(name = "join")
    @Permission("mrstudios.command.deathrun.join.others")
    public void joinPlayerMapOrder(
            @Context CommandSender sender,
            @Arg("player") Player target,
            @Arg("map") String mapId
    ) {
        this.joinPlayerToMap(target, mapId, sender);
    }

    @Execute(name = "start")
    @Permission("mrstudios.command.deathrun.start")
    public void startCurrent(
            @Context Player player
    ) {
        ArenaManager.ArenaRuntime runtime = this.arenaManager.runtimeForPlayer(player);
        if (runtime == null) {
            this.message(player, this.configuration.language().commandMessageStartNoCurrentMap);
            return;
        }

        this.handleForceStartResult(player, runtime.mapId(), this.arenaManager.forceStartMap(runtime.mapId()));
    }

    @Execute(name = "start")
    @Permission("mrstudios.command.deathrun.start")
    public void startMap(
            @Context CommandSender sender,
            @Arg("map") String mapId
    ) {
        this.handleForceStartResult(sender, mapId, this.arenaManager.forceStartMap(mapId));
    }

    @Execute(name = "stop")
    @Permission("mrstudios.command.deathrun.stop")
    public void stopCurrent(
            @Context Player player
    ) {
        ArenaManager.ArenaRuntime runtime = this.arenaManager.runtimeForPlayer(player);
        if (runtime == null) {
            this.message(player, this.configuration.language().commandMessageStopNoCurrentMap);
            return;
        }

        this.handleForceStopResult(player, runtime.mapId(), this.arenaManager.forceStopMap(runtime.mapId()));
    }

    @Execute(name = "stop")
    @Permission("mrstudios.command.deathrun.stop")
    public void stopMap(
            @Context CommandSender sender,
            @Arg("map") String mapId
    ) {
        this.handleForceStopResult(sender, mapId, this.arenaManager.forceStopMap(mapId));
    }

    @Execute(name = "reload")
    @Permission("mrstudios.command.deathrun.reload")
    public void reload(
            @Context CommandSender sender
    ) {
        try {
            this.configuration.plugin().load();
            this.configuration.language().load();
            this.configuration.map().load();
            this.configuration.map().ensureMapsMutable();

            this.arenaManager.initialize();
            this.message(sender, this.configuration.language().commandMessageReloadSuccess);
        } catch (Exception exception) {
            this.message(sender, this.configuration.language().commandMessageReloadFailed
                    .replace("<reason>", requireNonNull(exception.getMessage(), "unknown")));
        }
    }

    @Execute(name = "leave")
    @Permission("mrstudios.command.deathrun.leave")
    public void leave(
            @Context Player player
    ) {
        boolean leftMap = this.arenaManager.leaveCurrentMap(player, true);
        boolean leftQueue = this.signManager.leaveQueue(player);
        if (!leftMap && !leftQueue)
            return;

        this.arenaManager.returnPlayerToHub(player);
        this.message(player, "<yellow>You have left the match and returned to the Hub.");
    }

    /* Setup Command */
    @Execute(name = "setup")
    @Permission("mrstudios.command.deathrun.setup")
    public void noArgumentsSetup(
            @Context Player player
    ) {
        this.message(player, PREFIX + "<gray>Setup command is now action-only. Use setup subcommands directly.");
        this.message(player, PREFIX + "<gray>Example: <white>/dr map list</white>");
    }

    @Execute(name = "help")
    @Permission("mrstudios.command.deathrun.setup")
    public void helpSetup(
            @Context Player player
    ) {
        this.sendSetupHelpPage(player, 1);
    }

    @Execute(name = "help")
    @Permission("mrstudios.command.deathrun.setup")
    public void helpSetupPage(
            @Context Player player,
            @Arg("page") int page
    ) {
        this.sendSetupHelpPage(player, page);
    }

    @Execute(name = "map list")
    @Permission("mrstudios.command.deathrun.setup")
    public void setupMapsList(
            @Context Player player
    ) {
        this.configuration.map().ensureMapsMutable();
        List<MapConfiguration.MapDefinition> maps = this.configuration.map().resolvedMaps();
        if (maps.isEmpty()) {
            this.message(player, this.configuration.language().commandMessageSetupMapListEmpty);
            return;
        }

        this.message(player, "<gold>[DR]</gold> <gray>Configured maps:");
        for (MapConfiguration.MapDefinition map : maps) {
            this.message(player, this.configuration.language().commandMessageSetupMapListLine
                    .replace("<id>", this.safe(map.id))
                    .replace("<name>", this.safe(map.name))
                    .replace("<world>", this.safe(map.world))
                    .replace("<state>", map.arenaSetupEnabled
                            ? this.configuration.language().commandMessageSetupMapStateEnabled
                            : this.configuration.language().commandMessageSetupMapStateDisabled));
        }
    }

    @Execute(name = "map edit")
    @Permission("mrstudios.command.deathrun.setup")
    public void setupMapsUse(
            @Context Player player,
            @Arg("id") String id
    ) {
        if (this.setupEditModePlayers.contains(player.getUniqueId())) {
            this.message(player, this.configuration.language().commandMessageSetupEditModeAlreadyActive);
            return;
        }

        this.configuration.map().ensureMapsMutable();
        MapConfiguration.MapDefinition map = this.configuration.map().getMapById(id);
        if (map == null) {
            this.message(player, this.configuration.language().commandMessageSetupMapMissing.replace("<map>", id));
            return;
        }

        String normalizedMapId = this.configuration.map().normalizedMapId(map.id);
        if (this.arenaManager.isMapLockedForEditing(normalizedMapId)) {
            this.message(player, this.configuration.language().mapSelectorMapEditing);
            return;
        }

        this.arenaManager.ensureMapWorldBindings(map);

        Location target = this.arenaManager.resolveMapLocation(this.setupTeleportTarget(player, map), map);
        if (target != null && target.getWorld() == null) {
            this.message(player, this.configuration.language().commandMessageSetupMapWorldUnavailable
                    .replace("<map>", this.safe(map.id))
                    .replace("<world>", this.safe(map.world)));
            return;
        }

        this.setupMapSelection.put(player.getUniqueId(), normalizedMapId);
        this.setupEditModePlayers.add(player.getUniqueId());
        this.arenaManager.setMapEditLocked(normalizedMapId, true);

        if (target != null)
            player.teleport(target);

        this.message(player, this.configuration.language().commandMessageSetupMapSelected.replace("<map>", map.id));
        this.message(player, this.configuration.language().commandMessageSetupEditModeEntered.replace("<map>", this.safe(map.id)));
    }

    @Execute(name = "map create")
    @Permission("mrstudios.command.deathrun.setup")
    public void setupMapsCreate(
            @Context Player player,
            @Arg("id") String id,
            @Arg("world") String worldName
    ) {
        this.configuration.map().ensureMapsMutable();
        String normalized = this.configuration.map().normalizedMapId(id);
        if (this.configuration.map().getMapById(normalized) != null) {
            this.message(player, this.configuration.language().commandMessageSetupMapAlreadyExists.replace("<map>", normalized));
            return;
        }

        World world = this.plugin.getServer().getWorld(worldName);
        if (world == null) {
            this.message(player, this.configuration.language().commandMessageSetupMapInvalidWorld.replace("<world>", worldName));
            return;
        }

        MapConfiguration.MapDefinition map = new MapConfiguration.MapDefinition();
        map.id = normalized;
        map.name = id;
        map.world = world.getName();
        map.arenaSetupEnabled = true;

        this.configuration.map().maps.add(map);
        this.setupMapSelection.put(player.getUniqueId(), map.id);
        this.configuration.map().save();
        this.arenaManager.reloadRuntime(map.id);

        this.message(player, this.configuration.language().commandMessageSetupMapCreated
                .replace("<map>", map.id)
                .replace("<world>", map.world));
    }

    @Execute(name = "map delete")
    @Permission("mrstudios.command.deathrun.setup")
    public void setupMapsDelete(
            @Context Player player,
            @Arg("id") String id
    ) {
        this.configuration.map().ensureMapsMutable();
        if (this.configuration.map().maps.size() <= 1) {
            this.message(player, this.configuration.language().commandMessageSetupMapDeleteLastBlocked);
            return;
        }

        MapConfiguration.MapDefinition map = this.configuration.map().getMapById(id);
        if (map == null) {
            this.message(player, this.configuration.language().commandMessageSetupMapMissing.replace("<map>", id));
            return;
        }

        this.configuration.map().maps.removeIf((candidate) -> this.configuration.map().normalizedMapId(candidate.id).equals(this.configuration.map().normalizedMapId(map.id)));
        this.setupMapSelection.values().removeIf((selected) -> this.configuration.map().normalizedMapId(selected).equals(this.configuration.map().normalizedMapId(map.id)));
        this.configuration.map().save();
        this.arenaManager.initialize();

        this.message(player, this.configuration.language().commandMessageSetupMapDeleted.replace("<map>", map.id));
    }

    @Execute(name = "map enable")
    @Permission("mrstudios.command.deathrun.setup")
    public void setupMapsEnable(
            @Context Player player,
            @Arg("id") String id
    ) {
        this.configuration.map().ensureMapsMutable();
        MapConfiguration.MapDefinition map = this.configuration.map().getMapById(id);
        if (map == null) {
            this.message(player, this.configuration.language().commandMessageSetupMapMissing.replace("<map>", id));
            return;
        }

        map.arenaSetupEnabled = true;
        this.setupMapSelection.put(player.getUniqueId(), this.configuration.map().normalizedMapId(map.id));
        this.configuration.map().save();
        this.message(player, this.configuration.language().commandMessageSetupMapEnabled.replace("<map>", map.id));
        this.message(player, this.configuration.language().commandMessageSetupMapSelected.replace("<map>", map.id));
    }

    @Execute(name = "map disable")
    @Permission("mrstudios.command.deathrun.setup")
    public void setupMapsDisable(
            @Context Player player,
            @Arg("id") String id
    ) {
        this.configuration.map().ensureMapsMutable();
        MapConfiguration.MapDefinition map = this.configuration.map().getMapById(id);
        if (map == null) {
            this.message(player, this.configuration.language().commandMessageSetupMapMissing.replace("<map>", id));
            return;
        }

        List<String> issues = this.mapPromotionIssues(map, true);
        if (!issues.isEmpty()) {
            this.message(player, this.configuration.language().commandMessageSetupMapPreflightFailed
                    .replace("<map>", this.safe(map.id))
                    .replace("<issues>", String.join(", ", issues)));
            return;
        }

        map.arenaSetupEnabled = false;
        this.configuration.map().save();
        this.message(player, this.configuration.language().commandMessageSetupMapPreflightPassed.replace("<map>", this.safe(map.id)));
        this.message(player, this.configuration.language().commandMessageSetupMapDisabled.replace("<map>", map.id));
    }

    @Execute(name = "map restore")
    @Permission("mrstudios.command.deathrun.setup")
    public void setupMapsRestore(
            @Context Player player,
            @Arg("id") String id
    ) {
        this.configuration.map().ensureMapsMutable();
        MapConfiguration.MapDefinition map = this.configuration.map().getMapById(id);
        if (map == null) {
            this.message(player, this.configuration.language().commandMessageSetupMapMissing.replace("<map>", id));
            return;
        }

        if (this.arenaManager.playersInMap(map.id) > 0) {
            this.message(player, this.configuration.language().commandMessageSetupMapRestorePlayersPresent);
            return;
        }

        String worldName = map.world;
        if (worldName == null || worldName.isBlank()) {
            this.message(player, this.configuration.language().commandMessageSetupMapRestoreWorldMissing.replace("<world>", "unknown"));
            return;
        }

        Path backupZip = get(this.plugin.getDataFolder().toString(), "backup", worldName + ".zip");
        if (!exists(backupZip)) {
            this.message(player, this.configuration.language().commandMessageSetupMapRestoreMissingBackup.replace("<world>", worldName));
            return;
        }

        try {
            World loadedWorld = this.plugin.getServer().getWorld(worldName);
            if (loadedWorld != null && !this.plugin.getServer().unloadWorld(loadedWorld, false)) {
                this.message(player, this.configuration.language().commandMessageSetupMapRestoreUnloadFailed.replace("<world>", worldName));
                return;
            }

            Path worldFolder = this.plugin.getServer().getWorldContainer().toPath().resolve(worldName);
            if (exists(worldFolder))
                deleteDirectory(worldFolder.toFile());

            try (ZipFile zipFile = new ZipFile(backupZip.toFile())) {
                zipFile.extractAll(this.plugin.getServer().getWorldContainer().getAbsolutePath());
            }

            World restoredWorld = this.plugin.getServer().createWorld(new WorldCreator(worldName));
            if (restoredWorld == null) {
                this.message(player, this.configuration.language().commandMessageSetupMapRestoreLoadFailed.replace("<world>", worldName));
                return;
            }

            this.rebindMapWorldReferences(map, restoredWorld);
            this.configuration.map().save();
            this.arenaManager.reloadRuntime(map.id);

            this.message(player, this.configuration.language().commandMessageSetupMapRestoreSuccess
                    .replace("<map>", map.id)
                    .replace("<world>", worldName));
        } catch (Exception exception) {
            this.message(player, this.configuration.language().commandMessageSetupMapRestoreFailed
                    .replace("<reason>", requireNonNull(exception.getMessage(), "unknown")));
        }
    }

    @Execute(name = "map check")
    @Permission("mrstudios.command.deathrun.setup")
    public void setupMapsCheck(
            @Context Player player
    ) {
        this.configuration.map().ensureMapsMutable();
        this.message(player, this.configuration.language().commandMessageSetupMapCheckHeader);

        boolean hasIssues = false;
        for (MapConfiguration.MapDefinition map : this.configuration.map().resolvedMaps()) {
            List<String> issues = this.mapIssues(map);
            if (issues.isEmpty()) {
                this.message(player, this.configuration.language().commandMessageSetupMapCheckEntryOk
                        .replace("<map>", this.safe(map.id)));
                continue;
            }

            hasIssues = true;
            this.message(player, this.configuration.language().commandMessageSetupMapCheckEntryIssues
                    .replace("<map>", this.safe(map.id))
                    .replace("<issues>", String.join(", ", issues)));
        }

        if (!hasIssues)
            this.message(player, this.configuration.language().commandMessageSetupMapCheckNoIssues);
    }

    @Execute(name = "map check")
    @Permission("mrstudios.command.deathrun.setup")
    public void setupMapsCheckSingle(
            @Context Player player,
            @Arg("id") String id
    ) {
        this.configuration.map().ensureMapsMutable();
        MapConfiguration.MapDefinition map = this.configuration.map().getMapById(id);
        if (map == null) {
            this.message(player, this.configuration.language().commandMessageSetupMapMissing.replace("<map>", id));
            return;
        }

        this.message(player, this.configuration.language().commandMessageSetupMapCheckHeader);
        List<String> issues = this.mapIssues(map);
        if (issues.isEmpty()) {
            this.message(player, this.configuration.language().commandMessageSetupMapCheckEntryOk
                    .replace("<map>", this.safe(map.id)));
            this.message(player, this.configuration.language().commandMessageSetupMapCheckNoIssues);
            return;
        }

        this.message(player, this.configuration.language().commandMessageSetupMapCheckEntryIssues
                .replace("<map>", this.safe(map.id))
                .replace("<issues>", String.join(", ", issues)));
    }

    @Execute(name = "map status")
    @Permission("mrstudios.command.deathrun.setup")
    public void setupMapsStatus(
            @Context Player player
    ) {
        this.configuration.map().ensureMapsMutable();
        this.message(player, this.configuration.language().commandMessageSetupMapStatusHeader);

        for (MapConfiguration.MapDefinition map : this.configuration.map().resolvedMaps())
            this.sendMapStatus(player, map, false);
    }

    @Execute(name = "map status")
    @Permission("mrstudios.command.deathrun.setup")
    public void setupMapsStatusSingle(
            @Context Player player,
            @Arg("id") String id
    ) {
        this.configuration.map().ensureMapsMutable();
        MapConfiguration.MapDefinition map = this.configuration.map().getMapById(id);
        if (map == null) {
            this.message(player, this.configuration.language().commandMessageSetupMapMissing.replace("<map>", id));
            return;
        }

        this.message(player, this.configuration.language().commandMessageSetupMapStatusHeader);
        this.sendMapStatus(player, map, true);
    }

    @Execute(name = "map fixbarrier")
    @Permission("mrstudios.command.deathrun.setup")
    public void setupMapsFixBarrier(
            @Context Player player,
            @Arg("id") String id
    ) {
        this.configuration.map().ensureMapsMutable();
        MapConfiguration.MapDefinition map = this.configuration.map().getMapById(id);
        if (map == null) {
            this.message(player, this.configuration.language().commandMessageSetupMapMissing.replace("<map>", id));
            return;
        }

        if (map.arenaStartBarrierBlocks.isEmpty()) {
            this.message(player, this.configuration.language().commandMessageSetupMapFixBarrierNoBarrier.replace("<map>", map.id));
            return;
        }

        map.arenaStartBarrierRestoreMaterials = map.arenaStartBarrierBlocks.stream()
                .map((location) -> location.getBlock().getType())
                .toList();
        this.configuration.map().save();
        this.message(player, this.configuration.language().commandMessageSetupMapFixBarrierSuccess.replace("<map>", map.id));
    }

    @Execute(name = "map backup")
    @Permission("mrstudios.command.deathrun.setup")
    public void setupMapsBackup(
            @Context Player player,
            @Arg("id") String id
    ) {
        this.configuration.map().ensureMapsMutable();
        MapConfiguration.MapDefinition map = this.configuration.map().getMapById(id);
        if (map == null) {
            this.message(player, this.configuration.language().commandMessageSetupMapMissing.replace("<map>", id));
            return;
        }

        String worldName = map.world;
        if (worldName == null || worldName.isBlank()) {
            this.message(player, this.configuration.language().commandMessageSetupMapRestoreWorldMissing.replace("<world>", "unknown"));
            return;
        }

        World world = this.plugin.getServer().getWorld(worldName);
        if (world == null) {
            this.message(player, this.configuration.language().commandMessageSetupMapBackupWorldMissing.replace("<world>", worldName));
            return;
        }

        try {
            this.refreshWorldBackup(worldName, world);
            this.message(player, this.configuration.language().commandMessageSetupMapBackupSuccess
                    .replace("<map>", this.safe(map.id))
                    .replace("<world>", worldName));
        } catch (Exception exception) {
            this.message(player, this.configuration.language().commandMessageSetupMapBackupFailed
                    .replace("<reason>", requireNonNull(exception.getMessage(), "unknown")));
        }
    }

    @Execute(name = "map autofix")
    @Permission("mrstudios.command.deathrun.setup")
    public void setupMapsAutofix(
            @Context Player player,
            @Arg("id") String id
    ) {
        this.configuration.map().ensureMapsMutable();
        MapConfiguration.MapDefinition map = this.configuration.map().getMapById(id);
        if (map == null) {
            this.message(player, this.configuration.language().commandMessageSetupMapMissing.replace("<map>", id));
            return;
        }

        List<String> actions = new ArrayList<>();
        boolean configChanged = false;

        if (this.rebuildBarrierSnapshot(map)) {
            actions.add("barrier-snapshot-refreshed");
            configChanged = true;
        } else {
            actions.add("barrier-snapshot-skipped(no-barrier)");
        }

        String worldName = map.world;
        if (worldName != null && !worldName.isBlank()) {
            World world = this.plugin.getServer().getWorld(worldName);
            if (world != null) {
                try {
                    this.refreshWorldBackup(worldName, world);
                    actions.add("backup-refreshed");
                } catch (Exception exception) {
                    this.message(player, this.configuration.language().commandMessageSetupMapBackupFailed
                            .replace("<reason>", requireNonNull(exception.getMessage(), "unknown")));
                    return;
                }
            } else {
                actions.add("backup-skipped(world-not-loaded)");
            }
        } else {
            actions.add("backup-skipped(world-not-set)");
        }

        if (configChanged)
            this.configuration.map().save();

        if (actions.isEmpty()) {
            this.message(player, this.configuration.language().commandMessageSetupMapAutofixNoChanges
                    .replace("<map>", this.safe(map.id)));
            return;
        }

        this.message(player, this.configuration.language().commandMessageSetupMapAutofixApplied
                .replace("<map>", this.safe(map.id))
                .replace("<actions>", String.join(", ", actions)));
    }

    @Execute(name = "cp add")
    @Permission("mrstudios.command.deathrun.setup")
    public void addCheckpoint(
            @Context Player player
    ) {

        MapConfiguration.MapDefinition map = this.selectedMapForSetup(player, true);
        if (map == null)
            return;

        this.ensureMutableSetupCollections(map);

        List<Location> selectedLocations = this.locations(player);
        if (selectedLocations.isEmpty()) {
            this.message(player, this.configuration.language().commandMessageCheckpointAreaEmpty);
            return;
        }

        int checkpointId = map.arenaCheckpoints.size();
        checkpointId = checkpointId + 1;
        map.arenaCheckpoints.add(
            new Checkpoint(checkpointId, player.getLocation().toCenterLocation(), selectedLocations, "")
        );

        this.configuration.map().save();

        this.message(player, this.configuration.language().commandMessageCheckpointAdded
            .replace("<checkpoint>", String.valueOf(checkpointId))
            .replace("<map>", this.safe(map.id)));
        this.message(player, this.configuration.language().commandMessageCheckpointAreaInfo
            .replace("<blocks>", String.valueOf(selectedLocations.size())));

    }

    @Execute(name = "cp list")
    @Permission("mrstudios.command.deathrun.setup")
    public void setupCheckpointList(
            @Context Player player
    ) {
        MapConfiguration.MapDefinition map = this.selectedMapForSetup(player, false);
        if (map == null)
            return;

        if (map.arenaCheckpoints.isEmpty()) {
            this.message(player, PREFIX + "<gray>No checkpoints set for map <white>" + this.safe(map.id) + "<gray>.");
            return;
        }

        String normalizedMapId = this.configuration.map().normalizedMapId(map.id);
        int token = this.nextCheckpointListToken(player);
        this.message(player, PREFIX + "<gray>Checkpoints for <white>" + this.safe(map.id) + "<gray>:");
        for (int i = 0; i < map.arenaCheckpoints.size(); i++) {
            Checkpoint checkpoint = map.arenaCheckpoints.get(i);
            Location spawn = checkpoint.spawn();
            String displayName = (checkpoint.name() == null || checkpoint.name().isBlank())
                    ? "#" + checkpoint.id()
                    : checkpoint.name();

            Component line = Component.text("[", NamedTextColor.GRAY)
                .append(Component.text(i + 1, NamedTextColor.WHITE))
                .append(Component.text("] ", NamedTextColor.GRAY))
                .append(Component.text(displayName, NamedTextColor.WHITE))
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(Component.text(spawn.getBlockX() + ", " + spawn.getBlockY() + ", " + spawn.getBlockZ(), NamedTextColor.GRAY))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("[Teleport]", NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.runCommand("/dr cp tpclick " + token + " " + checkpoint.id())))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("[Delete]", NamedTextColor.RED)
                    .clickEvent(ClickEvent.runCommand("/dr cp delclick " + token + " " + checkpoint.id())));
            this.message(player, line);
        }
    }

    @Execute(name = "cp tpclick")
    @Permission("mrstudios.command.deathrun.setup")
    public void setupCheckpointTeleportClick(
            @Context Player player,
            @Arg("token") int token,
            @Arg("id") int checkpointId
    ) {
        if (!this.isCheckpointListTokenValid(player, token)) {
            this.message(player, PREFIX + "<yellow>This entry no longer exists. Please run the list command again.");
            return;
        }

        this.setupCheckpointTeleport(player, checkpointId);
    }

    @Execute(name = "cp tp")
    @Permission("mrstudios.command.deathrun.setup")
    public void setupCheckpointTeleport(
            @Context Player player,
            @Arg("id") int checkpointId
    ) {
        MapConfiguration.MapDefinition map = this.selectedMapForSetup(player, false);
        if (map == null)
            return;

        Checkpoint checkpoint = map.arenaCheckpoints.stream()
                .filter((candidate) -> candidate.id() == checkpointId)
                .findFirst()
                .orElse(null);

        if (checkpoint == null) {
            this.message(player, PREFIX + "<red>Checkpoint <white>#" + checkpointId + "<red> was not found on map <white>" + this.safe(map.id) + "<red>.");
            return;
        }

        Location spawn = this.arenaManager.resolveMapLocation(checkpoint.spawn(), map);
        if (spawn == null || spawn.getWorld() == null) {
            this.message(player, this.configuration.language().commandMessageSetupMapWorldUnavailable
                .replace("<map>", this.safe(map.id))
                .replace("<world>", this.safe(map.world)));
            return;
        }

        player.teleport(spawn);
        this.message(player, PREFIX + "<gray>Teleported to checkpoint <white>" + this.displayCheckpointNumber(map, checkpoint.id()) + "<gray> at <white>"
            + spawn.getBlockX() + ", " + spawn.getBlockY() + ", " + spawn.getBlockZ());
    }

    @Execute(name = "cp tp")
    @Permission("mrstudios.command.deathrun.setup")
    public void setupCheckpointTeleportMap(
            @Context Player player,
            @Arg("map") String mapId,
            @Arg("id") int checkpointId
    ) {
        this.configuration.map().ensureMapsMutable();
        MapConfiguration.MapDefinition map = this.configuration.map().getMapById(mapId);
        if (map == null) {
            this.message(player, this.configuration.language().commandMessageSetupMapMissing.replace("<map>", mapId));
            return;
        }

        Checkpoint checkpoint = map.arenaCheckpoints.stream()
                .filter((candidate) -> candidate.id() == checkpointId)
                .findFirst()
                .orElse(null);

        if (checkpoint == null) {
            this.message(player, this.configuration.language().commandMessageCheckpointNotFound
                    .replace("<checkpoint>", String.valueOf(checkpointId))
                    .replace("<map>", this.safe(map.id)));
            return;
        }

        this.setupMapSelection.put(player.getUniqueId(), this.configuration.map().normalizedMapId(map.id));
        Location spawn = this.arenaManager.resolveMapLocation(checkpoint.spawn(), map);
        if (spawn == null || spawn.getWorld() == null) {
            this.message(player, this.configuration.language().commandMessageSetupMapWorldUnavailable
                .replace("<map>", this.safe(map.id))
                .replace("<world>", this.safe(map.world)));
            return;
        }

        player.teleport(spawn);
        this.message(player, PREFIX + "<gray>Teleported to checkpoint <white>" + this.displayCheckpointNumber(map, checkpoint.id()) + "<gray> at <white>"
            + spawn.getBlockX() + ", " + spawn.getBlockY() + ", " + spawn.getBlockZ());
    }

    @Execute(name = "cp delete")
    @Permission("mrstudios.command.deathrun.setup")
    public void setupCheckpointDelete(
            @Context Player player,
            @Arg("id") int checkpointId
    ) {
        MapConfiguration.MapDefinition map = this.selectedMapForSetup(player, true);
        if (map == null)
            return;

        this.ensureMutableSetupCollections(map);

        Checkpoint removed = null;
        List<Checkpoint> checkpoints = new ArrayList<>(map.arenaCheckpoints);
        for (int i = 0; i < checkpoints.size(); i++) {
            if (checkpoints.get(i).id() != checkpointId)
                continue;

            removed = checkpoints.remove(i);
            break;
        }

        if (removed == null) {
            this.message(player, this.configuration.language().commandMessageCheckpointNotFound
                    .replace("<checkpoint>", String.valueOf(checkpointId))
                    .replace("<map>", this.safe(map.id)));
            return;
        }

        map.arenaCheckpoints = checkpoints;
        this.normalizeCheckpointIds(map);
        if (map.arenaFinishCheckpointId != null && map.arenaFinishCheckpointId == removed.id())
            map.arenaFinishCheckpointId = null;
        this.configuration.map().save();
        this.message(player, this.configuration.language().commandMessageCheckpointDeleted
            .replace("<checkpoint>", String.valueOf(this.displayCheckpointNumber(map, removed.id())))
                .replace("<map>", this.safe(map.id)));

        this.setupCheckpointList(player);
    }

    @Execute(name = "cp delclick")
    @Permission("mrstudios.command.deathrun.setup")
    public void setupCheckpointDeleteClick(
            @Context Player player,
            @Arg("token") int token,
            @Arg("id") int checkpointId
    ) {
        if (!this.isCheckpointListTokenValid(player, token)) {
            this.message(player, PREFIX + "<yellow>This entry no longer exists. Please run the list command again.");
            return;
        }

        this.setupCheckpointDelete(player, checkpointId);
    }

    @Execute(name = "cp setorder")
    @Permission("mrstudios.command.deathrun.setup")
    public void setupCheckpointSetOrder(
            @Context Player player,
            @Arg("id") int checkpointId,
            @Arg("position") int position
    ) {
        MapConfiguration.MapDefinition map = this.selectedMapForSetup(player, true);
        if (map == null)
            return;

        if (map.arenaCheckpoints.isEmpty()) {
            this.message(player, PREFIX + "<gray>No checkpoints set for map <white>" + this.safe(map.id) + "<gray>.");
            return;
        }

        int sourceIndex = -1;
        for (int i = 0; i < map.arenaCheckpoints.size(); i++) {
            if (map.arenaCheckpoints.get(i).id() == checkpointId) {
                sourceIndex = i;
                break;
            }
        }

        if (sourceIndex < 0) {
            this.message(player, this.configuration.language().commandMessageCheckpointNotFound
                    .replace("<checkpoint>", String.valueOf(checkpointId))
                    .replace("<map>", this.safe(map.id)));
            return;
        }

        int targetIndex = Math.max(1, Math.min(position, map.arenaCheckpoints.size())) - 1;
        if (targetIndex == sourceIndex) {
            this.message(player, this.configuration.language().commandMessageCheckpointOrderUpdated
                    .replace("<checkpoint>", String.valueOf(checkpointId))
                    .replace("<position>", String.valueOf(targetIndex + 1)));
            return;
        }

        List<Checkpoint> reordered = new ArrayList<>(map.arenaCheckpoints);
        Checkpoint checkpoint = reordered.remove(sourceIndex);
        reordered.add(targetIndex, checkpoint);
        map.arenaCheckpoints = reordered;
        this.normalizeCheckpointIds(map);
        this.configuration.map().save();

        this.message(player, this.configuration.language().commandMessageCheckpointOrderUpdated
            .replace("<checkpoint>", String.valueOf(this.displayCheckpointNumber(map, checkpoint.id())))
                .replace("<position>", String.valueOf(targetIndex + 1)));
    }

    @Execute(name = "cp setfinish")
    @Permission("mrstudios.command.deathrun.setup")
    public void setupCheckpointSetFinish(
            @Context Player player,
            @Arg("id") int checkpointId
    ) {
        MapConfiguration.MapDefinition map = this.selectedMapForSetup(player, true);
        if (map == null)
            return;

        if (map.arenaCheckpoints.isEmpty()) {
            this.message(player, PREFIX + "<gray>No checkpoints set for map <white>" + this.safe(map.id) + "<gray>.");
            return;
        }

        int sourceIndex = -1;
        for (int i = 0; i < map.arenaCheckpoints.size(); i++) {
            if (map.arenaCheckpoints.get(i).id() == checkpointId) {
                sourceIndex = i;
                break;
            }
        }

        if (sourceIndex < 0) {
            this.message(player, this.configuration.language().commandMessageCheckpointNotFound
                    .replace("<checkpoint>", String.valueOf(checkpointId))
                    .replace("<map>", this.safe(map.id)));
            return;
        }

        List<Checkpoint> reordered = new ArrayList<>(map.arenaCheckpoints);
        Checkpoint checkpoint = reordered.remove(sourceIndex);
        reordered.add(checkpoint);
        map.arenaCheckpoints = reordered;
        this.normalizeCheckpointIds(map);
        map.arenaFinishCheckpointId = map.arenaCheckpoints.get(map.arenaCheckpoints.size() - 1).id();
        this.configuration.map().save();

        this.message(player, this.configuration.language().commandMessageCheckpointFinishSet
            .replace("<checkpoint>", String.valueOf(this.displayCheckpointNumber(map, map.arenaFinishCheckpointId))));
    }

    @Execute(name = "cp setnameindex")
    @Permission("mrstudios.command.deathrun.setup")
    public void setupCheckpointSetName(
            @Context Player player,
            @Arg("index") int index,
            @Arg("name") String name
    ) {
        MapConfiguration.MapDefinition map = this.selectedMapForSetup(player, true);
        if (map == null)
            return;

        this.ensureMutableSetupCollections(map);

        if (map.arenaCheckpoints.isEmpty()) {
            this.message(player, PREFIX + "<gray>No checkpoints set for map <white>" + this.safe(map.id) + "<gray>.");
            return;
        }

        int targetIndex = index - 1;
        if (targetIndex < 0 || targetIndex >= map.arenaCheckpoints.size()) {
            this.message(player, this.configuration.language().commandMessageCheckpointNotFound
                    .replace("<checkpoint>", String.valueOf(index))
                    .replace("<map>", this.safe(map.id)));
            return;
        }

        Checkpoint checkpoint = map.arenaCheckpoints.get(targetIndex);
        map.arenaCheckpoints.set(targetIndex, new Checkpoint(
                checkpoint.id(),
                checkpoint.spawn(),
                checkpoint.locations(),
                name
        ));
        this.configuration.map().save();

        this.message(player, this.configuration.language().commandMessageCheckpointNameSet
            .replace("<checkpoint>", String.valueOf(this.displayCheckpointNumber(map, checkpoint.id())))
                .replace("<name>", name));
    }

    @Execute(name = "cp setname")
    @Permission("mrstudios.command.deathrun.setup")
    public void setupCheckpointSetNameById(
            @Context Player player,
            @Arg("id") int checkpointId,
            @Arg("name") String name
    ) {
        MapConfiguration.MapDefinition map = this.selectedMapForSetup(player, true);
        if (map == null)
            return;

        this.ensureMutableSetupCollections(map);

        for (int i = 0; i < map.arenaCheckpoints.size(); i++) {
            Checkpoint checkpoint = map.arenaCheckpoints.get(i);
            if (checkpoint.id() != checkpointId)
                continue;

            map.arenaCheckpoints.set(i, new Checkpoint(
                    checkpoint.id(),
                    checkpoint.spawn(),
                    checkpoint.locations(),
                    name
            ));
                this.configuration.map().save();

            this.message(player, this.configuration.language().commandMessageCheckpointNameSet
                    .replace("<checkpoint>", String.valueOf(this.displayCheckpointNumber(map, checkpoint.id())))
                    .replace("<name>", name));
            return;
        }

        this.message(player, this.configuration.language().commandMessageCheckpointNotFound
                .replace("<checkpoint>", String.valueOf(checkpointId))
                .replace("<map>", this.safe(map.id)));
    }

    @Execute(name = "cp move")
    @Permission("mrstudios.command.deathrun.setup")
    public void setupCheckpointMove(
            @Context Player player,
            @Arg("id") int checkpointId
    ) {
        MapConfiguration.MapDefinition map = this.selectedMapForSetup(player, true);
        if (map == null)
            return;

        for (int i = 0; i < map.arenaCheckpoints.size(); i++) {
            Checkpoint checkpoint = map.arenaCheckpoints.get(i);
            if (checkpoint.id() != checkpointId)
                continue;

            map.arenaCheckpoints.set(i, new Checkpoint(
                    checkpoint.id(),
                    player.getLocation().toCenterLocation(),
                    checkpoint.locations(),
                    checkpoint.name()
            ));
                this.configuration.map().save();

            this.message(player, this.configuration.language().commandMessageCheckpointMoved
                    .replace("<checkpoint>", String.valueOf(this.displayCheckpointNumber(map, checkpoint.id()))));
            return;
        }

        this.message(player, this.configuration.language().commandMessageCheckpointNotFound
                .replace("<checkpoint>", String.valueOf(checkpointId))
                .replace("<map>", this.safe(map.id)));
    }

    @Execute(name = "addspawn")
    @Permission("mrstudios.command.deathrun.setup")
    public void addSpawn(
            @Context Player player,
            @Arg("role") Role role
    ) {

        MapConfiguration.MapDefinition map = this.selectedMapForSetup(player, true);
        if (map == null)
            return;

        switch (role) {

            case RUNNER ->
                map.arenaRunnerSpawnLocations.add(player.getLocation().toCenterLocation());

            case DEATH ->
                map.arenaDeathSpawnLocations.add(player.getLocation().toCenterLocation());

            default ->
                    this.message(player, this.configuration.language().commandMessageRoleInvalid);

        }

        if (role != DEATH && role != RUNNER)
            return;

        this.configuration.map().save();
        this.message(player, this.configuration.language().commandMessageRoleSpawnAdded.replace("<role>", role.name()));

    }

    @Execute(name = "trap add")
    @Permission("mrstudios.command.deathrun.setup")
    public void addTrap(
            @Context Player player,
            @Arg("type") String type
    ) throws Exception {
        this.trap(player, type, (Object) null);
    }

    @Execute(name = "trap add")
    @Permission("mrstudios.command.deathrun.setup")
    public void addTrap(
            @Context Player player,
            @Arg("type") String type,
            @Arg("material") Material material
    ) throws Exception {
        this.trap(player, type, material);
    }

    @Execute(name = "trap add")
    @Permission("mrstudios.command.deathrun.setup")
    public void addTrap(
            @Context Player player,
            @Arg("type") String type,
            @Arg("particle") Particle particle
    ) throws Exception {
        this.trap(player, type, particle, 5, 0.25);
    }

    @Execute(name = "trap add")
    @Permission("mrstudios.command.deathrun.setup")
    public void addTrap(
            @Context Player player,
            @Arg("type") String type,
            @Arg("particle") Particle particle,
            @Arg("count") int count,
            @Arg("offset") double offset
    ) throws Exception {
        this.trap(player, type, particle, count, offset);
    }

    @Execute(name = "trap list")
    @Permission("mrstudios.command.deathrun.setup")
    public void setupTrapList(
            @Context Player player
    ) {
        MapConfiguration.MapDefinition map = this.selectedMapForSetup(player, false);
        if (map == null)
            return;

        if (map.arenaTraps.isEmpty()) {
            this.message(player, PREFIX + "<gray>No traps set for map <white>" + this.safe(map.id) + "<gray>.");
            return;
        }

        this.message(player, PREFIX + "<gray>Traps for <white>" + this.safe(map.id) + "<gray>:");
        int token = this.nextTrapListToken(player);
        for (int i = 0; i < map.arenaTraps.size(); i++) {
            ITrap trap = map.arenaTraps.get(i);
            Location button = this.arenaManager.resolveMapLocation(trap.getButton(), map);
            String trapName = trap.getClass().getSimpleName().replace("Trap", "");
            long durationSeconds = trap.getDuration().toSeconds();

            String coordinates = (button != null && button.getWorld() != null)
                    ? button.getBlockX() + ", " + button.getBlockY() + ", " + button.getBlockZ()
                    : "unknown";

                Component line = Component.text("[", NamedTextColor.GRAY)
                    .append(Component.text(i + 1, NamedTextColor.WHITE))
                    .append(Component.text("] ", NamedTextColor.GRAY))
                    .append(Component.text(trapName, NamedTextColor.WHITE))
                    .append(Component.text(" - button ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(coordinates, NamedTextColor.GRAY))
                    .append(Component.text(" | targets ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(trap.getLocations().size(), NamedTextColor.WHITE))
                    .append(Component.text(" | duration ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(durationSeconds + "s", NamedTextColor.WHITE))
                    .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("[Teleport]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/dr trap tpclick " + token + " " + (i + 1))))
                    .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("[Delete]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/dr trap delclick " + token + " " + (i + 1))));
                this.message(player, line);
        }
    }

    @Execute(name = "trap tpclick")
    @Permission("mrstudios.command.deathrun.setup")
    public void setupTrapTeleportClick(
            @Context Player player,
            @Arg("token") int token,
            @Arg("index") int index
    ) {
        if (!this.isTrapListTokenValid(player, token)) {
            this.message(player, PREFIX + "<yellow>This entry no longer exists. Please run the list command again.");
            return;
        }

        this.setupTrapTeleport(player, index);
    }

    @Execute(name = "trap tp")
    @Permission("mrstudios.command.deathrun.setup")
    public void setupTrapTeleport(
            @Context Player player,
            @Arg("index") int index
    ) {
        MapConfiguration.MapDefinition map = this.selectedMapForSetup(player, false);
        if (map == null)
            return;

        if (index <= 0 || index > map.arenaTraps.size()) {
            this.message(player, PREFIX + "<red>Trap <white>#" + index + "<red> was not found on map <white>" + this.safe(map.id) + "<red>.");
            return;
        }

        ITrap trap = map.arenaTraps.get(index - 1);
        Location button = this.arenaManager.resolveMapLocation(trap.getButton(), map);
        if (button == null || button.getWorld() == null) {
            this.message(player, this.configuration.language().commandMessageSetupMapWorldUnavailable
                    .replace("<map>", this.safe(map.id))
                    .replace("<world>", this.safe(map.world)));
            return;
        }

        player.teleport(button.toCenterLocation());
        this.message(player, PREFIX + "<gray>Teleported to trap <white>#" + index + "<gray> at <white>"
                + button.getBlockX() + ", " + button.getBlockY() + ", " + button.getBlockZ());
    }

    @Execute(name = "trap delete")
    @Permission("mrstudios.command.deathrun.setup")
    public void setupTrapDelete(
            @Context Player player,
            @Arg("index") int index
    ) {
        MapConfiguration.MapDefinition map = this.selectedMapForSetup(player, true);
        if (map == null)
            return;

        this.ensureMutableSetupCollections(map);

        if (index <= 0 || index > map.arenaTraps.size()) {
            this.message(player, PREFIX + "<red>Trap <white>#" + index + "<red> was not found on map <white>" + this.safe(map.id) + "<red>.");
            return;
        }

        ITrap removed = map.arenaTraps.remove(index - 1);
        this.configuration.map().save();

        String trapName = removed.getClass().getSimpleName().replace("Trap", "");
        this.message(player, PREFIX + "<green>Deleted trap <white>#" + index + " <green>(" + trapName + ") from map <white>" + this.safe(map.id) + "<green>.");

        this.setupTrapList(player);
    }

    @Execute(name = "trap delclick")
    @Permission("mrstudios.command.deathrun.setup")
    public void setupTrapDeleteClick(
            @Context Player player,
            @Arg("token") int token,
            @Arg("index") int index
    ) {
        if (!this.isTrapListTokenValid(player, token)) {
            this.message(player, PREFIX + "<yellow>This entry no longer exists. Please run the list command again.");
            return;
        }

        this.setupTrapDelete(player, index);
    }

    @Execute(name = "edit create")
    @Permission("mrstudios.command.deathrun.setup")
    public void setupCreate(
            @Context Player player,
            @Arg("name") String name
    ) {

        this.configuration.map().ensureMapsMutable();
        String normalized = this.configuration.map().normalizedMapId(name);
        if (this.configuration.map().getMapById(normalized) != null) {
            this.message(player, this.configuration.language().commandMessageSetupMapAlreadyExists.replace("<map>", normalized));
            return;
        }

        MapConfiguration.MapDefinition map = new MapConfiguration.MapDefinition();
        map.id = normalized;
        map.name = name;
        map.world = player.getWorld().getName();
        map.arenaSetupEnabled = true;

        this.configuration.map().maps.add(map);
        this.setupMapSelection.put(player.getUniqueId(), normalized);
        this.setupEditModePlayers.add(player.getUniqueId());

        this.configuration.map().save();
        this.arenaManager.reloadRuntime(normalized);

        this.message(player, this.configuration.language().commandMessageSetupMapCreated
                .replace("<map>", map.id)
                .replace("<world>", map.world));
        this.message(player, this.configuration.language().commandMessageSetupMapSelected.replace("<map>", map.id));
        this.message(player, this.configuration.language().commandMessageSetupEditModeEntered.replace("<map>", this.safe(map.id)));
    }

    @Execute(name = "setbarrier")
    @Permission("mrstudios.command.deathrun.setup")
    public void setStartBarrier(
            @Context Player player
    ) {
        this.setStartBarrier(player, null);
    }

    @Execute(name = "setbarrier")
    @Permission("mrstudios.command.deathrun.setup")
    public void setStartBarrier(
            @Context Player player,
            @Arg("material") Material material
    ) {

        MapConfiguration.MapDefinition map = this.selectedMapForSetup(player, true);
        if (map == null)
            return;

        List<Location> locations = this.locations(player);

        if (material != null)
            locations.removeIf((location) -> !location.getBlock().getType().equals(material));

        map.arenaStartBarrierBlocks = locations;
        map.arenaStartBarrierRestoreMaterials = locations.stream()
            .map((location) -> location.getBlock().getType())
            .toList();
        this.configuration.map().save();
        this.message(player, this.configuration.language().commandMessageStartBarrierSet);

    }

    @Execute(name = "setlobby")
    @Permission("mrstudios.command.deathrun.setup")
    public void setWaitingLobby(
            @Context Player player
    ) {

        MapConfiguration.MapDefinition map = this.selectedMapForSetup(player, true);
        if (map == null)
            return;

        map.world = player.getWorld().getName();
        map.arenaWaitingLobbyLocation = player.getLocation().toCenterLocation();
        this.configuration.map().save();
        this.message(player, this.configuration.language().commandMessageWaitingLobbySet);

    }

    @Execute(name = "sethub")
    @Permission("mrstudios.command.deathrun.setup")
    public void setMainHub(
            @Context Player player
    ) {
        this.configuration.plugin().mainHubLocation = player.getLocation().toCenterLocation();
        this.configuration.plugin().save();
        this.message(player, "<gold>[DR]</gold> <gray>Main hub location set to your current position.");
    }

    @Execute(name = "addteleport")
    @Permission("mrstudios.command.deathrun.setup")
    public void addTeleportPad(
            @Context Player player
    ) {

        MapConfiguration.MapDefinition map = this.selectedMapForSetup(player, true);
        if (map == null)
            return;

        List<Location> locations = locations(player);
        if (locations.isEmpty()) {
            this.message(player, this.configuration.language().commandMessageTrapLookAtButton);
            return;
        }

        map.teleportPads.add(new TeleportPad(locations.get(0), player.getLocation().toCenterLocation().add(0, -0.5, 0)));
        this.configuration.map().save();
        this.message(player, this.configuration.language().commandMessageTeleportPadAdded);

    }

    @Execute(name = "save")
    @Permission("mrstudios.command.deathrun.setup")
    public void save(
            @Context Player player
    ) {

        MapConfiguration.MapDefinition map = this.selectedMapForSetup(player, true);
        if (map == null)
            return;

        if (map.world == null || map.world.isBlank())
            map.world = player.getWorld().getName();

        this.rebuildBarrierSnapshot(map);

        List<String> issues = this.mapPromotionIssues(map, false);
        if (!issues.isEmpty()) {
            this.message(player, this.configuration.language().commandMessageSetupMapPreflightFailed
                    .replace("<map>", this.safe(map.id))
                    .replace("<issues>", String.join(", ", issues)));
            return;
        }

        String worldName = map.world == null || map.world.isBlank() ? player.getWorld().getName() : map.world;
        World world = this.plugin.getServer().getWorld(worldName);
        if (world == null) {
            this.message(player, this.configuration.language().commandMessageSetupMapBackupWorldMissing.replace("<world>", worldName));
            return;
        }

        try {
            this.refreshWorldBackup(worldName, world);
        } catch (Exception exception) {
            throw new RuntimeException("Unable to save world backup due to an exception.", exception);
        }

        map.arenaSetupEnabled = false;
        this.configuration.map().save();
        this.saveMapWorldNow(map.world);
        this.setupEditModePlayers.remove(player.getUniqueId());
        this.arenaManager.setMapEditLocked(this.configuration.map().normalizedMapId(map.id), false);
        this.arenaManager.returnPlayerToHub(player);

        this.message(player, this.configuration.language().commandMessageSetupMapPreflightPassed.replace("<map>", this.safe(map.id)));
        this.message(player, this.configuration.language().commandMessageSaveSuccess);
        this.message(player, this.configuration.language().commandMessageSetupEditModeSaved);

    }

    @Execute(name = "cancel")
    @Permission("mrstudios.command.deathrun.setup")
    public void cancelSetup(
            @Context Player player
    ) {
        MapConfiguration.MapDefinition map = this.selectedMapForSetup(player, false);
        if (map == null)
            return;

        this.setupEditModePlayers.remove(player.getUniqueId());
        this.saveMapWorldNow(map.world);
        this.arenaManager.setMapEditLocked(this.configuration.map().normalizedMapId(map.id), false);
        this.setupMapSelection.remove(player.getUniqueId());
        this.arenaManager.returnPlayerToHub(player);

        this.message(player, this.configuration.language().commandMessageSetupEditModeCancelled
                .replace("<map>", this.safe(map.id)));
    }

    protected void message(
            @NotNull Player player, String message,
            @Nullable Object... args
    ) {
        String content = (args != null && args.length > 0)
            ? java.lang.String.format(message, args)
            : message;
        content = this.parsePlaceholders(player, content);
        this.audiences.player(player).sendMessage(miniMessage().deserialize(content));
    }

    protected void message(
            @NotNull Player player,
            @NotNull Component component
    ) {
        player.sendMessage(component);
    }

    private void message(
            @NotNull CommandSender sender,
            @NotNull String message
    ) {
        if (sender instanceof Player player) {
            this.audiences.player(player).sendMessage(miniMessage().deserialize(this.parsePlaceholders(player, message)));
            return;
        }

        sender.sendMessage(plainText().serialize(miniMessage().deserialize(message)));
    }

    private @NotNull String parsePlaceholders(
            @NotNull Player player,
            @NotNull String content
    ) {
        if (this.plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null)
            return content;

        return PlaceholderAPI.setPlaceholders(player, content);
    }

    protected List<Location> locations(@NotNull Player player) {

        try {

            List<Location> locations = new ArrayList<>();
            LocalSession session = this.worldEdit.getSessionManager().findByName(player.getName());

            assert session != null;
            Region region = session.getSelection(session.getSelectionWorld());

            region.forEach((vector) -> locations.add(adapt(player.getWorld(), vector)));

            return locations;

        } catch (@NotNull Exception ignored) {}

        return emptyList();

    }

    protected void trap(
            @NotNull Player player,
            @NotNull String type,
            @Nullable Object... objects
    ) throws Exception {

        MapConfiguration.MapDefinition map = this.selectedMapForSetup(player, true);
        if (map == null)
            return;

        Block target = player.getTargetBlock(null, 250);
        List<Location> locations = this.locations(player);
        Class<? extends ITrap> trapClass = this.trapRegistry.get(type.toUpperCase());

        if (of(
                STONE_BUTTON,
                OAK_BUTTON,
                ACACIA_BUTTON,
                BIRCH_BUTTON,
                CRIMSON_BUTTON,
                JUNGLE_BUTTON,
                SPRUCE_BUTTON,
                WARPED_BUTTON,
                POLISHED_BLACKSTONE_BUTTON,
                DARK_OAK_BUTTON
        ).noneMatch((button) -> target.getType().equals(button))) {
            this.message(player, this.configuration.language().commandMessageTrapLookAtButton);
            return;
        }

        if (trapClass == null) {
            this.message(player, this.configuration.language().commandMessageTrapNotExists.replace("<type>", type.toUpperCase()));
            return;
        }

        ITrap trap = trapClass.getDeclaredConstructor().newInstance();

        trap.setButton(target.getLocation());
        trap.setLocations(trap.filter(locations, objects));
        ofNullable(objects).ifPresent(trap::setExtra);

        map.arenaTraps.add(trap);
        this.configuration.map().save();
        this.message(player, this.configuration.language().commandMessageTrapAdded.replace("<type>", type.toUpperCase()));

    }

    private @Nullable MapConfiguration.MapDefinition selectedMapForSetup(
            @NotNull Player player,
            boolean requireSetupEnabled
    ) {
        if (!this.setupEditModePlayers.contains(player.getUniqueId())) {
            this.message(player, this.configuration.language().commandMessageSetupEditModeRequired);
            return null;
        }

        this.configuration.map().ensureMapsMutable();
        List<MapConfiguration.MapDefinition> maps = this.configuration.map().resolvedMaps();
        if (maps.isEmpty()) {
            this.message(player, this.configuration.language().commandMessageSetupMapListEmpty);
            return null;
        }

        String selected = this.setupMapSelection.get(player.getUniqueId());
        if (selected == null || selected.isBlank()) {
            if (maps.size() == 1) {
                selected = this.configuration.map().normalizedMapId(maps.get(0).id);
                this.setupMapSelection.put(player.getUniqueId(), selected);
            } else {
                this.message(player, this.configuration.language().commandMessageSetupMapNoSelection);
                return null;
            }
        }

        MapConfiguration.MapDefinition map = this.configuration.map().getMapById(selected);
        if (map == null) {
            if (maps.size() == 1) {
                this.setupMapSelection.put(player.getUniqueId(), this.configuration.map().normalizedMapId(maps.get(0).id));
                map = maps.get(0);
            } else {
                this.setupMapSelection.remove(player.getUniqueId());
                this.message(player, this.configuration.language().commandMessageSetupMapNoSelection);
                return null;
            }
        }

        if (requireSetupEnabled && !map.arenaSetupEnabled) {
            if (this.setupEditModePlayers.contains(player.getUniqueId()))
                return this.mutableSetupMap(map);

            this.message(player, this.configuration.language().commandMessageSetupMapLocked);
            return null;
        }

        return this.mutableSetupMap(map);
    }

    private @Nullable Location setupTeleportTarget(
            @NotNull Player player,
            @NotNull MapConfiguration.MapDefinition map
    ) {
        if (!map.arenaCheckpoints.isEmpty()) {
            Checkpoint checkpoint = map.arenaCheckpoints.get(map.arenaCheckpoints.size() - 1);
            if (checkpoint.spawn() != null)
                return checkpoint.spawn();
        }

        if (map.arenaWaitingLobbyLocation != null)
            return map.arenaWaitingLobbyLocation;

        if (map.world != null && !map.world.isBlank()) {
            World world = this.plugin.getServer().getWorld(map.world);
            if (world != null)
                return world.getSpawnLocation().toCenterLocation();
        }

        return player.getLocation();
    }

    private void joinPlayerToMap(
            @NotNull Player target,
            @NotNull String mapId,
            @Nullable CommandSender actor
    ) {
        if (mapId.equalsIgnoreCase("lobby") || mapId.equalsIgnoreCase("leave")) {
            this.signManager.leaveQueue(target);
            this.arenaManager.leaveCurrentMap(target, true);
            this.arenaManager.returnPlayerToHub(target);
            this.message(target, "<yellow>You have left the match and returned to the Hub.");

            if (actor != null && actor != target)
                this.message(actor, this.configuration.language().commandMessageJoinForcedLobbyActor
                        .replace("<player>", target.getName()));
            return;
        }

        ArenaManager.JoinResult result = this.arenaManager.joinMap(target, mapId);
        String content = switch (result) {
            case JOINED -> this.configuration.language().mapSelectorMapSelected.replace("<map>", mapId);
            case ALREADY_IN_MAP -> this.configuration.language().mapSelectorAlreadyJoined;
            case MAP_NOT_READY -> this.configuration.language().mapSelectorMapNotReady;
            case MAP_FULL -> this.configuration.language().mapSelectorMapFull;
            case MATCH_IN_PROGRESS -> this.configuration.language().mapSelectorMapInProgress;
            case MAP_EDITING -> this.configuration.language().mapSelectorMapEditing;
            default -> this.configuration.language().mapSelectorMapUnavailable;
        };

        this.message(target, content);
        if (actor != null && actor != target)
            this.message(actor, this.configuration.language().commandMessageJoinForcedActor
                .replace("<player>", target.getName())
                .replace("<map>", mapId));
    }

    private void handleForceStartResult(
            @NotNull CommandSender sender,
            @NotNull String mapId,
            @NotNull ArenaManager.ForceStartResult result
    ) {
        String content = switch (result) {
            case STARTED -> this.configuration.language().commandMessageStartSuccess.replace("<map>", mapId);
            case MAP_UNAVAILABLE -> this.configuration.language().commandMessageStartMapUnavailable.replace("<map>", mapId);
            case NO_PLAYERS -> this.configuration.language().commandMessageStartNoPlayers.replace("<map>", mapId);
            case MATCH_ALREADY_RUNNING -> this.configuration.language().commandMessageStartAlreadyRunning.replace("<map>", mapId);
        };

        this.message(sender, content);
    }

    private void sendSetupHelpPage(
            @NotNull Player player,
            int page
    ) {
        List<String> lines = this.configuration.language().commandHelpSetupLines;
        if (lines.isEmpty())
            return;

        int pageSize = 8;
        int totalPages = (int) Math.ceil(lines.size() / (double) pageSize);
        int currentPage = Math.max(1, Math.min(page, totalPages));

        int fromIndex = (currentPage - 1) * pageSize;
        int toIndex = Math.min(lines.size(), fromIndex + pageSize);

        this.message(player, PREFIX + "<gray>Setup help page <white>" + currentPage + "</white>/<white>" + totalPages + "</white>");

        String content = java.lang.String.join("<br>", lines.subList(fromIndex, toIndex))
                .replace("<version>", this.plugin.getDescription().getVersion());
        this.message(player, content);
    }

    private void handleForceStopResult(
            @NotNull CommandSender sender,
            @NotNull String mapId,
            @NotNull ArenaManager.ForceStopResult result
    ) {
        String content = switch (result) {
            case STOPPED -> this.configuration.language().commandMessageStopSuccess.replace("<map>", mapId);
            case MAP_UNAVAILABLE -> this.configuration.language().commandMessageStopMapUnavailable.replace("<map>", mapId);
            case ALREADY_WAITING -> this.configuration.language().commandMessageStopAlreadyWaiting.replace("<map>", mapId);
        };

        this.message(sender, content);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private int nextCheckpointListToken(
            @NotNull Player player
    ) {
        return this.checkpointListTokens.merge(player.getUniqueId(), 1, Integer::sum);
    }

    private boolean isCheckpointListTokenValid(
            @NotNull Player player,
            int token
    ) {
        return this.checkpointListTokens.getOrDefault(player.getUniqueId(), -1) == token;
    }

    private int nextTrapListToken(
            @NotNull Player player
    ) {
        return this.trapListTokens.merge(player.getUniqueId(), 1, Integer::sum);
    }

    private boolean isTrapListTokenValid(
            @NotNull Player player,
            int token
    ) {
        return this.trapListTokens.getOrDefault(player.getUniqueId(), -1) == token;
    }

    private void refreshWorldBackup(
            @NotNull String worldName,
            @NotNull World world
    ) throws Exception {
        Path path = get(this.plugin.getDataFolder().toString(), "backup/", worldName + ".zip");
        Path tempPath = get(this.plugin.getDataFolder().toString(), "backup/", worldName + ".zip.tmp");
        createDirectories(path.getParent());
        deleteIfExists(tempPath);

        try (ZipFile zipFile = new ZipFile(tempPath.toString())) {
            zipFile.addFolder(world.getWorldFolder());
        }

        try {
            java.nio.file.Files.move(tempPath, path, REPLACE_EXISTING, ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            java.nio.file.Files.move(tempPath, path, REPLACE_EXISTING);
        }
    }

    private void saveMapWorldNow(
            @Nullable String worldName
    ) {
        if (worldName == null || worldName.isBlank())
            return;

        World world = this.plugin.getServer().getWorld(worldName);
        if (world == null)
            return;

        world.setAutoSave(true);
        world.save();
    }

    private boolean rebuildBarrierSnapshot(
            @NotNull MapConfiguration.MapDefinition map
    ) {
        if (map.arenaStartBarrierBlocks.isEmpty())
            return false;

        map.arenaStartBarrierRestoreMaterials = map.arenaStartBarrierBlocks.stream()
                .map((location) -> location.getBlock().getType())
                .toList();
        return true;
    }

    private @NotNull List<String> mapIssues(
            @NotNull MapConfiguration.MapDefinition map
    ) {
        List<String> issues = new ArrayList<>();

        if (map.world == null || map.world.isBlank()) {
            issues.add("world-not-set");
        } else {
            if (this.plugin.getServer().getWorld(map.world) == null)
                issues.add("world-not-loaded");

            Path backupPath = get(this.plugin.getDataFolder().toString(), "backup", map.world + ".zip");
            if (!exists(backupPath))
                issues.add("missing-backup");
        }

        if (map.arenaWaitingLobbyLocation == null)
            issues.add("missing-waiting-lobby");

        if (map.arenaRunnerSpawnLocations.isEmpty())
            issues.add("missing-runner-spawn");

        if (map.arenaDeathSpawnLocations.isEmpty())
            issues.add("missing-death-spawn");

        if (map.arenaCheckpoints.isEmpty())
            issues.add("missing-checkpoints");

        if (map.arenaCheckpoints.isEmpty()) {
            issues.add("finish-checkpoint-not-set");
        } else if (map.arenaFinishCheckpointId == null) {
            issues.add("finish-checkpoint-not-set");
        } else if (map.arenaCheckpoints.stream().noneMatch((checkpoint) -> checkpoint.id().equals(map.arenaFinishCheckpointId))) {
            issues.add("finish-checkpoint-invalid");
        }

        if (map.arenaStartBarrierBlocks.isEmpty())
            issues.add("missing-start-barrier");

        if (!map.arenaStartBarrierBlocks.isEmpty() && map.arenaStartBarrierRestoreMaterials.size() != map.arenaStartBarrierBlocks.size())
            issues.add("barrier-restore-size-mismatch");

        if (map.arenaSetupEnabled)
            issues.add("setup-enabled");

        return issues.stream().distinct().collect(Collectors.toList());
    }

    private @NotNull List<String> mapPromotionIssues(
            @NotNull MapConfiguration.MapDefinition map,
            boolean requireBackup
    ) {
        List<String> issues = this.mapIssues(map).stream()
                .filter((issue) -> switch (issue) {
                    case "world-not-set",
                         "world-not-loaded",
                         "missing-waiting-lobby",
                         "missing-runner-spawn",
                         "missing-death-spawn",
                         "missing-checkpoints",
                         "finish-checkpoint-not-set",
                         "finish-checkpoint-invalid",
                         "missing-start-barrier",
                         "barrier-restore-size-mismatch" -> true;
                    case "missing-backup" -> requireBackup;
                    default -> false;
                })
                .collect(Collectors.toCollection(ArrayList::new));

        if (map.world == null || map.world.isBlank())
            issues.add("world-not-set");

        return issues.stream().distinct().toList();
    }

    private void sendMapStatus(
            @NotNull Player player,
            @NotNull MapConfiguration.MapDefinition map,
            boolean includeIssueLine
    ) {
        List<String> issues = this.mapIssues(map);
        String mapId = this.configuration.map().normalizedMapId(this.safe(map.id));
        ArenaManager.ArenaRuntime runtime = this.arenaManager.runtimeByMapId(mapId);

        String state = runtime == null ? "RUNTIME_MISSING" : runtime.arena().getGameState().name();
        int players = runtime == null ? 0 : runtime.arena().getUsers().size();
        int maxPlayers = this.arenaManager.maxPlayers(map);
        String setup = map.arenaSetupEnabled ? "setup-enabled" : "setup-disabled";
        String health = issues.isEmpty() ? "healthy" : "issues(" + issues.size() + ")";

        this.message(player, this.configuration.language().commandMessageSetupMapStatusLine
                .replace("<map>", this.safe(map.id))
                .replace("<state>", state)
                .replace("<players>", String.valueOf(players))
                .replace("<maxPlayers>", String.valueOf(maxPlayers))
                .replace("<setup>", setup)
                .replace("<health>", health));

        if (includeIssueLine && !issues.isEmpty())
            this.message(player, this.configuration.language().commandMessageSetupMapStatusIssues
                    .replace("<issues>", String.join(", ", issues)));
    }

        private void rebindMapWorldReferences(
            @NotNull MapConfiguration.MapDefinition map,
            @NotNull World world
        ) {
        if (map.arenaWaitingLobbyLocation != null)
            map.arenaWaitingLobbyLocation = this.withWorld(map.arenaWaitingLobbyLocation, world);

        map.arenaRunnerSpawnLocations = map.arenaRunnerSpawnLocations.stream()
            .map((location) -> this.withWorld(location, world))
            .collect(Collectors.toCollection(ArrayList::new));

        map.arenaDeathSpawnLocations = map.arenaDeathSpawnLocations.stream()
            .map((location) -> this.withWorld(location, world))
            .collect(Collectors.toCollection(ArrayList::new));

        map.arenaStartBarrierBlocks = map.arenaStartBarrierBlocks.stream()
            .map((location) -> this.withWorld(location, world))
            .collect(Collectors.toCollection(ArrayList::new));

        map.arenaCheckpoints = map.arenaCheckpoints.stream()
            .map((checkpoint) -> new Checkpoint(
                checkpoint.id(),
                this.withWorld(checkpoint.spawn(), world),
                checkpoint.locations().stream().map((location) -> this.withWorld(location, world)).toList(),
                checkpoint.name()
            ))
            .collect(Collectors.toCollection(ArrayList::new));

        map.teleportPads = map.teleportPads.stream()
            .map((teleportPad) -> new TeleportPad(
                this.withWorld(teleportPad.padLocation(), world),
                this.withWorld(teleportPad.teleportLocation(), world)
            ))
            .collect(Collectors.toCollection(ArrayList::new));

        map.arenaTraps.forEach((trap) -> {
            trap.setButton(this.withWorld(trap.getButton(), world));
            trap.setLocations(trap.getLocations().stream().map((location) -> this.withWorld(location, world)).toList());
        });
        }

        private @NotNull Location withWorld(
            @NotNull Location location,
            @NotNull World world
        ) {
        Location clone = location.clone();
        clone.setWorld(world);
        return clone;
        }

    private void ensureMutableSetupCollections(
            @NotNull MapConfiguration.MapDefinition map
    ) {
        map.arenaRunnerSpawnLocations = new ArrayList<>(map.arenaRunnerSpawnLocations);
        map.arenaDeathSpawnLocations = new ArrayList<>(map.arenaDeathSpawnLocations);
        map.arenaCheckpoints = new ArrayList<>(map.arenaCheckpoints);
        map.arenaTraps = new ArrayList<>(map.arenaTraps);
        map.teleportPads = new ArrayList<>(map.teleportPads);
        map.arenaStartBarrierBlocks = new ArrayList<>(map.arenaStartBarrierBlocks);
        map.arenaStartBarrierRestoreMaterials = new ArrayList<>(map.arenaStartBarrierRestoreMaterials);
    }

    private @NotNull MapConfiguration.MapDefinition mutableSetupMap(
            @NotNull MapConfiguration.MapDefinition map
    ) {
        this.ensureMutableSetupCollections(map);
        this.normalizeCheckpointIds(map);
        return map;
    }

    private void normalizeCheckpointIds(
            @NotNull MapConfiguration.MapDefinition map
    ) {
        if (map.arenaCheckpoints.isEmpty()) {
            map.arenaFinishCheckpointId = null;
            return;
        }

        Integer previousFinishId = map.arenaFinishCheckpointId;
        int finishIndex = -1;
        if (previousFinishId != null) {
            for (int i = 0; i < map.arenaCheckpoints.size(); i++) {
                if (map.arenaCheckpoints.get(i).id().equals(previousFinishId)) {
                    finishIndex = i;
                    break;
                }
            }
        }

        List<Checkpoint> reindexed = new ArrayList<>();
        for (int i = 0; i < map.arenaCheckpoints.size(); i++) {
            Checkpoint checkpoint = map.arenaCheckpoints.get(i);
            reindexed.add(new Checkpoint(
                    i + 1,
                    checkpoint.spawn(),
                    checkpoint.locations(),
                    checkpoint.name()
            ));
        }

        map.arenaCheckpoints = reindexed;
        if (finishIndex >= 0 && finishIndex < map.arenaCheckpoints.size())
            map.arenaFinishCheckpointId = map.arenaCheckpoints.get(finishIndex).id();
    }

    private int displayCheckpointNumber(
            @NotNull MapConfiguration.MapDefinition map,
            int checkpointId
    ) {
        for (int i = 0; i < map.arenaCheckpoints.size(); i++) {
            if (map.arenaCheckpoints.get(i).id() == checkpointId)
                return i + 1;
        }

        return checkpointId;
    }

}
