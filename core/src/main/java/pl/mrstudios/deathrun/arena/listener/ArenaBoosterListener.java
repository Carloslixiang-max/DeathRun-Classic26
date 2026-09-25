package pl.mrstudios.deathrun.arena.listener;

import org.bukkit.Server;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.bukkit.item.ItemBuilder;
import pl.mrstudios.commons.inject.annotation.Inject;
import pl.mrstudios.deathrun.api.arena.booster.IBooster;
import pl.mrstudios.deathrun.api.arena.event.user.UserArenaUseBoosterEvent;
import pl.mrstudios.deathrun.api.arena.user.IUser;
import pl.mrstudios.deathrun.arena.Arena;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.config.Configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.Math.toRadians;
import static java.lang.String.valueOf;
import static java.lang.System.currentTimeMillis;
import static java.util.Objects.requireNonNull;
import static net.kyori.adventure.text.minimessage.MiniMessage.miniMessage;
import static org.bukkit.event.EventPriority.MONITOR;
import static org.bukkit.event.block.Action.RIGHT_CLICK_AIR;
import static org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK;
import static org.bukkit.inventory.ItemFlag.values;
import static pl.mrstudios.deathrun.api.arena.enums.GameState.PLAYING;
import static pl.mrstudios.deathrun.api.arena.user.enums.Role.RUNNER;

public class ArenaBoosterListener implements Listener {

    private final ArenaManager arenaManager;
    private final Plugin plugin;
    private final Server server;
    private final Configuration configuration;

    protected final Map<String, Map<IBooster, Long>> delay = new HashMap<>();

    @Inject
    public ArenaBoosterListener(
            @NotNull ArenaManager arenaManager,
            @NotNull Plugin plugin,
            @NotNull Server server,
            @NotNull Configuration configuration
    ) {
        this.arenaManager = arenaManager;
        this.plugin = plugin;
        this.server = server;
        this.configuration = configuration;
    }

    @EventHandler(priority = MONITOR)
    public void onItemUse(
            @NotNull PlayerInteractEvent event
    ) {

        if (event.getAction() != RIGHT_CLICK_AIR && event.getAction() != RIGHT_CLICK_BLOCK)
            return;

        ItemStack usedItem = this.resolveUsedItem(event);
        if (usedItem == null || usedItem.getType() == Material.AIR)
            return;

        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            ItemStack mainHand = event.getPlayer().getInventory().getItemInMainHand();
            if (mainHand != null && mainHand.getType() != Material.AIR)
                return;
        }

        Material usedType = usedItem.getType();

        this.configuration.plugin().boosters
                .stream()
                .filter((booster) -> booster.item().material() == usedType)
                .filter((booster) -> booster.slot() == event.getPlayer().getInventory().getHeldItemSlot())
                .findFirst().ifPresent((booster) -> {

                    Arena arena = this.arenaManager.arenaForPlayer(event.getPlayer());
                    if (arena == null)
                        return;

                    if (arena.getGameState() != PLAYING)
                        return;

                    IUser user = arena.getUser(event.getPlayer());

                    if (user == null)
                        return;

                    event.setCancelled(true);

                    if (!this.delay.containsKey(event.getPlayer().getName()))
                        this.delay.put(event.getPlayer().getName(), new HashMap<>());

                    if (!this.delay.get(event.getPlayer().getName()).containsKey(booster))
                        this.delay.get(event.getPlayer().getName()).put(booster, 0L);

                    if (this.delay.getOrDefault(event.getPlayer().getName(), new HashMap<>()).getOrDefault(booster, 0L) > currentTimeMillis())
                        return;

                    AtomicReference<Integer> taskId = new AtomicReference<>(-1);

                    this.boost(event.getPlayer(), booster);
                    this.delay.get(event.getPlayer().getName()).put(booster, currentTimeMillis() + (booster.delay() * 1000L));
                    if (booster.sound() != null)
                        event.getPlayer().playSound(event.getPlayer().getLocation(), booster.sound(), 1.0f, 1.0f);

                    this.server.getPluginManager().callEvent(new UserArenaUseBoosterEvent(user, arena, booster));

                    taskId.set(
                            this.server.getScheduler().scheduleSyncRepeatingTask(this.plugin, () -> {

                                IUser currentUser = arena.getUser(event.getPlayer());
                                if (arena.getGameState() != PLAYING || currentUser == null || currentUser.getRole() != RUNNER) {
                                    if (taskId.get() != -1)
                                        this.server.getScheduler().cancelTask(taskId.get());
                                    return;

                                }

                                long expiresAt = this.delay.get(event.getPlayer().getName()).get(booster);
                                long remainingMillis = expiresAt - currentTimeMillis();
                                int boosterDelay = (int) Math.ceil(remainingMillis / 1000.0d);

                                if (boosterDelay <= 0) {
                                    event.getPlayer().getInventory().setItem(
                                            booster.slot(),
                                            new ItemBuilder(booster.item().material())
                                                    .name(miniMessage().deserialize(booster.item().name()))
                                                    .texture((booster.item().texture() != null) ? requireNonNull(booster.item().texture()) : "")
                                                    .itemFlags(values())
                                                    .build()
                                    );

                                    if (taskId.get() != -1)
                                        this.server.getScheduler().cancelTask(taskId.get());

                                    return;
                                }

                                event.getPlayer().getInventory().setItem(
                                        booster.slot(),
                                        new ItemBuilder(booster.delayItem().material(), Math.max(1, boosterDelay))
                                                .name(miniMessage().deserialize(booster.delayItem().name().replace("<delay>", valueOf(boosterDelay))))
                                                .texture((booster.delayItem().texture() != null) ? requireNonNull(booster.delayItem().texture()) : "")
                                                .itemFlags(values())
                                                .build()
                                );

                            }, 0L, 20L)
                    );

                });

    }

    private ItemStack resolveUsedItem(
            @NotNull PlayerInteractEvent event
    ) {
        if (event.getItem() != null)
            return event.getItem();

        if (event.getHand() == EquipmentSlot.OFF_HAND)
            return event.getPlayer().getInventory().getItemInOffHand();

        return event.getPlayer().getInventory().getItemInMainHand();
    }

    protected void boost(
            @NotNull Player player,
            @NotNull IBooster booster
    ) {

        switch (booster.direction()) {

            case FORWARD ->
                player.setVelocity(player.getLocation().getDirection().multiply(booster.power()).setY(0.25));

            case BACKWARD ->
                player.setVelocity(player.getLocation().getDirection().multiply(-booster.power()).setY(0.25));

            case LEFT ->
                player.setVelocity(player.getLocation().getDirection().multiply(booster.power()).rotateAroundY(toRadians(90)).setY(0.25));

            case RIGHT ->
                player.setVelocity(player.getLocation().getDirection().multiply(booster.power()).rotateAroundY(toRadians(-90)).setY(0.25));

        }

    }


}
