package com.asphyxiamywife.elytrapitchhelper.config;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.UUID;

public interface ConfigFileSystem {
    Path configRoot();

    boolean exists(Path path, boolean noFollow);

    boolean isRegularFile(Path path, boolean noFollow);

    boolean isDirectory(Path path);

    boolean isSymbolicLink(Path path);

    Path readSymbolicLink(Path path) throws IOException;

    void createSymbolicLink(Path link, Path target) throws IOException;

    void createHardLink(Path link, Path existing) throws IOException;

    byte[] read(Path path) throws IOException;

    long modifiedAtMillis(Path path) throws IOException;

    BasicFileAttributes attributes(Path path) throws IOException;

    List<Path> list(Path directory) throws IOException;

    void createDirectories(Path directory) throws IOException;

    Path createTempDirectory(Path directory, String prefix) throws IOException;

    void writeDurable(Path writablePath, Path logicalPath, byte[] contents) throws IOException;

    void moveAtomic(Path source, Path target) throws IOException;

    void move(Path source, Path target) throws IOException;

    void fsync(Path directory) throws IOException;

    void delete(Path path) throws IOException;

    default void observeTemporary(Path temporary) {
    }

    default void writeAtomic(Path path, byte[] contents) throws IOException {
        createDirectories(path.getParent());
        Path temporary = path.resolveSibling(
                "eph-" + UUID.randomUUID() + ".tmp");
        observeTemporary(temporary);
        IOException originalFailure = null;
        try {
            writeDurable(temporary, path, contents);
            moveAtomic(temporary, path);
            ConfigSaveBatch.targetMutated(path);
            fsync(path.getParent());
        } catch (IOException failure) {
            originalFailure = failure;
            throw failure;
        } finally {
            try {
                delete(temporary);
            } catch (IOException cleanupFailure) {
                if (originalFailure != null) {
                    originalFailure.addSuppressed(cleanupFailure);
                } else {
                    org.slf4j.LoggerFactory.getLogger(ConfigFileSystem.class).warn(
                            "Could not clean temporary file {} after publishing {}",
                            temporary, path, cleanupFailure);
                }
            }
        }
    }
}
