package pl.mrstudios.deathrun.arena.listener;

import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.arena.ArenaManager;

import static java.lang.Integer.MAX_VALUE;
import static org.bukkit.GameMode.ADVENTURE;
import static org.bukkit.event.EventPriority.MONITOR;
import static org.bukkit.potion.PotionEffectType.NIGHT_VISION;
import static org.bukkit.potion.PotionEffectType.SATURATION;

public class ArenaPlayerJoinListener implements Listener {

        private final ArenaManager arenaManager;
    private final BukkitAudiences audiences;

    @Inject
    public ArenaPlayerJoinListener(
                        @NotNull ArenaManager arenaManager,
                    @NotNull BukkitAudiences audiences
    ) {
                this.arenaManager = arenaManager;
        this.audiences = audiences;
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = MONITOR)
    public void onPlayerJoin(
            @NotNull PlayerJoinEvent event
    ) {

        event.setJoinMessage("");

        event.getPlayer().getActivePotionEffects()
                .stream()
                .map(PotionEffect::getType)
                .forEach(event.getPlayer()::removePotionEffect);

        event.getPlayer().getInventory().clear();
        event.getPlayer().setGameMode(ADVENTURE);
        event.getPlayer().addPotionEffect(new PotionEffect(SATURATION, MAX_VALUE, 1, false, false, false));
        event.getPlayer().addPotionEffect(new PotionEffect(NIGHT_VISION, MAX_VALUE, 1, false, false, false));

        this.arenaManager.recoverPlayerToHubIfNeeded(event.getPlayer(), true);

        this.audiences.player(event.getPlayer()).sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<gold>[DR]</gold> <gray>Use <white>/deathrun maps <gray>or queue signs to join a map."));

    }

}
