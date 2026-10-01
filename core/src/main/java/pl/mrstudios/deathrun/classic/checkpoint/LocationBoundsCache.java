package pl.mrstudios.deathrun.classic.checkpoint;

import org.bukkit.Location;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Configuration edits replace location lists; runtime reloads replace owners. */
public final class LocationBoundsCache {
    private final Map<Object, Cached> cache = new WeakHashMap<>();

    public boolean touches(Object owner, List<Location> locations, Location from, Location to) {
        return touches(owner, locations, from, to, 1.01);
    }
    public boolean touches(Object owner, List<Location> locations, Location from, Location to, double topPadding) {
        if (locations.isEmpty() || from.getWorld() == null || to.getWorld() == null
                || !from.getWorld().getUID().equals(to.getWorld().getUID())) return false;
        Cached cached = cache.get(owner);
        if (cached == null || cached.locations != locations || cached.size != locations.size()
                || !cached.world.equals(from.getWorld().getUID())) {
            cached = create(locations);
            if (cached == null) return false;
            cache.put(owner, cached);
        }
        return cached.world.equals(from.getWorld().getUID())
                && cached.bounds.touches(from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ(), topPadding);
    }

    private Cached create(List<Location> locations) {
        UUID world = null;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Location location : locations) {
            if (location == null || location.getWorld() == null) return null;
            UUID id = location.getWorld().getUID();
            if (world != null && !world.equals(id)) return null;
            world = id;
            minX = Math.min(minX, location.getBlockX()); maxX = Math.max(maxX, location.getBlockX());
            minY = Math.min(minY, location.getBlockY()); maxY = Math.max(maxY, location.getBlockY());
            minZ = Math.min(minZ, location.getBlockZ()); maxZ = Math.max(maxZ, location.getBlockZ());
        }
        return world == null ? null : new Cached(locations, locations.size(), world,
                new MovementBounds(minX, minY, minZ, maxX, maxY, maxZ));
    }

    private record Cached(List<Location> locations, int size, UUID world, MovementBounds bounds) {}
}
