package pl.mrstudios.deathrun.arena.world;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.arena.listener.ArenaWorldListener;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiFunction;
import static org.junit.jupiter.api.Assertions.*;

class ArenaMobProtectionTest {
    @TempDir Path root;
    final World arenaWorld=proxy(World.class,(name,args)->null);
    final World survivalWorld=proxy(World.class,(name,args)->null);
    final List<Entity> removed=new ArrayList<>();
    ArenaWorldListener listener() {
        Plugin plugin=proxy(Plugin.class,(name,args)->name.equals("getDataFolder") ? root.toFile() : null);
        return new ArenaWorldListener(new ArenaManager(plugin,null,null,null,null) {
            @Override public boolean isDeathRunWorld(World world) { return world==arenaWorld; }
        });
    }
    <T extends Entity> T entity(Class<T> type,World world,SpawnReason reason,boolean named,Set<String> tags) {
        return proxy(type,(name,args)->switch(name) {
            case "getWorld" -> world;
            case "getLocation" -> new Location(world,0,64,0);
            case "getEntitySpawnReason" -> reason;
            case "customName" -> named ? net.kyori.adventure.text.Component.text("NPC") : null;
            case "getScoreboardTags" -> tags;
            case "remove" -> { removed.add(null); yield null; }
            default -> null;
        });
    }
    @Test void allUnrequestedSpawnReasonsAreBlockedOnlyInArenaWorld() {
        ArenaWorldListener listener=listener();
        for(SpawnReason reason:SpawnReason.values()) {
            var inside=new CreatureSpawnEvent(entity(Zombie.class,arenaWorld,reason,false,Set.of()),reason);
            listener.onCreatureSpawn(inside);
            assertEquals(reason!=SpawnReason.CUSTOM && reason!=SpawnReason.COMMAND,inside.isCancelled(),reason.name());
            var outside=new CreatureSpawnEvent(entity(Zombie.class,survivalWorld,reason,false,Set.of()),reason);
            listener.onCreatureSpawn(outside); assertFalse(outside.isCancelled(),reason.name());
        }
    }
    @Test void spawnGuardDoesNotBlockArmorStandDecorationsEvenWithDefaultReason() {
        var stand=new CreatureSpawnEvent(entity(ArmorStand.class,arenaWorld,SpawnReason.DEFAULT,false,Set.of()),SpawnReason.DEFAULT);
        listener().onCreatureSpawn(stand); assertFalse(stand.isCancelled());
    }
    @Test void cleanupRemovesAmbientMobsButPreservesIntentionalAndDecorativeEntities() {
        assertTrue(ArenaMobProtection.removeAmbient(entity(Zombie.class,arenaWorld,SpawnReason.NATURAL,false,Set.of())));
        assertTrue(ArenaMobProtection.removeAmbient(entity(Cow.class,arenaWorld,SpawnReason.DEFAULT,false,Set.of())));
        assertFalse(ArenaMobProtection.removeAmbient(entity(Giant.class,arenaWorld,SpawnReason.CUSTOM,false,Set.of())));
        assertFalse(ArenaMobProtection.removeAmbient(entity(Villager.class,arenaWorld,SpawnReason.COMMAND,false,Set.of())));
        assertFalse(ArenaMobProtection.removeAmbient(entity(Villager.class,arenaWorld,SpawnReason.DEFAULT,true,Set.of())));
        assertFalse(ArenaMobProtection.removeAmbient(entity(ArmorStand.class,arenaWorld,SpawnReason.DEFAULT,false,Set.of())));
        assertFalse(ArenaMobProtection.removeAmbient(entity(Zombie.class,arenaWorld,SpawnReason.NATURAL,false,
                Set.of(pl.mrstudios.deathrun.classic.trap.DeathRunEntityTags.TRAP_EXPLOSIVE))));
        assertEquals(2,removed.size());
    }
    @Test void laterEntityChunkLoadsCleanOnlyConfiguredMapWorlds() {
        ArenaWorldListener listener=listener();
        Chunk arenaChunk=proxy(Chunk.class,(name,args)->name.equals("getWorld")?arenaWorld:null);
        Chunk survivalChunk=proxy(Chunk.class,(name,args)->name.equals("getWorld")?survivalWorld:null);
        var ambient=entity(Zombie.class,arenaWorld,SpawnReason.NATURAL,false,Set.of());
        var npc=entity(Villager.class,arenaWorld,SpawnReason.CUSTOM,false,Set.of());
        listener.onEntitiesLoad(new EntitiesLoadEvent(arenaChunk,List.of(ambient,npc)));
        assertEquals(1,removed.size());
        listener.onEntitiesLoad(new EntitiesLoadEvent(survivalChunk,List.of(ambient)));
        assertEquals(1,removed.size());
    }
    @SuppressWarnings("unchecked") static <T> T proxy(Class<T> type,BiFunction<String,Object[],Object> handler) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(self,method,args)-> {
            if(method.getName().equals("equals")) return self==args[0];
            if(method.getName().equals("hashCode")) return System.identityHashCode(self);
            Object value=handler.apply(method.getName(),args); if(value!=null) return value;
            if(method.getReturnType()==boolean.class) return false;
            if(method.getReturnType()==int.class) return 0;
            return null;
        });
    }
}
