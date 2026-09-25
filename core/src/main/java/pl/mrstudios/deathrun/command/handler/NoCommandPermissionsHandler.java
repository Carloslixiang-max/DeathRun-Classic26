package pl.mrstudios.deathrun.command.handler;

import dev.rollczi.litecommands.handler.result.ResultHandlerChain;
import dev.rollczi.litecommands.invocation.Invocation;
import dev.rollczi.litecommands.permission.MissingPermissions;
import dev.rollczi.litecommands.permission.MissingPermissionsHandler;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.config.Configuration;

import static net.kyori.adventure.text.minimessage.MiniMessage.miniMessage;

public class NoCommandPermissionsHandler implements MissingPermissionsHandler<CommandSender> {

    private static final String PREFIX = "<gold>[DR]</gold> ";

    private final Configuration configuration;

    @Inject
    public NoCommandPermissionsHandler(
            @NotNull Configuration configuration
    ) {
        this.configuration = configuration;
    }

    @Override
    public void handle(
            @NotNull Invocation<CommandSender> invocation,
            @NotNull MissingPermissions missingPermissions,
            @NotNull ResultHandlerChain<CommandSender> resultHandlerChain
    ) {
        invocation.sender().sendMessage(miniMessage().deserialize(
            PREFIX + this.configuration.language().chatMessageNoPermissions
        ));
    }

}
