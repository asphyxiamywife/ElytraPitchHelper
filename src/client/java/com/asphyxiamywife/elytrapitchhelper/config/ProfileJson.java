package com.asphyxiamywife.elytrapitchhelper.config;

import com.google.gson.JsonObject;

final class ProfileJson {
    private ProfileJson() {
    }

    static Profile parse(JsonObject json, String fileName, Profile defaults, Config.RepairLog repairs) {
        Profile profile = defaults.copy();
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
        profile.showOnlyWithFirework = ConfigJsonReader.readBoolean(json, "showOnlyWithFirework",
                defaults.showOnlyWithFirework, repairs);
        profile.showInThirdPerson = ConfigJsonReader.readBoolean(json, "showInThirdPerson",
                defaults.showInThirdPerson, repairs);
        profile.targetUpMinecraft = ConfigJsonReader.readFloat(json, "targetUpMinecraft",
                defaults.targetUpMinecraft, repairs);
        profile.targetDownMinecraft = ConfigJsonReader.readFloat(json, "targetDownMinecraft",
                defaults.targetDownMinecraft, repairs);
        profile.toleranceDegrees = ConfigJsonReader.readFloat(json, "toleranceDegrees", defaults.toleranceDegrees,
                repairs);
        profile.maxOffsetPixels = ConfigJsonReader.readInt(json, "maxOffsetPixels", defaults.maxOffsetPixels,
                repairs);
        profile.offsetPerDegree = ConfigJsonReader.readFloat(json, "offsetPerDegree", defaults.offsetPerDegree,
                repairs);
        profile.lineLengthPixels = ConfigJsonReader.readInt(json, "lineLengthPixels", defaults.lineLengthPixels,
                repairs);
        profile.lineWidthPixels = ConfigJsonReader.readInt(json, "lineWidthPixels", defaults.lineWidthPixels,
                repairs);
        profile.lineColorRgb = ConfigJsonReader.readInt(json, "lineColorRgb", defaults.lineColorRgb, repairs);
        profile.linePrideEnabled = ConfigJsonReader.readBoolean(json, "linePrideEnabled",
                defaults.linePrideEnabled, repairs);
        profile.linePrideFlag = ConfigJsonReader.readString(json, "linePrideFlag", defaults.linePrideFlag, repairs);
        profile.amplitudeHelperEnabled = ConfigJsonReader.readBoolean(json, "amplitudeHelperEnabled",
                defaults.amplitudeHelperEnabled, repairs);
        profile.amplitudeTriggerMode = ConfigJsonReader.readInt(json, "amplitudeTriggerMode",
                defaults.amplitudeTriggerMode, repairs);
        profile.amplitudeDownBlocks = ConfigJsonReader.readInt(json, "amplitudeDownBlocks",
                defaults.amplitudeDownBlocks, repairs);
        profile.amplitudeUpBlocks = ConfigJsonReader.readInt(json, "amplitudeUpBlocks", defaults.amplitudeUpBlocks,
                repairs);
        profile.amplitudeToleranceBlocks = ConfigJsonReader.readInt(json, "amplitudeToleranceBlocks",
                defaults.amplitudeToleranceBlocks, repairs);
        profile.amplitudeDownVelocity = ConfigJsonReader.readFloat(json, "amplitudeDownVelocity",
                defaults.amplitudeDownVelocity, repairs);
        profile.amplitudeUpVelocity = ConfigJsonReader.readFloat(json, "amplitudeUpVelocity",
                defaults.amplitudeUpVelocity, repairs);
        profile.amplitudeCueColorRgb = ConfigJsonReader.readInt(json, "amplitudeCueColorRgb",
                defaults.amplitudeCueColorRgb, repairs);
        profile.amplitudeCuePrideEnabled = ConfigJsonReader.readBoolean(json, "amplitudeCuePrideEnabled",
                defaults.amplitudeCuePrideEnabled, repairs);
        profile.amplitudeCuePrideFlag = ConfigJsonReader.readString(json, "amplitudeCuePrideFlag",
                defaults.amplitudeCuePrideFlag, repairs);
        return profile;
    }
}
