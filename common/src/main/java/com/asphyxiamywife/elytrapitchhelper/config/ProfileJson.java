package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingsRegistry;
import com.google.gson.JsonObject;

final class ProfileJson {
    private ProfileJson() {
    }

    static Profile parse(JsonObject json, String fileName, Profile defaults, RepairLog repairs) {
        if (defaults == null) {
            defaults = ProfileDefaults.template();
        }
        Profile profile = defaults.withFileName(ProfileFileNames.normalize(fileName));
        String fallbackName = profile.name() == null || profile.name().isBlank()
                ? ProfileFileNames.displayName(profile.fileName())
                : profile.name();

        if (json == null) {
            ConfigRepair.repair(repairs, "profile", "null json", "default profile");
            if (profile.name() == null || profile.name().isBlank()) {
                profile = profile.withName(fallbackName);
            }
            return profile;
        }

        profile = profile.withVersion(ConfigJsonReader.readInt(
                json, "version", defaults.version(), repairs));
        profile = profile.withName(ConfigJsonReader.readString(json, "name", fallbackName, repairs));
        return SettingsRegistry.read(json, profile, defaults, repairs);
    }

    static JsonObject write(Profile profile) {
        return SettingsRegistry.write(profile);
    }
}
