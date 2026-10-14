package flow;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.world.EntitiesLoadEvent;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.arena.listener.ArenaWorldListener;
import pl.mrstudios.deathrun.arena.trap.impl.TrapGiant;
import pl.mrstudios.deathrun.arena.world.ArenaMobProtection;
import java.util.*;

/** CI-only real entity/event checks, including unrelated-world isolation and the Giant trap. */
final class MobSmoke {
    int checks;
    void check(boolean success,String label) { if(!success) throw new AssertionError(label); checks++; }
    void start(FlowSmokePlugin owner,ArenaManager manager,ArenaManager.ArenaRuntime runtime) {
        Chunk mapChunk=runtime.map().arenaWaitingLobbyLocation.getChunk();
        World survival=Bukkit.getWorlds().stream().filter(w->!manager.isDeathRunWorld(w)).findFirst().orElseThrow();
        Chunk controlChunk=survival.getSpawnLocation().getChunk();
        boolean mapWasForced=mapChunk.isForceLoaded(), controlWasForced=controlChunk.isForceLoaded();
        mapChunk.setForceLoaded(true); controlChunk.setForceLoaded(true);
        // With no real clients, entity chunk activation may lag the initial block load.
        // Let tickets settle, then test actual world membership, not the tick-valid flag.
        owner.getServer().getScheduler().runTaskLater(owner, () -> {
            try { run(owner,manager,runtime); }
            catch(Throwable failure) {
                owner.getLogger().log(java.util.logging.Level.SEVERE,"[DR-MOB] FAIL",failure);
            } finally {
                mapChunk.setForceLoaded(mapWasForced); controlChunk.setForceLoaded(controlWasForced);
            }
        },20L);
    }
    void run(FlowSmokePlugin owner,ArenaManager manager,ArenaManager.ArenaRuntime runtime) {
        World world=runtime.map().arenaWaitingLobbyLocation.getWorld();
        World survival=Bukkit.getWorlds().stream().filter(w->!manager.isDeathRunWorld(w)).findFirst().orElseThrow();
        Boolean survivalRule=survival.getGameRuleValue(GameRules.SPAWN_MOBS);
        Location point=runtime.map().arenaWaitingLobbyLocation;
        List<Entity> created=new ArrayList<>();
        TrapGiant giantTrap=new TrapGiant(); giantTrap.setLocations(List.of(point));
        try {
            check(Boolean.FALSE.equals(world.getGameRuleValue(GameRules.SPAWN_MOBS)),"loaded map disables spawn_mobs");
            for(SpawnReason reason:List.of(SpawnReason.NATURAL,SpawnReason.SPAWNER,SpawnReason.TRIAL_SPAWNER,
                    SpawnReason.PATROL,SpawnReason.RAID,SpawnReason.NETHER_PORTAL,SpawnReason.REINFORCEMENTS,
                    SpawnReason.SPAWNER_EGG,SpawnReason.DISPENSE_EGG,SpawnReason.SLIME_SPLIT)) {
                Zombie zombie=world.spawn(point,Zombie.class,reason,false,mob->mob.setAI(false));
                if(zombie!=null) created.add(zombie);
                check(zombie==null || (!zombie.isInWorld() && world.getEntity(zombie.getUniqueId())==null),"map spawn cancelled: "+reason);
            }
            Villager npc=world.spawn(point,Villager.class,SpawnReason.CUSTOM,false,mob->mob.setAI(false)); created.add(npc);
            Villager summoned=world.spawn(point,Villager.class,SpawnReason.COMMAND,false,mob->mob.setAI(false)); created.add(summoned);
            ArmorStand decoration=world.spawn(point,ArmorStand.class); created.add(decoration);
            check(npc.isInWorld() && summoned.isInWorld() && decoration.isInWorld()
                    && world.getEntity(npc.getUniqueId())!=null && world.getEntity(summoned.getUniqueId())!=null
                    && world.getEntity(decoration.getUniqueId())!=null,"NPC, command and decoration preserved: npc="+npc.isInWorld()+"/"+npc.getEntitySpawnReason()
                    +" command="+summoned.isInWorld()+"/"+summoned.getEntitySpawnReason()
                    +" decoration="+decoration.isInWorld()+"/"+decoration.getEntitySpawnReason());
            Set<UUID> before=new HashSet<>(); world.getEntities().forEach(e->before.add(e.getUniqueId()));
            giantTrap.start();
            Giant giant=world.getEntitiesByClass(Giant.class).stream().filter(e->!before.contains(e.getUniqueId())).findFirst().orElseThrow();
            check(giant.isInWorld(),"real Giant trap starts with spawn guard active");
            Zombie old=survival.spawn(survival.getSpawnLocation(),Zombie.class,SpawnReason.NATURAL,false,mob->mob.setAI(false)); created.add(old);
            check(old.isInWorld(),"unrelated world still permits NATURAL spawn");
            check(old.teleport(point),"ambient mob fixture moves to map");
            ArenaMobProtection.protect(world);
            check(!old.isInWorld(),"reapplying map protection removes existing ambient mob");
            check(giant.isInWorld() && npc.isInWorld() && summoned.isInWorld() && decoration.isInWorld(),"cleanup preserves Giant and NPCs");
            Zombie late=survival.spawn(survival.getSpawnLocation(),Zombie.class,SpawnReason.NATURAL,false,mob->mob.setAI(false)); created.add(late);
            check(late.teleport(point),"late-loaded mob fixture arrives");
            new ArenaWorldListener(manager).onEntitiesLoad(new EntitiesLoadEvent(point.getChunk(),List.of(late,npc,giant)));
            check(!late.isInWorld() && npc.isInWorld() && giant.isInWorld(),"later entity chunk load removes ambient mob only");
            giantTrap.end(); check(!giant.isInWorld(),"Giant trap ends normally");
            check(Objects.equals(survivalRule,survival.getGameRuleValue(GameRules.SPAWN_MOBS)),"unrelated-world gamerule unchanged");
            owner.getLogger().info("[DR-MOB] PASS assertions="+checks+" map="+runtime.mapId());
        } finally {
            giantTrap.end(); created.forEach(Entity::remove);
        }
    }
}
