package com.asphyxiamywife.elytrapitchhelper.config;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static com.asphyxiamywife.elytrapitchhelper.testing.RangeAssertions.assertBetween;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ProfileInvariants {
    private ProfileInvariants() {
    }

    public static void assertValid(Profile profile) {
        assertNotNull(profile);
        assertEquals(Config.CURRENT_VERSION, profile.version());
        assertNotNull(profile.visibility());
        assertNotNull(profile.pitch());
        assertNotNull(profile.line());
        assertNotNull(profile.amplitude());
        assertNotNull(profile.voidWarning());

        assertBetween(profile.pitch().targetUpMinecraft(), -90.0f, 0.0f);
        assertBetween(profile.pitch().targetDownMinecraft(), 0.0f, 90.0f);
        assertBetween(profile.pitch().toleranceDegrees(), 1.0f, 45.0f);
        assertBetween(profile.pitch().maxOffsetPixels(), 0, 200);
        assertBetween(profile.pitch().offsetPerDegree(), 0.25f, 10.0f);

        assertBetween(profile.line().lengthPixels(), 2, 200);
        assertBetween(profile.line().widthPixels(), 1, 20);
        assertRgb(profile.line().colorRgb());
        assertTrue(PrideFlag.isValidId(profile.line().prideFlag()));
        assertColors(profile.line().customPrideColors());

        assertBetween(profile.amplitude().triggerMode(), Config.AMPLITUDE_TRIGGER_HEIGHT,
                Config.AMPLITUDE_TRIGGER_EITHER);
        assertBetween(profile.amplitude().downBlocks(), 1, 1024);
        assertBetween(profile.amplitude().upBlocks(), 1, 1024);
        assertBetween(profile.amplitude().toleranceBlocks(), 0, 128);
        assertBetween(profile.amplitude().downVelocity(), 0.1f, 5.0f);
        assertBetween(profile.amplitude().upVelocity(), 0.0f, 4.95f);
        assertTrue(profile.amplitude().upVelocity() < profile.amplitude().downVelocity());
        assertRgb(profile.amplitude().cueColorRgb());
        assertTrue(PrideFlag.isValidId(profile.amplitude().cuePrideFlag()));
        assertColors(profile.amplitude().customPrideColors());

        assertBetween(profile.voidWarning().mode(), VoidWarningSettings.MODE_PREDICTED_TIME,
                VoidWarningSettings.MODE_SIMPLE_HEIGHT);
        assertBetween(profile.voidWarning().lookaheadSeconds(), 1.0f, 15.0f);
        assertBetween(profile.voidWarning().simpleWarningBlocks(), 1, 512);
        assertRgb(profile.voidWarning().warningColorRgb());
        assertTrue(PrideFlag.isValidId(profile.voidWarning().warningPrideFlag()));
        assertColors(profile.voidWarning().customPrideColors());
        assertBetween(profile.voidWarning().customToleranceDegrees(),
                VoidWarningSettings.MIN_CUSTOM_TOLERANCE_DEGREES,
                VoidWarningSettings.MAX_CUSTOM_TOLERANCE_DEGREES);
        assertBetween(profile.voidWarning().customMaxOffsetPixels(), 0, 200);
        assertNotNull(profile.voidWarning().dimensionYOverrides());
        profile.voidWarning().dimensionYOverrides().forEach((dimension, value) -> {
            assertTrue(VoidWarningSettings.isValidDimensionId(dimension));
            assertBetween(value, VoidWarningSettings.MIN_VOID_Y, VoidWarningSettings.MAX_VOID_Y);
        });
    }

    public static void assertValidConfig(Config config) {
        assertNotNull(config.profiles);
        assertFalse(config.profiles.isEmpty());
        assertBetween(config.activeProfileIndex, 0, config.profiles.size() - 1);
        assertEquals(config.profiles.get(config.activeProfileIndex).fileName(), config.activeProfileFile);
        assertEquals(config.activeProfile(), config.profile(config.activeProfileIndex));
        assertBetween(config.profileSortMode, Config.PROFILE_SORT_CREATED, Config.PROFILE_SORT_MODIFIED);
        assertNotNull(config.commandPaletteUsage);

        Set<String> files = new HashSet<>();
        for (Profile profile : config.profiles) {
            assertNotNull(profile.name());
            assertFalse(profile.name().isBlank());
            assertLocalJsonFile(profile.fileName());
            assertValid(profile);
            assertTrue(files.add(profile.fileName().toLowerCase(Locale.ROOT)));
        }
    }

    public static void assertLocalJsonFile(String fileName) {
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
}
