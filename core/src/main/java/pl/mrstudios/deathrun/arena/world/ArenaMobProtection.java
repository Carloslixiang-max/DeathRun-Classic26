package pl.mrstudios.deathrun.arena.world;

import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import pl.mrstudios.deathrun.classic.trap.DeathRunEntityTags;

/** Dedicated DeathRun worlds must not acquire ambient mobs during any match state. */
public final class ArenaMobProtection {
    private ArenaMobProtection() {}

    public static boolean blocks(SpawnReason reason) {
        // Plugin-created trap Giants / NPCs and explicit administrator summons remain allowed.
        return reason != SpawnReason.CUSTOM && reason != SpawnReason.COMMAND;
    }

    public static boolean removeAmbient(Entity entity) {
        if (!(entity instanceof Mob mob) || !blocks(mob.getEntitySpawnReason())
                || mob.customName() != null || DeathRunEntityTags.isTrapEntity(mob))
            return false;
        mob.remove(); // No death event, loot or experience.
        return true;
    }

    public static void protect(World world) {
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        for (Entity entity : java.util.List.copyOf(world.getEntities()))
            removeAmbient(entity);
    }
}
