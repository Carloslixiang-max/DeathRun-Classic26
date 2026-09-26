package pl.mrstudios.deathrun.classic.playtest;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import pl.mrstudios.deathrun.api.arena.IArena;
import pl.mrstudios.deathrun.api.arena.user.IUser;
import pl.mrstudios.deathrun.arena.ArenaManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.deleteIfExists;
import static java.nio.file.Files.writeString;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

public final class PlaytestTraceService {

    private final Plugin plugin;
    private final ArenaManager arenaManager;
    private final Path researchDirectory;
    private final Set<String> enabledMaps = new HashSet<>();
    private final Map<String, Integer> lineCounts = new HashMap<>();

    public PlaytestTraceService(
            @NotNull Plugin plugin,
            @NotNull ArenaManager arenaManager
    ) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.researchDirectory = plugin.getDataFolder().toPath().resolve("research");
    }

    public synchronized @NotNull Path start(@NotNull String mapId) throws IOException {
        String normalized = this.normalize(mapId);
        createDirectories(this.researchDirectory);
        Path path = this.reportPath(normalized);

        String header = "# DeathRun Classic26 playtest trace" + System.lineSeparator()
                + "# map=" + normalized + System.lineSeparator()
                + "# started=" + Instant.now() + System.lineSeparator();
        writeString(path, header, StandardCharsets.UTF_8, CREATE, TRUNCATE_EXISTING);

        this.enabledMaps.add(normalized);
        this.lineCounts.put(normalized, 3);
        this.record(normalized, "TRACE_START", "enabled=true");
        return path;
    }

    public synchronized @NotNull TraceStatus stop(@NotNull String mapId) {
        String normalized = this.normalize(mapId);
        if (this.enabledMaps.contains(normalized))
            this.record(normalized, "TRACE_STOP", "enabled=false");
        this.enabledMaps.remove(normalized);
        return this.status(normalized);
    }

    public synchronized @NotNull TraceStatus clear(@NotNull String mapId) throws IOException {
        String normalized = this.normalize(mapId);
        this.enabledMaps.remove(normalized);
        this.lineCounts.remove(normalized);
        deleteIfExists(this.reportPath(normalized));
        return this.status(normalized);
    }

    public synchronized @NotNull TraceStatus status(@NotNull String mapId) {
        String normalized = this.normalize(mapId);
        return new TraceStatus(
                this.enabledMaps.contains(normalized),
                this.lineCounts.getOrDefault(normalized, 0),
                this.reportPath(normalized)
        );
    }

    public synchronized void record(
            @NotNull String mapId,
            @NotNull String event,
            @Nullable String details
    ) {
        String normalized = this.normalize(mapId);
        if (!this.enabledMaps.contains(normalized))
            return;

        try {
            createDirectories(this.researchDirectory);
            String line = Instant.now()
                    + " | " + this.singleLine(event)
                    + " | " + this.singleLine(details == null ? "" : details)
                    + System.lineSeparator();
            writeString(
                    this.reportPath(normalized),
                    line,
                    StandardCharsets.UTF_8,
                    CREATE,
                    APPEND
            );
            this.lineCounts.merge(normalized, 1, Integer::sum);
        } catch (IOException exception) {
            this.plugin.getLogger().warning(
                    "[DeathRun] Unable to write playtest trace for "
                            + normalized + ": " + exception.getMessage()
            );
        }
    }

    public void recordArena(
            @NotNull IArena arena,
            @NotNull String event,
            @Nullable String details
    ) {
        String mapId = this.mapIdForArena(arena);
        if (mapId != null)
            this.record(mapId, event, details);
    }

    public void recordUser(
            @NotNull IUser user,
            @NotNull String event,
            @Nullable String details
    ) {
        String mapId = this.mapIdForUser(user);
        if (mapId != null)
            this.record(mapId, event, details);
    }

    public @Nullable String mapIdForArena(@NotNull IArena arena) {
        return this.arenaManager.runtimes().stream()
                .filter(runtime -> runtime.arena() == arena)
                .map(ArenaManager.ArenaRuntime::mapId)
                .findFirst()
                .orElse(null);
    }

    public @Nullable String mapIdForUser(@NotNull IUser user) {
        Player player = user.asBukkit();
        if (player != null) {
            ArenaManager.ArenaRuntime runtime = this.arenaManager.runtimeForPlayer(player);
            if (runtime != null)
                return runtime.mapId();
        }

        return this.arenaManager.runtimes().stream()
                .filter(runtime -> runtime.arena().getUser(user.getUniqueId()) != null)
                .map(ArenaManager.ArenaRuntime::mapId)
                .findFirst()
                .orElse(null);
    }

    public @NotNull Path reportPath(@NotNull String mapId) {
        return this.researchDirectory.resolve(this.normalize(mapId) + "-playtest-trace.log");
    }

    public synchronized void shutdown() {
        for (String mapId : Set.copyOf(this.enabledMaps)) {
            this.record(mapId, "SERVER_DISABLE", "trace closed by plugin shutdown");
            this.enabledMaps.remove(mapId);
        }
    }

    private @NotNull String normalize(@NotNull String mapId) {
        String normalized = mapId.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-");
        return normalized.isBlank() ? "unknown-map" : normalized;
    }

    private @NotNull String singleLine(@NotNull String value) {
        return value
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .trim();
    }

    public record TraceStatus(
            boolean enabled,
            int lines,
            @NotNull Path path
    ) {}
}
