package com.asphyxiamywife.elytrapitchhelper.config;

import static com.asphyxiamywife.elytrapitchhelper.config.Config.CURRENT_VERSION;
import static com.asphyxiamywife.elytrapitchhelper.config.ConfigRepair.repairInt;

public final class Profile {
    public int version = CURRENT_VERSION;
    public String name;
    public VisibilitySettings visibility = new VisibilitySettings();
    public PitchSettings pitch = new PitchSettings();
    public LineSettings line = new LineSettings();
    public AmplitudeSettings amplitude = new AmplitudeSettings();
    public transient String fileName;

    Profile copy() {
        Profile profile = new Profile();
        profile.copyFrom(this);
        return profile;
    }

    void applyTo(Config config) {
        ensureValueObjects();
        config.ensureValueObjects();
        config.visibility.copyFrom(visibility);
        config.pitch.copyFrom(pitch);
        config.line.copyFrom(line);
        config.amplitude.copyFrom(amplitude);
    }

    void copyFrom(Config config) {
        ensureValueObjects();
        config.ensureValueObjects();
        visibility.copyFrom(config.visibility);
        pitch.copyFrom(config.pitch);
        line.copyFrom(config.line);
        amplitude.copyFrom(config.amplitude);
    }

    void copyFrom(Profile other) {
        version = other.version;
        name = other.name;
        fileName = other.fileName;
        visibility = other.visibility == null ? new VisibilitySettings() : other.visibility.copy();
        pitch = other.pitch == null ? new PitchSettings() : other.pitch.copy();
        line = other.line == null ? new LineSettings() : other.line.copy();
        amplitude = other.amplitude == null ? new AmplitudeSettings() : other.amplitude.copy();
    }

    void sanitize(Config.RepairLog repairs, Profile defaults) {
        if (defaults == null) {
            defaults = new Profile();
        }
        ensureValueObjects();
        defaults.ensureValueObjects();

        version = repairInt(repairs, "version", version, CURRENT_VERSION, CURRENT_VERSION, CURRENT_VERSION);
        pitch.sanitize(repairs, defaults.pitch);
        line.sanitize(repairs, defaults.line);
        amplitude.sanitize(repairs, defaults.amplitude);
    }

    void ensureValueObjects() {
        if (visibility == null) {
            visibility = new VisibilitySettings();
        }
        if (pitch == null) {
            pitch = new PitchSettings();
        }
        if (line == null) {
            line = new LineSettings();
        }
        if (amplitude == null) {
            amplitude = new AmplitudeSettings();
        }
    }
}
