package pl.mrstudios.deathrun.plugin;

import com.sk89q.worldedit.WorldEdit;
import dev.rollczi.litecommands.LiteCommands;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.argument.ArgumentKey;
import dev.rollczi.litecommands.suggestion.SuggestionResult;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.Injector;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.commons.reflection.Reflections;
import pl.mrstudios.deathrun.arena.Arena;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.arena.listener.ArenaBoosterListener;
import pl.mrstudios.deathrun.arena.listener.ArenaCheckpointReachedListener;
import pl.mrstudios.deathrun.arena.listener.ArenaClickItemListener;
import pl.mrstudios.deathrun.arena.listener.ArenaInventoryActionListener;
import pl.mrstudios.deathrun.arena.listener.ArenaMapSelectorListener;
import pl.mrstudios.deathrun.arena.listener.ArenaSignBreakListener;
import pl.mrstudios.deathrun.arena.listener.ArenaSignCreateListener;
import pl.mrstudios.deathrun.arena.listener.ArenaSignInteractListener;
import pl.mrstudios.deathrun.arena.sign.SignManager;
import pl.mrstudios.deathrun.arena.win.WinMapManager;
import pl.mrstudios.deathrun.arena.selector.MapSelectorService;
import pl.mrstudios.deathrun.arena.trap.TrapRegistry;
import pl.mrstudios.deathrun.arena.trap.impl.*;
import pl.mrstudios.deathrun.command.CommandDeathRun;
import pl.mrstudios.deathrun.command.handler.InvalidCommandUsageHandler;
import pl.mrstudios.deathrun.command.handler.NoCommandPermissionsHandler;
import pl.mrstudios.deathrun.config.Configuration;
import pl.mrstudios.deathrun.config.ConfigurationFactory;
import pl.mrstudios.deathrun.config.impl.LanguageConfiguration;
import pl.mrstudios.deathrun.config.impl.MapConfiguration;
import pl.mrstudios.deathrun.config.impl.PluginConfiguration;
import pl.mrstudios.deathrun.exception.MissingDependencyException;
import pl.mrstudios.deathrun.placeholder.DeathRunPlaceholderExpansion;
import pl.mrstudios.deathrun.player.PlayerStatisticsService;
import pl.mrstudios.deathrun.reward.RewardService;
import org.bukkit.map.MapPalette;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Modifier;
import java.util.List;

import static com.sk89q.worldedit.WorldEdit.getInstance;
import static dev.rollczi.litecommands.annotations.LiteCommandsAnnotations.of;
import static dev.rollczi.litecommands.bukkit.LiteCommandsBukkit.builder;
import static dev.rollczi.litecommands.schematic.SchematicFormat.angleBrackets;
import static java.util.Arrays.asList;
import static net.kyori.adventure.platform.bukkit.BukkitAudiences.create;
import static pl.mrstudios.deathrun.api.API.apiInstance;
import static pl.mrstudios.deathrun.api.API.createInstance;

@SuppressWarnings("all")
public class Entrypoint extends JavaPlugin {

    private ArenaManager arenaManager;
    private TrapRegistry trapRegistry;
    private SignManager signManager;
    private WinMapManager winMapManager;
    private PlayerStatisticsService playerStatisticsService;
    private RewardService rewardService;
    private volatile BufferedImage winParchmentImage;
    private volatile BufferedImage loseParchmentImage;

    private BukkitAudiences audiences;

    private Configuration configuration;
    private ConfigurationFactory configurationFactory;

    private Injector injector;
    private WorldEdit worldEdit;
    private LiteCommands<CommandSender> liteCommands;

