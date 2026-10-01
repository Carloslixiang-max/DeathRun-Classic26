package pl.mrstudios.deathrun.player;

import java.util.Locale;

public final class RecoveryCommands {
    private RecoveryCommands() {}
    public static boolean allowed(String command) {
        String[] parts = command.trim().toLowerCase(Locale.ROOT).split("\\s+");
        if (parts.length != 2) return false;
        String name = parts[0];
        if (name.startsWith("/")) name = name.substring(1);
        if (name.startsWith("deathrun:")) name = name.substring("deathrun:".length());
        return (name.equals("dr") || name.equals("deathrun"))
                && (parts[1].equals("recover") || parts[1].equals("leave"));
    }
}
