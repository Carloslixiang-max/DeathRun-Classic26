package pl.mrstudios.deathrun.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.UUID;
import java.util.zip.ZipInputStream;

/** Stage and validate an archive without touching the current world; replace by rename. */
public final class WorldRestoreTransaction implements AutoCloseable {
    private final Path world;
    private final Path staging;
    private final Path prepared;
    private final Path previous;
    private boolean movedPrevious;
    private boolean installed;
    private boolean committed;

    private WorldRestoreTransaction(Path world, Path staging) {
        this.world = world;
        this.staging = staging;
        this.prepared = staging.resolve(world.getFileName());
        this.previous = world.resolveSibling(world.getFileName() + ".before-restore-" + UUID.randomUUID());
    }

    public static WorldRestoreTransaction prepare(Path archive, Path worldFolder) throws IOException {
        Path world = worldFolder.toAbsolutePath().normalize();
        if (!Files.isDirectory(world) || world.getParent() == null)
            throw new IOException("Current world directory is unavailable");
        Path staging = Files.createTempDirectory(world.getParent(), ".deathrun-restore-");
        WorldRestoreTransaction transaction = new WorldRestoreTransaction(world, staging);
        try {
            transaction.extract(archive);
            if (!Files.isDirectory(transaction.prepared.resolve("region")))
                throw new IOException("Backup has no expected world region directory");
            try (var files = Files.list(transaction.prepared.resolve("region"))) {
                if (files.noneMatch(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".mca")))
                    throw new IOException("Backup has no region files");
            }
            return transaction;
        } catch (IOException | RuntimeException failure) {
            try { transaction.close(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private void extract(Path archive) throws IOException {
        var seen = new HashSet<Path>();
        try (var zip = new ZipInputStream(Files.newInputStream(archive))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.contains("\\") || name.startsWith("/"))
                    throw new IOException("Invalid archive entry");
                Path target = staging.resolve(name).normalize();
                if (!target.startsWith(prepared) || !seen.add(target))
                    throw new IOException("Archive entry outside expected world or duplicate entry: " + name);
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zip, target);
                }
                zip.closeEntry(); // Reads/checks the CRC; truncated or corrupt entries fail before installation.
            }
        }
    }

    /** Caller must unload the world first. The previous directory is retained on success. */
    public void install() throws IOException {
        if (installed || movedPrevious) throw new IllegalStateException("Restore already installed");
        Files.move(world, previous, StandardCopyOption.ATOMIC_MOVE);
        movedPrevious = true;
        try {
            Files.move(prepared, world, StandardCopyOption.ATOMIC_MOVE);
            installed = true;
        } catch (IOException failure) {
            try { rollback(); } catch (IOException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            throw failure;
        }
    }

    public Path previousWorld() { return previous; }
    public void commit() {
        if (!installed) throw new IllegalStateException("World has not been installed");
        committed = true;
    }

    /** Caller must unload a newly loaded replacement world before rollback. */
    public void rollback() throws IOException {
        if (committed || !movedPrevious) return;
        if (installed && Files.exists(world)) {
            // Preserve even a failed replacement for diagnosis; never delete the only copy.
            Files.move(world, staging.resolve("failed-world"), StandardCopyOption.ATOMIC_MOVE);
        }
        Files.move(previous, world, StandardCopyOption.ATOMIC_MOVE);
        installed = false;
        movedPrevious = false;
    }

    @Override public void close() throws IOException {
        if (!committed) rollback();
        deleteStaging(staging);
    }

    private static void deleteStaging(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
}
