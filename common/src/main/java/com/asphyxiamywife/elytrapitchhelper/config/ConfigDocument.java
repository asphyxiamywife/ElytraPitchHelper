package com.asphyxiamywife.elytrapitchhelper.config;

import java.util.List;

public record ConfigDocument(
        int version,
        boolean enabled,
        int activeProfileIndex,
        String activeProfileFile,
        int profileSortMode,
        CommandPaletteUsageSettings commandPaletteUsage,
        SectionCollapseSettings sectionCollapse,
        boolean usedSettingsSearch,
        List<Profile> profiles) {

    public ConfigDocument {
        commandPaletteUsage = commandPaletteUsage == null
                ? new CommandPaletteUsageSettings()
                : commandPaletteUsage;
        sectionCollapse = sectionCollapse == null ? new SectionCollapseSettings() : sectionCollapse;
        profiles = profiles == null ? List.of() : List.copyOf(profiles);
    }

    static ConfigDocument from(Config config) {
        List<Profile> profiles = config.profiles == null || config.profiles.isEmpty()
                ? ProfileDefaults.profiles() : config.profiles;
        return new ConfigDocument(
                config.version,
                config.enabled,
                config.activeProfileIndex,
                config.activeProfileFile,
                config.profileSortMode,
                config.commandPaletteUsage,
                config.sectionCollapse,
                config.usedSettingsSearch,
                profiles);
    }

    void applyTo(Config config) {
        config.version = version;
        config.enabled = enabled;
        config.activeProfileIndex = activeProfileIndex;
        config.activeProfileFile = activeProfileFile;
        config.profileSortMode = profileSortMode;
        config.commandPaletteUsage = commandPaletteUsage;
        config.sectionCollapse = sectionCollapse;
        config.usedSettingsSearch = usedSettingsSearch;
        config.profiles = ProfileDefaults.copyProfiles(profiles);
        config.profilesNormalized = false;
    }
}
