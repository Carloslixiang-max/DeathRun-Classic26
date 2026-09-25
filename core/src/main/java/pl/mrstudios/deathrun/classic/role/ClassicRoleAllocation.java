package pl.mrstudios.deathrun.classic.role;

public final class ClassicRoleAllocation {

    private ClassicRoleAllocation() {}

    /**
     * Classic always keeps at least one Runner. With the locked 22-player
     * configuration and 2 configured Deaths this yields 20 Runners / 2 Deaths.
     */
    public static int deathCount(int totalPlayers, int configuredDeaths) {
        int players = Math.max(0, totalPlayers);
        int deaths = Math.max(0, configuredDeaths);
        if (players <= 1 || deaths == 0)
            return 0;

        return Math.min(deaths, players - 1);
    }
}
