package com.asphyxiamywife.elytrapitchhelper.config;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfigModelFuzzTest {
    @FuzzTest(maxDuration = "30s")
    void sanitizingCopyingAndProfileOperationsPreserveInvariants(FuzzedDataProvider data) {
        Profile defaults = new Profile();
        defaults.name = "Default";
        Profile directProfile = arbitraryProfile(data);
        directProfile.sanitize(null, defaults);
        assertValidSettings(directProfile);
        assertProfileRoundTrip(directProfile, defaults);

        Config config = new Config();
        config.profiles = new ArrayList<>();
        int initialProfiles = data.consumeInt(1, 8);
        for (int i = 0; i < initialProfiles; i++) {
            Profile profile = arbitraryProfile(data);
            if (i > 0 && data.consumeBoolean()) {
                profile.fileName = config.profiles.get(0).fileName;
            }
            config.profiles.add(profile);
        }
        config.activeProfileIndex = data.consumeInt();
        config.activeProfileFile = data.consumeBoolean() ? null : data.consumeString(64);
        config.profileSortMode = data.consumeInt(Config.PROFILE_SORT_CREATED, Config.PROFILE_SORT_MODIFIED);
        ConfigProfileManager.ensureProfiles(config);
        ConfigProfileManager.bindActiveProfile(config);
        assertValidConfig(config);

        int operations = data.consumeInt(1, 24);
        for (int i = 0; i < operations; i++) {
            int index = data.consumeInt();
            switch (data.consumeInt(0, 8)) {
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
                default -> config.applyProfileIfActive(index);
            }
            ConfigProfileManager.ensureProfiles(config);
            ConfigProfileManager.bindActiveProfile(config);
            assertValidConfig(config);
        }

        Config copy = config.copy();
        assertTrue(config.hasSameState(copy));
        copy.enabled = !config.enabled;
        assertFalse(config.hasSameState(copy));
        copy.restoreFrom(config);
        assertTrue(config.hasSameState(copy));
    }

    private static Profile arbitraryProfile(FuzzedDataProvider data) {
        Profile profile = new Profile();
        profile.version = data.consumeInt();
        profile.name = data.consumeBoolean() ? null : data.consumeString(64);
        profile.fileName = data.consumeBoolean() ? null : data.consumeString(64);
        profile.visibility.showOnlyWithFirework = data.consumeBoolean();
        profile.visibility.showInThirdPerson = data.consumeBoolean();
        profile.visibility.anyElytraGlide = data.consumeBoolean();

        profile.pitch.targetUpMinecraft = data.consumeFloat();
        profile.pitch.targetDownMinecraft = data.consumeFloat();
        profile.pitch.toleranceDegrees = data.consumeFloat();
        profile.pitch.maxOffsetPixels = data.consumeInt();
        profile.pitch.offsetPerDegree = data.consumeFloat();

        profile.line.lengthPixels = data.consumeInt();
        profile.line.widthPixels = data.consumeInt();
        profile.line.colorRgb = data.consumeInt();
        profile.line.prideEnabled = data.consumeBoolean();
        profile.line.prideFlag = data.consumeBoolean() ? null : data.consumeString(32);
        profile.line.customPrideColors = data.consumeBoolean() ? null : data.consumeInts(32);

        profile.amplitude.enabled = data.consumeBoolean();
        profile.amplitude.triggerMode = data.consumeInt();
        profile.amplitude.downBlocks = data.consumeInt();
        profile.amplitude.upBlocks = data.consumeInt();
        profile.amplitude.toleranceBlocks = data.consumeInt();
        profile.amplitude.downVelocity = data.consumeFloat();
        profile.amplitude.upVelocity = data.consumeFloat();
        profile.amplitude.cueColorRgb = data.consumeInt();
        profile.amplitude.cuePrideEnabled = data.consumeBoolean();
        profile.amplitude.cuePrideFlag = data.consumeBoolean() ? null : data.consumeString(32);
        profile.amplitude.customPrideColors = data.consumeBoolean() ? null : data.consumeInts(32);

        profile.voidWarning.enabled = data.consumeBoolean();
        profile.voidWarning.mode = data.consumeInt();
        profile.voidWarning.lookaheadSeconds = data.consumeFloat();
        profile.voidWarning.simpleWarningBlocks = data.consumeInt();
        profile.voidWarning.warningColorRgb = data.consumeInt();
        profile.voidWarning.warningPrideEnabled = data.consumeBoolean();
        profile.voidWarning.warningPrideFlag = data.consumeBoolean() ? null : data.consumeString(32);
        profile.voidWarning.customPrideColors = data.consumeBoolean() ? null : data.consumeInts(32);
        profile.voidWarning.toleranceOverride = data.consumeBoolean();
        profile.voidWarning.customToleranceDegrees = data.consumeFloat();
        profile.voidWarning.maxOffsetOverride = data.consumeBoolean();
        profile.voidWarning.customMaxOffsetPixels = data.consumeInt();
        if (data.consumeBoolean()) {
            profile.voidWarning.dimensionYOverrides = null;
        } else {
            profile.voidWarning.dimensionYOverrides = new HashMap<>();
            int overrides = data.consumeInt(0, 8);
            for (int i = 0; i < overrides; i++) {
                profile.voidWarning.dimensionYOverrides.put(data.consumeString(32), data.consumeInt());
            }
        }
        return profile;
    }

    private static void assertProfileRoundTrip(Profile profile, Profile defaults) {
        Profile restored = ConfigFiles.GSON.fromJson(ConfigFiles.GSON.toJson(profile), Profile.class);
        assertNotNull(restored);
        restored.sanitize(null, defaults);
        assertEquals(ConfigFiles.GSON.toJsonTree(profile), ConfigFiles.GSON.toJsonTree(restored));
    }

    private static void assertValidConfig(Config config) {
        assertNotNull(config.profiles);
        assertFalse(config.profiles.isEmpty());
        assertBetween(config.activeProfileIndex, 0, config.profiles.size() - 1);
        assertEquals(config.profiles.get(config.activeProfileIndex).fileName, config.activeProfileFile);
        assertTrue(ConfigProfileManager.isBoundToActiveProfile(config));
        assertBetween(config.profileSortMode, Config.PROFILE_SORT_CREATED, Config.PROFILE_SORT_MODIFIED);

        Set<String> files = new HashSet<>();
        for (Profile profile : config.profiles) {
            assertNotNull(profile);
            assertNotNull(profile.name);
            assertFalse(profile.name.isBlank());
            assertLocalJsonFile(profile.fileName);
            assertTrue(files.add(profile.fileName.toLowerCase(Locale.ROOT)));
            assertValidSettings(profile);
        }
    }

    private static void assertValidSettings(Profile profile) {
        assertEquals(Config.CURRENT_VERSION, profile.version);
        assertNotNull(profile.visibility);
        assertNotNull(profile.pitch);
        assertNotNull(profile.line);
        assertNotNull(profile.amplitude);
        assertNotNull(profile.voidWarning);

        assertBetween(profile.pitch.targetUpMinecraft, -90.0f, 0.0f);
        assertBetween(profile.pitch.targetDownMinecraft, 0.0f, 90.0f);
        assertBetween(profile.pitch.toleranceDegrees, 1.0f, 45.0f);
        assertBetween(profile.pitch.maxOffsetPixels, 0, 200);
        assertBetween(profile.pitch.offsetPerDegree, 0.25f, 10.0f);

        assertBetween(profile.line.lengthPixels, 2, 200);
        assertBetween(profile.line.widthPixels, 1, 20);
        assertRgb(profile.line.colorRgb);
        assertTrue(PrideFlag.isValidId(profile.line.prideFlag));
        assertColors(profile.line.customPrideColors);

        assertBetween(profile.amplitude.triggerMode, Config.AMPLITUDE_TRIGGER_HEIGHT,
                Config.AMPLITUDE_TRIGGER_EITHER);
        assertBetween(profile.amplitude.downBlocks, 1, 1024);
        assertBetween(profile.amplitude.upBlocks, 1, 1024);
        assertBetween(profile.amplitude.toleranceBlocks, 0, 128);
        assertBetween(profile.amplitude.downVelocity, 0.1f, 10.0f);
        assertBetween(profile.amplitude.upVelocity, 0.0f, 10.0f);
        assertTrue(profile.amplitude.upVelocity < profile.amplitude.downVelocity);
        assertRgb(profile.amplitude.cueColorRgb);
        assertTrue(PrideFlag.isValidId(profile.amplitude.cuePrideFlag));
        assertColors(profile.amplitude.customPrideColors);

        assertBetween(profile.voidWarning.mode, VoidWarningSettings.MODE_PREDICTED_TIME,
                VoidWarningSettings.MODE_SIMPLE_HEIGHT);
        assertBetween(profile.voidWarning.lookaheadSeconds, 1.0f, 15.0f);
        assertBetween(profile.voidWarning.simpleWarningBlocks, 1, 512);
        assertRgb(profile.voidWarning.warningColorRgb);
        assertTrue(PrideFlag.isValidId(profile.voidWarning.warningPrideFlag));
        assertColors(profile.voidWarning.customPrideColors);
        assertBetween(profile.voidWarning.customToleranceDegrees, 1.0f, 45.0f);
        assertBetween(profile.voidWarning.customMaxOffsetPixels, 0, 200);
        assertNotNull(profile.voidWarning.dimensionYOverrides);
        profile.voidWarning.dimensionYOverrides.values().forEach(value ->
                assertBetween(value, VoidWarningSettings.MIN_VOID_Y, VoidWarningSettings.MAX_VOID_Y));
    }

    private static void assertLocalJsonFile(String fileName) {
        assertNotNull(fileName);
        assertFalse(fileName.isBlank());
        assertFalse(fileName.contains("/"));
        assertFalse(fileName.contains("\\"));
        assertTrue(fileName.toLowerCase(Locale.ROOT).endsWith(ProfileFileNames.JSON_SUFFIX));
    }

    private static void assertColors(int[] colors) {
        assertNotNull(colors);
        assertBetween(colors.length, PrideFlag.MIN_CUSTOM_COLORS, PrideFlag.MAX_CUSTOM_COLORS);
        for (int color : colors) {
            assertRgb(color);
        }
    }

    private static void assertRgb(int color) {
        assertBetween(color, 0, 0x00FFFFFF);
    }

    private static void assertBetween(int value, int minimum, int maximum) {
        assertTrue(value >= minimum && value <= maximum,
                () -> value + " is outside [" + minimum + ", " + maximum + "]");
    }

    private static void assertBetween(float value, float minimum, float maximum) {
        assertTrue(Float.isFinite(value) && value >= minimum && value <= maximum,
                () -> value + " is outside [" + minimum + ", " + maximum + "]");
    }
}
