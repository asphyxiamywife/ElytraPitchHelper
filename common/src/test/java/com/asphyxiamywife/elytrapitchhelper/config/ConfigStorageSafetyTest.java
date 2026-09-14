package com.asphyxiamywife.elytrapitchhelper.config;

import static com.asphyxiamywife.elytrapitchhelper.config.ConfigTestFixtures.fs;

import com.asphyxiamywife.elytrapitchhelper.testing.IsolatedConfigRoot;
import com.asphyxiamywife.elytrapitchhelper.testing.FaultingConfigFileSystem;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static com.asphyxiamywife.elytrapitchhelper.config.ConfigFileAssertions.backupContains;
import static com.asphyxiamywife.elytrapitchhelper.config.ConfigFileAssertions.directoryContents;
import static com.asphyxiamywife.elytrapitchhelper.config.ConfigFileAssertions.jsonFileCount;
import static com.asphyxiamywife.elytrapitchhelper.config.ConfigTestFixtures.minimalProfile;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfigStorageSafetyTest {
    @IsolatedConfigRoot
    Path configRoot;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"CON.json", "bad:name.json", "name.json.", "name.json "})
    void incompatibleDiskNamesEnterReadOnlyModeWithoutTouchingFiles(String name) throws IOException {
        org.junit.jupiter.api.Assumptions.assumeFalse(
                System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).startsWith("windows"));
        Config.load(fs());
        Path incompatible = Config.getProfileDirectory(fs()).resolve(name);
        Files.writeString(incompatible, "{broken");
        byte[] mainBefore = Files.readAllBytes(Config.getConfigPath(fs()));
        FaultingConfigFileSystem.current().observeMutations(path -> {
            throw new AssertionError("Invalid-name load attempted to mutate " + path);
        });
        try {
            for (Config loaded : List.of(Config.load(fs()), Config.reloadFromDisk(fs()))) {
                assertTrue(loaded.isReadOnly());
                assertTrue(loaded.readOnlyWarning().contains(name));
            }
            assertEquals("{broken", Files.readString(incompatible));
            assertArrayEquals(mainBefore, Files.readAllBytes(Config.getConfigPath(fs())));
        } finally {
            FaultingConfigFileSystem.current().observeMutations(null);
        }
        Files.move(incompatible, incompatible.resolveSibling("renamed.json"));
        assertFalse(Config.reloadFromDisk(fs()).isReadOnly());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"profile", "metadata"})
    void fractionalFutureVersionInOtherFilesBlocksStartupAndReload(String source) throws IOException {
        Config config = Config.load(fs());
        Path path = source.equals("profile") ? config.getProfilePath(fs(), 0)
                : Config.getProfileMetadataPath(fs());
        String future = "{\"version\":" + Config.CURRENT_VERSION + ".0001}";
        Files.writeString(path, future);
        assertTrue(Config.reloadFromDisk(fs()).isReadOnly());
        assertTrue(Config.load(fs()).isReadOnly());
        assertEquals(future, Files.readString(path));
    }

    @Test
    void integralDecimalCurrentVersionRemainsWritable() throws IOException {
        Path path = Config.getConfigPath(fs());
        Files.createDirectories(path.getParent());
        Files.writeString(path, "{\"version\":" + Config.CURRENT_VERSION + ".0}");
        assertFalse(Config.load(fs()).isReadOnly());
    }

    @Test
    void explicitNullSectionCollapseRecoversOnStartupAndReload() throws IOException {
        Path mainPath = Config.getConfigPath(fs());
        Files.createDirectories(mainPath.getParent());
        String json = "{\"enabled\":false,\"sectionCollapse\":null}";
        Files.writeString(mainPath, json);
        Config started = Config.load(fs());
        assertFalse(started.enabled);
        assertEquals(new SectionCollapseSettings(), started.sectionCollapse);
        assertEquals(started.sectionCollapse, started.copy().sectionCollapse);

        Files.writeString(mainPath, json);
        Config reloaded = Config.reloadFromDisk(fs());
        assertFalse(reloaded.enabled);
        assertEquals(new SectionCollapseSettings(), reloaded.sectionCollapse);
        assertEquals(json, Files.readString(mainPath));
        reloaded.save(fs());
        assertEquals(new SectionCollapseSettings(), Config.reloadFromDisk(fs()).sectionCollapse);
    }

    @Test
    void reloadReadsLegacyBackupWithoutMigratingRestoringOrQuarantiningFiles() throws IOException {
        Config config = Config.load(fs());
        Path profilePath = Config.getProfileDirectory(fs()).resolve("foo.json");
        String fileName = profilePath.getFileName().toString();
        Path legacy = ProfileBackups.legacyProfileBackupDirectoryForTests(fs(), fileName.toUpperCase());
        Files.createDirectories(legacy);
        Path legacyBackup = legacy.resolve(ProfileBackups.backupFileName("20260801-000000-000", 1));
        ConfigFiles.writeJsonAtomic(fs(), legacyBackup, profile("Legacy backup", fileName));
        config.profileMetadata.markCreated(fileName.toUpperCase(), 100L, "Legacy");
        ConfigFiles.writeJsonAtomic(fs(), Config.getProfileMetadataPath(fs()),
                config.profileMetadata.toJson(config.storeMetadata));
        byte[] broken = "{broken".getBytes(StandardCharsets.UTF_8);
        Files.write(profilePath, broken);
        Path lost = profilePath.resolveSibling("no-backup.json");
        Files.write(lost, broken);
        byte[] backupBefore = Files.readAllBytes(legacyBackup);
        FaultingConfigFileSystem.current().observeMutations(path -> {
            throw new AssertionError("Reload attempted to mutate " + path);
        });
        try {
            Config loaded = Config.reloadFromDisk(fs());
            assertTrue(loaded.profiles.stream().anyMatch(p -> p.name().equals("Legacy backup")));
            assertArrayEquals(broken, Files.readAllBytes(profilePath));
            assertArrayEquals(broken, Files.readAllBytes(lost));
            assertArrayEquals(backupBefore, Files.readAllBytes(legacyBackup));
        } finally {
            FaultingConfigFileSystem.current().observeMutations(null);
        }
    }

    @Test
    void reloadDoesNotBackUpValidProfilesOrMalformedMainAndMetadata() throws IOException {
        Config config = Config.load(fs());
        Path profilePath = config.getProfilePath(fs(), 0);
        ConfigFiles.writeJsonAtomic(fs(), profilePath,
                profile("Externally changed", profilePath.getFileName().toString()));
        Files.writeString(Config.getConfigPath(fs()), "{broken");
        Files.writeString(Config.getProfileMetadataPath(fs()), "{broken");
        FaultingConfigFileSystem.current().observeMutations(path -> {
            throw new AssertionError("Reload attempted to mutate " + path);
        });
        try {
            Config loaded = Config.reloadFromDisk(fs());
            assertTrue(loaded.profiles.stream().anyMatch(p -> p.name().equals("Externally changed")));
            assertEquals("{broken", Files.readString(Config.getConfigPath(fs())));
            assertEquals("{broken", Files.readString(Config.getProfileMetadataPath(fs())));
        } finally {
            FaultingConfigFileSystem.current().observeMutations(null);
        }
    }

    @Test
    void unreadableProfileDirectoryEntersReadOnlyModeWithoutChangingFiles() throws IOException {
        Config main = new Config();
        main.enabled = false;
        ConfigFiles.writeJsonAtomic(fs(), Config.getConfigPath(fs()), main);
        Path profilePath = Config.getProfileDirectory(fs()).resolve("kept.json");
        ConfigFiles.writeJsonAtomic(fs(), profilePath, profile("Kept", "kept.json"));
        byte[] mainBefore = Files.readAllBytes(Config.getConfigPath(fs()));
        byte[] profileBefore = Files.readAllBytes(profilePath);
        FaultingConfigFileSystem.current().failListsWhen(
                path -> path.equals(Config.getProfileDirectory(fs())));

        Config loaded = Config.load(fs());

        assertTrue(loaded.isReadOnly());
        assertTrue(loaded.readOnlyWarning().contains(Config.getProfileDirectory(fs()).toString()));
        assertTrue(loaded.readOnlyWarning().contains("could not be read"));
        assertArrayEquals(mainBefore, Files.readAllBytes(Config.getConfigPath(fs())));
        assertArrayEquals(profileBefore, Files.readAllBytes(profilePath));
        try (Stream<Path> paths = Files.list(Config.getProfileDirectory(fs()))) {
            assertEquals(List.of("kept.json"), paths.map(path -> path.getFileName().toString()).toList());
        }
    }

    @Test
    void newerMainConfigEntersReadOnlyDefaultsAndPreservesBytes() throws IOException {
        byte[] future = ("{\"version\":" + (Config.CURRENT_VERSION + 1)
                + ",\"futureField\":{\"keep\":true}}")
                .getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(Config.getConfigPath(fs()).getParent());
        Files.write(Config.getConfigPath(fs()), future);

        Config loaded = Config.load(fs());

        assertTrue(loaded.isReadOnly());
        assertTrue(loaded.copy().isReadOnly());
        assertTrue(loaded.readOnlyWarning().contains("elytra-pitch-helper.json"));
        assertArrayEquals(future, Files.readAllBytes(Config.getConfigPath(fs())));
        assertThrows(NewerConfigVersionException.class, () -> loaded.save(fs()));
        assertArrayEquals(future, Files.readAllBytes(Config.getConfigPath(fs())));
    }

    @Test
    void fractionalFutureVersionEntersReadOnlyMode() throws IOException {
        Path mainPath = Config.getConfigPath(fs());
        Files.createDirectories(mainPath.getParent());
        Files.writeString(mainPath, "{\"version\":" + Config.CURRENT_VERSION + ".5}");

        ConfigVersionGuard.Result result = ConfigVersionGuard.detectNewerFiles(fs(), mainPath,
                Config.getProfileDirectory(fs()), Config.getProfileMetadataPath(fs()));

        assertTrue(result.foundNewerVersion());
        assertTrue(result.warning().contains(Config.CURRENT_VERSION + ".5"));
        String before = Files.readString(mainPath);
        assertTrue(Config.load(fs()).isReadOnly());
        assertEquals(before, Files.readString(mainPath));
    }

    @Test
    void enormousFutureVersionEntersReadOnlyModeWithoutIntegralExpansion() throws IOException {
        Path mainPath = Config.getConfigPath(fs());
        Files.createDirectories(mainPath.getParent());
        Files.writeString(mainPath, "{\"version\":1e100}");

        ConfigVersionGuard.Result result = ConfigVersionGuard.detectNewerFiles(fs(), mainPath,
                Config.getProfileDirectory(fs()), Config.getProfileMetadataPath(fs()));

        assertTrue(result.foundNewerVersion());
        assertEquals(new java.math.BigDecimal("1e100"), result.newerFiles().getFirst().version());
    }

    @Test
    void newerProfileEntersGlobalReadOnlyDefaultsWithoutTouchingAnyConfigFile() throws IOException {
        JsonObject main = new JsonObject();
        main.addProperty("version", Config.CURRENT_VERSION);
        main.addProperty("enabled", false);
        ConfigFiles.writeJsonAtomic(fs(), Config.getConfigPath(fs()), main);
        Path profilePath = Config.getProfileDirectory(fs()).resolve("future.json");
        byte[] futureProfile = ("{\"version\":" + (Config.CURRENT_VERSION + 7)
                + ",\"name\":\"Future\",\"futureSetting\":42}")
                .getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(profilePath.getParent());
        Files.write(profilePath, futureProfile);
        byte[] mainBefore = Files.readAllBytes(Config.getConfigPath(fs()));

        Config loaded = Config.load(fs());

        assertTrue(loaded.isReadOnly());
        assertTrue(loaded.readOnlyWarning().contains("future.json"));
        assertArrayEquals(mainBefore, Files.readAllBytes(Config.getConfigPath(fs())));
        assertArrayEquals(futureProfile, Files.readAllBytes(profilePath));
    }

    @Test
    void newerProfileMetadataEntersReadOnlyModeWithoutOverwritingUnknownFields() throws IOException {
        JsonObject main = new JsonObject();
        main.addProperty("version", Config.CURRENT_VERSION);
        main.addProperty("enabled", false);
        ConfigFiles.writeJsonAtomic(fs(), Config.getConfigPath(fs()), main);
        byte[] futureMetadata = ("{\"version\":" + (Config.CURRENT_VERSION + 3)
                + ",\"profiles\":[],\"futureMetadata\":{\"keep\":true}}")
                .getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(Config.getProfileMetadataPath(fs()).getParent());
        Files.write(Config.getProfileMetadataPath(fs()), futureMetadata);
        byte[] mainBefore = Files.readAllBytes(Config.getConfigPath(fs()));

        Config loaded = Config.load(fs());

        assertTrue(loaded.isReadOnly());
        assertTrue(loaded.readOnlyWarning().contains("profile-metadata.json"));
        assertArrayEquals(mainBefore, Files.readAllBytes(Config.getConfigPath(fs())));
        assertArrayEquals(futureMetadata, Files.readAllBytes(Config.getProfileMetadataPath(fs())));
    }

    @Test
    void startupSaveFailureKeepsLoadedConfigAvailableInReadOnlyMode() throws IOException {
        Config main = new Config();
        main.enabled = false;
        ConfigFiles.writeJsonAtomic(fs(), Config.getConfigPath(fs()), main);
        Profile kept = profile("Kept", "kept.json");
        kept = kept.withPitch(kept.pitch().withTargetUpMinecraft(-12.0f));
        Path profilePath = Config.getProfileDirectory(fs()).resolve("kept.json");
        ConfigFiles.writeJsonAtomic(fs(), profilePath, kept);
        byte[] mainBefore = Files.readAllBytes(Config.getConfigPath(fs()));
        byte[] profileBefore = Files.readAllBytes(profilePath);
        FaultingConfigFileSystem.current().failWritesWhen(path -> true);

        Config loaded = Config.load(fs());

        assertTrue(loaded.isReadOnly());
        assertTrue(loaded.readOnlyWarning().contains("could not be saved"));
        assertFalse(loaded.enabled);
        assertEquals("Kept", loaded.activeProfileName());
        assertEquals(-12.0f, loaded.pitch().targetUpMinecraft());
        assertArrayEquals(mainBefore, Files.readAllBytes(Config.getConfigPath(fs())));
        assertArrayEquals(profileBefore, Files.readAllBytes(profilePath));
        assertThrows(NewerConfigVersionException.class, () -> loaded.save(fs()));
    }

    @Test
    void malformedMainConfigIsBackedUpBeforeStartupReplacesIt() throws IOException {
        byte[] malformed = "{\"enabled\":true".getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(Config.getConfigPath(fs()).getParent());
        Files.write(Config.getConfigPath(fs()), malformed);

        Config loaded = Config.load(fs());

        assertFalse(loaded.isReadOnly());
        ProfileInvariants.assertValidConfig(loaded);
        assertTrue(backupContains(ConfigBackups.directory(fs(), "main"), malformed));
        JsonObject replacement = ConfigFiles.GSON.fromJson(
                Files.readString(Config.getConfigPath(fs()), StandardCharsets.UTF_8), JsonObject.class);
        assertEquals(Config.CURRENT_VERSION, replacement.get("version").getAsInt());
    }

    @Test
    void mainConfigBackupRotationUsesParsedLegacySequenceOrder() throws IOException {
        Path backupDirectory = ConfigBackups.directory(fs(), "main");
        Files.createDirectories(backupDirectory);
        String timestamp = "20000101-000000-000";
        for (int sequence = 1; sequence <= 8; sequence++) {
            String fileName = sequence == 1 ? timestamp + ".json" : timestamp + "-" + sequence + ".json";
            Files.writeString(backupDirectory.resolve(fileName), "legacy-" + sequence, StandardCharsets.UTF_8);
        }
        Files.createDirectories(Config.getConfigPath(fs()).getParent());
        Files.writeString(Config.getConfigPath(fs()), "current", StandardCharsets.UTF_8);

        ConfigBackups.saveMalformedMainOrMetadata(fs(), Config.getConfigPath(fs()), "main");

        assertEquals(8, jsonFileCount(backupDirectory));
        assertFalse(Files.exists(backupDirectory.resolve(timestamp + ".json")));
        assertTrue(Files.exists(backupDirectory.resolve(timestamp + "-2.json")));
    }

    @Test
    void malformedMainConfigRemainsUntouchedWhenRawBackupCannotBeCreated() throws IOException {
        byte[] malformed = "{\"enabled\":true".getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(Config.getConfigPath(fs()).getParent());
        Files.write(Config.getConfigPath(fs()), malformed);
        Path backupRoot = Config.getConfigDirectory(fs()).resolve("backups");
        Files.writeString(backupRoot, "not a directory", StandardCharsets.UTF_8);

        Config loaded = Config.load(fs());

        assertTrue(loaded.isReadOnly());
        assertArrayEquals(malformed, Files.readAllBytes(Config.getConfigPath(fs())));
    }

    @Test
    void malformedProfileMetadataIsBackedUpBeforeStartupReplacesIt() throws IOException {
        ConfigFiles.writeJsonAtomic(fs(), Config.getConfigPath(fs()), new Config());
        byte[] malformed = "{\"profiles\":[".getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(Config.getProfileMetadataPath(fs()).getParent());
        Files.write(Config.getProfileMetadataPath(fs()), malformed);

        Config loaded = Config.load(fs());

        assertFalse(loaded.isReadOnly());
        assertTrue(backupContains(ConfigBackups.directory(fs(), "profile-metadata"), malformed));
        JsonObject replacement = ConfigFiles.GSON.fromJson(
                Files.readString(Config.getProfileMetadataPath(fs()), StandardCharsets.UTF_8), JsonObject.class);
        assertEquals(Config.CURRENT_VERSION, replacement.get("version").getAsInt());
    }

    @Test
    void malformedProfileMetadataRemainsUntouchedWhenRawBackupCannotBeCreated() throws IOException {
        ConfigFiles.writeJsonAtomic(fs(), Config.getConfigPath(fs()), new Config());
        byte[] malformed = "{\"profiles\":[".getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(Config.getProfileMetadataPath(fs()).getParent());
        Files.write(Config.getProfileMetadataPath(fs()), malformed);
        Files.writeString(Config.getConfigDirectory(fs()).resolve("backups"), "not a directory",
                StandardCharsets.UTF_8);

        Config loaded = Config.load(fs());

        assertTrue(loaded.isReadOnly());
        assertTrue(loaded.readOnlyWarning().contains("profile-metadata.json"));
        assertArrayEquals(malformed, Files.readAllBytes(Config.getProfileMetadataPath(fs())));
    }

    @Test
    void readOnlyModeBlocksImmediateProfileDeletion() throws IOException {
        Config config = Config.load(fs());
        config.createProfile();
        config.save(fs());
        Path activeProfile = config.getProfilePath(fs(), config.activeProfileIndex);
        Path otherProfile = config.getProfilePath(fs(), config.activeProfileIndex == 0 ? 1 : 0);
        config.enterReadOnlyMode("test protection");

        assertThrows(NewerConfigVersionException.class, () -> config.deleteProfile(fs(), 0));

        assertTrue(Files.exists(activeProfile));
        assertTrue(Files.exists(otherProfile));
        assertEquals(2, config.profileCount());
    }

    @Test
    void lateMetadataWriteFailureRollsBackEarlierWritesAndRetrySucceeds() throws IOException {
        Config config = Config.load(fs());
        Path mainPath = Config.getConfigPath(fs());
        Path profilePath = config.getProfilePath(fs(), 0);
        byte[] mainBefore = Files.readAllBytes(mainPath);
        byte[] profileBefore = Files.readAllBytes(profilePath);
        config.enabled = !config.enabled;
        config.replaceProfile(0, config.profile(0).withName("Written before metadata failed"));
        FaultingConfigFileSystem.current().failWritesWhen(
                path -> path.equals(Config.getProfileMetadataPath(fs())));

        assertThrows(ConfigSaveException.class, () -> config.save(fs()));

        assertArrayEquals(mainBefore, Files.readAllBytes(mainPath));
        assertArrayEquals(profileBefore, Files.readAllBytes(profilePath));

        FaultingConfigFileSystem.current().failWritesWhen(null);
        config.save(fs());
        Config onDisk = ConfigFiles.GSON.fromJson(
                Files.readString(mainPath, StandardCharsets.UTF_8), Config.class);
        assertEquals(config.enabled, onDisk.enabled);
        Profile persistedProfile = ConfigFiles.GSON.fromJson(
                Files.readString(profilePath, StandardCharsets.UTF_8), Profile.class);
        assertEquals("Written before metadata failed", persistedProfile.name());
    }

    @Test
    void rollbackDoesNotOverwriteAnExternalEditToAFileThisSaveDidNotMutate() throws IOException {
        Config config = Config.load(fs());
        Path mainPath = Config.getConfigPath(fs());
        Path profilePath = config.getProfilePath(fs(), 0);
        byte[] mainBefore = Files.readAllBytes(mainPath);
        Profile external = config.profile(0).withName("External during failed save");
        AtomicBoolean changed = new AtomicBoolean();
        config.enabled = !config.enabled;
        FaultingConfigFileSystem.current().beforeAtomicMove(() -> {
            if (!changed.compareAndSet(false, true)) {
                return;
            }
            try {
                Files.writeString(profilePath, ConfigFiles.GSON.toJson(external), StandardCharsets.UTF_8);
            } catch (IOException failure) {
                throw new ConfigSaveException("Test edit failed", failure);
            }
        });
        FaultingConfigFileSystem.current().failWritesWhen(
                path -> path.equals(Config.getProfileMetadataPath(fs())));

        assertThrows(ConfigSaveException.class, () -> config.save(fs()));

        assertArrayEquals(mainBefore, Files.readAllBytes(mainPath));
        assertEquals("External during failed save",
                ProfilePersistence.readProfileFile(fs(), profilePath, external.fileName(),
                        "external profile", ProfileDefaults.template()).name());
    }

    @Test
    void failedSaveRetainsUnrecoverableProfileProtectionForRetry() throws IOException {
        Config config = Config.load(fs());
        Path profilePath = config.getProfilePath(fs(), 0);
        String diskProfileName = config.profileName(0);
        config.replaceProfile(0, config.profile(0).withName("Fallback must not be saved"));
        config.skipNextProfileSave = true;
        FaultingConfigFileSystem.current().failWritesWhen(
                path -> path.equals(Config.getProfileMetadataPath(fs())));

        assertThrows(ConfigSaveException.class, () -> config.save(fs()));

        assertTrue(config.skipNextProfileSave);
        FaultingConfigFileSystem.current().failWritesWhen(null);
        config.save(fs());

        assertFalse(config.skipNextProfileSave);
        Profile persisted = ConfigFiles.GSON.fromJson(
                Files.readString(profilePath, StandardCharsets.UTF_8), Profile.class);
        assertEquals(diskProfileName, persisted.name());
    }

    @Test
    void rolledBackProfileWriteDoesNotEnterBackupHistory() throws IOException {
        Config config = Config.load(fs());
        Path profilePath = config.getProfilePath(fs(), 0);
        Path backupDirectory = ProfileBackups.profileBackupDirectoryForTests(fs(), 
                profilePath.getFileName().toString());
        Map<String, byte[]> backupsBefore = directoryContents(backupDirectory);
        config.replaceProfile(0, config.profile(0).withName("Uncommitted profile state"));
        FaultingConfigFileSystem.current().failWritesWhen(
                path -> path.equals(Config.getProfileMetadataPath(fs())));

        assertThrows(ConfigSaveException.class, () -> config.save(fs()));

        Map<String, byte[]> backupsAfter = directoryContents(backupDirectory);
        assertEquals(backupsBefore.keySet(), backupsAfter.keySet());
        for (Map.Entry<String, byte[]> backup : backupsBefore.entrySet()) {
            assertArrayEquals(backup.getValue(), backupsAfter.get(backup.getKey()));
        }

        FaultingConfigFileSystem.current().failWritesWhen(null);
        config.save(fs());
        assertTrue(directoryContents(backupDirectory).size() > backupsBefore.size());
    }

    @Test
    void lateMetadataWriteFailureRestoresAnEarlierProfileDeletion() throws IOException {
        Config config = Config.load(fs());
        config.createProfile();
        config.save(fs());
        StagedProfileDelete deleted = config.stageDeleteProfile(0);
        assertTrue(deleted != null);
        Path deletedPath = Config.getProfileDirectory(fs()).resolve(deleted.fileName());
        assertTrue(Files.exists(deletedPath));
        FaultingConfigFileSystem.current().failWritesWhen(
                path -> path.equals(Config.getProfileMetadataPath(fs())));

        assertThrows(ConfigSaveException.class,
                () -> config.saveDeletingProfileFiles(fs(), List.of(deleted.fileName())));

        assertTrue(Files.exists(deletedPath));

        FaultingConfigFileSystem.current().failWritesWhen(null);
        config.saveDeletingProfileFiles(fs(), List.of(deleted.fileName()));
        assertFalse(Files.exists(deletedPath));
    }

    @Test
    void externalProfileEditConflictsBeforeDeletionIsStaged() throws IOException {
        Config config = Config.load(fs());
        config.createProfile();
        config.save(fs());
        StagedProfileDelete deleted = config.stageDeleteProfile(0);
        assertTrue(deleted != null);
        Path deletedPath = Config.getProfileDirectory(fs()).resolve(deleted.fileName());
        Files.writeString(deletedPath, Files.readString(deletedPath).replace("\"name\"", "\"external\""));

        assertThrows(ProfileConflictException.class,
                () -> config.saveDeletingProfileFiles(fs(), List.of(deleted.fileName())));

        assertTrue(Files.exists(deletedPath));
    }

    @Test
    void configScreenDeletionRestoresAnEditMadeImmediatelyBeforeStagingAndLeavesMainConfigUntouched()
            throws IOException {
        Config config = Config.load(fs());
        config.createProfile();
        config.save(fs());
        StagedProfileDelete deleted = config.stageDeleteProfile(0);
        assertTrue(deleted != null);
        Path deletedPath = Config.getProfileDirectory(fs()).resolve(deleted.fileName());
        Profile external = ProfilePersistence.readProfileFile(fs(), deletedPath, deleted.fileName(), "external edit", ProfileDefaults.template());
        external = external.withName("Edit during config-screen deletion");
        Profile externalEdit = external;
        byte[] mainBefore = Files.readAllBytes(Config.getConfigPath(fs()));
        config.enabled = !config.enabled;
        FaultingConfigFileSystem.current().beforeAtomicMove(() -> {
            try {
                ConfigFiles.writeJsonAtomic(
                        FaultingConfigFileSystem.current().undecorated(), deletedPath, externalEdit);
            } catch (IOException e) {
                throw new ConfigSaveException("Test edit failed", e);
            }
        });

        assertThrows(ProfileConflictException.class,
                () -> config.saveDeletingProfileFiles(fs(), List.of(deleted.fileName())));

        Profile preserved = ProfilePersistence.readProfileFile(fs(), deletedPath, deleted.fileName(),
                "preserved external edit", ProfileDefaults.template());
        assertEquals("Edit during config-screen deletion", preserved.name());
        assertArrayEquals(mainBefore, Files.readAllBytes(Config.getConfigPath(fs())));
    }

    @Test
    void deletionPreflightUsesCapturedBaselineRatherThanLaterDiskBytes() throws IOException {
        Config config = Config.load(fs());
        config.createProfile();
        config.save(fs());
        StagedProfileDelete deleted = config.stageDeleteProfile(0);
        assertTrue(deleted != null);
        Path deletedPath = Config.getProfileDirectory(fs()).resolve(deleted.fileName());
        byte[] loadedBytes = Files.readAllBytes(deletedPath);
        Files.writeString(deletedPath, Files.readString(deletedPath).replace("\"name\"", "\"external\""));
        Map<Path, DiskSnapshot> baseline = ProfilePersistence.captureBaseline(fs(), List.of(deletedPath));
        Files.write(deletedPath, loadedBytes);

        assertThrows(ProfileConflictException.class,
                () -> ProfilePersistence.preflightProfileDeletes(fs(), List.of(deleted.fileName()),
                        config, Set.of(), baseline));
    }

    @Test
    void deletionPreflightAcceptsAProfileThatIsAlreadyAbsent() throws IOException {
        Config config = Config.load(fs());
        config.createProfile();
        config.save(fs());
        StagedProfileDelete deleted = config.stageDeleteProfile(0);
        assertTrue(deleted != null);
        Path deletedPath = Config.getProfileDirectory(fs()).resolve(deleted.fileName());
        Files.delete(deletedPath);
        Map<Path, DiskSnapshot> baseline = ProfilePersistence.captureBaseline(fs(), List.of(deletedPath));

        ProfilePersistence.preflightProfileDeletes(fs(), List.of(deleted.fileName()),
                config, Set.of(), baseline);
    }

    @Test
    void failedSaveRestoresDeletedProfileSymlink() throws IOException {
        Config config = Config.load(fs());
        config.createProfile();
        config.save(fs());
        StagedProfileDelete deleted = config.stageDeleteProfile(0);
        assertTrue(deleted != null);
        Path profilePath = Config.getProfileDirectory(fs()).resolve(deleted.fileName());
        Path target = configRoot.resolve("shared-profile.json");
        Files.move(profilePath, target);
        try {
            Files.createSymbolicLink(profilePath, target);
        } catch (UnsupportedOperationException | IOException e) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Symbolic links are unavailable");
        }
        FaultingConfigFileSystem.current().failWritesWhen(
                path -> path.equals(Config.getProfileMetadataPath(fs())));

        assertThrows(ConfigSaveException.class,
                () -> config.saveDeletingProfileFiles(fs(), List.of(deleted.fileName())));

        assertTrue(Files.isSymbolicLink(profilePath));
        assertEquals(target, Files.readSymbolicLink(profilePath));
        assertTrue(Files.exists(target));

        FaultingConfigFileSystem.current().failWritesWhen(null);
        config.saveDeletingProfileFiles(fs(), List.of(deleted.fileName()));
        assertFalse(Files.exists(profilePath, LinkOption.NOFOLLOW_LINKS));
        assertTrue(Files.exists(target));
    }

    @Test
    void existingTargetIsReplacedByASingleAtomicRename() throws IOException {
        Path target = Config.getConfigPath(fs());
        Files.createDirectories(target.getParent());
        Files.writeString(target, "original", StandardCharsets.UTF_8);
        Path temporary = target.resolveSibling("publication.tmp");
        fs().writeDurable(temporary, target, "replacement".getBytes(StandardCharsets.UTF_8));

        fs().moveAtomic(temporary, target);

        assertEquals("replacement", Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    void publicationRefusesWhenAtomicReplaceIsUnavailableAndPreservesTheTarget() throws IOException {
        Path target = Config.getConfigPath(fs());
        Files.createDirectories(target.getParent());
        Files.writeString(target, "original", StandardCharsets.UTF_8);
        FaultingConfigFileSystem.current().rejectAtomicReplaceWhen(path -> path.equals(target));
        Path temporary = target.resolveSibling("fallback.tmp");
        fs().writeDurable(temporary, target, "replacement".getBytes(StandardCharsets.UTF_8));

        assertThrows(AtomicMoveNotSupportedException.class,
                () -> FaultingConfigFileSystem.current().moveAtomic(temporary, target));
        assertEquals("original", Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    void atomicCreateDoesNotCacheAtomicReplaceSupport() throws IOException {
        Path target = Config.getConfigDirectory(fs()).resolve("capability.json");
        ConfigFiles.writeBytesAtomic(
                FaultingConfigFileSystem.current(), target, "created".getBytes(StandardCharsets.UTF_8));
        FaultingConfigFileSystem.current().rejectAtomicReplaceWhen(path -> path.equals(target));

        assertThrows(AtomicMoveNotSupportedException.class,
                () -> ConfigFiles.writeBytesAtomic(FaultingConfigFileSystem.current(),
                        target, "replacement".getBytes(StandardCharsets.UTF_8)));
        assertEquals("created", Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    void atomicWritesCreateAndReplaceMaximumLengthFileNames() throws IOException {
        String fileName = ProfileFileNames.unique("a".repeat(300), new java.util.HashSet<>());
        assertEquals(255, fileName.length());
        Path target = Config.getProfileDirectory(fs()).resolve(fileName);

        ConfigFiles.writeBytesAtomic(fs(), target, "created".getBytes(StandardCharsets.UTF_8));
        assertEquals("created", Files.readString(target));
        ConfigFiles.writeBytesAtomic(fs(), target, "updated".getBytes(StandardCharsets.UTF_8));
        assertEquals("updated", Files.readString(target));
        try (var entries = Files.list(target.getParent())) {
            assertEquals(List.of(target), entries.toList());
        }
    }

    @Test
    void temporaryCleanupFailureDoesNotMaskPublicationFailure() {
        Path target = Config.getConfigDirectory(fs()).resolve("cleanup-failure.json");
        FaultingConfigFileSystem fileSystem = FaultingConfigFileSystem.current();
        fileSystem.failWritesWhen(path -> path.equals(target));
        fileSystem.failDeletesWhen(path -> path.getFileName().toString().endsWith(".tmp"));

        IOException failure = assertThrows(IOException.class,
                () -> ConfigFiles.writeBytesAtomic(fileSystem, target, "replacement".getBytes(StandardCharsets.UTF_8)));

        assertTrue(failure.getMessage().contains("Injected config write failure"));
        assertEquals(1, failure.getSuppressed().length);
        assertTrue(failure.getSuppressed()[0].getMessage().contains("Injected delete failure"));
    }

    @Test
    void temporaryCleanupFailureAfterPublicationDoesNotTurnTheSaveIntoAFailure() throws IOException {
        Path target = Config.getConfigDirectory(fs()).resolve("published-before-cleanup-failure.json");
        FaultingConfigFileSystem fileSystem = FaultingConfigFileSystem.current();
        fileSystem.failDeletesWhen(path -> path.getFileName().toString().endsWith(".tmp"));

        ConfigFiles.writeBytesAtomic(fileSystem, target, "published".getBytes(StandardCharsets.UTF_8));

        assertEquals("published", Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    void jsonValueComparisonIgnoresNumericRepresentationButNotValueChanges() {
        JsonObject parsed = ConfigFiles.GSON.fromJson(
                "{\"decimal\":2.00,\"nested\":{\"count\":3}}", JsonObject.class);
        JsonObject equivalent = ConfigFiles.GSON.fromJson(
                "{\"nested\":{\"count\":3.0},\"decimal\":2}", JsonObject.class);
        JsonObject changed = ConfigFiles.GSON.fromJson(
                "{\"decimal\":2.01,\"nested\":{\"count\":3}}", JsonObject.class);

        assertTrue(ConfigFiles.jsonValuesEqual(parsed, equivalent));
        assertFalse(ConfigFiles.jsonValuesEqual(parsed, changed));
    }

    @Test
    void jsonValueComparisonTreatsMissingAndExplicitNullAsEquivalent() {
        assertTrue(ConfigFiles.jsonValuesEqual(null, com.google.gson.JsonNull.INSTANCE));
        assertTrue(ConfigFiles.jsonValuesEqual(com.google.gson.JsonNull.INSTANCE, null));
    }

    @Test
    void profileChangedMidSaveIsNeverClobberedAndConflictsOnTheNextSave() throws IOException {
        Config config = new Config();
        config.save(fs());
        Path profilePath = Config.getProfileDirectory(fs()).resolve(config.profile(0).fileName());
        Config edited = config.copy();
        edited.enabled = !edited.enabled;
        Profile external = profile("External", config.profile(0).fileName());
        AtomicBoolean changed = new AtomicBoolean();
        FaultingConfigFileSystem.current().observeTemporaryPaths(temporary -> {
            if (!changed.compareAndSet(false, true)) {
                return;
            }
            try {
                Files.writeString(profilePath, ConfigFiles.GSON.toJson(external), StandardCharsets.UTF_8);
            } catch (IOException failure) {
                throw new RuntimeException(failure);
            }
        });

        edited.save(fs());

        FaultingConfigFileSystem.current().observeTemporaryPaths(null);
        Profile preserved = ProfilePersistence.readProfileFile(fs(), 
                profilePath, external.fileName(), "external profile", ProfileDefaults.template());
        assertEquals("External", preserved.name());

        Config later = edited.copy();
        later.replaceProfile(0, later.profile(0).withName("Later in-game edit"));
        assertThrows(ProfileConflictException.class, () -> later.save(fs()));
        assertEquals("External", ProfilePersistence.readProfileFile(fs(), profilePath, external.fileName(),
                "external profile", ProfileDefaults.template()).name());
    }

    @Test
    void profileBackupRestoreReinstallsTheNewestValidBackup() throws IOException {
        Path profilePath = Config.getProfileDirectory(fs()).resolve("broken.json");
        ProfileBackups.save(fs(), profilePath, profile("Restored", "broken.json"));
        Files.createDirectories(profilePath.getParent());
        Files.writeString(profilePath, "{broken", StandardCharsets.UTF_8);

        Profile restored = ProfileBackups.restore(fs(), profilePath, null);

        assertEquals("Restored", restored.name());
        Profile installed = ProfilePersistence.readProfileFile(fs(), 
                profilePath, "broken.json", "restored profile", ProfileDefaults.template());
        assertEquals("Restored", installed.name());
        assertTrue(backupContains(
                ProfileBackups.profileBackupDirectoryForTests(fs(), "broken.json"),
                "{broken".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void profileBackupRestoreRefusesWhenAtomicReplaceIsUnavailable() throws IOException {
        Path profilePath = Config.getProfileDirectory(fs()).resolve("broken.json");
        ProfileBackups.save(fs(), profilePath, profile("Restored", "broken.json"));
        Files.createDirectories(profilePath.getParent());
        Files.writeString(profilePath, "{broken", StandardCharsets.UTF_8);
        FaultingConfigFileSystem.current().rejectAtomicReplaceWhen(path -> path.equals(profilePath));

        assertNull(ProfileBackups.restore(FaultingConfigFileSystem.current(), profilePath, null));

        assertEquals("{broken", Files.readString(profilePath, StandardCharsets.UTF_8));
        assertTrue(backupContains(
                ProfileBackups.profileBackupDirectoryForTests(fs(), "broken.json"),
                "{broken".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void legacyBackupMigrationDoesNotEmptyASymlinkedDirectory() throws IOException {
        Path profilePath = Config.getProfileDirectory(fs()).resolve("linked.json");
        Files.createDirectories(profilePath.getParent());
        ConfigFiles.writeJsonAtomic(fs(), profilePath, minimalProfile("Linked", "linked.json"));

        Path outsider = configRoot.resolve("somewhere-else");
        Files.createDirectories(outsider);
        Path outsiderBackup = outsider.resolve("20200101-000000-000~000001.json");
        ConfigFiles.writeJsonAtomic(fs(), outsiderBackup, minimalProfile("Linked", "linked.json"));

        Path legacyDirectory = ProfileBackups.legacyProfileBackupDirectoryForTests(fs(), "oldname.json");
        Files.createDirectories(legacyDirectory.getParent());
        try {
            Files.createSymbolicLink(legacyDirectory, outsider);
        } catch (IOException | UnsupportedOperationException unsupported) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Symbolic links are unavailable");
        }

        ProfileBackups.latestValid(fs(), profilePath, "oldname.json");

        assertTrue(Files.exists(outsiderBackup), "migration deleted a file outside the config directory");
        assertTrue(Files.exists(legacyDirectory, LinkOption.NOFOLLOW_LINKS), "migration unlinked the symlink");
    }

    @Test
    void concurrentSaveAttemptFailsWithARetryableException() throws IOException {
        Config config = Config.load(fs());
        Path lockPath = Config.getConfigDirectory(fs()).resolve(".internal").resolve("config-save.lock");
        Files.createDirectories(lockPath.getParent());
        try (FileChannel channel = FileChannel.open(lockPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock ignored = channel.lock()) {
            ConfigSaveException failure = assertThrows(ConfigSaveException.class, () -> config.save(fs()));
            assertTrue(failure.isRetryable());
        }

        config.save(fs());
    }

    @Test
    void lossySlugCollisionsUseDifferentBackupDirectories() throws IOException {
        Path spaced = writeRawProfile("foo bar.json", "spaced");
        Path plus = writeRawProfile("foo+bar.json", "plus");
        Path dashed = writeRawProfile("foo-bar.json", "dashed");

        ProfileBackups.saveRawOrThrow(fs(), spaced);
        ProfileBackups.saveRawOrThrow(fs(), plus);
        ProfileBackups.saveRawOrThrow(fs(), dashed);

        Path spacedBackups = ProfileBackups.profileBackupDirectoryForTests(fs(), "foo bar.json");
        Path plusBackups = ProfileBackups.profileBackupDirectoryForTests(fs(), "foo+bar.json");
        Path dashedBackups = ProfileBackups.profileBackupDirectoryForTests(fs(), "foo-bar.json");
        assertNotEquals(spacedBackups, plusBackups);
        assertNotEquals(spacedBackups, dashedBackups);
        assertNotEquals(plusBackups, dashedBackups);
        assertTrue(backupContains(spacedBackups, "spaced".getBytes(StandardCharsets.UTF_8)));
        assertTrue(backupContains(plusBackups, "plus".getBytes(StandardCharsets.UTF_8)));
        assertTrue(backupContains(dashedBackups, "dashed".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void caseDistinctProfileNamesShareCanonicalBackupIdentity() {
        assertEquals(ProfileBackups.profileBackupDirectoryForTests(fs(), "Foo.json"),
                ProfileBackups.profileBackupDirectoryForTests(fs(), "foo.json"));
        assertNotEquals(ProfileBackups.legacyProfileBackupDirectoryForTests(fs(), "Foo.json"),
                ProfileBackups.legacyProfileBackupDirectoryForTests(fs(), "foo.json"));
    }

    @Test
    void caseDistinctProfilesAreRejectedWithoutChangingDiskOnCaseSensitiveFileSystems() throws IOException {
        Path profileDirectory = Config.getProfileDirectory(fs());
        Files.createDirectories(profileDirectory);
        Path upper = profileDirectory.resolve("Foo.json");
        Path lower = profileDirectory.resolve("foo.json");
        ConfigFiles.writeJsonAtomic(fs(), upper, profile("Upper", "Foo.json"));
        org.junit.jupiter.api.Assumptions.assumeFalse(Files.exists(lower),
                "The temporary filesystem is case-insensitive");
        ConfigFiles.writeJsonAtomic(fs(), lower, profile("Lower", "foo.json"));
        byte[] upperBefore = Files.readAllBytes(upper);
        byte[] lowerBefore = Files.readAllBytes(lower);

        Config config = Config.reloadFromDisk(fs());
        assertTrue(config.isReadOnly());
        assertTrue(config.readOnlyWarning().contains("Foo.json"));
        assertTrue(config.readOnlyWarning().contains("foo.json"));
        assertTrue(config.readOnlyWarning().contains("Rename"));
        assertThrows(NewerConfigVersionException.class, () -> config.save(fs()));

        Config reloaded = Config.reloadFromDisk(fs());
        assertTrue(reloaded.isReadOnly());

        assertArrayEquals(upperBefore, Files.readAllBytes(upper));
        assertArrayEquals(lowerBefore, Files.readAllBytes(lower));
        try (Stream<Path> paths = Files.list(profileDirectory)) {
            assertEquals(2, paths.filter(path -> ConfigFiles.isJsonFile(fs(), path)).count());
        }
    }

    @Test
    void caseDistinctProfileCollisionsAreDetectedPortably() {
        assertEquals(List.of("Foo.json", "foo.json", "BAR.JSON", "bar.json"),
                ProfilePersistence.caseInsensitiveCollisions(
                        List.of("Foo.json", "foo.json", "unrelated.json", "BAR.JSON", "bar.json")));
    }

    @Test
    void ownerlessLegacyBackupDirectoryIsNeverClaimedAutomatically() throws IOException {
        Path profilePath = writeRawProfile("alpha.json", "{broken");
        Path legacy = Config.getConfigDirectory(fs()).resolve("backups/profiles/alpha");
        Files.createDirectories(legacy);
        ConfigFiles.writeJsonAtomic(fs(), legacy.resolve("20260710-120000-000.json"), profile("Legacy", "alpha.json"));

        Profile restored = ProfileBackups.latestValid(fs(), profilePath, null);

        assertNull(restored);
        assertTrue(Files.isDirectory(legacy));
        assertFalse(Files.exists(ProfileBackups.profileBackupDirectoryForTests(fs(), "alpha.json")));
    }

    @Test
    void ambiguousLegacySlugIsNeverUsedForAnotherProfile() throws IOException {
        Path spaced = writeRawProfile("foo bar.json", "{broken");
        writeRawProfile("foo+bar.json", "{also broken");
        Path legacy = Config.getConfigDirectory(fs()).resolve("backups/profiles/foo-bar");
        Files.createDirectories(legacy);
        ConfigFiles.writeJsonAtomic(fs(), legacy.resolve("20260710-120000-000.json"),
                profile("Unknown owner", "foo bar.json"));

        assertNull(ProfileBackups.latestValid(fs(), spaced, null));
        assertTrue(Files.isDirectory(legacy));
    }

    private Path writeRawProfile(String fileName, String contents) throws IOException {
        Path path = Config.getProfileDirectory(fs()).resolve(fileName);
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents, StandardCharsets.UTF_8);
        return path;
    }

    private static Profile profile(String name, String fileName) {
        return minimalProfile(name, fileName);
    }
}
