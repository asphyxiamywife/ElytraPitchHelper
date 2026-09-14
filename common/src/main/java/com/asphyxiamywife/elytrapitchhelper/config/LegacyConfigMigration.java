package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.ModConstants;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;

final class LegacyConfigMigration {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModConstants.MOD_ID);

    private LegacyConfigMigration() {
    }


    static Config load(ConfigFileSystem fileSystem, Path legacyPath, Path nextPath) {
        if (!fileSystem.exists(legacyPath, false)) {
            return null;
        }

        JsonObject root = ConfigJsonFiles.read(fileSystem, legacyPath, JsonObject.class, failure -> {
            LOGGER.warn("Failed to migrate legacy config {}, using defaults", legacyPath, failure);
            return null;
        });
        if (root == null) {
            return null;
        }

        RepairLog repairs = new RepairLog("legacy config " + legacyPath);
        Profile defaults = ProfileDefaults.template();
        Profile migratedProfile = defaults.withName(defaults.name())
                .withFileName(ProfileDefaults.DEFAULT_PROFILE_FILE_NAME);
        migratedProfile = migratedProfile.withVisibility(
                migratedProfile.visibility().withShowOnlyWithFirework(
                        ConfigJsonReader.readOptionalBoolean(root, "showOnlyWithFirework",
                                migratedProfile.visibility().showOnlyWithFirework(), repairs)));
        PitchSettings pitch = migratedProfile.pitch();
        pitch = pitch.withTargetUpMinecraft(ConfigJsonReader.readLegacyFloat(root, "targetUpMinecraft",
                pitch.targetUpMinecraft(), -90.0f, 0.0f, repairs));
        pitch = pitch.withTargetDownMinecraft(ConfigJsonReader.readLegacyFloat(root, "targetDownMinecraft",
                pitch.targetDownMinecraft(), 0.0f, 90.0f, repairs));
        pitch = pitch.withToleranceDegrees(ConfigJsonReader.readLegacyFloat(root, "toleranceDegrees",
                pitch.toleranceDegrees(), 1.0f, 45.0f, repairs));
        pitch = pitch.withMaxOffsetPixels(ConfigJsonReader.readLegacyInt(root, "maxOffsetPixels",
                pitch.maxOffsetPixels(), 0, 200, repairs));
        pitch = pitch.withOffsetPerDegree(ConfigJsonReader.readLegacyFloat(root, "offsetPerDegree",
                pitch.offsetPerDegree(), 0.25f, 10.0f, repairs));
        migratedProfile = migratedProfile.withPitch(pitch).withLine(
                migratedProfile.line().withColorRgb(ConfigJsonReader.readLegacyColor(
                        root, "lineColorRgb", migratedProfile.line().colorRgb(), repairs)));
        migratedProfile = migratedProfile.sanitized(repairs, defaults);

        Config cfg = new Config();
        cfg.enabled = ConfigJsonReader.readOptionalBoolean(root, "enabled", cfg.enabled, repairs);
        cfg.profiles = new ArrayList<>();
        cfg.profiles.add(migratedProfile);
        cfg.activeProfileIndex = 0;
        cfg.activeProfileFile = migratedProfile.fileName();
        cfg.profileSortMode = Config.PROFILE_SORT_CREATED;
        repairs.log();
        LOGGER.info("Migrated legacy Elytra Pitch Helper config from {} to {}", legacyPath, nextPath);
        return cfg;
    }
}
