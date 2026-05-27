package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.ModConstants;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

final class LegacyConfigMigration {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModConstants.MOD_ID);

    private LegacyConfigMigration() {
    }

    static Config load(Path legacyPath, Path nextPath) {
        if (!Files.exists(legacyPath)) {
            return null;
        }

        try (BufferedReader reader = Files.newBufferedReader(legacyPath, StandardCharsets.UTF_8)) {
            JsonObject root = ConfigFiles.GSON.fromJson(reader, JsonObject.class);
            if (root == null) {
                return null;
            }

            Config.RepairLog repairs = new Config.RepairLog("legacy config " + legacyPath);
            Profile defaults = ProfileDefaults.template();
            Profile migratedProfile = defaults.copy();
            migratedProfile.name = defaults.name == null || defaults.name.isBlank() ? "Default" : defaults.name;
            migratedProfile.fileName = ProfileDefaults.DEFAULT_PROFILE_FILE_NAME;
            migratedProfile.visibility.showOnlyWithFirework = ConfigJsonReader.readOptionalBoolean(root,
                    "showOnlyWithFirework", migratedProfile.visibility.showOnlyWithFirework, repairs);
            migratedProfile.pitch.targetUpMinecraft = ConfigJsonReader.readLegacyFloat(root, "targetUpMinecraft",
                    migratedProfile.pitch.targetUpMinecraft, -90.0f, 0.0f, repairs);
            migratedProfile.pitch.targetDownMinecraft = ConfigJsonReader.readLegacyFloat(root, "targetDownMinecraft",
                    migratedProfile.pitch.targetDownMinecraft, 0.0f, 90.0f, repairs);
            migratedProfile.pitch.toleranceDegrees = ConfigJsonReader.readLegacyFloat(root, "toleranceDegrees",
                    migratedProfile.pitch.toleranceDegrees, 1.0f, 45.0f, repairs);
            migratedProfile.pitch.maxOffsetPixels = ConfigJsonReader.readLegacyInt(root, "maxOffsetPixels",
                    migratedProfile.pitch.maxOffsetPixels, 0, 200, repairs);
            migratedProfile.pitch.offsetPerDegree = ConfigJsonReader.readLegacyFloat(root, "offsetPerDegree",
                    migratedProfile.pitch.offsetPerDegree, 0.25f, 10.0f, repairs);
            migratedProfile.line.colorRgb = ConfigJsonReader.readLegacyColor(root, "lineColorRgb",
                    migratedProfile.line.colorRgb, repairs);
            migratedProfile.sanitize(repairs, defaults);

            Config cfg = new Config();
            cfg.enabled = ConfigJsonReader.readOptionalBoolean(root, "enabled", cfg.enabled, repairs);
            cfg.profiles = new ArrayList<>();
            cfg.profiles.add(migratedProfile);
            cfg.activeProfileIndex = 0;
            cfg.activeProfileFile = migratedProfile.fileName;
            cfg.profileSortMode = Config.PROFILE_SORT_CREATED;
            repairs.log();
            LOGGER.info("Migrated legacy Elytra Pitch Helper config from {} to {}", legacyPath, nextPath);
            return cfg;
        } catch (IOException | JsonParseException | IllegalStateException e) {
            LOGGER.warn("Failed to migrate legacy config {}, using defaults", legacyPath, e);
            return null;
        }
    }
}
