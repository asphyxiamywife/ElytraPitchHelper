package com.asphyxiamywife.elytrapitchhelper.config;

import static com.asphyxiamywife.elytrapitchhelper.config.ConfigTestFixtures.fs;

import com.asphyxiamywife.elytrapitchhelper.testing.IsolatedConfigRoot;
import com.asphyxiamywife.elytrapitchhelper.testing.FaultingConfigFileSystem;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.asphyxiamywife.elytrapitchhelper.config.ConfigFileAssertions.backupContains;
import static com.asphyxiamywife.elytrapitchhelper.config.ConfigFileAssertions.jsonFileCount;
import static com.asphyxiamywife.elytrapitchhelper.config.ConfigTestFixtures.tunedProfile;
import static com.asphyxiamywife.elytrapitchhelper.config.LegacyJsonFixtures.flatProfileJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProfilePersistenceTest {
    @IsolatedConfigRoot
    Path configRoot;

    @Test
    void saveProfilesWritesChangedProfileAndCreatesBackup() throws IOException {
        Path profilePath = writeProfile(profile("Alpha", "alpha.json"));
        long loadedAt = Files.getLastModifiedTime(profilePath).toMillis();
        Profile updated = profile("Alpha Updated", "alpha.json");
        updated = updated.withPitch(updated.pitch().withTargetUpMinecraft(-35.0f));
        Config config = loadedConfig(List.of(updated), profilePath, "alpha.json", loadedAt);

        assertTrue(ProfilePersistence.saveProfiles(fs(), config, java.util.Set.of()));

        Profile saved = ProfilePersistence.readProfileFile(fs(), profilePath, "alpha.json", "saved profile",
                profile("Defaults", "default.json"));
        assertEquals("Alpha Updated", saved.name());
        assertEquals(-35.0f, saved.pitch().targetUpMinecraft());
        assertEquals(1, jsonFileCount(profileBackupDirectory("alpha")));
        assertEquals(Files.getLastModifiedTime(profilePath).toMillis(),
                config.profileFileState("alpha.json").modifiedAtMillis());
        assertTrue(config.profileMetadata.profile("alpha.json").lastModifiedAtMillis() >= loadedAt);
    }

    @Test
    void saveProfilesRefusesToOverwriteProfileChangedExternallySinceLoad() throws IOException {
        Path profilePath = writeProfile(profile("Original", "alpha.json"));
        long loadedAt = Files.getLastModifiedTime(profilePath).toMillis();
        Config config = loadedConfig(
                List.of(profile("In Memory", "alpha.json")), profilePath, "alpha.json", loadedAt);
        Profile external = profile("External", "alpha.json");
        ConfigFiles.writeJsonAtomic(fs(), profilePath, external);
        Files.setLastModifiedTime(profilePath, FileTime.fromMillis(loadedAt + 10_000L));

        assertThrows(ProfileConflictException.class,
                () -> ProfilePersistence.saveProfiles(fs(), config, java.util.Set.of()));

        Profile saved = ProfilePersistence.readProfileFile(fs(), profilePath, "alpha.json", "saved profile",
                profile("Defaults", "default.json"));
        assertEquals("External", saved.name());
    }

    @Test
    void fingerprintDetectsExternalChangeWithUnchangedTimestamp() throws IOException {
        Path profilePath = writeProfile(profile("Original", "alpha.json"));
        long loadedAt = Files.getLastModifiedTime(profilePath).toMillis();
        Config config = loadedConfig(
                List.of(profile("In Memory", "alpha.json")), profilePath, "alpha.json", loadedAt);

        ConfigFiles.writeJsonAtomic(fs(), profilePath, profile("External", "alpha.json"));
        Files.setLastModifiedTime(profilePath, FileTime.fromMillis(loadedAt));

        assertThrows(ProfileConflictException.class,
                () -> ProfilePersistence.saveProfiles(fs(), config, java.util.Set.of()));
    }

    @Test
    void deletingLoadedProfileExternallyCreatesConflictInsteadOfRecreatingIt() throws IOException {
        Path profilePath = writeProfile(profile("Original", "alpha.json"));
        long loadedAt = Files.getLastModifiedTime(profilePath).toMillis();
        Config config = loadedConfig(
                List.of(profile("In Memory", "alpha.json")), profilePath, "alpha.json", loadedAt);
        Files.delete(profilePath);

        assertThrows(ProfileConflictException.class,
                () -> ProfilePersistence.saveProfiles(fs(), config, java.util.Set.of()));
        assertFalse(Files.exists(profilePath));
    }

    @Test
    void explicitOverwriteResolvesConflictAndPreservesExternalVersionAsBackup() throws IOException {
        Path profilePath = writeProfile(profile("Original", "alpha.json"));
        long loadedAt = Files.getLastModifiedTime(profilePath).toMillis();
        Config config = loadedConfig(
                List.of(profile("In Memory", "alpha.json")), profilePath, "alpha.json", loadedAt);
        ConfigFiles.writeJsonAtomic(fs(), profilePath, profile("External", "alpha.json"));
        byte[] externalContents = Files.readAllBytes(profilePath);

        assertTrue(ProfilePersistence.saveProfiles(fs(), config, Set.of(profilePath)));

        Profile saved = ProfilePersistence.readProfileFile(fs(), profilePath, "alpha.json", "saved profile",
                profile("Defaults", "default.json"));
        assertEquals("In Memory", saved.name());
        assertTrue(backupContains(profileBackupDirectory("alpha"), externalContents));
    }

    @Test
    void explicitOverwritePreservesMalformedExternalBytes() throws IOException {
        Path profilePath = writeProfile(profile("Original", "alpha.json"));
        long loadedAt = Files.getLastModifiedTime(profilePath).toMillis();
        Config config = loadedConfig(
                List.of(profile("In Memory", "alpha.json")), profilePath, "alpha.json", loadedAt);
        byte[] malformedExternal = "{\"unfinished\":true".getBytes(StandardCharsets.UTF_8);
        Files.write(profilePath, malformedExternal);

        assertTrue(ProfilePersistence.saveProfiles(fs(), config, Set.of(profilePath)));

        assertTrue(backupContains(profileBackupDirectory("alpha"), malformedExternal));
    }

    @Test
    void explicitOverwriteAbortsWhenExternalVersionCannotBeBackedUp() throws IOException {
        Path profilePath = writeProfile(profile("Original", "alpha.json"));
        long loadedAt = Files.getLastModifiedTime(profilePath).toMillis();
        Config config = loadedConfig(
                List.of(profile("In Memory", "alpha.json")), profilePath, "alpha.json", loadedAt);
        ConfigFiles.writeJsonAtomic(fs(), profilePath, profile("External", "alpha.json"));
        Path backupRoot = Config.getConfigDirectory(fs()).resolve("backups");
        Files.createDirectories(backupRoot.getParent());
        Files.writeString(backupRoot, "blocks backup directory", StandardCharsets.UTF_8);

        assertThrows(ConfigSaveException.class,
                () -> ProfilePersistence.saveProfiles(fs(), config, Set.of(profilePath)));

        Profile saved = ProfilePersistence.readProfileFile(fs(), profilePath, "alpha.json", "saved profile",
                profile("Defaults", "default.json"));
        assertEquals("External", saved.name());
    }

    @Test
    void sameTimestampBackupsRestoreHighestSequence() throws IOException {
        Path profilePath = Config.getProfileDirectory(fs()).resolve("alpha.json");
        Path backupDirectory = profileBackupDirectory("alpha");
        Files.createDirectories(backupDirectory);
        String timestamp = "20260621-120000-000";
        ConfigFiles.writeJsonAtomic(fs(), backupDirectory.resolve(ProfileBackups.backupFileName(timestamp, 1)),
                profile("External", "alpha.json"));
        ConfigFiles.writeJsonAtomic(fs(), backupDirectory.resolve(ProfileBackups.backupFileName(timestamp, 2)),
                profile("Kept In Game", "alpha.json"));

        Profile restored = ProfileBackups.restore(fs(), profilePath, null);

        assertEquals("Kept In Game", restored.name());
    }

    @Test
    void legacySameTimestampBackupsRestoreHighestSequence() throws IOException {
        Path profilePath = Config.getProfileDirectory(fs()).resolve("alpha.json");
        Path backupDirectory = profileBackupDirectory("alpha");
        Files.createDirectories(backupDirectory);
        String timestamp = "20260621-120000-000";
        ConfigFiles.writeJsonAtomic(fs(), backupDirectory.resolve(timestamp + ".json"),
                profile("Original", "alpha.json"));
        ConfigFiles.writeJsonAtomic(fs(), backupDirectory.resolve(timestamp + "-2.json"),
                profile("Later", "alpha.json"));

        Profile restored = ProfileBackups.restore(fs(), profilePath, null);

        assertEquals("Later", restored.name());
    }

    @Test
    void mixedBackupFormatsUseNumericSequenceOrdering() throws IOException {
        Path profilePath = Config.getProfileDirectory(fs()).resolve("alpha.json");
        Path backupDirectory = profileBackupDirectory("alpha");
        Files.createDirectories(backupDirectory);
        String timestamp = "20260621-120000-000";
        ConfigFiles.writeJsonAtomic(fs(), backupDirectory.resolve(ProfileBackups.backupFileName(timestamp, 2)),
                profile("Modern Two", "alpha.json"));
        ConfigFiles.writeJsonAtomic(fs(), backupDirectory.resolve(timestamp + "-10.json"),
                profile("Legacy Ten", "alpha.json"));

        Profile restored = ProfileBackups.restore(fs(), profilePath, null);

        assertEquals("Legacy Ten", restored.name());
    }

    @Test
    void newerTimestampOutranksHigherSequenceFromOlderTimestamp() throws IOException {
        Path profilePath = Config.getProfileDirectory(fs()).resolve("alpha.json");
        Path backupDirectory = profileBackupDirectory("alpha");
        Files.createDirectories(backupDirectory);
        ConfigFiles.writeJsonAtomic(fs(), backupDirectory.resolve("20260621-120000-000-99.json"),
                profile("Older Timestamp", "alpha.json"));
        ConfigFiles.writeJsonAtomic(fs(), backupDirectory.resolve("20260621-120000-001.json"),
                profile("Newer Timestamp", "alpha.json"));

        Profile restored = ProfileBackups.restore(fs(), profilePath, null);

        assertEquals("Newer Timestamp", restored.name());
    }

    @Test
    void restoreSkipsMalformedNewestBackup() throws IOException {
        Path profilePath = Config.getProfileDirectory(fs()).resolve("alpha.json");
        Path backupDirectory = profileBackupDirectory("alpha");
        Files.createDirectories(backupDirectory);
        String timestamp = "20260621-120000-000";
        ConfigFiles.writeJsonAtomic(fs(), backupDirectory.resolve(ProfileBackups.backupFileName(timestamp, 1)),
                profile("Valid", "alpha.json"));
        Files.writeString(backupDirectory.resolve(ProfileBackups.backupFileName(timestamp, 2)), "{broken",
                StandardCharsets.UTF_8);

        Profile restored = ProfileBackups.restore(fs(), profilePath, null);

        assertEquals("Valid", restored.name());
    }

    @Test
    void caseOnlyRenameMigratesLegacyBackupsAndRecoversCorruptProfile() throws IOException {
        Path profilePath = Config.getProfileDirectory(fs()).resolve("foo.json");
        Files.createDirectories(profilePath.getParent());
        Files.writeString(profilePath, "{broken", StandardCharsets.UTF_8);
        ProfileMetadataStore metadata = new ProfileMetadataStore();
        metadata.markCreated("Foo.json", 100L, "Hand Tuned");
        Path legacyDirectory = ProfileBackups.legacyProfileBackupDirectoryForTests(fs(), "Foo.json");
        Files.createDirectories(legacyDirectory);
        ConfigFiles.writeJsonAtomic(fs(), 
                legacyDirectory.resolve(ProfileBackups.backupFileName("20260714-000000-000", 1)),
                profile("Recovered", "Foo.json"));

        Config config = new Config();
        config.profileMetadata = metadata;
        ProfilePersistence.LoadResult result = ProfilePersistence.loadProfiles(fs(), true, config);

        assertEquals(1, result.profiles().size());
        assertEquals("Recovered", result.profiles().get(0).name());
        assertEquals("foo.json", result.profiles().get(0).fileName());
        Profile restored = ProfilePersistence.readProfileFile(fs(), profilePath, "foo.json", "restored profile",
                profile("Defaults", "default.json"));
        assertEquals("Recovered", restored.name());
        Path canonicalDirectory = ProfileBackups.profileBackupDirectoryForTests(fs(), "foo.json");
        assertTrue(Files.isDirectory(canonicalDirectory));
        assertTrue(jsonFileCount(canonicalDirectory) >= 1L);
        assertFalse(Files.exists(legacyDirectory));
    }

    @Test
    void rotationRemovesOldestLegacySequenceFirst() throws IOException {
        Path profilePath = Config.getProfileDirectory(fs()).resolve("alpha.json");
        Path backupDirectory = profileBackupDirectory("alpha");
        Files.createDirectories(backupDirectory);
        String timestamp = "20000101-000000-000";
        for (int sequence = 1; sequence <= 8; sequence++) {
            String backupName = sequence == 1 ? timestamp + ".json" : timestamp + "-" + sequence + ".json";
            ConfigFiles.writeJsonAtomic(fs(), backupDirectory.resolve(backupName),
                    profile("Legacy " + sequence, "alpha.json"));
        }

        ProfileBackups.save(fs(), profilePath, profile("Current", "alpha.json"));

        assertEquals(8, jsonFileCount(backupDirectory));
        assertFalse(Files.exists(backupDirectory.resolve(timestamp + ".json")));
        assertTrue(Files.exists(backupDirectory.resolve(timestamp + "-2.json")));
    }

    @Test
    void matchingLatestLegacyBackupIsNotDuplicated() throws IOException {
        Path profilePath = Config.getProfileDirectory(fs()).resolve("alpha.json");
        Path backupDirectory = profileBackupDirectory("alpha");
        Files.createDirectories(backupDirectory);
        String timestamp = "20260621-120000-000";
        Profile latest = profile("Latest", "alpha.json");
        latest = latest.sanitized(null, ProfileDefaults.template());
        ConfigFiles.writeJsonAtomic(fs(), backupDirectory.resolve(timestamp + ".json"),
                profile("Original", "alpha.json"));
        ConfigFiles.writeJsonAtomic(fs(), backupDirectory.resolve(timestamp + "-2.json"), latest);

        ProfileBackups.save(fs(), profilePath, latest);

        assertEquals(2, jsonFileCount(backupDirectory));
    }

    @Test
    void profileConflictIsDetectedBeforeAnyProfileInTheBatchIsWritten() throws IOException {
        Path alphaPath = writeProfile(profile("Alpha", "alpha.json"));
        Path betaPath = writeProfile(profile("Beta", "beta.json"));
        long alphaLoadedAt = Files.getLastModifiedTime(alphaPath).toMillis();
        long betaLoadedAt = Files.getLastModifiedTime(betaPath).toMillis();
        Config config = loadedConfig(List.of(
                profile("In-memory Alpha", "alpha.json"),
                profile("In-memory Beta", "beta.json")),
                alphaPath, "alpha.json", alphaLoadedAt);
        config.restoreProfileFileState("beta.json", LoadedFileState.existing(
                betaLoadedAt, ConfigFiles.fileFingerprint(fs(), betaPath)));

        ConfigFiles.writeJsonAtomic(fs(), alphaPath, profile("External Alpha", "alpha.json"));
        ConfigFiles.writeJsonAtomic(fs(), betaPath, profile("External Beta", "beta.json"));

        ProfileConflictException conflict = assertThrows(ProfileConflictException.class,
                () -> ProfilePersistence.saveProfiles(fs(), config, java.util.Set.of()));

        assertEquals(List.of(alphaPath, betaPath), conflict.profilePaths());
        Profile savedAlpha = ProfilePersistence.readProfileFile(fs(), alphaPath, "alpha.json", "saved profile",
                profile("Defaults", "default.json"));
        assertEquals("External Alpha", savedAlpha.name());
    }

    @Test
    void approvingOneConflictDoesNotOverwriteAnotherProfile() throws IOException {
        Path alphaPath = writeProfile(profile("Alpha", "alpha.json"));
        Path betaPath = writeProfile(profile("Beta", "beta.json"));
        Config config = loadedConfig(List.of(
                profile("In-memory Alpha", "alpha.json"),
                profile("In-memory Beta", "beta.json")),
                alphaPath, "alpha.json", Files.getLastModifiedTime(alphaPath).toMillis());
        config.restoreProfileFileState("beta.json", LoadedFileState.existing(
                Files.getLastModifiedTime(betaPath).toMillis(), ConfigFiles.fileFingerprint(fs(), betaPath)));
        ConfigFiles.writeJsonAtomic(fs(), alphaPath, profile("External Alpha", "alpha.json"));
        ConfigFiles.writeJsonAtomic(fs(), betaPath, profile("External Beta", "beta.json"));

        ProfileConflictException conflict = assertThrows(ProfileConflictException.class,
                () -> ProfilePersistence.saveProfiles(fs(), config, Set.of(alphaPath)));

        assertEquals(List.of(betaPath), conflict.profilePaths());
        assertEquals("External Alpha", ProfilePersistence.readProfileFile(fs(), alphaPath, "alpha.json", "alpha",
                profile("Defaults", "default.json")).name());
        assertEquals("External Beta", ProfilePersistence.readProfileFile(fs(), betaPath, "beta.json", "beta",
                profile("Defaults", "default.json")).name());
    }

    @Test
    void deleteProfileFilePropagatesFailure() throws IOException {
        Path profilePath = Config.getProfileDirectory(fs()).resolve("alpha.json");
        Files.createDirectories(profilePath);
        Files.writeString(profilePath.resolve("child"), "not empty", StandardCharsets.UTF_8);

        assertThrows(ConfigSaveException.class, () -> ProfilePersistence.deleteProfileFiles(fs(), List.of("alpha.json"), null));
        assertTrue(Files.exists(profilePath));
    }

    @Test
    void batchDeleteFailureLeavesEarlierProfilesOnDisk() throws IOException {
        Path alphaPath = writeProfile(profile("Alpha", "alpha.json"));
        Path invalidBetaPath = Config.getProfileDirectory(fs()).resolve("beta.json");
        Files.createDirectories(invalidBetaPath);
        Files.writeString(invalidBetaPath.resolve("child"), "not a profile", StandardCharsets.UTF_8);

        assertThrows(ConfigSaveException.class,
                () -> ProfilePersistence.deleteProfileFiles(fs(), List.of("alpha.json", "beta.json"), null));

        assertTrue(Files.isRegularFile(alphaPath));
        assertTrue(Files.isDirectory(invalidBetaPath));
    }

    @Test
    void batchDeleteRemovesEveryProfileAfterSuccessfulStaging() throws IOException {
        Path alphaPath = writeProfile(profile("Alpha", "alpha.json"));
        Path betaPath = writeProfile(profile("Beta", "beta.json"));

        ProfilePersistence.deleteProfileFiles(fs(), List.of("alpha.json", "beta.json"), null);

        assertFalse(Files.exists(alphaPath));
        assertFalse(Files.exists(betaPath));
    }

    @Test
    void deleteProfileFileRemovesSymlinkWithoutDeletingItsTarget() throws IOException {
        Path target = configRoot.resolve("shared-profile.json");
        ConfigFiles.writeJsonAtomic(fs(), target, profile("Shared", "linked.json"));
        Path link = Config.getProfileDirectory(fs()).resolve("linked.json");
        Files.createDirectories(link.getParent());
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException e) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Symbolic links are unavailable");
        }
        assertTrue(ConfigFiles.isJsonFile(fs(), link));

        ProfilePersistence.deleteProfileFiles(fs(), List.of("linked.json"), null);

        assertFalse(Files.exists(link, LinkOption.NOFOLLOW_LINKS));
        assertTrue(Files.exists(target));
    }

    @Test
    void committedDeleteIsNotRestoredWhenBatchCleanupInitiallyFails() throws IOException {
        Config config = Config.load(fs());
        config.createProfile();
        config.save(fs());
        Path deletedPath = config.getProfilePath(fs(), 0);
        FaultingConfigFileSystem.current().failDeletesWhen(
                path -> path.getFileName().toString().startsWith("deleted-"));

        assertTrue(config.deleteProfile(fs(), 0));
        assertFalse(Files.exists(deletedPath, LinkOption.NOFOLLOW_LINKS));

        FaultingConfigFileSystem.current().failDeletesWhen(null);
        Config.load(fs());

        assertFalse(Files.exists(deletedPath, LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void deleteProfileRestoresModelWhenFileDeletionFails() throws IOException {
        Profile alpha = profile("Alpha", "alpha.json");
        Profile beta = profile("Beta", "beta.json");
        Config config = new Config();
        config.profiles = new ArrayList<>(List.of(alpha, beta));
        config.activeProfileFile = alpha.fileName();
        ConfigProfileManager.ensureProfiles(config);
        Path profilePath = Config.getProfileDirectory(fs()).resolve(alpha.fileName());
        Files.createDirectories(profilePath);
        Files.writeString(profilePath.resolve("child"), "not empty", StandardCharsets.UTF_8);

        assertThrows(ConfigSaveException.class, () -> config.deleteProfile(fs(), 0));
        assertEquals(2, config.profileCount());
        assertEquals("alpha.json", config.profile(0).fileName());
        assertEquals("alpha.json", config.activeProfileFile);
    }

    @Test
    void deleteProfileRefusesToDeleteAnExternallyChangedFileAndRestoresModel() throws IOException {
        Config config = Config.load(fs());
        config.createProfile();
        config.save(fs());
        String deletedFileName = config.profile(0).fileName();
        Path deletedPath = config.getProfilePath(fs(), 0);
        Profile external = ProfilePersistence.readProfileFile(fs(), deletedPath, deletedFileName, "external edit", ProfileDefaults.template());
        external = external.withName("Newer external edit");
        ConfigFiles.writeJsonAtomic(fs(), deletedPath, external);

        ProfileConflictException conflict = assertThrows(ProfileConflictException.class,
                () -> config.deleteProfile(fs(), 0));

        assertEquals(List.of(deletedPath), conflict.profilePaths());
        assertTrue(Files.exists(deletedPath));
        assertEquals(2, config.profileCount());
        assertEquals(deletedFileName, config.profile(0).fileName());
    }

    @Test
    void deleteProfileRestoresAnEditMadeAfterItsFinalPreflight() throws IOException {
        Config config = Config.load(fs());
        config.createProfile();
        config.save(fs());
        String deletedFileName = config.profile(0).fileName();
        Path deletedPath = config.getProfilePath(fs(), 0);
        Profile external = ProfilePersistence.readProfileFile(fs(), deletedPath, deletedFileName, "external edit", ProfileDefaults.template());
        external = external.withName("Edit during deletion");
        Profile externalEdit = external;
        FaultingConfigFileSystem.current().beforeAtomicMove(() -> {
            try {
                ConfigFiles.writeJsonAtomic(
                        FaultingConfigFileSystem.current().undecorated(), deletedPath, externalEdit);
            } catch (IOException e) {
                throw new ConfigSaveException("Test edit failed", e);
            }
        });

        assertThrows(ProfileConflictException.class, () -> config.deleteProfile(fs(), 0));

        Profile preserved = ProfilePersistence.readProfileFile(fs(), deletedPath, deletedFileName,
                "preserved external edit", ProfileDefaults.template());
        assertEquals("Edit during deletion", preserved.name());
        assertEquals(2, config.profileCount());
    }

    @Test
    void reloadPreservesActiveProfileAcrossCaseOnlyRenameAndReordering() throws IOException {
        Config mainConfig = new Config();
        mainConfig.activeProfileIndex = 0;
        mainConfig.activeProfileFile = "Foo.json";
        mainConfig.profileSortMode = Config.PROFILE_SORT_NAME;
        ConfigFiles.writeJsonAtomic(fs(), Config.getConfigPath(fs()), mainConfig);
        Profile renamedActive = profile("Zulu", "foo.json");
        renamedActive = renamedActive.withPitch(
                renamedActive.pitch().withTargetUpMinecraft(-10.0f));
        Path renamedPath = writeProfile(renamedActive);
        writeProfile(profile("Alpha", "alpha.json"));
        long loadedAt = Files.getLastModifiedTime(renamedPath).toMillis();
        String loadedFingerprint = ConfigFiles.fileFingerprint(fs(), renamedPath);
        ProfileMetadataStore metadata = new ProfileMetadataStore();
        metadata.markCreated("Foo.json", 100L, "Hand Tuned");
        metadata.markModified("Foo.json", 200L, "Hand Tuned");
        StoreMetadata storedBaselines = StoreMetadata.EMPTY.withProfile(
                "Foo.json", LoadedFileState.existing(loadedAt, loadedFingerprint));
        ConfigFiles.writeJsonAtomic(fs(), 
                Config.getProfileMetadataPath(fs()), metadata.toJson(storedBaselines));

        Config reloaded = Config.reloadFromDisk(fs());

        assertEquals("alpha.json", reloaded.profile(0).fileName());
        assertEquals("foo.json", reloaded.profile(1).fileName());
        assertEquals(1, reloaded.activeProfileIndex);
        assertEquals("foo.json", reloaded.activeProfileFile);
        assertEquals("Zulu", reloaded.activeProfileName());
        assertEquals(-10.0f, reloaded.pitch().targetUpMinecraft());
        ProfileMetadata reloadedHistory = reloaded.profileMetadata.profile("foo.json");
        assertEquals("foo.json", reloadedHistory.fileName());
        assertEquals("Hand Tuned", reloadedHistory.basedOn());
        assertEquals(100L, reloadedHistory.createdAtMillis());
        assertEquals(200L, reloadedHistory.lastModifiedAtMillis());
        assertEquals(loadedAt, reloaded.profileFileState("foo.json").modifiedAtMillis());
        assertEquals(loadedFingerprint, reloaded.profileFileState("foo.json").fingerprint());
    }

    @Test
    void restoredSnapshotCanOverwriteTheResetFileItReplaces() throws IOException {
        Profile original = profile("Original", "alpha.json");
        Path profilePath = writeProfile(original);
        long loadedAt = Files.getLastModifiedTime(profilePath).toMillis();
        Config config = new Config();
        config.profiles = new ArrayList<>(List.of(original));
        config.activeProfileIndex = 0;
        config.activeProfileFile = original.fileName();
        ConfigProfileManager.ensureProfiles(config);
        config.restoreProfileFileState(
                "alpha.json", LoadedFileState.existing(loadedAt, ConfigFiles.fileFingerprint(fs(), profilePath)));
        Config snapshot = config.copy();

        config.replaceProfile(0, config.profile(0).withPitch(
                config.profile(0).pitch().withTargetUpMinecraft(-25.0f)));
        assertTrue(ProfilePersistence.saveProfiles(fs(), config, java.util.Set.of()));

        config.restoreProfileStateFrom(snapshot, "alpha.json");
        assertTrue(ProfilePersistence.saveProfiles(fs(), config, java.util.Set.of()));

        Profile saved = ProfilePersistence.readProfileFile(fs(), profilePath, "alpha.json", "saved profile",
                profile("Defaults", "default.json"));
        assertEquals(-40.0f, saved.pitch().targetUpMinecraft());
    }

    @Test
    void fullSnapshotRestoreKeepsCurrentLoadedTimestampAndProfileIdentity() throws IOException {
        Profile original = profile("Original", "alpha.json");
        Path profilePath = writeProfile(original);
        long loadedAt = Files.getLastModifiedTime(profilePath).toMillis();
        Config config = new Config();
        config.profiles = new ArrayList<>(List.of(original));
        config.activeProfileIndex = 0;
        config.activeProfileFile = original.fileName();
        ConfigProfileManager.ensureProfiles(config);
        config.restoreProfileFileState(
                "alpha.json", LoadedFileState.existing(loadedAt, ConfigFiles.fileFingerprint(fs(), profilePath)));
        Config snapshot = config.copy();

        config.replaceProfile(0, config.profile(0).withPitch(
                config.profile(0).pitch().withTargetUpMinecraft(-25.0f)));
        assertTrue(ProfilePersistence.saveProfiles(fs(), config, java.util.Set.of()));

        snapshot.prepareSnapshotRestoreFrom(config);
        config.restoreFrom(snapshot);
        assertSame(original, config.profile(0));
        assertTrue(ProfilePersistence.saveProfiles(fs(), config, java.util.Set.of()));

        Profile saved = ProfilePersistence.readProfileFile(fs(), profilePath, "alpha.json", "saved profile",
                profile("Defaults", "default.json"));
        assertEquals(-40.0f, saved.pitch().targetUpMinecraft());
    }

    @Test
    void loadProfilesRestoresNewestValidBackupWhenProfileJsonIsCorrupt() throws IOException {
        Path profilePath = Config.getProfileDirectory(fs()).resolve("broken.json");
        Profile backup = profile("Restored", "broken.json");
        backup = backup.withPitch(backup.pitch().withTargetDownMinecraft(55.0f));
        ProfileBackups.save(fs(), profilePath, backup);
        Files.createDirectories(profilePath.getParent());
        byte[] malformedProfile = "{broken".getBytes(StandardCharsets.UTF_8);
        Files.write(profilePath, malformedProfile);

        ProfilePersistence.LoadResult result = ProfilePersistence.loadProfiles(fs(), true, new Config());

        assertEquals(1, result.profiles().size());
        assertEquals("Restored", result.profiles().get(0).name());
        assertEquals(55.0f, result.profiles().get(0).pitch().targetDownMinecraft());
        Profile restored = ProfilePersistence.readProfileFile(fs(), profilePath, "broken.json", "restored profile",
                profile("Defaults", "default.json"));
        assertEquals("Restored", restored.name());
        assertTrue(backupContains(profileBackupDirectory("broken"), malformedProfile));
    }

    @Test
    void loadProfilesQuarantinesCorruptProfileWhenNoBackupIsValid() throws IOException {
        Path profilePath = Config.getProfileDirectory(fs()).resolve("lost-profile.json");
        Files.createDirectories(profilePath.getParent());
        byte[] malformedProfile = "{broken".getBytes(StandardCharsets.UTF_8);
        Files.write(profilePath, malformedProfile);

        ProfilePersistence.loadProfiles(fs(), true, new Config());

        assertFalse(Files.exists(profilePath));
        assertTrue(backupContains(profileBackupDirectory("lost-profile"), malformedProfile));
    }

    @Test
    void saveProfilesMigratesLegacyFlatProfileJsonToGroupedValueObjects() throws IOException {
        Profile legacyProfile = profile("Legacy", "legacy.json");
        Path profilePath = Config.getProfileDirectory(fs()).resolve(legacyProfile.fileName());
        JsonObject legacyJson = flatProfileJson(legacyProfile);
        ConfigFiles.writeJsonAtomic(fs(), profilePath, legacyJson);
        long loadedAt = Files.getLastModifiedTime(profilePath).toMillis();
        Profile loaded = ProfilePersistence.readProfileFile(fs(), profilePath, "legacy.json", "legacy profile",
                profile("Defaults", "default.json"));
        Config config = loadedConfig(List.of(loaded), profilePath, "legacy.json", loadedAt);

        assertTrue(ProfilePersistence.saveProfiles(fs(), config, java.util.Set.of()));

        JsonObject migrated = ConfigFiles.GSON.fromJson(Files.readString(profilePath, StandardCharsets.UTF_8),
                JsonObject.class);
        assertTrue(migrated.has("pitch"));
        assertTrue(migrated.has("line"));
        assertTrue(migrated.has("amplitude"));
        assertFalse(migrated.has("targetUpMinecraft"));
        assertFalse(migrated.has("lineColorRgb"));
        assertFalse(migrated.has("amplitudeHelperEnabled"));
        assertEquals(-40.0f, migrated.getAsJsonObject("pitch").get("targetUpMinecraft").getAsFloat());
        assertEquals(0xFFFFFF, migrated.getAsJsonObject("line").get("colorRgb").getAsInt());
        assertTrue(migrated.getAsJsonObject("amplitude").get("enabled").getAsBoolean());
    }

    private static Path writeProfile(Profile profile) throws IOException {
        Path profilePath = Config.getProfileDirectory(fs()).resolve(profile.fileName());
        ConfigFiles.writeJsonAtomic(fs(), profilePath, profile);
        return profilePath;
    }

    private static Config loadedConfig(
            List<Profile> profiles, Path path, String fileName, long loadedAt) throws IOException {
        Config config = new Config();
        config.profiles = new ArrayList<>(profiles);
        config.activeProfileIndex = 0;
        config.activeProfileFile = profiles.get(0).fileName();
        config.profilesNormalized = false;
        ConfigProfileManager.ensureProfiles(config);
        config.profileMetadata.markLoaded(fileName, loadedAt, "Custom", false);
        config.restoreProfileFileState(
                fileName, LoadedFileState.existing(loadedAt, ConfigFiles.fileFingerprint(fs(), path)));
        return config;
    }

    private static Path profileBackupDirectory(String slug) {
        return ProfileBackups.profileBackupDirectoryForTests(fs(), slug + ".json");
    }

    private static Profile profile(String name, String fileName) {
        return tunedProfile(name, fileName);
    }
}
