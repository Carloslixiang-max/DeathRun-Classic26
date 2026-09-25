package pl.mrstudios.deathrun.arena.trap;

import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.deathrun.api.arena.trap.ITrap;

import java.util.List;

public abstract class Trap implements ITrap {

    public Location button;
    public List<Location> locations;

    @Override
    public @NotNull Location getButton() {
        return this.button;
    }

    @Override
    public void setButton(
            @NotNull Location location
    ) {
        this.button = location;
    }

    @Override
    public @NotNull List<Location> getLocations() {
        return this.locations;
    }

    @Override
    public void setLocations(
            @NotNull List<Location> locations
    ) {
        this.locations = locations;
    }

    protected boolean hasAnyNullWorldLocation() {
        if (this.locations == null || this.locations.isEmpty())
            return false;

        return this.locations.stream().anyMatch((location) -> location == null || location.getWorld() == null);
    }

    protected boolean abortIfAnyNullWorldLocation(
            @NotNull String phase
    ) {
        if (!this.hasAnyNullWorldLocation())
            return false;

        Bukkit.getLogger().severe("[DeathRun] " + this.getClass().getSimpleName() + " aborted during " + phase
                + ": one or more trap locations have null world references."
                + " Re-save map setup or ensure map world is loaded.");
        return true;
    }

}
