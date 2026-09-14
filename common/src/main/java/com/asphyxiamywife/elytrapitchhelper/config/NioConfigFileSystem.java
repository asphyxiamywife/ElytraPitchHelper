package com.asphyxiamywife.elytrapitchhelper.config;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NioConfigFileSystem implements ConfigFileSystem {
    private final Path configRoot;
    private final Map<String, Boolean> unsupportedDirectorySync = new ConcurrentHashMap<>();

    private NioConfigFileSystem(Path configRoot) {
        this.configRoot = configRoot.toAbsolutePath().normalize();
    }

    public static NioConfigFileSystem rootedAt(Path configRoot) {
        return new NioConfigFileSystem(configRoot);
    }

    @Override
    public Path configRoot() {
        return configRoot;
    }

    @Override
    public boolean exists(Path path, boolean noFollow) {
        return noFollow
                ? Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                : Files.exists(path);
    }

    @Override
    public boolean isRegularFile(Path path, boolean noFollow) {
        return noFollow
                ? Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                : Files.isRegularFile(path);
    }

    @Override
    public boolean isDirectory(Path path) {
        return Files.isDirectory(path);
    }

    @Override
    public boolean isSymbolicLink(Path path) {
        return Files.isSymbolicLink(path);
    }

    @Override
    public Path readSymbolicLink(Path path) throws IOException {
        return Files.readSymbolicLink(path);
    }

    @Override
    public void createSymbolicLink(Path link, Path target) throws IOException {
        Files.createSymbolicLink(link, target);
    }

    @Override
    public void createHardLink(Path link, Path existing) throws IOException {
        try {
            Files.createLink(link, existing);
        } catch (UnsupportedOperationException failure) {
            throw new IOException("Filesystem cannot safely restore a deleted config file: " + link, failure);
        }
    }

    @Override
    public byte[] read(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) {
            byte[] contents = input.readNBytes(ConfigInputLimits.MAX_FILE_BYTES + 1);
            if (contents.length > ConfigInputLimits.MAX_FILE_BYTES) {
                throw new IOException("Config file exceeds " + ConfigInputLimits.MAX_FILE_BYTES + " bytes: " + path);
            }
            return contents;
        }
    }

    @Override
    public long modifiedAtMillis(Path path) throws IOException {
        return Files.getLastModifiedTime(path).toMillis();
    }

    @Override
    public BasicFileAttributes attributes(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class);
    }

    @Override
    public List<Path> list(Path directory) throws IOException {
        try (var paths = Files.list(directory)) {
            return paths.toList();
        }
    }

    @Override
    public void createDirectories(Path directory) throws IOException {
        if (directory != null) {
            Files.createDirectories(directory);
        }
    }

    @Override
    public Path createTempDirectory(Path directory, String prefix) throws IOException {
        return Files.createTempDirectory(directory, prefix);
    }

    @Override
    public void writeDurable(Path writablePath, Path logicalPath, byte[] contents) throws IOException {
        createDirectories(writablePath.getParent());
        try (FileChannel channel = FileChannel.open(writablePath,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteBuffer remaining = ByteBuffer.wrap(contents);
            while (remaining.hasRemaining()) {
                channel.write(remaining);
            }
            channel.force(true);
        }
    }

    @Override
    public void moveAtomic(Path source, Path target) throws IOException {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    }

    @Override
    public void move(Path source, Path target) throws IOException {
        Files.move(source, target);
    }

    @Override
    public void fsync(Path directory) throws IOException {
        if (directory == null) {
            return;
        }
        String capabilityKey = directory.getFileSystem().provider().getClass().getName();
        if (unsupportedDirectorySync.containsKey(capabilityKey) || knownUnsupportedProvider(directory)) {
            unsupportedDirectorySync.put(capabilityKey, true);
            return;
        }
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (UnsupportedOperationException failure) {
            unsupportedDirectorySync.put(capabilityKey, true);
        } catch (IOException failure) {
            if (!knownUnsupportedFailure(directory, failure)) {
                throw failure;
            }
            unsupportedDirectorySync.put(capabilityKey, true);
        }
    }

    @Override
    public void delete(Path path) throws IOException {
        Files.deleteIfExists(path);
    }

    private static boolean knownUnsupportedProvider(Path directory) {
        return isWindowsProvider(directory)
                && System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    private static boolean knownUnsupportedFailure(Path directory, IOException failure) {
        if (!isWindowsProvider(directory)) {
            return false;
        }
        if (failure instanceof AccessDeniedException) {
            return true;
        }
        if (failure instanceof FileSystemException fileSystemFailure) {
            String reason = fileSystemFailure.getReason();
            if (reason != null) {
                String normalized = reason.toLowerCase(Locale.ROOT);
                return normalized.contains("access is denied")
                        || normalized.contains("incorrect function")
                        || normalized.contains("is a directory");
            }
        }
        return false;
    }

    private static boolean isWindowsProvider(Path directory) {
        return directory.getFileSystem().provider().getClass().getName().contains("WindowsFileSystemProvider");
    }
}
