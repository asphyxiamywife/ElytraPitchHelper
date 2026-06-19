package com.asphyxiamywife.elytrapitchhelper.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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
        updated.pitch.targetUpMinecraft = -35.0f;

        assertTrue(ProfilePersistence.saveProfiles(List.of(updated), metadata));

        Profile saved = ProfilePersistence.readProfileFile(profilePath, "alpha.json", "saved profile",
                profile("Defaults", "default.json"));
        assertEquals("Alpha Updated", saved.name);
        assertEquals(-35.0f, saved.pitch.targetUpMinecraft);
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
    void restoredSnapshotCanOverwriteTheResetFileItReplaces() throws IOException {
        Profile original = profile("Original", "alpha.json");
        Path profilePath = writeProfile(original);
        long loadedAt = Files.getLastModifiedTime(profilePath).toMillis();
        Config config = new Config();
        config.profiles = new ArrayList<>(List.of(original));
        config.activeProfileIndex = 0;
        config.activeProfileFile = original.fileName;
        original.bindTo(config);
        config.profileMetadata = loadedMetadata("alpha.json", loadedAt);
        Config snapshot = config.copy();

        config.profile(0).pitch.targetUpMinecraft = -25.0f;
        assertTrue(ProfilePersistence.saveProfiles(config.profiles, config.profileMetadata));

        config.restoreProfileStateFrom(snapshot, "alpha.json");
        assertTrue(ProfilePersistence.saveProfiles(config.profiles, config.profileMetadata));

        Profile saved = ProfilePersistence.readProfileFile(profilePath, "alpha.json", "saved profile",
                profile("Defaults", "default.json"));
        assertEquals(-40.0f, saved.pitch.targetUpMinecraft);
    }

    @Test
    void fullSnapshotRestoreKeepsCurrentLoadedTimestampAndProfileIdentity() throws IOException {
        Profile original = profile("Original", "alpha.json");
        Path profilePath = writeProfile(original);
        long loadedAt = Files.getLastModifiedTime(profilePath).toMillis();
        Config config = new Config();
        config.profiles = new ArrayList<>(List.of(original));
        config.activeProfileIndex = 0;
        config.activeProfileFile = original.fileName;
        original.bindTo(config);
        config.profileMetadata = loadedMetadata("alpha.json", loadedAt);
        Config snapshot = config.copy();

        config.profile(0).pitch.targetUpMinecraft = -25.0f;
        assertTrue(ProfilePersistence.saveProfiles(config.profiles, config.profileMetadata));

        snapshot.prepareSnapshotRestoreFrom(config);
        config.restoreFrom(snapshot);
        assertSame(original, config.profile(0));
        assertTrue(ProfilePersistence.saveProfiles(config.profiles, config.profileMetadata));

        Profile saved = ProfilePersistence.readProfileFile(profilePath, "alpha.json", "saved profile",
                profile("Defaults", "default.json"));
        assertEquals(-40.0f, saved.pitch.targetUpMinecraft);
    }

    @Test
    void loadProfilesRestoresNewestValidBackupWhenProfileJsonIsCorrupt() throws IOException {
        Path profilePath = Config.getProfileDirectory().resolve("broken.json");
        Profile backup = profile("Restored", "broken.json");
        backup.pitch.targetDownMinecraft = 55.0f;
        ProfileBackups.save(profilePath, backup);
        Files.createDirectories(profilePath.getParent());
        Files.writeString(profilePath, "{broken", StandardCharsets.UTF_8);

        ProfilePersistence.LoadResult result = ProfilePersistence.loadProfiles(false, new ProfileMetadataStore());

        assertEquals(1, result.profiles().size());
        assertEquals("Restored", result.profiles().get(0).name);
        assertEquals(55.0f, result.profiles().get(0).pitch.targetDownMinecraft);
        Profile restored = ProfilePersistence.readProfileFile(profilePath, "broken.json", "restored profile",
                profile("Defaults", "default.json"));
        assertEquals("Restored", restored.name);
    }

    @Test
    void saveProfilesMigratesLegacyFlatProfileJsonToGroupedValueObjects() throws IOException {
        Profile legacyProfile = profile("Legacy", "legacy.json");
        Path profilePath = Config.getProfileDirectory().resolve(legacyProfile.fileName);
        JsonObject legacyJson = legacyFlatJson(legacyProfile);
        ConfigFiles.writeJsonAtomic(profilePath, legacyJson);
        long loadedAt = Files.getLastModifiedTime(profilePath).toMillis();
        Profile loaded = ProfilePersistence.readProfileFile(profilePath, "legacy.json", "legacy profile",
                profile("Defaults", "default.json"));
        ProfileMetadataStore metadata = loadedMetadata("legacy.json", loadedAt);

        assertTrue(ProfilePersistence.saveProfiles(List.of(loaded), metadata));

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
        profile.visibility.showOnlyWithFirework = false;
        profile.visibility.showInThirdPerson = false;
        profile.pitch.targetUpMinecraft = -40.0f;
        profile.pitch.targetDownMinecraft = 40.0f;
        profile.pitch.toleranceDegrees = 6.0f;
        profile.pitch.maxOffsetPixels = 42;
        profile.pitch.offsetPerDegree = 2.0f;
        profile.line.lengthPixels = 26;
        profile.line.widthPixels = 2;
        profile.line.colorRgb = 0xFFFFFF;
        profile.line.prideEnabled = false;
        profile.line.prideFlag = "rainbow";
        profile.amplitude.enabled = true;
        profile.amplitude.triggerMode = Config.AMPLITUDE_TRIGGER_HEIGHT;
        profile.amplitude.downBlocks = 50;
        profile.amplitude.upBlocks = 45;
        profile.amplitude.toleranceBlocks = 4;
        profile.amplitude.downVelocity = 2.0f;
        profile.amplitude.upVelocity = 0.2f;
        profile.amplitude.cueColorRgb = 0xFF0000;
        profile.amplitude.cuePrideEnabled = false;
        profile.amplitude.cuePrideFlag = "trans";
        return profile;
    }

    private static JsonObject legacyFlatJson(Profile profile) {
        JsonObject json = new JsonObject();
        json.addProperty("version", 1);
        json.addProperty("name", profile.name);
        json.addProperty("showOnlyWithFirework", profile.visibility.showOnlyWithFirework);
        json.addProperty("showInThirdPerson", profile.visibility.showInThirdPerson);
        json.addProperty("targetUpMinecraft", profile.pitch.targetUpMinecraft);
        json.addProperty("targetDownMinecraft", profile.pitch.targetDownMinecraft);
        json.addProperty("toleranceDegrees", profile.pitch.toleranceDegrees);
        json.addProperty("maxOffsetPixels", profile.pitch.maxOffsetPixels);
        json.addProperty("offsetPerDegree", profile.pitch.offsetPerDegree);
        json.addProperty("lineLengthPixels", profile.line.lengthPixels);
        json.addProperty("lineWidthPixels", profile.line.widthPixels);
        json.addProperty("lineColorRgb", profile.line.colorRgb);
        json.addProperty("linePrideEnabled", profile.line.prideEnabled);
        json.addProperty("linePrideFlag", profile.line.prideFlag);
        json.addProperty("amplitudeHelperEnabled", profile.amplitude.enabled);
        json.addProperty("amplitudeTriggerMode", profile.amplitude.triggerMode);
        json.addProperty("amplitudeDownBlocks", profile.amplitude.downBlocks);
        json.addProperty("amplitudeUpBlocks", profile.amplitude.upBlocks);
        json.addProperty("amplitudeToleranceBlocks", profile.amplitude.toleranceBlocks);
        json.addProperty("amplitudeDownVelocity", profile.amplitude.downVelocity);
        json.addProperty("amplitudeUpVelocity", profile.amplitude.upVelocity);
        json.addProperty("amplitudeCueColorRgb", profile.amplitude.cueColorRgb);
        json.addProperty("amplitudeCuePrideEnabled", profile.amplitude.cuePrideEnabled);
        json.addProperty("amplitudeCuePrideFlag", profile.amplitude.cuePrideFlag);
        return json;
    }
}
