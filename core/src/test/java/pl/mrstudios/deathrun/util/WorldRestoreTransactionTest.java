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
    @Test void damagedEntryCrcFailsBeforeInstallation() throws Exception {
        Path world=world(), path=root.resolve("damaged.zip");
        byte[] data="region bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        CRC32 crc=new CRC32(); crc.update(data);
        try(var zip=new ZipOutputStream(Files.newOutputStream(path))) {
            ZipEntry entry=new ZipEntry("bee/region/r.0.0.mca");
            entry.setMethod(ZipEntry.STORED); entry.setSize(data.length); entry.setCrc(crc.getValue());
            zip.putNextEntry(entry); zip.write(data); zip.closeEntry();
        }
        byte[] bytes=Files.readAllBytes(path);
        for(int i=0;i<=bytes.length-data.length;i++) {
            if(ArraysEqual(bytes,i,data)) { bytes[i]^=1; break; }
        }
        Files.write(path,bytes);
        assertThrows(java.io.IOException.class,()->WorldRestoreTransaction.prepare(path,world));
        assertEquals("original world",Files.readString(world.resolve("original.txt")));
    }
    private boolean ArraysEqual(byte[] bytes,int start,byte[] data) {
        for(int i=0;i<data.length;i++) if(bytes[start+i]!=data[i]) return false;
        return true;
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
