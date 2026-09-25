package pl.mrstudios.deathrun.classic.trap;

import pl.mrstudios.deathrun.api.arena.trap.ITrap;

import java.util.Set;
import java.util.UUID;

public record TrapActivationContext(
        String mapId,
        int trapIndex,
        String trapType,
        UUID activatedByDeathUUID,
        long activatedAt,
        long expiresAt,
        ITrap trap,
        Set<UUID> victims
) {}
