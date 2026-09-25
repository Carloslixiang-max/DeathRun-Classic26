package pl.mrstudios.deathrun.api.arena.user;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pl.mrstudios.deathrun.api.arena.checkpoint.ICheckpoint;
import pl.mrstudios.deathrun.api.arena.user.enums.Role;

import java.util.UUID;

public interface IUser {

    @NotNull String getName();
    @NotNull UUID getUniqueId();

    @NotNull Role getRole();
    void setRole(@NotNull Role role);

    @NotNull ICheckpoint getCheckpoint();
    void setCheckpoint(@NotNull ICheckpoint checkpoint);

    int getDeaths();
    void setDeaths(int deaths);

    int getLives();
    void setLives(int lives);

    int getRoundPoints();
    void setRoundPoints(int roundPoints);

    boolean isEliminated();
    void setEliminated(boolean eliminated);

    @Nullable Player asBukkit();
}
