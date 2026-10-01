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
    boolean teleportAllowed=true, rejectStorage=false;
    Plugin plugin; Player player; World world; Server server;

    @BeforeEach void setup() throws Exception {
        world=proxy(World.class, (method,args) -> switch(method) {
            case "getUID" -> worldId; case "getName" -> "saved"; default -> null;
        });
        server=proxy(Server.class, (method,args) -> switch(method) {
            case "getWorld" -> world;
            case "getLogger" -> Logger.getLogger("recovery-test");
            case "getName", "getVersion", "getBukkitVersion" -> "recovery-test";
            default -> null;
        });
        Bukkit.setServer(server);
        plugin=proxy(Plugin.class, (method,args) -> switch(method) {
            case "getDataFolder" -> root.toFile(); case "getServer" -> server;
            case "getLogger" -> Logger.getLogger("recovery-test"); default -> null;
        });
        PlayerInventory inventory=proxy(PlayerInventory.class, (method,args) -> {
            if(method.equals("getStorageContents")) return new ItemStack[36];
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
                +"\nlocation:\n  world-uuid: '"+worldId+"'\n  world-name: saved\n  x: 85.5\n  y: 25\n  z: 82.5\n");
    }
    @Test void actualRejectedTeleportNeverAppliesInventory() throws Exception {
        journal("PENDING"); teleportAllowed=false;
        PlayerSnapshotService service=new PlayerSnapshotService(plugin);
        assertFalse(service.restore(player));
        assertEquals(List.of("teleport"),calls);
        assertTrue(service.hasPending(playerId));
        assertTrue(Files.readString(journal()).contains("PENDING"));
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
