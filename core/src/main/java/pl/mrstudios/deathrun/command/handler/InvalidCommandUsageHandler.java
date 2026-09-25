package pl.mrstudios.deathrun.command.handler;

import dev.rollczi.litecommands.handler.result.ResultHandlerChain;
import dev.rollczi.litecommands.invalidusage.InvalidUsage;
import dev.rollczi.litecommands.invalidusage.InvalidUsageHandler;
import dev.rollczi.litecommands.invocation.Invocation;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;

import static net.kyori.adventure.text.minimessage.MiniMessage.miniMessage;

public class InvalidCommandUsageHandler implements InvalidUsageHandler<CommandSender> {

    private static final String PREFIX = "<gold>[DR]</gold> ";

    private final BukkitAudiences audiences;

    @Inject
    public InvalidCommandUsageHandler(
            @NotNull BukkitAudiences audiences
    ) {
        this.audiences = audiences;
    }

    @Override
    public void handle(
            @NotNull Invocation<CommandSender> invocation,
            @NotNull InvalidUsage<CommandSender> result,
            @NotNull ResultHandlerChain<CommandSender> chain
    ) {
        String usage = usageFor(invocation);
        this.audiences.sender(invocation.sender()).sendMessage(miniMessage().deserialize(PREFIX + usage));
    }

    private @NotNull String usageFor(@NotNull Invocation<CommandSender> invocation) {
        if (invocation.arguments().asList().isEmpty()) {
            return "<red>Invalid usage. <gray>Try: <white>/dr <dark_gray>| <white>/dr join <map> <dark_gray>| <white>/dr start (map) <dark_gray>| <white>/dr stop (map) <dark_gray>| <white>/dr reload <dark_gray>| <white>/dr leave <dark_gray>| <white>/dr map list";
        }

        String first = invocation.arguments().asList().get(0).toLowerCase();
        if (!("map".equals(first) || "cp".equals(first) || "trap".equals(first) || "edit".equals(first))) {
            return "<red>Unknown subcommand. <gray>Use <white>/dr join <map><gray>, <white>/dr start (map)<gray>, <white>/dr stop (map)<gray>, <white>/dr reload<gray>, <white>/dr leave<gray>, <white>/dr map ...<gray>, <white>/dr cp ... <gray>or <white>/dr trap ...";
        }

        if (invocation.arguments().asList().size() == 1) {
            return "<gray>Edit commands: <white>/dr map ... <gray>| <white>/dr cp ... <gray>| <white>/dr trap ...";
        }

        return "<red>Invalid edit usage. <gray>Try: <white>/dr edit create <name><dark_gray>, <white>/dr setlobby<dark_gray>, <white>/dr setbarrier (material)<dark_gray>, <white>/dr addspawn <death/runner><dark_gray>, <white>/dr trap add <type> (args)<dark_gray>, <white>/dr cp add<dark_gray>, <white>/dr addteleport<dark_gray>, <white>/dr save";
    }

}
