package com.asphyxiamywife.elytrapitchhelper.config;

import static com.asphyxiamywife.elytrapitchhelper.config.Config.AMPLITUDE_TRIGGER_EITHER;
import static com.asphyxiamywife.elytrapitchhelper.config.Config.AMPLITUDE_TRIGGER_HEIGHT;
import static com.asphyxiamywife.elytrapitchhelper.config.Config.CURRENT_VERSION;
import static com.asphyxiamywife.elytrapitchhelper.config.Config.clamp;
import static com.asphyxiamywife.elytrapitchhelper.config.Config.repair;
import static com.asphyxiamywife.elytrapitchhelper.config.Config.repairColor;
import static com.asphyxiamywife.elytrapitchhelper.config.Config.repairFloat;
import static com.asphyxiamywife.elytrapitchhelper.config.Config.repairInt;
import static com.asphyxiamywife.elytrapitchhelper.config.Config.repairPrideFlag;

public final class Profile {
    public int version = CURRENT_VERSION;
    public String name;
    public boolean showOnlyWithFirework = false;
    public boolean showInThirdPerson = false;
    public float targetUpMinecraft;
    public float targetDownMinecraft;
    public float toleranceDegrees;
    public int maxOffsetPixels;
    public float offsetPerDegree;
    public int lineLengthPixels;
    public int lineWidthPixels;
    public int lineColorRgb;
    public boolean linePrideEnabled;
    public String linePrideFlag = PrideFlag.defaultId();
    public boolean amplitudeHelperEnabled;
    public int amplitudeTriggerMode;
    public int amplitudeDownBlocks;
    public int amplitudeUpBlocks;
    public int amplitudeToleranceBlocks;
    public float amplitudeDownVelocity;
    public float amplitudeUpVelocity;
    public int amplitudeCueColorRgb;
    public boolean amplitudeCuePrideEnabled;
    public String amplitudeCuePrideFlag = PrideFlag.defaultId();
    public transient String fileName;

    Profile copy() {
        Profile profile = new Profile();
        profile.copyFrom(this);
        return profile;
    }

    void applyTo(Config config) {
        config.showOnlyWithFirework = showOnlyWithFirework;
        config.showInThirdPerson = showInThirdPerson;
        config.targetUpMinecraft = targetUpMinecraft;
        config.targetDownMinecraft = targetDownMinecraft;
        config.toleranceDegrees = toleranceDegrees;
        config.maxOffsetPixels = maxOffsetPixels;
        config.offsetPerDegree = offsetPerDegree;
        config.lineLengthPixels = lineLengthPixels;
        config.lineWidthPixels = lineWidthPixels;
        config.lineColorRgb = lineColorRgb;
        config.linePrideEnabled = linePrideEnabled;
        config.linePrideFlag = linePrideFlag;
        config.amplitudeHelperEnabled = amplitudeHelperEnabled;
        config.amplitudeTriggerMode = amplitudeTriggerMode;
        config.amplitudeDownBlocks = amplitudeDownBlocks;
        config.amplitudeUpBlocks = amplitudeUpBlocks;
        config.amplitudeToleranceBlocks = amplitudeToleranceBlocks;
        config.amplitudeDownVelocity = amplitudeDownVelocity;
        config.amplitudeUpVelocity = amplitudeUpVelocity;
        config.amplitudeCueColorRgb = amplitudeCueColorRgb;
        config.amplitudeCuePrideEnabled = amplitudeCuePrideEnabled;
        config.amplitudeCuePrideFlag = amplitudeCuePrideFlag;
    }

    void copyFrom(Config config) {
        showOnlyWithFirework = config.showOnlyWithFirework;
        showInThirdPerson = config.showInThirdPerson;
        targetUpMinecraft = config.targetUpMinecraft;
        targetDownMinecraft = config.targetDownMinecraft;
        toleranceDegrees = config.toleranceDegrees;
        maxOffsetPixels = config.maxOffsetPixels;
        offsetPerDegree = config.offsetPerDegree;
        lineLengthPixels = config.lineLengthPixels;
        lineWidthPixels = config.lineWidthPixels;
        lineColorRgb = config.lineColorRgb;
        linePrideEnabled = config.linePrideEnabled;
        linePrideFlag = config.linePrideFlag;
        amplitudeHelperEnabled = config.amplitudeHelperEnabled;
        amplitudeTriggerMode = config.amplitudeTriggerMode;
        amplitudeDownBlocks = config.amplitudeDownBlocks;
        amplitudeUpBlocks = config.amplitudeUpBlocks;
        amplitudeToleranceBlocks = config.amplitudeToleranceBlocks;
        amplitudeDownVelocity = config.amplitudeDownVelocity;
        amplitudeUpVelocity = config.amplitudeUpVelocity;
        amplitudeCueColorRgb = config.amplitudeCueColorRgb;
        amplitudeCuePrideEnabled = config.amplitudeCuePrideEnabled;
        amplitudeCuePrideFlag = config.amplitudeCuePrideFlag;
    }

