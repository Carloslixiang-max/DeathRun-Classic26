package pl.mrstudios.deathrun.player;

import org.bukkit.*;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Scoreboard;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.arena.listener.PlayerRecoveryGuardListener;
import pl.mrstudios.deathrun.config.Configuration;
import pl.mrstudios.deathrun.config.impl.*;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

/** Actual service and event listener exercised with interface proxies, no mock agent or NMS. */
class PlayerSnapshotRecoveryTest {
    @TempDir Path root;
    final UUID playerId=UUID.randomUUID(), worldId=UUID.randomUUID();
    final List<String> calls=new ArrayList<>();
    boolean teleportAllowed=true, rejectStorage=false, worldsAvailable=true, rejectCapture=false;
    java.util.function.Consumer<org.bukkit.event.Event> eventHook = ignored -> {};
    Plugin plugin; Player player; World world; Server server;

    @BeforeEach void setup() throws Exception {
        world=proxy(World.class, (method,args) -> switch(method) {
            case "getUID" -> worldId; case "getName" -> "saved"; default -> null;
        });
        server=proxy(Server.class, (method,args) -> switch(method) {
            case "getWorld" -> worldsAvailable ? world : null;
            case "getOnlinePlayers" -> List.of(player);
            case "getLogger" -> Logger.getLogger("recovery-test");
            case "getName", "getVersion", "getBukkitVersion" -> "recovery-test";
            case "getScheduler" -> proxy(org.bukkit.scheduler.BukkitScheduler.class,(name,arguments) ->
                    name.equals("runTaskTimer") ? proxy(org.bukkit.scheduler.BukkitTask.class,(n,a)->null) : null);
            case "getPluginManager" -> proxy(org.bukkit.plugin.PluginManager.class,(name,arguments) -> {
                if (name.equals("callEvent")) eventHook.accept((org.bukkit.event.Event) arguments[0]);
                return null;
            });
            default -> null;
        });
        // Paper's public setter logs ServerBuildInfo through a server-only service provider.
        // Install the interface fixture directly; teardown restores the singleton below.
        Field serverField=Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true); serverField.set(null,server);
        plugin=proxy(Plugin.class, (method,args) -> switch(method) {
            case "getDataFolder" -> root.toFile(); case "getServer" -> server;
            case "getLogger" -> Logger.getLogger("recovery-test"); default -> null;
        });
        PlayerInventory inventory=proxy(PlayerInventory.class, (method,args) -> {
            if(method.equals("getStorageContents")) {
                if (rejectCapture) throw new IllegalStateException("injected capture failure");
                return new ItemStack[36];
            }
            if(method.equals("getArmorContents")) return new ItemStack[4];
            if(method.equals("setStorageContents")) {
                calls.add("inventory");
                assertTrue(Files.readString(journal()).contains("recovery-phase: RESTORING"));
                if(rejectStorage) throw new IllegalStateException("injected inventory failure");
            }
            return null;
        });
        Scoreboard scoreboard=proxy(Scoreboard.class, (method,args) -> null);
        player=proxy(Player.class, (method,args) -> switch(method) {
            case "getServer" -> server;
            case "getUniqueId" -> playerId; case "getName" -> "Runner"; case "getWorld" -> world;
            case "getLocation" -> new Location(world,85.5,25,82.5);
            case "getInventory" -> inventory; case "getScoreboard" -> scoreboard;
            case "getActivePotionEffects" -> Set.of(); case "getGameMode" -> GameMode.SURVIVAL;
            case "getHealth", "getMaxHealth" -> 20.0;
            case "getFoodLevel" -> 20;
            case "isOnline", "isOp" -> true;
            case "teleport" -> { calls.add("teleport"); yield teleportAllowed; }
            case "saveData" -> { calls.add("save-player"); yield null; }
            default -> null;
        });
    }
    @AfterEach void resetBukkit() throws Exception {
        Field field=Bukkit.class.getDeclaredField("server"); field.setAccessible(true); field.set(null,null);
    }
    Path journal() { return root.resolve("recovery/"+playerId+".yml"); }
    void journal(String phase) throws Exception {
        Files.createDirectories(journal().getParent());
        Files.writeString(journal(), "format-version: 2\nuuid: '"+playerId+"'\nrecovery-phase: "+phase
                +"\nlocation:\n  world-uuid: '"+worldId+"'\n  world-name: saved\n  x: 85.5\n  y: 25\n  z: 82.5\n  yaw: 0\n  pitch: 0\n"
                +"inventory:\n  storage: ["+String.join(", ",Collections.nCopies(36,"null"))+"]\n"
                +"  armor: [null, null, null, null]\n");
    }
    @Test void incompleteJournalNeverTeleportsOrClearsInventory() throws Exception {
        journal("PENDING");
        var yaml=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(journal().toFile());
        yaml.set("inventory.storage",null); yaml.save(journal().toFile());
        String original=Files.readString(journal());
        var service=new PlayerSnapshotService(plugin);
        assertFalse(service.restore(player));
        assertTrue(calls.isEmpty());
        assertEquals(original,Files.readString(journal()));
    }
    @Test void invalidSlotAndNonFiniteLocationAreRejectedBeforeTeleport() throws Exception {
        journal("PENDING");
        var yaml=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(journal().toFile());
        var slots=new ArrayList<Object>(Collections.nCopies(36,null)); slots.set(0,"not-an-item");
        yaml.set("inventory.storage",slots); yaml.save(journal().toFile());
        var service=new PlayerSnapshotService(plugin);
        assertFalse(service.restore(player)); assertTrue(calls.isEmpty());
        journal("PENDING");
        yaml=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(journal().toFile());
        yaml.set("location.x",Double.NaN); yaml.save(journal().toFile());
        assertFalse(service.restore(player)); assertTrue(calls.isEmpty());
    }
    @Test void captureGetterFailureReturnsFailureWithoutLeavingSessionOwnership() throws Exception {
        rejectCapture=true;
        var service=new PlayerSnapshotService(plugin);
        assertFalse(service.capture(player));
        assertFalse(service.hasPending(playerId));
        assertFalse(service.isCurrentSessionSnapshot(playerId));
        assertFalse(Files.exists(root.resolve("recovery/"+playerId+".yml.tmp")));
        rejectCapture=false;
        assertTrue(service.capture(player));
        assertTrue(service.isCurrentSessionSnapshot(playerId));
    }
    @Test void leavingEventCannotReenterOrRecoverUntilOuterRestoreCompletes() throws Exception {
        var config=new Configuration(null,new LanguageConfiguration(),new MapConfiguration());
        var wins=new pl.mrstudios.deathrun.arena.win.WinMapManager();
        var manager=new ArenaManager(plugin,server,config,wins,null) {
            @Override public boolean isMapConfigured(MapConfiguration.MapDefinition map) { return true; }
        };
        assertTrue(manager.ensureSnapshot(player));
        var map=new MapConfiguration.MapDefinition(); map.id="bee"; map.world="saved";
        var arena=new pl.mrstudios.deathrun.arena.Arena("bee");
        arena.getUsers().add(new pl.mrstudios.deathrun.arena.user.User(player));
        var service=new pl.mrstudios.deathrun.arena.ArenaServiceRunnable(arena,map,manager,wins,null,plugin,server,config) {
            @Override protected void setState(pl.mrstudios.deathrun.api.arena.enums.GameState state) {}
            @Override protected void resetRoundState() {}
        };
        Field field=ArenaManager.class.getDeclaredField("runtimesByMapId"); field.setAccessible(true);
        @SuppressWarnings("unchecked") Map<String,ArenaManager.ArenaRuntime> runtimes=(Map<String,ArenaManager.ArenaRuntime>)field.get(manager);
        runtimes.put("bee",new ArenaManager.ArenaRuntime("bee",map,arena,service));
        var otherMap=new MapConfiguration.MapDefinition(); otherMap.id="other"; otherMap.world="saved";
        otherMap.arenaWaitingLobbyLocation=new Location(world,34.5,35,59.5);
        var otherArena=new pl.mrstudios.deathrun.arena.Arena("other");
        runtimes.put("other",new ArenaManager.ArenaRuntime("other",otherMap,otherArena,service));
        field=ArenaManager.class.getDeclaredField("playerMapIndex"); field.setAccessible(true);
        @SuppressWarnings("unchecked") Map<UUID,String> index=(Map<UUID,String>)field.get(manager);
        index.put(playerId,"bee");
        eventHook=event -> {
            if (!(event instanceof pl.mrstudios.deathrun.api.arena.event.arena.ArenaUserLeftEvent)) return;
            assertNull(manager.runtimeForPlayer(player));
            assertTrue(manager.isRecoveryBlocked(player));
            assertFalse(manager.ensureSnapshot(player));
            assertFalse(manager.restorePendingSnapshot(player));
            assertFalse(manager.leaveCurrentMap(player,false));
            assertEquals(ArenaManager.JoinResult.PLAYER_STATE_SAVE_FAILED,manager.joinMap(player,"other"));
        };
        assertTrue(manager.leaveCurrentMap(player,false));
        assertTrue(arena.getUsers().isEmpty());
        assertTrue(otherArena.getUsers().isEmpty());
        assertFalse(manager.hasPendingSnapshot(player));
        assertFalse(manager.isRecoveryBlocked(player));
        assertEquals(List.of("teleport","inventory","save-player"),calls);
    }
    @Test void actualRejectedTeleportNeverAppliesInventory() throws Exception {
        journal("PENDING"); teleportAllowed=false;
        PlayerSnapshotService service=new PlayerSnapshotService(plugin);
        assertFalse(service.restore(player));
        assertEquals(List.of("teleport"),calls);
        assertTrue(service.hasPending(playerId));
        assertTrue(Files.readString(journal()).contains("PENDING"));
    }

    @Test void loadingMapBeforeItsWorldDoesNotRewriteWaitingWorldMetadata() throws Exception {
        worldsAvailable=false;
        Path mapFile=root.resolve("map.yml");
        String original="maps:\n- id: bee\n  world: unloaded-room\n  arena-waiting-lobby-location:\n"
                +"    world: unloaded-room\n    worldUuid: '"+worldId+"'\n"
                +"    x: 90.5\n    y: 25.0\n    z: 79.5\n    yaw: 33.0\n    pitch: 0.0\n";
        Files.writeString(mapFile,original);
        MapConfiguration loaded=new pl.mrstudios.deathrun.config.ConfigurationFactory(root)
                .produce(MapConfiguration.class,mapFile.toFile());
        assertNull(loaded.getMapById("bee").arenaWaitingLobbyLocation.getWorld());
        assertEquals(original,Files.readString(mapFile));
    }
    @Test void actualSuccessfulRecoverySavesPlayerBeforeRetiringJournal() throws Exception {
        journal("PENDING"); PlayerSnapshotService service=new PlayerSnapshotService(plugin);
        assertTrue(service.restore(player));
        assertEquals(List.of("teleport","inventory","save-player"),calls);
        assertFalse(Files.exists(journal()));
    }
    @Test void actualAppliedJournalDoesNotReissueInventory() throws Exception {
        journal("APPLIED"); PlayerSnapshotService service=new PlayerSnapshotService(plugin);
        assertTrue(service.restore(player)); assertTrue(calls.isEmpty());
        assertFalse(Files.exists(journal()));
    }
    @Test void partialRecoveryRemainsGuardedEvenForOperators() throws Exception {
        journal("PENDING"); rejectStorage=true;
        Configuration config=new Configuration(null,new LanguageConfiguration(),null);
        ArenaManager manager=new ArenaManager(plugin,server,config,null,null);
        assertFalse(manager.restorePendingSnapshot(player));
        assertTrue(manager.isRecoveryBlocked(player));
        assertTrue(Files.readString(journal()).contains("RESTORING"));
        var drop=new PlayerDropItemEvent(player,proxy(Item.class,(method,args)->null));
        new PlayerRecoveryGuardListener(manager,config).drop(drop);
        assertTrue(drop.isCancelled());
        rejectStorage=false; calls.clear();
        assertTrue(manager.restorePendingSnapshot(player));
        assertFalse(manager.isRecoveryBlocked(player));
        assertEquals(List.of("teleport","inventory","save-player"),calls);
    }
    @Test void queueSnapshotIsProtectedWithoutFreezingNormalQueueMovement() throws Exception {
        journal("PENDING");
        Configuration config=new Configuration(null,new LanguageConfiguration(),null);
        ArenaManager manager=new ArenaManager(plugin,server,config,null,null);
        var signs=new pl.mrstudios.deathrun.arena.sign.SignManager(plugin,manager,config);
        // Seed the same queue index used by queuePlayerToMap, without constructing a map world.
        Field field=signs.getClass().getDeclaredField("playerQueue"); field.setAccessible(true);
        @SuppressWarnings("unchecked") Map<UUID,String> queue=(Map<UUID,String>)field.get(signs);
        queue.put(playerId,"bee"); manager.setSignManager(signs);
        assertFalse(manager.isRecoveryBlocked(player));
        assertTrue(manager.isPendingStateProtected(player));
        var guard=new PlayerRecoveryGuardListener(manager,config);
        var drop=new PlayerDropItemEvent(player,proxy(Item.class,(method,args)->null));
        guard.drop(drop); assertTrue(drop.isCancelled());
        var sell=new org.bukkit.event.player.PlayerCommandPreprocessEvent(player,"/sellall");
        guard.command(sell); assertTrue(sell.isCancelled());
        var vote=new org.bukkit.event.player.PlayerCommandPreprocessEvent(player,"/dr vote");
        guard.command(vote); assertFalse(vote.isCancelled());
        var move=new org.bukkit.event.player.PlayerMoveEvent(player,new Location(world,0,25,0),new Location(world,1,25,0));
        guard.move(move); assertFalse(move.isCancelled());
    }

    @Test void successfulQueueRecoveryRemovesBothQueueIndexesBeforeUnlockingItems() throws Exception {
        journal("PENDING");
        QueuedFixture queued=queuedFixture();
        assertTrue(queued.signs().isQueued(player));
        assertEquals(1,queued.signs().queuedPlayersCount("bee"));
        assertTrue(queued.manager().restorePendingSnapshot(player));
        assertFalse(queued.signs().isQueued(player));
        assertEquals(0,queued.signs().queuedPlayersCount("bee"));
        assertFalse(queued.manager().isPendingStateProtected(player));
        assertFalse(Files.exists(journal()));
        assertEquals(List.of("teleport","inventory","save-player"),calls);
    }

    @Test void failedQueueRecoveryCannotBeReadmittedAndStillProtectsSnapshotItems() throws Exception {
        journal("PENDING"); teleportAllowed=false;
        QueuedFixture queued=queuedFixture();
        assertFalse(queued.manager().restorePendingSnapshot(player));
        assertFalse(queued.signs().isQueued(player));
        assertEquals(0,queued.signs().queuedPlayersCount("bee"));
        assertTrue(queued.manager().isRecoveryBlocked(player));
        var drop=new PlayerDropItemEvent(player,proxy(Item.class,(method,args)->null));
        new PlayerRecoveryGuardListener(queued.manager(),queued.configuration()).drop(drop);
        assertTrue(drop.isCancelled());
        assertTrue(Files.exists(journal()));
        assertEquals(List.of("teleport"),calls);
    }

    @Test void queuedMenuExceptionAllowsOnlyOwnedMenusAndNeverPendingRecovery() throws Exception {
        journal("PENDING");
        QueuedFixture queued=queuedFixture();
        var guard=new PlayerRecoveryGuardListener(queued.manager(),queued.configuration());
        Constructor<?> holderConstructor=Class.forName(
                "pl.mrstudios.deathrun.arena.selector.MapSelectorService$SelectorInventoryHolder").getDeclaredConstructor();
        holderConstructor.setAccessible(true);
        Object ownedHolder=holderConstructor.newInstance();
        var menu=openInventoryEvent(ownedHolder);
        guard.open(menu); assertFalse(menu.isCancelled());
        var container=openInventoryEvent(proxy(org.bukkit.inventory.InventoryHolder.class,(n,a)->null));
        guard.open(container); assertTrue(container.isCancelled());
        queued.manager().leaveQueue(player);
        assertTrue(queued.manager().isRecoveryBlocked(player));
        var orphanMenu=openInventoryEvent(ownedHolder);
        guard.open(orphanMenu); assertTrue(orphanMenu.isCancelled());
    }

    private org.bukkit.event.inventory.InventoryOpenEvent openInventoryEvent(Object holder) {
        var inventory=proxy(org.bukkit.inventory.Inventory.class,(method,args)->switch(method) {
            case "getHolder" -> holder;
            case "getType" -> org.bukkit.event.inventory.InventoryType.CHEST;
            case "getSize" -> 9;
            default -> null;
        });
        var view=proxy(org.bukkit.inventory.InventoryView.class,(method,args)->switch(method) {
            case "getPlayer" -> player;
            case "getTopInventory" -> inventory;
            case "getBottomInventory" -> player.getInventory();
            case "getType" -> org.bukkit.event.inventory.InventoryType.CHEST;
            default -> null;
        });
        return new org.bukkit.event.inventory.InventoryOpenEvent(view);
    }

    private QueuedFixture queuedFixture() throws Exception {
        Configuration config=new Configuration(null,new LanguageConfiguration(),null);
        ArenaManager manager=new ArenaManager(plugin,server,config,null,null);
        var signs=new pl.mrstudios.deathrun.arena.sign.SignManager(plugin,manager,config);
        Field indexField=signs.getClass().getDeclaredField("playerQueue"); indexField.setAccessible(true);
        @SuppressWarnings("unchecked") Map<UUID,String> index=(Map<UUID,String>)indexField.get(signs);
        index.put(playerId,"bee");
        Field queueField=signs.getClass().getDeclaredField("queuedPlayers"); queueField.setAccessible(true);
        @SuppressWarnings("unchecked") Map<String,LinkedHashSet<UUID>> queues=(Map<String,LinkedHashSet<UUID>>)queueField.get(signs);
        queues.put("bee",new LinkedHashSet<>(List.of(playerId)));
        manager.setSignManager(signs);
        return new QueuedFixture(manager,signs,config);
    }

    private record QueuedFixture(ArenaManager manager,
                                 pl.mrstudios.deathrun.arena.sign.SignManager signs,
                                 Configuration configuration) {}

    interface Handler { Object invoke(String method,Object[] args) throws Throwable; }
    static <T> T proxy(Class<T> type,Handler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(object,method,args)->{
            if(method.getName().equals("hashCode")) return System.identityHashCode(object);
            if(method.getName().equals("equals")) return object==args[0];
            if(method.getName().equals("toString")) return "test-"+type.getSimpleName();
            Object result=handler.invoke(method.getName(),args);
            if(result!=null || !method.getReturnType().isPrimitive()) return result;
            Class<?> r=method.getReturnType();
            if(r==boolean.class) return false; if(r==int.class) return 0;
            if(r==long.class) return 0L; if(r==float.class) return 0f; if(r==double.class) return 0d;
            if(r==short.class) return (short)0; if(r==byte.class) return (byte)0; if(r==char.class) return '\0';
            return null;
        }));
    }
}
