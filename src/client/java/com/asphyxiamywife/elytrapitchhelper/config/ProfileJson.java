package com.asphyxiamywife.elytrapitchhelper.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

final class ProfileJson {
    private ProfileJson() {
    }

    static Profile parse(JsonObject json, String fileName, Profile defaults, Config.RepairLog repairs) {
        if (defaults == null) {
            defaults = new Profile();
        }
        Profile profile = defaults.copy();
        profile.ensureValueObjects();
        defaults.ensureValueObjects();
        profile.fileName = ProfileFileNames.normalize(fileName);
        String fallbackName = profile.name == null || profile.name.isBlank()
                ? ProfileFileNames.displayName(profile.fileName)
                : profile.name;

        if (json == null) {
            ConfigRepair.repair(repairs, "profile", "null json", "default profile");
            if (profile.name == null || profile.name.isBlank()) {
                profile.name = fallbackName;
            }
            return profile;
        }

        profile.version = ConfigJsonReader.readInt(json, "version", defaults.version, repairs);
        profile.name = ConfigJsonReader.readString(json, "name", fallbackName, repairs);
        readVisibility(json, profile, defaults, repairs);
        readPitch(json, profile, defaults, repairs);
        readLine(json, profile, defaults, repairs);
        readAmplitude(json, profile, defaults, repairs);
        return profile;
    }

    private static void readVisibility(JsonObject json, Profile profile, Profile defaults, Config.RepairLog repairs) {
        JsonObject section = section(json, "visibility", repairs);
        profile.visibility.showOnlyWithFirework = readBoolean(json, section, "showOnlyWithFirework",
                "showOnlyWithFirework", defaults.visibility.showOnlyWithFirework, repairs);
        profile.visibility.showInThirdPerson = readBoolean(json, section, "showInThirdPerson",
                "showInThirdPerson", defaults.visibility.showInThirdPerson, repairs);
    }

    private static void readPitch(JsonObject json, Profile profile, Profile defaults, Config.RepairLog repairs) {
        JsonObject section = section(json, "pitch", repairs);
        profile.pitch.targetUpMinecraft = readFloat(json, section, "targetUpMinecraft", "targetUpMinecraft",
                defaults.pitch.targetUpMinecraft, repairs);
        profile.pitch.targetDownMinecraft = readFloat(json, section, "targetDownMinecraft", "targetDownMinecraft",
                defaults.pitch.targetDownMinecraft, repairs);
        profile.pitch.toleranceDegrees = readFloat(json, section, "toleranceDegrees", "toleranceDegrees",
                defaults.pitch.toleranceDegrees, repairs);
        profile.pitch.maxOffsetPixels = readInt(json, section, "maxOffsetPixels", "maxOffsetPixels",
                defaults.pitch.maxOffsetPixels, repairs);
        profile.pitch.offsetPerDegree = readFloat(json, section, "offsetPerDegree", "offsetPerDegree",
                defaults.pitch.offsetPerDegree, repairs);
    }

    private static void readLine(JsonObject json, Profile profile, Profile defaults, Config.RepairLog repairs) {
        JsonObject section = section(json, "line", repairs);
        profile.line.lengthPixels = readInt(json, section, "lengthPixels", "lineLengthPixels",
                defaults.line.lengthPixels, repairs);
        profile.line.widthPixels = readInt(json, section, "widthPixels", "lineWidthPixels",
                defaults.line.widthPixels, repairs);
        profile.line.colorRgb = readInt(json, section, "colorRgb", "lineColorRgb", defaults.line.colorRgb,
                repairs);
        profile.line.prideEnabled = readBoolean(json, section, "prideEnabled", "linePrideEnabled",
                defaults.line.prideEnabled, repairs);
        profile.line.prideFlag = readString(json, section, "prideFlag", "linePrideFlag", defaults.line.prideFlag,
                repairs);
    }

    private static void readAmplitude(JsonObject json, Profile profile, Profile defaults, Config.RepairLog repairs) {
        JsonObject section = section(json, "amplitude", repairs);
        profile.amplitude.enabled = readBoolean(json, section, "enabled", "amplitudeHelperEnabled",
                defaults.amplitude.enabled, repairs);
        profile.amplitude.triggerMode = readInt(json, section, "triggerMode", "amplitudeTriggerMode",
                defaults.amplitude.triggerMode, repairs);
        profile.amplitude.downBlocks = readInt(json, section, "downBlocks", "amplitudeDownBlocks",
                defaults.amplitude.downBlocks, repairs);
        profile.amplitude.upBlocks = readInt(json, section, "upBlocks", "amplitudeUpBlocks",
                defaults.amplitude.upBlocks, repairs);
        profile.amplitude.toleranceBlocks = readInt(json, section, "toleranceBlocks", "amplitudeToleranceBlocks",
                defaults.amplitude.toleranceBlocks, repairs);
        profile.amplitude.downVelocity = readFloat(json, section, "downVelocity", "amplitudeDownVelocity",
                defaults.amplitude.downVelocity, repairs);
        profile.amplitude.upVelocity = readFloat(json, section, "upVelocity", "amplitudeUpVelocity",
                defaults.amplitude.upVelocity, repairs);
        profile.amplitude.cueColorRgb = readInt(json, section, "cueColorRgb", "amplitudeCueColorRgb",
                defaults.amplitude.cueColorRgb, repairs);
        profile.amplitude.cuePrideEnabled = readBoolean(json, section, "cuePrideEnabled",
                "amplitudeCuePrideEnabled", defaults.amplitude.cuePrideEnabled, repairs);
        profile.amplitude.cuePrideFlag = readString(json, section, "cuePrideFlag", "amplitudeCuePrideFlag",
                defaults.amplitude.cuePrideFlag, repairs);
    }

    private static JsonObject section(JsonObject json, String name, Config.RepairLog repairs) {
        JsonElement element = json.get(name);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonObject()) {
            ConfigRepair.repair(repairs, name, element, "object");
            return null;
        }
        return element.getAsJsonObject();
    }

    private static boolean readBoolean(JsonObject root, JsonObject section, String field, String legacyField,
            boolean fallback, Config.RepairLog repairs) {
        if (section != null && section.has(field)) {
            return ConfigJsonReader.readBoolean(section, field, fallback, repairs);
        }
        return ConfigJsonReader.readBoolean(root, legacyField, fallback, repairs);
    }

    private static int readInt(JsonObject root, JsonObject section, String field, String legacyField, int fallback,
            Config.RepairLog repairs) {
        if (section != null && section.has(field)) {
            return ConfigJsonReader.readInt(section, field, fallback, repairs);
        }
        return ConfigJsonReader.readInt(root, legacyField, fallback, repairs);
    }

    private static float readFloat(JsonObject root, JsonObject section, String field, String legacyField,
            float fallback, Config.RepairLog repairs) {
        if (section != null && section.has(field)) {
            return ConfigJsonReader.readFloat(section, field, fallback, repairs);
        }
        return ConfigJsonReader.readFloat(root, legacyField, fallback, repairs);
    }

    private static String readString(JsonObject root, JsonObject section, String field, String legacyField,
            String fallback, Config.RepairLog repairs) {
        if (section != null && section.has(field)) {
            return ConfigJsonReader.readString(section, field, fallback, repairs);
        }
        return ConfigJsonReader.readString(root, legacyField, fallback, repairs);
    }
}
