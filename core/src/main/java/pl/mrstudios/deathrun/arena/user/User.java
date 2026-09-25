package pl.mrstudios.deathrun.arena.user;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pl.mrstudios.deathrun.api.arena.checkpoint.ICheckpoint;
import pl.mrstudios.deathrun.api.arena.user.IUser;
import pl.mrstudios.deathrun.api.arena.user.enums.Role;

import java.util.UUID;

import static org.bukkit.Bukkit.getServer;
import static pl.mrstudios.deathrun.api.arena.user.enums.Role.UNKNOWN;

public class User implements IUser {

    private final String name;
    private final UUID uniqueId;

    private Role role;
    private ICheckpoint checkpoint;
    private int deaths;
    private int lives;
    private int roundPoints;
    private boolean eliminated;

    public User(@NotNull Player player) {
        this.name = player.getName();
        this.uniqueId = player.getUniqueId();
        this.deaths = 0;
        this.lives = 0;
        this.roundPoints = 0;
        this.eliminated = false;
        this.role = UNKNOWN;
    }

    @Override public @NotNull String getName() { return this.name; }
    @Override public @NotNull UUID getUniqueId() { return this.uniqueId; }
    @Override public @NotNull Role getRole() { return this.role; }
    @Override public void setRole(@NotNull Role role) { this.role = role; }
    @Override public @NotNull ICheckpoint getCheckpoint() { return this.checkpoint; }
    @Override public void setCheckpoint(@NotNull ICheckpoint checkpoint) { this.checkpoint = checkpoint; }
    @Override public int getDeaths() { return this.deaths; }
    @Override public void setDeaths(int deaths) { this.deaths = deaths; }
    @Override public int getLives() { return this.lives; }
    @Override public void setLives(int lives) { this.lives = Math.max(0, lives); }
    @Override public int getRoundPoints() { return this.roundPoints; }
    @Override public void setRoundPoints(int roundPoints) { this.roundPoints = Math.max(0, roundPoints); }
    @Override public boolean isEliminated() { return this.eliminated; }
    @Override public void setEliminated(boolean eliminated) { this.eliminated = eliminated; }
    @Override public @Nullable Player asBukkit() { return getServer().getPlayer(this.uniqueId); }
}