    void copyFrom(Profile other) {
        version = other.version;
        name = other.name;
        fileName = other.fileName;
        showOnlyWithFirework = other.showOnlyWithFirework;
        showInThirdPerson = other.showInThirdPerson;
        targetUpMinecraft = other.targetUpMinecraft;
        targetDownMinecraft = other.targetDownMinecraft;
        toleranceDegrees = other.toleranceDegrees;
        maxOffsetPixels = other.maxOffsetPixels;
        offsetPerDegree = other.offsetPerDegree;
        lineLengthPixels = other.lineLengthPixels;
        lineWidthPixels = other.lineWidthPixels;
        lineColorRgb = other.lineColorRgb;
        linePrideEnabled = other.linePrideEnabled;
        linePrideFlag = other.linePrideFlag;
        amplitudeHelperEnabled = other.amplitudeHelperEnabled;
        amplitudeTriggerMode = other.amplitudeTriggerMode;
        amplitudeDownBlocks = other.amplitudeDownBlocks;
        amplitudeUpBlocks = other.amplitudeUpBlocks;
        amplitudeToleranceBlocks = other.amplitudeToleranceBlocks;
        amplitudeDownVelocity = other.amplitudeDownVelocity;
        amplitudeUpVelocity = other.amplitudeUpVelocity;
        amplitudeCueColorRgb = other.amplitudeCueColorRgb;
        amplitudeCuePrideEnabled = other.amplitudeCuePrideEnabled;
        amplitudeCuePrideFlag = other.amplitudeCuePrideFlag;
    }

    void sanitize(Config.RepairLog repairs, Profile defaults) {
        if (defaults == null) {
            defaults = new Profile();
        }

        version = repairInt(repairs, "version", version, CURRENT_VERSION, CURRENT_VERSION, CURRENT_VERSION);
        targetUpMinecraft = repairFloat(repairs, "targetUpMinecraft", targetUpMinecraft,
                defaults.targetUpMinecraft, -90.0f, 0.0f);
        targetDownMinecraft = repairFloat(repairs, "targetDownMinecraft", targetDownMinecraft,
                defaults.targetDownMinecraft, 0.0f, 90.0f);
        toleranceDegrees = repairFloat(repairs, "toleranceDegrees", toleranceDegrees,
                defaults.toleranceDegrees, 1.0f, 45.0f);
        maxOffsetPixels = repairInt(repairs, "maxOffsetPixels", maxOffsetPixels,
                defaults.maxOffsetPixels, 0, 200);
        offsetPerDegree = repairFloat(repairs, "offsetPerDegree", offsetPerDegree,
                defaults.offsetPerDegree, 0.25f, 10.0f);
        lineLengthPixels = repairInt(repairs, "lineLengthPixels", lineLengthPixels,
                defaults.lineLengthPixels, 2, 200);
        lineWidthPixels = repairInt(repairs, "lineWidthPixels", lineWidthPixels,
                defaults.lineWidthPixels, 1, 20);
        lineColorRgb = repairColor(repairs, "lineColorRgb", lineColorRgb, defaults.lineColorRgb);
        linePrideFlag = repairPrideFlag(repairs, "linePrideFlag", linePrideFlag, defaults.linePrideFlag);
        amplitudeTriggerMode = repairInt(repairs, "amplitudeTriggerMode", amplitudeTriggerMode,
                defaults.amplitudeTriggerMode, AMPLITUDE_TRIGGER_HEIGHT, AMPLITUDE_TRIGGER_EITHER);
        amplitudeDownBlocks = repairInt(repairs, "amplitudeDownBlocks", amplitudeDownBlocks,
                defaults.amplitudeDownBlocks, 1, 1024);
        amplitudeUpBlocks = repairInt(repairs, "amplitudeUpBlocks", amplitudeUpBlocks,
                defaults.amplitudeUpBlocks, 1, 1024);
        amplitudeToleranceBlocks = repairInt(repairs, "amplitudeToleranceBlocks", amplitudeToleranceBlocks,
                defaults.amplitudeToleranceBlocks, 0, 128);
        amplitudeDownVelocity = repairFloat(repairs, "amplitudeDownVelocity", amplitudeDownVelocity,
                defaults.amplitudeDownVelocity, 0.1f, 10.0f);
        amplitudeUpVelocity = repairFloat(repairs, "amplitudeUpVelocity", amplitudeUpVelocity,
                defaults.amplitudeUpVelocity, 0.0f, 10.0f);
        if (amplitudeUpVelocity >= amplitudeDownVelocity) {
            float oldValue = amplitudeUpVelocity;
            amplitudeUpVelocity = clamp(defaults.amplitudeUpVelocity, 0.0f, Math.max(0.0f,
                    amplitudeDownVelocity - 0.1f));
            repair(repairs, "amplitudeUpVelocity", oldValue, amplitudeUpVelocity);
        }
        amplitudeCueColorRgb = repairColor(repairs, "amplitudeCueColorRgb", amplitudeCueColorRgb,
                defaults.amplitudeCueColorRgb);
        amplitudeCuePrideFlag = repairPrideFlag(repairs, "amplitudeCuePrideFlag", amplitudeCuePrideFlag,
                defaults.amplitudeCuePrideFlag);
    }
}
