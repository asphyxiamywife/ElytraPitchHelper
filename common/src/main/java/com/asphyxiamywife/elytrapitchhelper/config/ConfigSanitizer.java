package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingsRegistry;

import static com.asphyxiamywife.elytrapitchhelper.config.ConfigRepair.repairInt;

final class ConfigSanitizer {
    private ConfigSanitizer() {
    }

    static void clampValues(Config cfg, RepairLog repairs) {
        ensureValueObjects(cfg);
        ConfigProfileManager.ensureProfiles(cfg);
        Profile defaults = ProfileDefaults.template();
        cfg.version = repairInt(repairs, "version", cfg.version, Config.CURRENT_VERSION,
                Config.CURRENT_VERSION, Config.CURRENT_VERSION);
        cfg.profileSortMode = repairInt(repairs, "profileSortMode", cfg.profileSortMode,
                Config.PROFILE_SORT_CREATED, Config.PROFILE_SORT_CREATED, Config.PROFILE_SORT_MODIFIED);
        cfg.commandPaletteUsage = cfg.commandPaletteUsage.sanitized(repairs);
        cfg.sectionCollapse = cfg.sectionCollapse.sanitized(repairs);
        cfg.activeProfileIndex = ConfigProfileManager.clampIndex(cfg, cfg.activeProfileIndex);
        for (int i = 0; i < cfg.profiles.size(); i++) {
            cfg.profiles.set(i, cfg.profiles.get(i).sanitized(
                    i == cfg.activeProfileIndex ? repairs : null, defaults));
        }
        cfg.activeProfileFile = cfg.profiles.get(cfg.activeProfileIndex).fileName();
    }

    static void ensureValueObjects(Config cfg) {
        SettingsSections.ensureAll(cfg);
    }
}
