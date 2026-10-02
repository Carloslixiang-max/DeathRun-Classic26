package pl.mrstudios.deathrun.classic.tobee;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import pl.mrstudios.deathrun.config.ConfigurationFactory;
import pl.mrstudios.deathrun.config.impl.MapConfiguration;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ToBeeWaitingLobbyTest {
    @TempDir Path root;
    World world;
    boolean clear = true;
    @BeforeEach void setup() throws Exception {
        UUID id = UUID.randomUUID();
        world = proxy(World.class, (name, args) -> switch (name) {
            case "getName" -> "tobee";
            case "getUID" -> id;
            case "getBlockAt" -> {
                int y = (int) args[1];
                yield proxy(Block.class, (method, ignored) -> switch (method) {
                    case "getType" -> y == 34 ? Material.STONE_BRICKS : Material.LIGHT_WEIGHTED_PRESSURE_PLATE;
                    case "isPassable" -> clear;
                    default -> null;
                });
            }
            default -> null;
        });
        Server server = proxy(Server.class, (name, args) -> name.equals("getWorld") ? world : null);
        Field field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true); field.set(null, server);
    }
    @AfterEach void teardown() throws Exception {
        Field field = Bukkit.class.getDeclaredField("server"); field.setAccessible(true); field.set(null, null);
    }
    @Test void oldRacePlatformAndMissingPointMoveIntoCourtyard() {
        var target = new Location(world, 34.5, 35, 59.5, 180f, 0f);
        assertEquals(target, ToBeeWaitingLobby.choose(world, null));
        assertEquals(target, ToBeeWaitingLobby.choose(world, new Location(world, 85.5, 25, 79.5, 90f, 0f)));
        assertEquals(target, ToBeeWaitingLobby.choose(world, new Location(world, 89.5, 26, 85.5)));
    }
    @Test void customRoomKeepsExactWorldCoordinatesAndOrientation() {
        var custom = new Location(world, 35.75, 35, 59.25, 33f, 12f);
        assertEquals(custom, ToBeeWaitingLobby.choose(world, custom));
        assertNotSame(custom, ToBeeWaitingLobby.choose(world, custom));
    }
    @Test void obstructedCourtyardDoesNotReplaceOldPoint() {
        clear = false;
        assertThrows(IllegalStateException.class, () -> ToBeeWaitingLobby.choose(world, null));
    }
    @Test void migrationBacksUpOldConfigurationAndPersistsOnce() throws Exception {
        Path file = root.resolve("map.yml");
        var config = new ConfigurationFactory(root).produce(MapConfiguration.class, file.toFile());
        var map = new MapConfiguration.MapDefinition();
        map.id = ToBeeCandidateProfileService.MAP_ID; map.world = "tobee"; map.name = "蜂与不蜂";
        map.arenaWaitingLobbyLocation = new Location(world, 85.5, 25, 79.5, 90f, 0f);
        config.maps.add(map); config.save();
        String before = Files.readString(file);
        assertTrue(ToBeeWaitingLobby.migrate(config, world));
        assertEquals(before, Files.readString(root.resolve("map.yml.before-courtyard-v1.bak")));
        var loaded = new ConfigurationFactory(root).produce(MapConfiguration.class, file.toFile());
        var restored = loaded.getMapById(map.id);
        assertEquals("To Bee Or Not To Bee", restored.name);
        assertEquals(new Location(world, 34.5, 35, 59.5, 180f, 0f), restored.arenaWaitingLobbyLocation);
        assertFalse(ToBeeWaitingLobby.migrate(loaded, world));
        assertEquals(before, Files.readString(root.resolve("map.yml.before-courtyard-v1.bak")));
    }
    interface Handler { Object call(String name, Object[] args); }
    static <T> T proxy(Class<T> type, Handler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (object, method, args) -> {
            if (method.getName().equals("equals")) return object == args[0];
            if (method.getName().equals("hashCode")) return System.identityHashCode(object);
            Object result = handler.call(method.getName(), args);
            if (result != null || !method.getReturnType().isPrimitive()) return result;
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == void.class) return null;
            return 0;
        }));
    }
}
