package com.asphyxiamywife.elytrapitchhelper.config;

final class ConfigCopy {
    private ConfigCopy() {
    }

    static Config copyOf(Config source) {
        Config copy = new Config();
        copyFrom(copy, source);
        return copy;
    }

    static void copyFrom(Config target, Config source) {
        ConfigSanitizer.ensureValueObjects(source);
        ConfigSanitizer.ensureValueObjects(target);
        target.version = source.version;
        target.enabled = source.enabled;
        target.activeProfileIndex = source.activeProfileIndex;
        target.activeProfileFile = source.activeProfileFile;
        target.profileSortMode = source.profileSortMode;
        target.profiles = ProfileDefaults.copyProfiles(source.profiles);
        target.profileMetadata = source.profileMetadata == null ? new ProfileMetadataStore()
                : source.profileMetadata.copy();
        target.profilesNormalized = false;
        ConfigProfileManager.ensureProfiles(target);
        if (!ConfigProfileManager.hasActiveProfileBinding(source)) {
            ConfigProfileManager.profile(target, target.activeProfileIndex).copyFrom(source);
        }
        ConfigProfileManager.bindActiveProfile(target);
    }
}
