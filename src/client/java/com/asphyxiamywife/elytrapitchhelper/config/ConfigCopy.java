package com.asphyxiamywife.elytrapitchhelper.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    static void copyFromPreservingProfiles(Config target, Config source) {
        ConfigSanitizer.ensureValueObjects(source);
        ConfigSanitizer.ensureValueObjects(target);
        ConfigProfileManager.ensureProfiles(source);
        ConfigProfileManager.ensureProfiles(target);

        Map<String, Profile> currentProfiles = new HashMap<>();
        for (Profile profile : target.profiles) {
            if (profile.fileName != null) {
                currentProfiles.put(profile.fileName.toLowerCase(Locale.ROOT), profile);
            }
        }
        List<Profile> restoredProfiles = new ArrayList<>();
        for (Profile snapshotProfile : source.profiles) {
            Profile restored = snapshotProfile.fileName == null ? null
                    : currentProfiles.get(snapshotProfile.fileName.toLowerCase(Locale.ROOT));
            if (restored == null) {
                restored = snapshotProfile.copy();
            } else {
                restored.copyFrom(snapshotProfile);
            }
            restoredProfiles.add(restored);
        }

        target.version = source.version;
        target.enabled = source.enabled;
        target.activeProfileIndex = source.activeProfileIndex;
        target.activeProfileFile = source.activeProfileFile;
        target.profileSortMode = source.profileSortMode;
        target.profiles = restoredProfiles;
        target.profileMetadata = source.profileMetadata == null ? new ProfileMetadataStore()
                : source.profileMetadata.copy();
        target.profilesNormalized = false;
        ConfigProfileManager.ensureProfiles(target);
        ConfigProfileManager.bindActiveProfile(target);
    }
}
