package com.asphyxiamywife.elytrapitchhelper.config;

import static com.asphyxiamywife.elytrapitchhelper.config.ConfigTestFixtures.fs;

import com.asphyxiamywife.elytrapitchhelper.testing.IsolatedConfigRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfigReloadIsolationTest {
    @IsolatedConfigRoot
    Path configRoot;

    @Test
    void reloadLeavesAnInFlightWritesTemporaryFileAlone() throws IOException {
        Path profileDirectory = Config.getProfileDirectory(fs());
        Files.createDirectories(profileDirectory);
        Path inFlightTemp = profileDirectory.resolve("default.json.d3adb33f.tmp");
        Files.writeString(inFlightTemp, "half written", StandardCharsets.UTF_8);

        Config.reloadFromDisk(fs());

        assertTrue(Files.exists(inFlightTemp),
                "reload must not sweep temporary files out from under a concurrent save");
    }

    @Test
    void startupStillSweepsStrayTemporaryFiles() throws IOException {
        Path profileDirectory = Config.getProfileDirectory(fs());
        Files.createDirectories(profileDirectory);
        Path strayTemp = profileDirectory.resolve("default.json.d3adb33f.tmp");
        Files.writeString(strayTemp, "left by an interrupted write", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(strayTemp, FileTime.from(
                Instant.now().minusMillis(ConfigStartupSweep.STRAY_TEMP_MIN_AGE_MILLIS * 2L)));

        Config.load(fs());

        assertFalse(Files.exists(strayTemp), "startup is still responsible for clearing stray temp files");
    }

    @Test
    void deletingProfileFilesOutsideASaveTakesTheSaveLock() throws IOException {
        Config config = Config.load(fs());
        Path profilePath = Config.getProfileDirectory(fs()).resolve("doomed.json");
        ConfigFiles.writeJsonAtomic(fs(), profilePath, ConfigTestFixtures.minimalProfile("Doomed", "doomed.json"));

        Path lockPath = ConfigSaveLock.lockPathForTests(fs());
        Files.createDirectories(lockPath.getParent());
        try (FileChannel channel = FileChannel.open(lockPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock ignored = channel.lock()) {
            ConfigSaveException failure = assertThrows(ConfigSaveException.class,
                    () -> config.deleteProfileFiles(fs(), List.of("doomed.json")));
            assertTrue(failure.isRetryable(), "a lock conflict is transient and should be retried");
        }

        assertTrue(Files.exists(profilePath), "the delete must not have touched disk while blocked");
    }

    @Test
    void reentrantSaveLockReportsTheInProcessProgrammingError() {
        try (ConfigSaveLock ignored = ConfigSaveLock.acquire(fs())) {
            ConfigSaveException failure = assertThrows(
                    ConfigSaveException.class, () -> ConfigSaveLock.acquire(fs()));

            assertTrue(failure.getMessage().contains("reentrant"));
            assertFalse(failure.isRetryable());
        }
    }
}
