package com.asphyxiamywife.elytrapitchhelper.config;

import static com.asphyxiamywife.elytrapitchhelper.config.ConfigRepair.repairInt;

final class ConfigSanitizer {
    private ConfigSanitizer() {
    }

    static void clampValues(Config cfg) {
        clampValues(cfg, null);
    }

    static void clampValues(Config cfg, RepairLog repairs) {
        ensureValueObjects(cfg);
        ConfigProfileManager.ensureProfiles(cfg);
        Profile defaults = ProfileDefaults.template();
        defaults.ensureValueObjects();
        cfg.version = repairInt(repairs, "version", cfg.version, Config.CURRENT_VERSION,
                Config.CURRENT_VERSION, Config.CURRENT_VERSION);
        cfg.profileSortMode = repairInt(repairs, "profileSortMode", cfg.profileSortMode,
                Config.PROFILE_SORT_CREATED, Config.PROFILE_SORT_CREATED, Config.PROFILE_SORT_MODIFIED);
        cfg.pitch.sanitize(repairs, defaults.pitch);
        cfg.line.sanitize(repairs, defaults.line);
        cfg.amplitude.sanitize(repairs, defaults.amplitude);
        cfg.voidWarning.sanitize(repairs, defaults.voidWarning);
        cfg.activeProfileIndex = ConfigProfileManager.clampIndex(cfg, cfg.activeProfileIndex);
        cfg.activeProfileFile = cfg.profiles.get(cfg.activeProfileIndex).fileName;
        for (Profile profile : cfg.profiles) {
            profile.sanitize(null, defaults);
        }
    }

    static void ensureValueObjects(Config cfg) {
        if (cfg.visibility == null) {
            cfg.visibility = new VisibilitySettings();
        }
        if (cfg.pitch == null) {
            cfg.pitch = new PitchSettings();
        }
        if (cfg.line == null) {
            cfg.line = new LineSettings();
        }
        if (cfg.amplitude == null) {
            cfg.amplitude = new AmplitudeSettings();
        }
        if (cfg.voidWarning == null) {
            cfg.voidWarning = new VoidWarningSettings();
        }
    }
}
