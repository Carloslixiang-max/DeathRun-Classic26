package pl.mrstudios.deathrun.classic.vote;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pl.mrstudios.deathrun.api.arena.enums.GameState;
import pl.mrstudios.deathrun.arena.ArenaManager;
import pl.mrstudios.deathrun.config.Configuration;
import pl.mrstudios.deathrun.config.impl.MapConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import static net.kyori.adventure.text.minimessage.MiniMessage.miniMessage;

public final class ClassicVoteService {

    public static final String RANDOM = "__random__";
    private static final int[] MAP_SLOTS = {10, 11, 12, 13, 14};
    private static final int RANDOM_SLOT = 16;

    private final Plugin plugin;
    private final Configuration configuration;
    private final ArenaManager arenaManager;
    private final NamespacedKey choiceKey;

    private final Map<UUID, String> votes = new ConcurrentHashMap<>();
    private final Set<UUID> participants = ConcurrentHashMap.newKeySet();
    private List<String> sessionCandidates = List.of();
    private BukkitTask countdownTask;
    private int secondsRemaining;

    public ClassicVoteService(
            @NotNull Plugin plugin,
            @NotNull Configuration configuration,
            @NotNull ArenaManager arenaManager
    ) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.arenaManager = arenaManager;
        this.choiceKey = new NamespacedKey(plugin, "classic_vote_choice");
    }

    public void open(@NotNull Player player) {
        if (this.arenaManager.runtimeForPlayer(player) != null) {
            player.sendMessage(miniMessage().deserialize(this.configuration.language().classicVoteInMatch));
            return;
        }

        List<MapConfiguration.MapDefinition> maps = this.countdownTask == null
                ? this.currentCandidates()
                : this.sessionCandidates.stream()
                        .map(id -> this.configuration.map().getMapById(id))
                        .filter(java.util.Objects::nonNull)
                        .toList();

        if (maps.isEmpty()) {
            player.sendMessage(miniMessage().deserialize(this.configuration.language().classicVoteNoMaps));
            return;
        }

        if (this.countdownTask == null)
            this.sessionCandidates = maps.stream().map(this::mapId).toList();

        VoteInventoryHolder holder = new VoteInventoryHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27, this.configuration.language().classicVoteTitle);
        holder.bind(inventory);
        for (int i = 0; i < maps.size() && i < MAP_SLOTS.length; i++)
            inventory.setItem(MAP_SLOTS[i], this.mapItem(maps.get(i)));
        inventory.setItem(RANDOM_SLOT, this.randomItem());
        player.openInventory(inventory);
    }

    public boolean isVoteInventory(@NotNull Inventory inventory) {
        return inventory.getHolder() instanceof VoteInventoryHolder;
    }

    public void handleClick(@NotNull Player player, @Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return;

        String choice = item.getItemMeta().getPersistentDataContainer().get(this.choiceKey, PersistentDataType.STRING);
        if (choice == null)
            return;

        if (this.arenaManager.runtimeForPlayer(player) != null) {
            player.closeInventory();
            player.sendMessage(miniMessage().deserialize(this.configuration.language().classicVoteInMatch));
            return;
        }

        if (this.countdownTask == null) {
            List<MapConfiguration.MapDefinition> candidates = this.currentCandidates();
            if (candidates.isEmpty()) {
                player.closeInventory();
                player.sendMessage(miniMessage().deserialize(this.configuration.language().classicVoteNoMaps));
                return;
            }
            this.sessionCandidates = candidates.stream().map(this::mapId).toList();
        }

        if (!RANDOM.equals(choice) && !this.sessionCandidates.contains(choice))
            return;

        this.participants.add(player.getUniqueId());
        this.votes.put(player.getUniqueId(), choice);

        String displayChoice = RANDOM.equals(choice)
                ? "Random"
                : this.displayName(this.configuration.map().getMapById(choice));
        player.sendMessage(miniMessage().deserialize(
                this.configuration.language().classicVoteRecorded
                        .replace("<choice>", this.sanitize(displayChoice))
        ));
        player.closeInventory();

        if (this.countdownTask == null)
            this.startCountdown();
    }

    public void leave(@NotNull Player player, boolean notify) {
        this.votes.remove(player.getUniqueId());
        this.participants.remove(player.getUniqueId());
        if (notify)
            player.sendMessage(miniMessage().deserialize(this.configuration.language().classicVoteLeft));

        if (this.participants.isEmpty())
            this.resetSession();
    }

    public void shutdown() {
        this.resetSession();
    }

    public int votesFor(@NotNull String choice) {
        return (int) this.votes.values().stream().filter(choice::equals).count();
    }

    private void startCountdown() {
        this.secondsRemaining = Math.max(5, this.configuration.plugin().classicVoteSeconds);
        this.countdownTask = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            if (this.participants.isEmpty()) {
                this.resetSession();
                return;
            }

            if (this.secondsRemaining <= 0) {
                this.finishVote();
                return;
            }

            if (this.secondsRemaining <= 5 || this.secondsRemaining % 5 == 0)
                this.broadcastCountdown();

            this.secondsRemaining--;
        }, 0L, 20L);
    }

    private void broadcastCountdown() {
        Component message = miniMessage().deserialize(
                this.configuration.language().classicVoteCountdown
                        .replace("<seconds>", String.valueOf(this.secondsRemaining))
        );
        for (UUID uuid : this.participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline())
                player.sendActionBar(message);
        }
    }

    private void finishVote() {
        List<MapConfiguration.MapDefinition> candidates = this.sessionCandidates.stream()
                .map(id -> this.configuration.map().getMapById(id))
                .filter(java.util.Objects::nonNull)
                .filter(this::isEligible)
                .toList();

        if (candidates.isEmpty()) {
            this.forEachParticipant(player ->
                    player.sendMessage(miniMessage().deserialize(this.configuration.language().classicVoteNoMaps)));
            this.resetSession();
            return;
        }

        String winnerId = this.chooseWinner(candidates);
        MapConfiguration.MapDefinition winner = this.configuration.map().getMapById(winnerId);
        if (winner == null) {
            this.resetSession();
            return;
        }

        Component selectedMessage = miniMessage().deserialize(
                this.configuration.language().classicVoteWinner
                        .replace("<map>", this.sanitize(this.displayName(winner)))
                        .replace("<creator>", this.sanitize(this.creator(winner)))
        );

        List<UUID> voters = new ArrayList<>(this.participants);
        this.resetSession();

        for (UUID uuid : voters) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline() || this.arenaManager.runtimeForPlayer(player) != null)
                continue;

            player.sendMessage(selectedMessage);
            ArenaManager.JoinResult result = this.arenaManager.joinMap(player, winnerId);
            if (result != ArenaManager.JoinResult.JOINED && result != ArenaManager.JoinResult.ALREADY_IN_MAP)
                player.sendMessage(miniMessage().deserialize(
                        "<red>Could not join selected map: <white>" + result.name()
                ));
        }
    }

    private @NotNull String chooseWinner(@NotNull List<MapConfiguration.MapDefinition> candidates) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (MapConfiguration.MapDefinition map : candidates)
            counts.put(this.mapId(map), this.votesFor(this.mapId(map)));
        counts.put(RANDOM, this.votesFor(RANDOM));

        int best = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<String> top = counts.entrySet().stream()
                .filter(entry -> entry.getValue() == best)
                .map(Map.Entry::getKey)
                .toList();

        String selected = top.get(ThreadLocalRandom.current().nextInt(top.size()));
        if (!RANDOM.equals(selected))
            return selected;

        return this.mapId(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())));
    }

    private @NotNull List<MapConfiguration.MapDefinition> currentCandidates() {
        int max = Math.max(1, Math.min(5, this.configuration.plugin().classicVoteMapCandidates));
        List<String> configured = this.configuration.plugin().classicVoteMapIds == null
                ? List.of()
                : this.configuration.plugin().classicVoteMapIds.stream()
                        .filter(java.util.Objects::nonNull)
                        .map(String::trim)
                        .filter(id -> !id.isBlank())
                        .toList();

        if (!configured.isEmpty()) {
            LinkedHashMap<String, MapConfiguration.MapDefinition> ordered = new LinkedHashMap<>();
            for (String id : configured) {
                MapConfiguration.MapDefinition map = this.configuration.map().getMapById(id);
                if (map == null || !this.isEligible(map))
                    continue;
                ordered.putIfAbsent(this.mapId(map), map);
                if (ordered.size() >= max)
                    break;
            }
            return List.copyOf(ordered.values());
        }

        return this.configuration.map().resolvedMaps().stream()
                .filter(this::isEligible)
                .sorted(Comparator.comparing(this::displayName, String.CASE_INSENSITIVE_ORDER))
                .limit(max)
                .toList();
    }

    private boolean isEligible(@NotNull MapConfiguration.MapDefinition map) {
        if (!this.arenaManager.isMapConfigured(map))
            return false;

        String id = this.mapId(map);
        if (this.arenaManager.isMapLockedForEditing(id))
            return false;

        ArenaManager.ArenaRuntime runtime = this.arenaManager.runtimeByMapId(id);
        return runtime != null && runtime.arena().getGameState() == GameState.WAITING;
    }

    private @NotNull ItemStack mapItem(@NotNull MapConfiguration.MapDefinition map) {
        ItemStack item = new ItemStack(Material.MAP);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(miniMessage().deserialize("<gold>" + this.sanitize(this.displayName(map))));

        String id = this.mapId(map);
        meta.lore(this.configuration.language().classicVoteMapLore.stream()
                .map(line -> line
                        .replace("<creator>", this.sanitize(this.creator(map)))
                        .replace("<votes>", String.valueOf(this.votesFor(id))))
                .map(miniMessage()::deserialize)
                .toList());
        meta.getPersistentDataContainer().set(this.choiceKey, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    private @NotNull ItemStack randomItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(miniMessage().deserialize(this.configuration.language().classicVoteRandomName));
        meta.lore(this.configuration.language().classicVoteRandomLore.stream()
                .map(line -> line.replace("<votes>", String.valueOf(this.votesFor(RANDOM))))
                .map(miniMessage()::deserialize)
                .toList());
        meta.getPersistentDataContainer().set(this.choiceKey, PersistentDataType.STRING, RANDOM);
        item.setItemMeta(meta);
        return item;
    }

    private @NotNull String mapId(@NotNull MapConfiguration.MapDefinition map) {
        return this.configuration.map().normalizedMapId(
                map.id == null || map.id.isBlank() ? map.name : map.id
        );
    }

    private @NotNull String displayName(@Nullable MapConfiguration.MapDefinition map) {
        if (map == null || map.name == null || map.name.isBlank())
            return "Unnamed";
        return map.name;
    }

    private @NotNull String creator(@NotNull MapConfiguration.MapDefinition map) {
        return map.creator == null || map.creator.isBlank() ? "Unknown" : map.creator;
    }

    private @NotNull String sanitize(@NotNull String input) {
        return input.replace("<", "").replace(">", "");
    }

    private void forEachParticipant(java.util.function.Consumer<Player> action) {
        for (UUID uuid : this.participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline())
                action.accept(player);
        }
    }

    private void resetSession() {
        if (this.countdownTask != null) {
            this.countdownTask.cancel();
            this.countdownTask = null;
        }
        this.votes.clear();
        this.participants.clear();
        this.sessionCandidates = List.of();
        this.secondsRemaining = 0;
    }

    private static final class VoteInventoryHolder implements InventoryHolder {
        private Inventory inventory;

        private void bind(@NotNull Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return java.util.Objects.requireNonNull(this.inventory, "vote inventory not bound");
        }
    }
}