    @Override
    public void onEnable() {

        /* Dependency Check */
        if (!this.getServer().getPluginManager().isPluginEnabled("WorldEdit"))
            throw new MissingDependencyException("You must have WorldEdit (v7.2.9+) installed on your server to use this plugin.");

        /* World Edit */
        this.worldEdit = getInstance();

        /* Configuration */
        this.configurationFactory = new ConfigurationFactory(this.getDataFolder().toPath());
        this.configuration = new Configuration(
                this.configurationFactory.produce(PluginConfiguration.class, "config.yml"),
                this.configurationFactory.produce(LanguageConfiguration.class, "language.yml"),
                this.configurationFactory.produce(MapConfiguration.class, "map.yml")
        );
        this.playerStatisticsService = new PlayerStatisticsService(this);
        this.rewardService = new RewardService(this, this.configuration);

        /* Data Folders */
        File songsDirectory = new File(this.getDataFolder(), "songs");
        if (!songsDirectory.exists() && !songsDirectory.mkdirs())
            this.getLogger().warning("Failed to create songs directory: " + songsDirectory.getAbsolutePath());

        /* Kyori */
        this.audiences = create(this);

        /* Win Map Manager */
        this.winMapManager = new WinMapManager();
        this.winMapManager.initialize(this, 16);
        this.getLogger().info("[DR-DBG] Win map manager created and initialized.");
        this.loadParchmentImagesAsync();

        /* Arena Manager */
        this.arenaManager = new ArenaManager(this, this.getServer(), this.audiences, this.configuration, this.winMapManager, this.rewardService);
        this.signManager = new SignManager(this, this.arenaManager);
        this.arenaManager.setSignManager(this.signManager);

        /* Trap Registry */
        this.trapRegistry = new TrapRegistry();

        /* Initialize Injector */
        this.injector = new Injector()

                /* Bukkit */
                .register(Plugin.class, this)
                .register(Server.class, this.getServer())

                /* Kyori */
                .register(BukkitAudiences.class, this.audiences)

                /* World Edit */
                .register(WorldEdit.class, this.worldEdit)

                /* Plugin Stuff */
                .register(ArenaManager.class, this.arenaManager)
                .register(SignManager.class, this.signManager)
                .register(WinMapManager.class, this.winMapManager)
                .register(RewardService.class, this.rewardService)
                .register(TrapRegistry.class, this.trapRegistry)
                .register(PlayerStatisticsService.class, this.playerStatisticsService)
                .register(MapSelectorService.class, new MapSelectorService(this, this.configuration, this.arenaManager, this.audiences))
                .register(Configuration.class, this.configuration);

            this.arenaManager.initialize();
        this.arenaManager.saveLoadedMapWorlds();

        /* Register Traps */
        asList(
                TrapTNT.class,
                TrapAppearingBlocks.class,
                TrapDisappearingBlocks.class,
                TrapArrows.class,
                TrapParticles.class
        ).forEach(this.trapRegistry::register);

        /* Register Commands */
        this.liteCommands = builder()

                /* Settings */
                .settings((settings) -> settings.nativePermissions(false))

                /* Handler */
                .invalidUsage(this.injector.inject(InvalidCommandUsageHandler.class))
                .missingPermission(this.injector.inject(NoCommandPermissionsHandler.class))

                /* Commands */
                .commands(of(this.injector.inject(CommandDeathRun.class)))

                /* Schematic */
                .schematicGenerator(angleBrackets())

                /* Suggesters */
                .argumentSuggestion(String.class, ArgumentKey.of("type"), SuggestionResult.of(this.trapRegistry.trapRegistryKeys()))
                .argumentSuggester(String.class, ArgumentKey.of("id"), (invocation, argument, context) ->
                    SuggestionResult.of(this.configuration.map().resolvedMaps().stream()
                        .map((map) -> {
                            String id = map.id;
                            if (id == null || id.isBlank())
                                id = this.configuration.map().normalizedMapId(map.name);
                            return id;
                        })
                        .filter((id) -> id != null && !id.isBlank())
                        .distinct()
                        .toList()
                    )
                )
                .argumentSuggester(String.class, ArgumentKey.of("map"), (invocation, argument, context) ->
                    SuggestionResult.of(java.util.stream.Stream.concat(
                            java.util.stream.Stream.of("lobby"),
                            this.configuration.map().resolvedMaps().stream()
                                .map((map) -> {
                                    String id = map.id;
                                    if (id == null || id.isBlank())
                                        id = this.configuration.map().normalizedMapId(map.name);
                                    return id;
                                })
                                .filter((id) -> id != null && !id.isBlank())
                        )
                        .distinct()
                        .toList()
                    )
                )
                .argumentSuggestion(String.class, ArgumentKey.of("world"), SuggestionResult.of(
                    this.getServer().getWorlds().stream()
                        .map(org.bukkit.World::getName)
                        .toList()
                ))

                /* Build */
                .build();

        /* Register Listeners */
        List<Class<? extends Listener>> listenerClasses = new Reflections<Listener>("pl.mrstudios.deathrun.arena.listener")
            .getClassesImplementing(Listener.class).stream()
            .filter((listener) -> !Modifier.isAbstract(listener.getModifiers()))
            .toList();

        int registeredListeners = 0;
        for (Class<? extends Listener> listenerClass : listenerClasses) {
            try {
                this.getServer().getPluginManager().registerEvents(this.injector.inject(listenerClass), this);
                registeredListeners++;
            } catch (Exception exception) {
                this.getLogger().warning("Failed to register listener via reflection: " + listenerClass.getName());
                this.getLogger().warning("Reason: " + exception.getMessage());
            }
        }

        this.getLogger().info("Registered listeners via reflection: " + registeredListeners + "/" + listenerClasses.size());

        /* Initialize API */
        createInstance(java.util.Objects.requireNonNullElseGet(this.arenaManager.primaryArena(), () -> new Arena("default")), this.trapRegistry);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new DeathRunPlaceholderExpansion(this).register();
        }

