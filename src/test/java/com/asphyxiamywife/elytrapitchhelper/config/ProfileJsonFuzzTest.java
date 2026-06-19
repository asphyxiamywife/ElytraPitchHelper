package com.asphyxiamywife.elytrapitchhelper.config;

import com.code_intelligence.jazzer.junit.FuzzTest;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProfileJsonFuzzTest {
    @MethodSource("validProfileSeeds")
    @FuzzTest(maxDuration = "30s")
    void arbitraryJsonProducesAValidSanitizedProfile(byte[] input) {
        JsonElement json;
        try {
            json = JsonParser.parseString(new String(input, StandardCharsets.UTF_8));
        } catch (JsonParseException expectedForMalformedInput) {
            return;
        }

        if (!json.isJsonObject()) {
            return;
        }

        Profile defaults = new Profile();
        defaults.name = "Default";
        Profile profile = ProfileJson.parse(json.getAsJsonObject(), "fuzz.json", defaults, null);
        profile.sanitize(null, defaults);

        assertEquals(Config.CURRENT_VERSION, profile.version);
        assertEquals("fuzz.json", profile.fileName);
        assertNotNull(profile.name);
        assertFalse(profile.name.isBlank());

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

    private static Stream<Arguments> validProfileSeeds() {
        return Stream.of(
                "{}",
                "{\"name\":\"Fuzz\",\"pitch\":{\"targetUpMinecraft\":-40}}",
                "{\"line\":{\"customPrideColors\":[0,16777215]}}",
                "{\"voidWarning\":{\"dimensionYOverrides\":{\"minecraft:overworld\":-64}}}",
                "{\"targetUpMinecraft\":-35,\"lineWidthPixels\":3}")
                .map(seed -> Arguments.of((Object) seed.getBytes(StandardCharsets.UTF_8)));
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
