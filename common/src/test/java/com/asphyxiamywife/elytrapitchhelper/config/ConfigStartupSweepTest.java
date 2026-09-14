package com.asphyxiamywife.elytrapitchhelper.config;

import static com.asphyxiamywife.elytrapitchhelper.config.ConfigTestFixtures.fs;

import com.asphyxiamywife.elytrapitchhelper.testing.IsolatedConfigRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfigStartupSweepTest {
    @IsolatedConfigRoot
    Path configRoot;

    @Test
    void removesStrayTemporaryFilesLeftByAnInterruptedWrite() throws IOException {
        Path configDirectory = Config.getConfigDirectory(fs());
        Path profileDirectory = Config.getProfileDirectory(fs());
        Path internalDirectory = configDirectory.resolve(".internal");
        Files.createDirectories(profileDirectory);
        Files.createDirectories(internalDirectory);
        Path strayConfigTemp = configDirectory.resolve("elytra-pitch-helper.json.abc.tmp");
        Path strayProfileTemp = profileDirectory.resolve("default.json.def.tmp");
        Path strayMetadataTemp = internalDirectory.resolve("profile-metadata.json.ghi.tmp");
        writeStaleTemp(strayConfigTemp);
        writeStaleTemp(strayProfileTemp);
        writeStaleTemp(strayMetadataTemp);

        ConfigStartupSweep.run(fs());

        assertFalse(Files.exists(strayConfigTemp));
        assertFalse(Files.exists(strayProfileTemp));
        assertFalse(Files.exists(strayMetadataTemp));
    }

    @Test
    void leavesATemporaryFileAnotherInstanceMayStillBeWriting() throws IOException {
        Path profileDirectory = Config.getProfileDirectory(fs());
        Files.createDirectories(profileDirectory);
        Path inFlightTemp = profileDirectory.resolve("default.json.d3adb33f.tmp");
        Files.writeString(inFlightTemp, "half written", StandardCharsets.UTF_8);

        ConfigStartupSweep.run(fs());

        assertTrue(Files.exists(inFlightTemp));
    }

    @Test
    void removesATemporaryFileOnceItIsOldEnoughToBeResidue() throws IOException {
        Path profileDirectory = Config.getProfileDirectory(fs());
        Files.createDirectories(profileDirectory);
        Path temp = profileDirectory.resolve("default.json.d3adb33f.tmp");
        Files.writeString(temp, "half written", StandardCharsets.UTF_8);
        long writtenAt = Files.getLastModifiedTime(temp).toMillis();

        ConfigStartupSweep.run(fs(), () -> writtenAt + ConfigStartupSweep.STRAY_TEMP_MIN_AGE_MILLIS - 1L);
        assertTrue(Files.exists(temp));

        ConfigStartupSweep.run(fs(), () -> writtenAt + ConfigStartupSweep.STRAY_TEMP_MIN_AGE_MILLIS);
        assertFalse(Files.exists(temp));
    }

    private static void writeStaleTemp(Path path) throws IOException {
        Files.writeString(path, "partial", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(path, FileTime.from(
                Instant.now().minusMillis(ConfigStartupSweep.STRAY_TEMP_MIN_AGE_MILLIS * 2L)));
    }

    @Test
    void restoresAProfileLeftMidDeletionByAnInterruptedSave() throws IOException {
        Path profileDirectory = Config.getProfileDirectory(fs());
        Files.createDirectories(profileDirectory);
        Path internalDirectory = Config.getConfigDirectory(fs()).resolve(".internal");
        Path stagingDirectory = internalDirectory.resolve("profile-delete-abcd1234");
        Files.createDirectories(stagingDirectory);
        byte[] contents = "{\"name\":\"Orphaned\"}".getBytes(StandardCharsets.UTF_8);
        Files.write(stagingDirectory.resolve("0-orphaned.json"), contents);

        ConfigStartupSweep.run(fs());

        Path restored = profileDirectory.resolve("orphaned.json");
        assertTrue(Files.exists(restored));
        assertArrayEquals(contents, Files.readAllBytes(restored));
        assertFalse(Files.exists(stagingDirectory));
    }

    @Test
    void discardsAStagedProfileWhenTheOriginalAlreadyExists() throws IOException {
        Path profileDirectory = Config.getProfileDirectory(fs());
        Files.createDirectories(profileDirectory);
        Path original = profileDirectory.resolve("kept.json");
        Files.writeString(original, "{\"name\":\"Current\"}", StandardCharsets.UTF_8);
        Path internalDirectory = Config.getConfigDirectory(fs()).resolve(".internal");
        Path stagingDirectory = internalDirectory.resolve("profile-delete-abcd1234");
        Files.createDirectories(stagingDirectory);
        Files.writeString(stagingDirectory.resolve("0-kept.json"), "{\"name\":\"Stale\"}", StandardCharsets.UTF_8);

        ConfigStartupSweep.run(fs());

        assertEquals("{\"name\":\"Current\"}", Files.readString(original, StandardCharsets.UTF_8));
        assertFalse(Files.exists(stagingDirectory));
    }

    @Test
    void restoresAStagedProfileSymlinkWithoutFollowingItsTarget() throws IOException {
        Path target = configRoot.resolve("shared-profile.json");
        Files.writeString(target, "{\"name\":\"Shared\"}", StandardCharsets.UTF_8);
        Path internalDirectory = Config.getConfigDirectory(fs()).resolve(".internal");
        Path stagingDirectory = internalDirectory.resolve("profile-delete-abcd1234");
        Files.createDirectories(stagingDirectory);
        Path stagedLink = stagingDirectory.resolve("0-linked.json");
        try {
            Files.createSymbolicLink(stagedLink, target);
        } catch (UnsupportedOperationException | IOException failure) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Symbolic links are unavailable");
        }

        ConfigStartupSweep.run(fs());

        Path restored = Config.getProfileDirectory(fs()).resolve("linked.json");
        assertTrue(Files.isSymbolicLink(restored));
        assertEquals(target, Files.readSymbolicLink(restored));
        assertTrue(Files.exists(target));
        assertFalse(Files.exists(stagingDirectory, LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void leavesLegacyDeleteStagingUntouchedWhileAnotherSaveOwnsTheLock() throws Exception {
        Path profileDirectory = Config.getProfileDirectory(fs());
        Path internalDirectory = Config.getConfigDirectory(fs()).resolve(".internal");
        Path stagingDirectory = internalDirectory.resolve("profile-delete-abcd1234");
        Files.createDirectories(stagingDirectory);
        Path staged = stagingDirectory.resolve("0-in-flight.json");
        Files.writeString(staged, "{\"name\":\"In flight\"}", StandardCharsets.UTF_8);
        Path restored = profileDirectory.resolve("in-flight.json");
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> holder = executor.submit(() -> {
            try (ConfigSaveLock ignored = ConfigSaveLock.acquire(fs())) {
                locked.countDown();
                assertTrue(release.await(5L, TimeUnit.SECONDS));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
        });

        try {
            assertTrue(locked.await(5L, TimeUnit.SECONDS));
            ConfigStartupSweep.run(fs());
            assertTrue(Files.exists(staged));
            assertFalse(Files.exists(restored));
        } finally {
            release.countDown();
            holder.get(5L, TimeUnit.SECONDS);
            executor.shutdownNow();
        }

        ConfigStartupSweep.run(fs());
        assertTrue(Files.exists(restored));
        assertFalse(Files.exists(stagingDirectory));
    }

    @Test
    void removesLegacyPersistenceArtifactsFromBeforeTheJournalWasRemoved() throws IOException {
        Path internalDirectory = Config.getConfigDirectory(fs()).resolve(".internal");
        Path legacyTransaction = internalDirectory.resolve("config-save-1-someuuid");
        Files.createDirectories(legacyTransaction);
        Files.writeString(legacyTransaction.resolve("journal.json"), "{}", StandardCharsets.UTF_8);
        Path legacyPendingSaves = internalDirectory.resolve("pending-saves");
        Files.createDirectories(legacyPendingSaves);
        Files.writeString(legacyPendingSaves.resolve("recovery-ledger-a.json"), "{}", StandardCharsets.UTF_8);
        Path legacyLedgerSlot = internalDirectory.resolve("transaction-ledger-a.json");
        Files.writeString(legacyLedgerSlot, "{}", StandardCharsets.UTF_8);

        ConfigStartupSweep.run(fs());

        assertFalse(Files.exists(legacyTransaction));
        assertFalse(Files.exists(legacyPendingSaves));
        assertFalse(Files.exists(legacyLedgerSlot));
    }
}
