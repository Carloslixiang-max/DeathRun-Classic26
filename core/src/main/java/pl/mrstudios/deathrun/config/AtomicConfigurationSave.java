package pl.mrstudios.deathrun.config;

import eu.okaeri.configs.OkaeriConfig;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;

public final class AtomicConfigurationSave {
    private AtomicConfigurationSave() {}
    public static void save(OkaeriConfig config) throws IOException {
        Path target = config.getBindFile();
        if (target == null) throw new IOException("Configuration is not bound to a file");
        Path staged = Files.createTempFile(target.toAbsolutePath().getParent(), ".deathrun-config-", ".tmp");
        try {
            config.setBindFile(staged);
            config.save();
            try (FileChannel channel = FileChannel.open(staged, StandardOpenOption.WRITE)) { channel.force(true); }
            Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            config.setBindFile(target);
            Files.deleteIfExists(staged);
        }
    }
    public static void restore(Path target, byte[] content) throws IOException {
        Path staged = Files.createTempFile(target.toAbsolutePath().getParent(), ".deathrun-config-", ".tmp");
        try {
            Files.write(staged, content);
            try (FileChannel channel = FileChannel.open(staged, StandardOpenOption.WRITE)) { channel.force(true); }
            Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(staged); }
    }
}
