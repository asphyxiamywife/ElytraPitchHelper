package com.asphyxiamywife.elytrapitchhelper.config;

import static com.asphyxiamywife.elytrapitchhelper.config.ConfigTestFixtures.fs;

import com.asphyxiamywife.elytrapitchhelper.testing.IsolatedConfigRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TimestampedBackupStoreTest {
    @IsolatedConfigRoot
    Path configRoot;

    @Test
    void aNewBackupSortsAfterEveryBackupAlreadyPresent() throws IOException {
        Path directory = configRoot.resolve("backups");
        TimestampedBackupStore store = new TimestampedBackupStore(64);
        Files.createDirectories(directory);

        for (int i = 0; i < 12; i++) {
            store.saveBytes(fs(), directory, ("backup " + i).getBytes(StandardCharsets.UTF_8));
        }

        List<Path> saved = fs().list(directory).stream().sorted(TimestampedBackupStore.ORDER).toList();
        assertEquals(12, saved.size());
        for (int i = 0; i < saved.size(); i++) {
            assertEquals("backup " + i, Files.readString(saved.get(i), StandardCharsets.UTF_8),
                    "backups are not in the order they were written");
        }
    }

    @Test
    void rotationDoesNotFreeASequenceNumberForReuse() throws IOException {
        Path directory = configRoot.resolve("rotating");
        TimestampedBackupStore store = new TimestampedBackupStore(3);
        Files.createDirectories(directory);
        String timestamp = "29991231-235959-999";
        for (int sequence = 1; sequence <= 3; sequence++) {
            Files.write(directory.resolve(TimestampedBackupStore.fileName(timestamp, sequence)),
                    ("planted " + sequence).getBytes(StandardCharsets.UTF_8));
        }

        store.saveBytes(fs(), directory, "first after".getBytes(StandardCharsets.UTF_8));
        store.saveBytes(fs(), directory, "second after".getBytes(StandardCharsets.UTF_8));

        assertEquals("second after",
                Files.readString(TimestampedBackupStore.latest(fs(), directory), StandardCharsets.UTF_8));
        assertEquals(3, fs().list(directory).size());
        assertTrue(Files.exists(directory.resolve(TimestampedBackupStore.fileName(timestamp, 5L))),
                "the newest save did not continue past the highest sequence on disk");
    }

    @Test
    void collisionAdvancesToTheFollowingSequence() throws IOException {
        Path directory = configRoot.resolve("colliding");
        ConfigFileSystem collisionFileSystem = (ConfigFileSystem) java.lang.reflect.Proxy.newProxyInstance(
                ConfigFileSystem.class.getClassLoader(), new Class<?>[] {ConfigFileSystem.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("list")) {
                        return List.of();
                    }
                    if (method.getName().equals("exists")) {
                        return ((Path) arguments[0]).getFileName().toString().endsWith("~000001.json");
                    }
                    return method.invoke(fs(), arguments);
                });
        TimestampedBackupStore store = new TimestampedBackupStore(64);
        Instant now = Instant.parse("2026-01-02T03:04:05.006Z");

        String name = store.nextFileName(collisionFileSystem, directory, now);

        assertEquals(TimestampedBackupStore.fileName("20260102-030405-006", 2L), name);
    }

    @Test
    void aBackwardClockStepStillProducesANewestBackup() throws IOException {
        Path directory = configRoot.resolve("rolled-back");
        TimestampedBackupStore store = new TimestampedBackupStore(64);
        Files.createDirectories(directory);
        Path fromTheFuture = directory.resolve(TimestampedBackupStore.fileName("29991231-235959-999", 4L));
        Files.write(fromTheFuture, "written before the clock was corrected".getBytes(StandardCharsets.UTF_8));

        store.saveBytes(fs(), directory, "written after".getBytes(StandardCharsets.UTF_8));

        Path newest = TimestampedBackupStore.latest(fs(), directory);
        assertEquals("written after", Files.readString(newest, StandardCharsets.UTF_8));
        assertTrue(TimestampedBackupStore.compareFileNames(
                newest.getFileName().toString(), fromTheFuture.getFileName().toString()) > 0);
    }
}
