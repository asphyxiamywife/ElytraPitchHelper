package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.testing.IsolatedConfigRoot;
import com.asphyxiamywife.elytrapitchhelper.testing.FaultingConfigFileSystem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

import static com.asphyxiamywife.elytrapitchhelper.config.ConfigTestFixtures.fs;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ConfigSaveBatchTest {
    @IsolatedConfigRoot
    Path configRoot;

    @Test
    void recoveryPreservesConflictsAndUntouchedFilesAfterRollbackMarkerFailure() throws Exception {
        Config config = Config.load(fs());
        Path profile = config.getProfilePath(fs(), 0);
        Path main = Config.getConfigPath(fs());
        Path createdExternally = Config.getProfileDirectory(fs()).resolve("external.json");
        byte[] mainBefore = Files.readAllBytes(main);
        ConfigSaveBatch batch = ConfigSaveBatch.begin(fs(), List.of(main, profile, createdExternally));
        try {
            ConfigFiles.writeBytesAtomic(fs(), main, "changed main".getBytes(StandardCharsets.UTF_8));
            Files.writeString(profile, "external edit");
            Files.writeString(createdExternally, "external creation");
            FaultingConfigFileSystem.current().failForcesWhen(
                    path -> path.getFileName().toString().equals("finalized"));
            IOException failure = new IOException("profile conflict");
            batch.rollback(failure, List.of(profile));
            assertTrue(failure.getSuppressed().length > 0);
        } finally {
            FaultingConfigFileSystem.current().failForcesWhen(null);
            batch.abandonForTests();
        }
        assertTrue(ConfigSaveBatch.recoverIncomplete(fs()));
        assertArrayEquals(mainBefore, Files.readAllBytes(main));
        assertEquals("external edit", Files.readString(profile));
        assertEquals("external creation", Files.readString(createdExternally));
        assertTrue(ConfigSaveBatch.recoverIncomplete(fs()));
    }

    @Test
    void recoveryUsesRollbackJournalEvenWhenItsDurabilityBarrierFailed() throws Exception {
        Config config = Config.load(fs());
        Path profile = config.getProfilePath(fs(), 0);
        Path main = Config.getConfigPath(fs());
        byte[] mainBefore = Files.readAllBytes(main);
        ConfigSaveBatch batch = ConfigSaveBatch.begin(fs(), List.of(main, profile));
        try {
            ConfigFiles.writeBytesAtomic(fs(), main, "changed main".getBytes(StandardCharsets.UTF_8));
            Files.writeString(profile, "external edit");
            FaultingConfigFileSystem.current().failForcesWhen(path -> true);
            IOException failure = new IOException("profile conflict");
            batch.rollback(failure, List.of(profile));
            assertTrue(failure.getSuppressed().length > 0);
        } finally {
            FaultingConfigFileSystem.current().failForcesWhen(null);
            batch.abandonForTests();
        }
        assertTrue(ConfigSaveBatch.recoverIncomplete(fs()));
        assertArrayEquals(mainBefore, Files.readAllBytes(main));
        assertEquals("external edit", Files.readString(profile));
    }

    @Test
    void rollbackJournalCreationFailureFallsBackToOriginalSnapshot() throws Exception {
        Config config = Config.load(fs());
        Path profile = config.getProfilePath(fs(), 0);
        Path main = Config.getConfigPath(fs());
        byte[] mainBefore = Files.readAllBytes(main);
        byte[] profileBefore = Files.readAllBytes(profile);
        ConfigSaveBatch batch = ConfigSaveBatch.begin(fs(), List.of(main, profile));
        try {
            ConfigFiles.writeBytesAtomic(fs(), main, "changed main".getBytes(StandardCharsets.UTF_8));
            Files.writeString(profile, "external edit");
            FaultingConfigFileSystem.current().failDurableWritesWhen(
                    path -> path.getFileName().toString().equals("rollback.json"));
            IOException failure = new IOException("profile conflict");
            batch.rollback(failure, List.of(profile));
            assertTrue(failure.getSuppressed().length > 0);
            Path directory = fs().list(Config.getConfigDirectory(fs()).resolve(".internal")).stream()
                    .filter(path -> path.getFileName().toString().startsWith("save-batch-"))
                    .findFirst().orElseThrow();
            assertFalse(Files.exists(directory.resolve("rollback.json")));
            assertEquals("external edit", Files.readString(profile));
        } finally {
            FaultingConfigFileSystem.current().failDurableWritesWhen(null);
            batch.abandonForTests();
        }
        assertTrue(ConfigSaveBatch.recoverIncomplete(fs()));
        assertArrayEquals(mainBefore, Files.readAllBytes(main));
        assertArrayEquals(profileBefore, Files.readAllBytes(profile));
    }

    @Test
    void incompleteRollbackJournalBlocksRecoveryInsteadOfReplayingOriginalJournal() throws Exception {
        Config config = Config.load(fs());
        Path profile = config.getProfilePath(fs(), 0);
        ConfigSaveBatch batch = ConfigSaveBatch.begin(fs(), List.of(profile));
        try {
            Files.writeString(profile, "external edit");
            Path directory = fs().list(Config.getConfigDirectory(fs()).resolve(".internal")).stream()
                    .filter(path -> path.getFileName().toString().startsWith("save-batch-"))
                    .findFirst().orElseThrow();
            Files.writeString(directory.resolve("rollback.json"), "{\"formatVersion\":2,");
        } finally {
            batch.abandonForTests();
        }
        assertFalse(ConfigSaveBatch.recoverIncomplete(fs()));
        assertEquals("external edit", Files.readString(profile));
    }

    @Test
    void recoveryFinishesARestorationWhosePublishedTargetWasNotSynced() throws Exception {
        Config config = Config.load(fs());
        Path target = config.getProfilePath(fs(), 0);
        byte[] original = Files.readAllBytes(target);
        ConfigSaveBatch batch = ConfigSaveBatch.begin(fs(), List.of(target));
        Path staged;
        try {
            staged = ConfigSaveBatch.stageDelete(fs(), target);
            FaultingConfigFileSystem.current().failForcesWhen(target.getParent()::equals);
            assertThrows(IOException.class, () -> ConfigSaveBatch.restoreStagedDelete(fs(), staged, target));
            assertArrayEquals(original, Files.readAllBytes(target));
            assertArrayEquals(original, Files.readAllBytes(staged));
        } finally {
            FaultingConfigFileSystem.current().failForcesWhen(null);
            batch.abandonForTests();
        }
        ConfigSaveBatch.recoverIncomplete(fs());
        assertArrayEquals(original, Files.readAllBytes(target));
        assertFalse(Files.exists(staged.getParent()));
    }

    @Test
    void recoveryStillAcceptsVersionOneJournals() throws Exception {
        Config config = Config.load(fs());
        Path target = config.getProfilePath(fs(), 0);
        byte[] original = Files.readAllBytes(target);
        ConfigSaveBatch batch = ConfigSaveBatch.begin(fs(), List.of(target));
        try {
            Path internal = Config.getConfigDirectory(fs()).resolve(".internal");
            Path directory = fs().list(internal).stream()
                    .filter(path -> path.getFileName().toString().startsWith("save-batch-"))
                    .findFirst().orElseThrow();
            Path journalPath = directory.resolve("journal.json");
            var journal = ConfigFiles.GSON.fromJson(Files.readString(journalPath),
                    com.google.gson.JsonObject.class);
            journal.addProperty("formatVersion", 1);
            Files.writeString(journalPath, ConfigFiles.GSON.toJson(journal));
            ConfigFiles.writeBytesAtomic(fs(), target, "interrupted write".getBytes(StandardCharsets.UTF_8));
        } finally {
            batch.abandonForTests();
        }
        ConfigSaveBatch.recoverIncomplete(fs());
        assertArrayEquals(original, Files.readAllBytes(target));
    }

    @Test
    void deleteRestorationPreservesRecreatedTargetAndStagedBytesAcrossRecovery() throws Exception {
        Config config = Config.load(fs());
        Path target = config.getProfilePath(fs(), 0);
        ConfigSaveBatch batch = ConfigSaveBatch.begin(fs(), List.of(target));
        Path staged;
        try {
            Files.writeString(target, "edit before staging");
            staged = ConfigSaveBatch.stageDelete(fs(), target);
            FaultingConfigFileSystem.current().beforeHardLink(() -> {
                try {
                    Files.writeString(target, "recreated while restoring");
                } catch (IOException failure) {
                    throw new RuntimeException(failure);
                }
            });
            assertThrows(IOException.class, () -> ConfigSaveBatch.restoreStagedDelete(fs(), staged, target));
            FaultingConfigFileSystem.current().beforeHardLink(null);
            IOException failure = new IOException("delete verification failed");
            batch.rollback(failure);
            assertTrue(failure.getSuppressed().length > 0);
        } finally {
            FaultingConfigFileSystem.current().beforeHardLink(null);
            batch.abandonForTests();
        }

        ConfigSaveBatch.recoverIncomplete(fs());
        ConfigSaveBatch.recoverIncomplete(fs());
        assertEquals("recreated while restoring", Files.readString(target));
        assertEquals("edit before staging", Files.readString(staged));
        assertTrue(Files.exists(staged.getParent().resolve("journal.json")));

        Files.delete(target);
        ConfigSaveBatch.recoverIncomplete(fs());
        assertEquals("edit before staging", Files.readString(target));
        assertFalse(Files.exists(staged.getParent()));
    }

    @Test
    void failedDeleteDurabilityBarrierDoesNotOverwriteARecreatedTarget() throws Exception {
        Config config = Config.load(fs());
        Path target = config.getProfilePath(fs(), 0);
        byte[] original = Files.readAllBytes(target);
        ConfigSaveBatch batch = ConfigSaveBatch.begin(fs(), List.of(target));
        try {
            FaultingConfigFileSystem.current().failForcesWhen(path -> {
                if (!path.equals(target.getParent()) || Files.exists(target)) {
                    return false;
                }
                try {
                    Files.writeString(target, "recreated after staging");
                } catch (IOException failure) {
                    throw new RuntimeException(failure);
                }
                return true;
            });
            IOException failure = assertThrows(IOException.class,
                    () -> ConfigSaveBatch.stageDelete(fs(), target));
            FaultingConfigFileSystem.current().failForcesWhen(null);
            batch.rollback(failure);
            assertTrue(failure.getSuppressed().length > 0);
        } finally {
            FaultingConfigFileSystem.current().failForcesWhen(null);
            batch.abandonForTests();
        }
        ConfigSaveBatch.recoverIncomplete(fs());
        assertEquals("recreated after staging", Files.readString(target));
        Files.delete(target);
        ConfigSaveBatch.recoverIncomplete(fs());
        assertArrayEquals(original, Files.readAllBytes(target));
    }

    @Test
    void startupRecoveryRestoresTheStagedEditInsteadOfTheOlderJournalSnapshot() throws Exception {
        Config config = Config.load(fs());
        Path target = config.getProfilePath(fs(), 0);
        ConfigSaveBatch batch = ConfigSaveBatch.begin(fs(), List.of(target));
        try {
            Files.writeString(target, "external edit before delete");
            ConfigSaveBatch.stageDelete(fs(), target);
        } finally {
            batch.abandonForTests();
        }
        ConfigSaveBatch.recoverIncomplete(fs());
        assertEquals("external edit before delete", Files.readString(target));
    }

    @Test
    void startupRecoveryRestoresEveryTargetFromAnInterruptedBatch() throws Exception {
        Config config = Config.load(fs());
        Path main = Config.getConfigPath(fs());
        Path profile = config.getProfilePath(fs(), 0);
        Path newlyCreated = Config.getProfileDirectory(fs()).resolve("interrupted.json");
        byte[] mainBefore = Files.readAllBytes(main);
        byte[] profileBefore = Files.readAllBytes(profile);

        ConfigSaveBatch batch = ConfigSaveBatch.begin(fs(), List.of(main, profile, newlyCreated));
        ConfigFiles.writeBytesAtomic(fs(), main, "changed main".getBytes(StandardCharsets.UTF_8));
        ConfigFiles.writeBytesAtomic(fs(), profile, "changed profile".getBytes(StandardCharsets.UTF_8));
        ConfigFiles.writeBytesAtomic(fs(), newlyCreated, "new file".getBytes(StandardCharsets.UTF_8));
        batch.abandonForTests();

        ConfigSaveBatch.recoverIncomplete(fs());

        assertArrayEquals(mainBefore, Files.readAllBytes(main));
        assertArrayEquals(profileBefore, Files.readAllBytes(profile));
        assertFalse(Files.exists(newlyCreated));
    }

    @Test
    void recoveredBatchDoesNotRollBackLaterEditsWhenCleanupPreviouslyFailed() throws Exception {
        Config config = Config.load(fs());
        Path profile = config.getProfilePath(fs(), 0);
        byte[] original = Files.readAllBytes(profile);
        ConfigSaveBatch batch = ConfigSaveBatch.begin(fs(), List.of(profile));
        ConfigFiles.writeBytesAtomic(fs(), profile, "interrupted edit".getBytes(StandardCharsets.UTF_8));
        batch.abandonForTests();

        FaultingConfigFileSystem faulting = FaultingConfigFileSystem.current();
        faulting.failDeletesWhen(path -> path.getParent().getFileName().toString().startsWith("save-batch-"));
        try {
            ConfigSaveBatch.recoverIncomplete(fs());
            assertArrayEquals(original, Files.readAllBytes(profile));
        } finally {
            faulting.failDeletesWhen(null);
        }

        Config edited = Config.reloadFromDisk(fs());
        edited.replaceProfile(0, edited.profile(0).withName("Saved after recovery"));
        edited.save(fs());
        byte[] laterEdit = Files.readAllBytes(profile);

        Config loaded = Config.load(fs());

        assertEquals("Saved after recovery", loaded.profileName(0));
        assertArrayEquals(laterEdit, Files.readAllBytes(profile));
        assertTrue(fs().list(Config.getConfigDirectory(fs()).resolve(".internal")).stream()
                .noneMatch(path -> path.getFileName().toString().startsWith("save-batch-")));
    }

    @Test
    void startupRecoveryRestoresASymlinkStagedForDeletion() throws Exception {
        Config.load(fs());
        Path target = configRoot.resolve("shared-profile.json");
        Files.writeString(target, "shared", StandardCharsets.UTF_8);
        Path link = Config.getProfileDirectory(fs()).resolve("linked.json");
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | java.io.IOException failure) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Symbolic links are unavailable");
        }

        ConfigSaveBatch batch = ConfigSaveBatch.begin(fs(), List.of(link));
        ConfigSaveBatch.stageDelete(fs(), link);
        batch.abandonForTests();
        assertFalse(Files.exists(link, LinkOption.NOFOLLOW_LINKS));

        ConfigSaveBatch.recoverIncomplete(fs());

        assertTrue(Files.isSymbolicLink(link));
        assertTrue(Files.exists(target));
        assertEquals(target, Files.readSymbolicLink(link));
    }

    @Test
    void startupRemovesABatchDirectoryInterruptedBeforeItsJournalWasWritten() throws Exception {
        Config.load(fs());
        Path internal = Config.getConfigDirectory(fs()).resolve(".internal");
        Files.createDirectories(internal);
        Path orphan = Files.createTempDirectory(internal, "save-batch-");
        Files.writeString(orphan.resolve("original-0.bin"), "half staged", StandardCharsets.UTF_8);

        ConfigSaveBatch.recoverIncomplete(fs());

        assertFalse(Files.exists(orphan));
    }

    @Test
    void startupPreservesABatchWhoseJournalCannotBeRead() throws Exception {
        Config.load(fs());
        Path internal = Config.getConfigDirectory(fs()).resolve(".internal");
        Files.createDirectories(internal);
        Path damaged = Files.createTempDirectory(internal, "save-batch-");
        Files.writeString(damaged.resolve("journal.json"), "{not json", StandardCharsets.UTF_8);

        ConfigSaveBatch.recoverIncomplete(fs());

        assertTrue(Files.exists(damaged));
    }
}
