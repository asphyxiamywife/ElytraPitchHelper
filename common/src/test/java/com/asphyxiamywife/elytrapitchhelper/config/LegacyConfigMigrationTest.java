package com.asphyxiamywife.elytrapitchhelper.config;

import static com.asphyxiamywife.elytrapitchhelper.config.ConfigTestFixtures.fs;

import com.asphyxiamywife.elytrapitchhelper.testing.IsolatedConfigRoot;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LegacyConfigMigrationTest {
    @TempDir
    Path tempDir;

    @IsolatedConfigRoot
    Path configRoot;

    @Test
    void missingLegacyConfigDoesNotMigrate() {
        assertNull(LegacyConfigMigration.load(fs(), tempDir.resolve("missing.json"), tempDir.resolve("new.json")));
    }

    @Test
    void migratesLegacyFieldsAndClampsUnsafeValues() throws IOException {
        Path legacyPath = tempDir.resolve("legacy.json");
        JsonObject legacy = new JsonObject();
        legacy.addProperty("enabled", false);
        legacy.addProperty("showOnlyWithFirework", true);
        legacy.addProperty("targetUpMinecraft", -120.0f);
        legacy.addProperty("targetDownMinecraft", 120.0f);
        legacy.addProperty("toleranceDegrees", 0.0f);
        legacy.addProperty("maxOffsetPixels", 500);
        legacy.addProperty("offsetPerDegree", 0.0f);
        legacy.addProperty("lineColorRgb", -1);
        ConfigFiles.writeJsonAtomic(fs(), legacyPath, legacy);

        Config migrated = LegacyConfigMigration.load(fs(), legacyPath, tempDir.resolve("new.json"));
        Profile profile = migrated.profile(0);

        assertFalse(migrated.enabled);
        assertEquals(1, migrated.profileCount());
        assertEquals(ProfileDefaults.DEFAULT_PROFILE_FILE_NAME, profile.fileName());
        assertTrue(profile.visibility().showOnlyWithFirework());
        assertEquals(-90.0f, profile.pitch().targetUpMinecraft());
        assertEquals(90.0f, profile.pitch().targetDownMinecraft());
        assertEquals(1.0f, profile.pitch().toleranceDegrees());
        assertEquals(200, profile.pitch().maxOffsetPixels());
        assertEquals(0.25f, profile.pitch().offsetPerDegree());
        assertEquals(0xFFFFFF, profile.line().colorRgb());
    }

    @Test
    void aFirstLaunchOnAV1InstallMigratesAndThenLeavesTheOldFileAlone() throws IOException {
        Path legacyPath = fs().configRoot().resolve("elytra-pitch-helper.json");
        JsonObject legacy = new JsonObject();
        legacy.addProperty("targetUpMinecraft", -35.0f);
        legacy.addProperty("lineColorRgb", 0x33FF66);
        ConfigFiles.writeJsonAtomic(fs(), legacyPath, legacy);

        Config migrated = Config.load(fs());

        assertEquals(1, migrated.profileCount());
        assertEquals(-35.0f, migrated.profile(0).pitch().targetUpMinecraft());
        assertEquals(0x33FF66, migrated.profile(0).line().colorRgb());
        assertTrue(Files.exists(Config.getProfileDirectory(fs())
                .resolve(ProfileDefaults.DEFAULT_PROFILE_FILE_NAME)));
        assertTrue(Files.exists(legacyPath));
        assertEquals(migrated.profile(0), Config.load(fs()).profile(0));
    }

    @Test
    void malformedLegacyConfigFallsBackWithoutThrowing() throws IOException {
        Path legacyPath = tempDir.resolve("legacy.json");
        Files.writeString(legacyPath, "{broken", StandardCharsets.UTF_8);

        assertNull(LegacyConfigMigration.load(fs(), legacyPath, tempDir.resolve("new.json")));
    }
}
