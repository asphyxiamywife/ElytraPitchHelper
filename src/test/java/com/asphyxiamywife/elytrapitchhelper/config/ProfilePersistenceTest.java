package com.asphyxiamywife.elytrapitchhelper.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProfilePersistenceTest {
    @TempDir
    Path configRoot;

    @BeforeEach
    void setConfigRoot() {
        Config.setConfigRootOverrideForTests(configRoot);
    }

    @AfterEach
    void clearConfigRoot() {
        Config.setConfigRootOverrideForTests(null);
    }

    @Test
    void saveProfilesWritesChangedProfileAndCreatesBackup() throws IOException {
        Path profilePath = writeProfile(profile("Alpha", "alpha.json"));
        long loadedAt = Files.getLastModifiedTime(profilePath).toMillis();
        ProfileMetadataStore metadata = loadedMetadata("alpha.json", loadedAt);
        Profile updated = profile("Alpha Updated", "alpha.json");
        updated.targetUpMinecraft = -35.0f;

        assertTrue(ProfilePersistence.saveProfiles(List.of(updated), metadata));

        Profile saved = ProfilePersistence.readProfileFile(profilePath, "alpha.json", "saved profile",
                profile("Defaults", "default.json"));
        assertEquals("Alpha Updated", saved.name);
        assertEquals(-35.0f, saved.targetUpMinecraft);
        assertEquals(1, jsonFileCount(profileBackupDirectory("alpha")));
        assertEquals(Files.getLastModifiedTime(profilePath).toMillis(),
                metadata.profile("alpha.json").loadedFileModifiedAtMillis);
        assertTrue(metadata.profile("alpha.json").lastModifiedAtMillis >= loadedAt);
    }

    @Test
    void saveProfilesSkipsProfileChangedExternallySinceLoad() throws IOException {
        Path profilePath = writeProfile(profile("Original", "alpha.json"));
        long loadedAt = Files.getLastModifiedTime(profilePath).toMillis();
        ProfileMetadataStore metadata = loadedMetadata("alpha.json", loadedAt);
        Profile external = profile("External", "alpha.json");
        ConfigFiles.writeJsonAtomic(profilePath, external);
        Files.setLastModifiedTime(profilePath, FileTime.fromMillis(loadedAt + 10_000L));

        assertFalse(ProfilePersistence.saveProfiles(List.of(profile("In Memory", "alpha.json")), metadata));

        Profile saved = ProfilePersistence.readProfileFile(profilePath, "alpha.json", "saved profile",
                profile("Defaults", "default.json"));
        assertEquals("External", saved.name);
    }

    @Test
    void loadProfilesRestoresNewestValidBackupWhenProfileJsonIsCorrupt() throws IOException {
        Path profilePath = Config.getProfileDirectory().resolve("broken.json");
        Profile backup = profile("Restored", "broken.json");
        backup.targetDownMinecraft = 55.0f;
        ProfileBackups.save(profilePath, backup);
        Files.createDirectories(profilePath.getParent());
        Files.writeString(profilePath, "{broken", StandardCharsets.UTF_8);

        ProfilePersistence.LoadResult result = ProfilePersistence.loadProfiles(false, new ProfileMetadataStore());

        assertEquals(1, result.profiles().size());
        assertEquals("Restored", result.profiles().get(0).name);
        assertEquals(55.0f, result.profiles().get(0).targetDownMinecraft);
        Profile restored = ProfilePersistence.readProfileFile(profilePath, "broken.json", "restored profile",
                profile("Defaults", "default.json"));
        assertEquals("Restored", restored.name);
    }

    private static Path writeProfile(Profile profile) throws IOException {
        Path profilePath = Config.getProfileDirectory().resolve(profile.fileName);
        ConfigFiles.writeJsonAtomic(profilePath, profile);
        return profilePath;
    }

    private static ProfileMetadataStore loadedMetadata(String fileName, long loadedAt) {
        ProfileMetadataStore metadata = new ProfileMetadataStore();
        metadata.profile(fileName).markLoaded(loadedAt, "Custom");
        return metadata;
    }

    private static long jsonFileCount(Path directory) throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.filter(ConfigFiles::isJsonFile).count();
        }
    }

    private static Path profileBackupDirectory(String slug) {
        return Config.getConfigDirectory().resolve("backups").resolve("profiles").resolve(slug);
    }

    private static Profile profile(String name, String fileName) {
        Profile profile = new Profile();
        profile.name = name;
        profile.fileName = fileName;
        profile.showOnlyWithFirework = false;
        profile.showInThirdPerson = false;
        profile.targetUpMinecraft = -40.0f;
        profile.targetDownMinecraft = 40.0f;
        profile.toleranceDegrees = 6.0f;
        profile.maxOffsetPixels = 42;
        profile.offsetPerDegree = 2.0f;
        profile.lineLengthPixels = 26;
        profile.lineWidthPixels = 2;
        profile.lineColorRgb = 0xFFFFFF;
        profile.linePrideEnabled = false;
        profile.linePrideFlag = "rainbow";
        profile.amplitudeHelperEnabled = true;
        profile.amplitudeTriggerMode = Config.AMPLITUDE_TRIGGER_HEIGHT;
        profile.amplitudeDownBlocks = 50;
        profile.amplitudeUpBlocks = 45;
        profile.amplitudeToleranceBlocks = 4;
        profile.amplitudeDownVelocity = 2.0f;
        profile.amplitudeUpVelocity = 0.2f;
        profile.amplitudeCueColorRgb = 0xFF0000;
        profile.amplitudeCuePrideEnabled = false;
        profile.amplitudeCuePrideFlag = "trans";
        return profile;
    }
}