        /* Check Branch */
        if (!apiInstance().pluginGitBranch().equals("ver/latest"))
            this.getLogger().warning(
                    """
                         
                         --------------------------------------------------------
                         
                                       DEVELOPMENT BUILD DETECTED
                                        
                          You are running on a development build of the plugin,
                          which may contain bugs and other issues. Please report
                          any bugs you found on our GitHub repository.
                          
                          Version: {version}
                          Current Branch: {branch}
                         
                         --------------------------------------------------------
                         """.replace("{version}", apiInstance().pluginVersion())
                            .replace("{branch}", apiInstance().pluginGitBranch())
            );

    }

    @Override
    public void onDisable() {

        if (this.arenaManager != null)
            this.arenaManager.saveLoadedMapWorlds();

        if (this.playerStatisticsService != null)
            this.playerStatisticsService.save();

        if (this.signManager != null)
            this.signManager.shutdown();

        if (this.winMapManager != null)
            this.winMapManager.shutdown();

        if (this.audiences != null)
            this.audiences.close();

    }

    @Override
    public void onLoad() {
        // Intentionally no-op.
        // Backups are restored manually via setup commands, not automatically on startup,
        // so regular world changes persist across restarts.
    }

    public @Nullable BufferedImage getWinParchmentImage() {
        return this.winParchmentImage;
    }

    public @Nullable BufferedImage getLoseParchmentImage() {
        return this.loseParchmentImage;
    }

    public @NotNull ArenaManager getArenaManager() {
        return this.arenaManager;
    }

    public @NotNull Configuration getConfiguration() {
        return this.configuration;
    }

    public @NotNull PlayerStatisticsService getPlayerStatisticsService() {
        return this.playerStatisticsService;
    }

    private void loadParchmentImagesAsync() {
        File shared = new File(this.getDataFolder(), "parchment.png");
        File win = new File(this.getDataFolder(), "parchment_win.png");
        File lose = new File(this.getDataFolder(), "parchment_lose.png");

        this.getLogger().info("[DR-DBG] Loading win parchment from: " + win.getAbsolutePath());
        this.getLogger().info("[DR-DBG] Loading lose parchment from: " + lose.getAbsolutePath());
        this.getLogger().info("[DR-DBG] Shared fallback parchment path: " + shared.getAbsolutePath());

        this.getServer().getScheduler().runTaskAsynchronously(this, () -> {
            this.winParchmentImage = this.loadParchmentWithFallback("win", win, shared);
            this.loseParchmentImage = this.loadParchmentWithFallback("lose", lose, shared);
        });
    }

    private @Nullable BufferedImage loadParchmentWithFallback(
            @NotNull String type,
            @NotNull File preferred,
            @NotNull File sharedFallback
    ) {
        File target = preferred.exists() ? preferred : sharedFallback;
        if (!target.exists()) {
            this.getLogger().warning("Win map feature disabled for " + type + ": missing "
                    + preferred.getName() + " and fallback parchment.png.");
            return null;
        }

        try {
            BufferedImage loaded = ImageIO.read(target);
            if (loaded == null) {
                this.getLogger().warning("Win map feature disabled for " + type + ": unable to decode " + target.getName() + ".");
                return null;
            }

            this.getLogger().info("[DR-DBG] " + type + " parchment loaded from " + target.getName()
                    + " size=" + loaded.getWidth() + "x" + loaded.getHeight() + " -> 128x128");
            return MapPalette.resizeImage(loaded);
        } catch (Exception exception) {
            this.getLogger().warning("Win map feature disabled for " + type + ": failed to load "
                    + target.getName() + " (" + exception.getMessage() + ").");
            return null;
        }
    }

}
