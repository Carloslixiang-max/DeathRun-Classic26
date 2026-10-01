package pl.mrstudios.deathrun.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldRestoreTransactionTest {
    @TempDir Path root;
    private Path world() throws Exception {
        Path world = Files.createDirectories(root.resolve("bee"));
        Files.writeString(world.resolve("original.txt"), "original world");
        return world;
    }
    private Path archive(String entry) throws Exception {
        Path path = root.resolve("backup.zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry(entry));
            zip.write("restored region".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return path;
    }
    @Test void corruptArchiveLeavesOriginalUntouched() throws Exception {
        Path world = world();
        Path archive = Files.writeString(root.resolve("bad.zip"), "not a zip");
        assertThrows(java.io.IOException.class, () -> WorldRestoreTransaction.prepare(archive, world));
        assertEquals("original world", Files.readString(world.resolve("original.txt")));
    }
    @Test void traversalCannotWriteOutsideStaging() throws Exception {
        Path world = world(); Path archive = archive("bee/../../escaped.txt");
        assertThrows(java.io.IOException.class, () -> WorldRestoreTransaction.prepare(archive, world));
        assertFalse(Files.exists(root.resolve("escaped.txt")));
        assertTrue(Files.exists(world.resolve("original.txt")));
    }
    @Test void wrongRootFolderIsRejectedBeforeWorldIsMoved() throws Exception {
        Path world = world(); Path archive = archive("wrong/region/r.0.0.mca");
        assertThrows(java.io.IOException.class, () -> WorldRestoreTransaction.prepare(archive, world));
        assertTrue(Files.exists(world.resolve("original.txt")));
    }
    @Test void failedReloadRollsBackOriginalDirectory() throws Exception {
        Path world = world();
        try (var restore = WorldRestoreTransaction.prepare(archive("bee/region/r.0.0.mca"), world)) {
            restore.install();
            assertTrue(Files.exists(world.resolve("region/r.0.0.mca")));
            // A failure before commit (e.g. createWorld/config save) rolls back on close.
        }
        assertEquals("original world", Files.readString(world.resolve("original.txt")));
        assertFalse(Files.exists(world.resolve("region/r.0.0.mca")));
    }
    @Test void successRetainsPreviousWorldAsSeparateCopy() throws Exception {
        Path world = world(); Path previous;
        try (var restore = WorldRestoreTransaction.prepare(archive("bee/region/r.0.0.mca"), world)) {
            restore.install(); previous = restore.previousWorld(); restore.commit();
        }
        assertEquals("original world", Files.readString(previous.resolve("original.txt")));
        assertEquals("restored region", Files.readString(world.resolve("region/r.0.0.mca")));
    }
}
