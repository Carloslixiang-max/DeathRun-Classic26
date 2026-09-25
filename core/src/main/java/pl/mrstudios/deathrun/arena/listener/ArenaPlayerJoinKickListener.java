package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.event.Listener;
import pl.mrstudios.commons.inject.annotation.Inject;

/** Intentionally empty. DeathRun must never override global login decisions. */
public class ArenaPlayerJoinKickListener implements Listener {
    @Inject public ArenaPlayerJoinKickListener() {}
}
