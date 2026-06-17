package com.asphyxiamywife.elytrapitchhelper.config;

import static com.asphyxiamywife.elytrapitchhelper.config.ConfigRepair.repairFloat;
import static com.asphyxiamywife.elytrapitchhelper.config.ConfigRepair.repairInt;

public final class PitchSettings {
    public float targetUpMinecraft = -40.0f;
    public float targetDownMinecraft = 40.0f;
    public float toleranceDegrees = 6.0f;
    public int maxOffsetPixels = 42;
    public float offsetPerDegree = 2.0f;

    public PitchSettings copy() {
        PitchSettings settings = new PitchSettings();
        settings.copyFrom(this);
        return settings;
    }

    public void copyFrom(PitchSettings other) {
        if (other == null) {
            return;
        }
        targetUpMinecraft = other.targetUpMinecraft;
        targetDownMinecraft = other.targetDownMinecraft;
        toleranceDegrees = other.toleranceDegrees;
        maxOffsetPixels = other.maxOffsetPixels;
        offsetPerDegree = other.offsetPerDegree;
    }

    void sanitize(RepairLog repairs, PitchSettings defaults) {
        if (defaults == null) {
            defaults = new PitchSettings();
        }
        targetUpMinecraft = repairFloat(repairs, "pitch.targetUpMinecraft", targetUpMinecraft,
                defaults.targetUpMinecraft, -90.0f, 0.0f);
        targetDownMinecraft = repairFloat(repairs, "pitch.targetDownMinecraft", targetDownMinecraft,
                defaults.targetDownMinecraft, 0.0f, 90.0f);
        toleranceDegrees = repairFloat(repairs, "pitch.toleranceDegrees", toleranceDegrees,
                defaults.toleranceDegrees, 1.0f, 45.0f);
        maxOffsetPixels = repairInt(repairs, "pitch.maxOffsetPixels", maxOffsetPixels,
                defaults.maxOffsetPixels, 0, 200);
        offsetPerDegree = repairFloat(repairs, "pitch.offsetPerDegree", offsetPerDegree,
                defaults.offsetPerDegree, 0.25f, 10.0f);
    }
}
