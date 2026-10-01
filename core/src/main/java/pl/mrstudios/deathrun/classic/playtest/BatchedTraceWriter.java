package pl.mrstudios.deathrun.classic.playtest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** One ordered writer, no Bukkit access on the worker thread. */
public final class BatchedTraceWriter implements AutoCloseable {
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "DeathRun-trace-writer");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<Path, StringBuilder> pending = new LinkedHashMap<>();
    private IOException failure;
    private int bufferedCharacters;
    private boolean closed;
    private final java.util.concurrent.ScheduledFuture<?> periodic;

    public BatchedTraceWriter() {
        periodic = executor.scheduleWithFixedDelay(this::writeBatch, 250L, 250L, TimeUnit.MILLISECONDS);
    }

    public synchronized void append(Path path, String line) throws IOException {
        if (closed) throw new IOException("Trace writer is closed");
        if (failure != null) throw new IOException("Trace writer failed", failure);
        if (bufferedCharacters + line.length() > 4_000_000)
            throw new IOException("Trace buffer limit exceeded");
        pending.computeIfAbsent(path, ignored -> new StringBuilder()).append(line);
        bufferedCharacters += line.length();
    }

    private void writeBatch() {
        Map<Path, StringBuilder> batch;
        synchronized (this) {
            if (failure != null || pending.isEmpty()) return;
            batch = new LinkedHashMap<>(pending);
            pending.clear();
            bufferedCharacters = 0;
        }
        try {
            for (var entry : batch.entrySet())
                Files.writeString(entry.getKey(), entry.getValue(), StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException exception) {
            synchronized (this) { failure = exception; }
        }
    }

    /** Administrative report/start/clear calls wait for all previously accepted lines. */
    public void flush() throws IOException {
        try {
            executor.submit(this::writeBatch).get(5L, TimeUnit.SECONDS);
            synchronized (this) {
                if (failure != null) throw new IOException("Trace writer failed", failure);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Trace flush interrupted", exception);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException
                 | java.util.concurrent.RejectedExecutionException exception) {
            throw new IOException("Trace flush failed", exception);
        }
    }

    @Override public void close() throws IOException {
        synchronized (this) { if (closed) return; closed = true; }
        periodic.cancel(false);
        try { flush(); }
        finally { executor.shutdown(); }
    }
}
