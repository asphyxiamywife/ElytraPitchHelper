package com.asphyxiamywife.elytrapitchhelper.config;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

import java.util.ArrayList;
import java.util.Set;

import static com.asphyxiamywife.elytrapitchhelper.config.FuzzGenerators.arbitraryProfile;
import static com.asphyxiamywife.elytrapitchhelper.config.ProfileInvariants.assertValid;
import static com.asphyxiamywife.elytrapitchhelper.config.ProfileInvariants.assertValidConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfigModelFuzzTest {
    @FuzzTest(maxDuration = "30s")
    void sanitizingCopyingAndProfileOperationsPreserveInvariants(FuzzedDataProvider data) {
        Profile defaults = new Profile();
        defaults = defaults.withName("Default");
        Profile directProfile = arbitraryProfile(data);
        directProfile = directProfile.sanitized(null, defaults);
        assertValid(directProfile);
        assertProfileRoundTrip(directProfile, defaults);

        Config config = new Config();
        config.profiles = new ArrayList<>();
        int initialProfiles = data.consumeInt(1, 8);
        for (int i = 0; i < initialProfiles; i++) {
            Profile profile = arbitraryProfile(data);
            if (i > 0 && data.consumeBoolean()) {
                profile = profile.withFileName(config.profiles.get(0).fileName());
            }
            config.profiles.add(profile);
        }
        config.activeProfileIndex = data.consumeInt();
        config.activeProfileFile = data.consumeBoolean() ? null : data.consumeString(64);
        config.profileSortMode = data.consumeInt(Config.PROFILE_SORT_CREATED, Config.PROFILE_SORT_MODIFIED);
        ConfigProfileManager.ensureProfiles(config);
        assertValidConfig(config);

        int operations = data.consumeInt(1, 24);
        for (int i = 0; i < operations; i++) {
            int index = data.consumeInt();
            switch (data.consumeInt(0, 9)) {
                case 0 -> config.createProfile();
                case 1 -> config.duplicateProfile(index);
                case 2 -> config.selectProfile(index);
                case 3 -> config.cycleProfileSortMode();
                case 4 -> config.cycleProfileSortModeBackward();
                case 5 -> {
                    StagedProfileDelete deleted = config.stageDeleteProfile(index);
                    if (deleted != null && data.consumeBoolean()) {
                        config.restoreStagedProfileDelete(deleted);
                    }
                }
                case 6 -> config.resetProfileToDefaults(index);
                case 7 -> config.setProfileName(index, data.consumeString(64));
                case 8 -> ConfigProfileManager.ensureProfiles(config);
                default -> acceptArbitraryDiskProfileState(data, config);
            }
            ConfigProfileManager.ensureProfiles(config);
            assertValidConfig(config);
        }

        Config copy = config.copy();
        assertTrue(config.hasSameState(copy));
        copy.enabled = !config.enabled;
        assertFalse(config.hasSameState(copy));
        copy.restoreFrom(config);
        assertTrue(config.hasSameState(copy));
    }

    private static void acceptArbitraryDiskProfileState(FuzzedDataProvider data, Config config) {
        Config disk = config.copy();
        int diskIndex = data.consumeInt(0, disk.profileCount() - 1);
        String conflictFile = disk.profile(diskIndex).fileName();
        boolean enabledBefore = config.enabled;
        int sortModeBefore = config.profileSortMode;
        if (data.consumeBoolean() && disk.profileCount() > 1) {
            disk.profiles.remove(diskIndex);
            disk.profilesNormalized = false;
        } else {
            disk.replaceProfile(diskIndex, disk.profile(diskIndex).withName(data.consumeString(64)));
            Profile diskProfile = disk.profile(diskIndex);
            disk.replaceProfile(diskIndex, diskProfile.withPitch(
                    diskProfile.pitch().withTargetUpMinecraft(data.consumeFloat())));
        }

        config.acceptProfileStatesFromDisk(disk, Set.of(conflictFile));

        assertEquals(enabledBefore, config.enabled);
        assertEquals(sortModeBefore, config.profileSortMode);
    }

    private static void assertProfileRoundTrip(Profile profile, Profile defaults) {
        Profile restored = ConfigFiles.GSON.fromJson(ConfigFiles.GSON.toJson(profile), Profile.class);
        assertNotNull(restored);
        restored = restored.sanitized(null, defaults);
        assertEquals(ConfigFiles.GSON.toJsonTree(profile), ConfigFiles.GSON.toJsonTree(restored));
    }

}
