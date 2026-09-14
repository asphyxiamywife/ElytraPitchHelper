package com.asphyxiamywife.elytrapitchhelper.config;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.locks.ReentrantLock;


final class ConfigSaveLock implements AutoCloseable {
    private static final String LOCK_FILE_NAME = "config-save.lock";
    private static final ReentrantLock PROCESS_LOCK = new ReentrantLock();

    private final FileChannel channel;
    private final FileLock lock;

    private ConfigSaveLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    static ConfigSaveLock acquire(ConfigFileSystem fileSystem) {
        if (PROCESS_LOCK.isHeldByCurrentThread()) {
            throw new ConfigSaveException("A reentrant save is already active on this thread");
        }
        if (!PROCESS_LOCK.tryLock()) {
            throw new ConfigSaveException("Another save is already active in this process", true);
        }
        Path internalDirectory = Config.getConfigDirectory(fileSystem).resolve(".internal");
        FileChannel channel = null;
        try {
            Files.createDirectories(internalDirectory);
            channel = FileChannel.open(internalDirectory.resolve(LOCK_FILE_NAME),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException e) {
                throw new ConfigSaveException("Another game instance is saving this config directory", e, true);
            }
            if (lock == null) {
                throw new ConfigSaveException("Another game instance is saving this config directory", true);
            }
            return new ConfigSaveLock(channel, lock);
        } catch (IOException | RuntimeException failure) {
            closeQuietly(channel);
            PROCESS_LOCK.unlock();
            if (failure instanceof ConfigSaveException saveFailure) {
                throw saveFailure;
            }
            throw new ConfigSaveException("Failed to lock the config directory for saving", failure);
        }
    }

    static Path lockPathForTests(ConfigFileSystem fileSystem) {
        return Config.getConfigDirectory(fileSystem).resolve(".internal").resolve(LOCK_FILE_NAME);
    }

    @Override
    public void close() {
        try {
            if (lock.isValid()) {
                lock.release();
            }
        } catch (IOException ignored) {
        }
        closeQuietly(channel);
        PROCESS_LOCK.unlock();
    }

    private static void closeQuietly(FileChannel channel) {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException ignored) {
            }
        }
    }
}
