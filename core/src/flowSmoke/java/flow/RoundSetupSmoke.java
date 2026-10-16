package flow;

import org.bukkit.*;
import pl.mrstudios.deathrun.plugin.Entrypoint;
import pl.mrstudios.deathrun.arena.checkpoint.Checkpoint;
import pl.mrstudios.deathrun.classic.tobee.*;
import pl.mrstudios.deathrun.config.AtomicConfigurationSave;
import java.util.*;

/** CI-only: real map YAML round-trip, explicit repair, sign numbers, then cold-boot verification. */
final class RoundSetupSmoke {
    int checks;
    void check(boolean value,String label) { if(!value) throw new AssertionError(label); checks++; }
    void run(FlowSmokePlugin owner,Entrypoint plugin,String id,boolean verifyOnly) throws Exception {
        var config=plugin.getConfiguration(); var manager=plugin.getArenaManager();
        var map=config.map().getMapById(id); World world=Bukkit.getWorld(map.world);
        Location runner=new Location(world,85.625,25,82.5,33f,5f);
        Location death=new Location(world,75.625,25,56.5,44f,6f);
        Location checkpoint=new Location(world,33.625,45,35.5,55f,7f);
        Location waiting=map.arenaWaitingLobbyLocation.clone();
        check(config.plugin().arenaRunnerStartLives==10,"configured 10 lives");
        check(config.plugin().arenaJumpEffectSeconds==10f,"configured 10-second jump effect");
        if(!verifyOnly) {
            map.arenaRunnerStartLocation=runner.clone();
            map.arenaRunnerSpawnLocations.set(0,runner.clone()); map.arenaCustomRunnerSpawns=true;
            map.arenaDeathSpawnLocations.set(0,death.clone()); map.arenaCustomDeathSpawns=true;
            var cp=map.arenaCheckpoints.getFirst();
            map.arenaCheckpoints.set(0,new Checkpoint(cp.id(),checkpoint.clone(),cp.locations(),cp.name()));
            map.arenaCustomCheckpointSpawns=new ArrayList<>(List.of(1));
            AtomicConfigurationSave.save(config.map());
            config.map().load(false); manager.initialize();
        }
        for(int pass=0;pass<2;pass++) {
            map=config.map().getMapById(id);
            check(map.arenaRunnerStartLocation!=null && map.arenaRunnerStartLocation.equals(runner),"main race start persists independently of waiting lobby");
            check(map.arenaCustomRunnerSpawns && map.arenaRunnerSpawnLocations.getFirst().equals(runner),"Runner start coordinates and orientation preserved");
            check(map.arenaCustomDeathSpawns && map.arenaDeathSpawnLocations.getFirst().equals(death),"Death start coordinates and orientation preserved");
            check(map.arenaCustomCheckpointSpawns.contains(1) && map.arenaCheckpoints.getFirst().spawn().equals(checkpoint),"checkpoint respawn preserved");
            check(map.arenaWaitingLobbyLocation.equals(waiting),"correct waiting location unchanged");
            check(ToBeeSignTranslations.numberedCount(world,map)==23,"all 23 signs have runtime numbers on both sides");
            check(ToBeeCandidateProfileService.spawnIssues(world,map).isEmpty(),"custom safe starts accepted");
            if(pass==0 && !verifyOnly) {
                var repaired=new ToBeeCandidateProfileService(config,manager).repairSpawns(world);
                check(repaired.success(),"explicit spawn repair succeeds: "+repaired.message());
                config.map().load(false); manager.initialize();
            }
        }
        owner.getLogger().info("[DR-SETUP] PASS assertions="+checks+" coldBoot="+verifyOnly);
    }
}
