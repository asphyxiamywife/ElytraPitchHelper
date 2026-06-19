package com.asphyxiamywife.elytrapitchhelper.config;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProfileJsonTest {
    @Test
    void nullJsonKeepsDefaultsAndDerivesNameFromFileNameWhenNeeded() {
        Profile defaults = defaults();
        defaults.name = "";

        Profile profile = ProfileJson.parse(null, "speed_run", defaults, null);

        assertEquals("speed_run.json", profile.fileName);
        assertEquals("Speed Run", profile.name);
        assertEquals(defaults.pitch.targetUpMinecraft, profile.pitch.targetUpMinecraft);
        assertEquals(defaults.amplitude.downVelocity, profile.amplitude.downVelocity);
    }

    @Test
    void invalidFieldTypesFallBackToDefaults() {
        Profile defaults = defaults();
        JsonObject json = new JsonObject();
        JsonObject visibility = new JsonObject();
        JsonObject pitch = new JsonObject();
        JsonObject line = new JsonObject();
        json.addProperty("name", "   ");
        visibility.addProperty("showInThirdPerson", "yes");
        pitch.addProperty("targetUpMinecraft", "fast");
        pitch.addProperty("maxOffsetPixels", "42");
        line.addProperty("prideFlag", "progress");
        json.add("visibility", visibility);
        json.add("pitch", pitch);
        json.add("line", line);

        Profile profile = ProfileJson.parse(json, "custom.json", defaults, null);

        assertEquals(defaults.name, profile.name);
        assertEquals(defaults.visibility.showInThirdPerson, profile.visibility.showInThirdPerson);
        assertEquals(defaults.pitch.targetUpMinecraft, profile.pitch.targetUpMinecraft);
        assertEquals(defaults.pitch.maxOffsetPixels, profile.pitch.maxOffsetPixels);
        assertEquals("progress", profile.line.prideFlag);
    }

    @Test
    void populatedGroupedJsonOverridesDefaults() {
        Profile profile = ProfileJson.parse(groupedJson(), "profiles/tuned", defaults(), null);

        assertPopulatedProfile(profile);
    }

    @Test
    void legacyFlatJsonOverridesDefaults() {
        Profile profile = ProfileJson.parse(legacyFlatJson(), "profiles/tuned", defaults(), null);

        assertPopulatedProfile(profile);
    }

    private static void assertPopulatedProfile(Profile profile) {
        assertEquals("tuned.json", profile.fileName);
        assertEquals("Tuned", profile.name);
        assertTrue(profile.visibility.showOnlyWithFirework);
        assertFalse(profile.visibility.showInThirdPerson);
        assertEquals(-35.0f, profile.pitch.targetUpMinecraft);
        assertEquals(45.0f, profile.pitch.targetDownMinecraft);
        assertEquals(8.0f, profile.pitch.toleranceDegrees);
        assertEquals(60, profile.pitch.maxOffsetPixels);
        assertEquals(3.0f, profile.pitch.offsetPerDegree);
        assertEquals(36, profile.line.lengthPixels);
        assertEquals(3, profile.line.widthPixels);
        assertEquals(0x00AAFF, profile.line.colorRgb);
        assertTrue(profile.line.prideEnabled);
        assertEquals("trans", profile.line.prideFlag);
        assertArrayEquals(new int[] { 0x111111, 0x222222, 0x333333 }, profile.line.customPrideColors);
        assertFalse(profile.amplitude.enabled);
        assertEquals(Config.AMPLITUDE_TRIGGER_EITHER, profile.amplitude.triggerMode);
        assertEquals(70, profile.amplitude.downBlocks);
        assertEquals(55, profile.amplitude.upBlocks);
        assertEquals(6, profile.amplitude.toleranceBlocks);
        assertEquals(2.4f, profile.amplitude.downVelocity);
        assertEquals(0.4f, profile.amplitude.upVelocity);
        assertEquals(0xFF3300, profile.amplitude.cueColorRgb);
        assertTrue(profile.amplitude.cuePrideEnabled);
        assertEquals("rainbow", profile.amplitude.cuePrideFlag);
        assertArrayEquals(new int[] { 0xAA0000, 0x00AA00 }, profile.amplitude.customPrideColors);
        assertEquals(VoidWarningSettings.MODE_SIMPLE_HEIGHT, profile.voidWarning.mode);
        assertEquals(96, profile.voidWarning.simpleWarningBlocks);
        assertEquals(0xF05500, profile.voidWarning.warningColorRgb);
        assertTrue(profile.voidWarning.warningPrideEnabled);
        assertEquals("progress", profile.voidWarning.warningPrideFlag);
        assertArrayEquals(new int[] { 0x101010, 0x202020 }, profile.voidWarning.customPrideColors);
        assertFalse(profile.voidWarning.toleranceOverride);
        assertEquals(12.5f, profile.voidWarning.customToleranceDegrees);
        assertFalse(profile.voidWarning.maxOffsetOverride);
        assertEquals(72, profile.voidWarning.customMaxOffsetPixels);
        assertTrue(profile.visibility.anyElytraGlide);
    }

    private static Profile defaults() {
        Profile profile = new Profile();
        profile.name = "Default";
        profile.visibility.showOnlyWithFirework = false;
        profile.visibility.showInThirdPerson = true;
        profile.pitch.targetUpMinecraft = -40.0f;
        profile.pitch.targetDownMinecraft = 40.0f;
        profile.pitch.toleranceDegrees = 6.0f;
        profile.pitch.maxOffsetPixels = 42;
        profile.pitch.offsetPerDegree = 2.0f;
        profile.line.lengthPixels = 26;
        profile.line.widthPixels = 2;
        profile.line.colorRgb = 0xFFFFFF;
        profile.line.prideEnabled = false;
        profile.line.prideFlag = "rainbow";
        profile.amplitude.enabled = true;
        profile.amplitude.triggerMode = Config.AMPLITUDE_TRIGGER_HEIGHT;
        profile.amplitude.downBlocks = 50;
        profile.amplitude.upBlocks = 50;
        profile.amplitude.toleranceBlocks = 4;
        profile.amplitude.downVelocity = 2.0f;
        profile.amplitude.upVelocity = 0.2f;
        profile.amplitude.cueColorRgb = 0xFF0000;
        profile.amplitude.cuePrideEnabled = false;
        profile.amplitude.cuePrideFlag = "trans";
        return profile;
    }

    private static JsonObject groupedJson() {
        JsonObject json = new JsonObject();
        json.addProperty("version", Config.CURRENT_VERSION);
        json.addProperty("name", "Tuned");
        JsonObject visibility = new JsonObject();
        visibility.addProperty("showOnlyWithFirework", true);
        visibility.addProperty("showInThirdPerson", false);
        visibility.addProperty("anyElytraGlide", true);
        JsonObject pitch = new JsonObject();
        pitch.addProperty("targetUpMinecraft", -35.0f);
        pitch.addProperty("targetDownMinecraft", 45.0f);
        pitch.addProperty("toleranceDegrees", 8.0f);
        pitch.addProperty("maxOffsetPixels", 60);
        pitch.addProperty("offsetPerDegree", 3.0f);
        JsonObject line = new JsonObject();
        line.addProperty("lengthPixels", 36);
        line.addProperty("widthPixels", 3);
        line.addProperty("colorRgb", 0x00AAFF);
        line.addProperty("prideEnabled", true);
        line.addProperty("prideFlag", "trans");
        com.google.gson.JsonArray lineCustomColors = new com.google.gson.JsonArray();
        lineCustomColors.add(0x111111);
        lineCustomColors.add(0x222222);
        lineCustomColors.add(0x333333);
        line.add("customPrideColors", lineCustomColors);
        JsonObject amplitude = new JsonObject();
        amplitude.addProperty("enabled", false);
        amplitude.addProperty("triggerMode", Config.AMPLITUDE_TRIGGER_EITHER);
        amplitude.addProperty("downBlocks", 70);
        amplitude.addProperty("upBlocks", 55);
        amplitude.addProperty("toleranceBlocks", 6);
        amplitude.addProperty("downVelocity", 2.4f);
        amplitude.addProperty("upVelocity", 0.4f);
        amplitude.addProperty("cueColorRgb", 0xFF3300);
        amplitude.addProperty("cuePrideEnabled", true);
        amplitude.addProperty("cuePrideFlag", "rainbow");
        com.google.gson.JsonArray amplitudeCustomColors = new com.google.gson.JsonArray();
        amplitudeCustomColors.add(0xAA0000);
        amplitudeCustomColors.add(0x00AA00);
        amplitude.add("customPrideColors", amplitudeCustomColors);
        JsonObject voidWarning = new JsonObject();
        voidWarning.addProperty("mode", VoidWarningSettings.MODE_SIMPLE_HEIGHT);
        voidWarning.addProperty("simpleWarningBlocks", 96);
        voidWarning.addProperty("warningColorRgb", 0xF05500);
        voidWarning.addProperty("warningPrideEnabled", true);
        voidWarning.addProperty("warningPrideFlag", "progress");
        com.google.gson.JsonArray voidWarningCustomColors = new com.google.gson.JsonArray();
        voidWarningCustomColors.add(0x101010);
        voidWarningCustomColors.add(0x202020);
        voidWarning.add("customPrideColors", voidWarningCustomColors);
        voidWarning.addProperty("toleranceOverride", false);
        voidWarning.addProperty("customToleranceDegrees", 12.5f);
        voidWarning.addProperty("maxOffsetOverride", false);
        voidWarning.addProperty("customMaxOffsetPixels", 72);
        json.add("visibility", visibility);
        json.add("pitch", pitch);
        json.add("line", line);
        json.add("amplitude", amplitude);
        json.add("voidWarning", voidWarning);
        return json;
    }

    private static JsonObject legacyFlatJson() {
        JsonObject json = new JsonObject();
        json.addProperty("version", 1);
        json.addProperty("name", "Tuned");
        json.addProperty("showOnlyWithFirework", true);
        json.addProperty("showInThirdPerson", false);
        json.addProperty("anyElytraGlide", true);
        json.addProperty("targetUpMinecraft", -35.0f);
        json.addProperty("targetDownMinecraft", 45.0f);
        json.addProperty("toleranceDegrees", 8.0f);
        json.addProperty("maxOffsetPixels", 60);
        json.addProperty("offsetPerDegree", 3.0f);
        json.addProperty("lineLengthPixels", 36);
        json.addProperty("lineWidthPixels", 3);
        json.addProperty("lineColorRgb", 0x00AAFF);
        json.addProperty("linePrideEnabled", true);
        json.addProperty("linePrideFlag", "trans");
        com.google.gson.JsonArray lineCustomColors = new com.google.gson.JsonArray();
        lineCustomColors.add(0x111111);
        lineCustomColors.add(0x222222);
        lineCustomColors.add(0x333333);
        json.add("lineCustomPrideColors", lineCustomColors);
        json.addProperty("amplitudeHelperEnabled", false);
        json.addProperty("amplitudeTriggerMode", Config.AMPLITUDE_TRIGGER_EITHER);
        json.addProperty("amplitudeDownBlocks", 70);
        json.addProperty("amplitudeUpBlocks", 55);
        json.addProperty("amplitudeToleranceBlocks", 6);
        json.addProperty("amplitudeDownVelocity", 2.4f);
        json.addProperty("amplitudeUpVelocity", 0.4f);
        json.addProperty("amplitudeCueColorRgb", 0xFF3300);
        json.addProperty("amplitudeCuePrideEnabled", true);
        json.addProperty("amplitudeCuePrideFlag", "rainbow");
        com.google.gson.JsonArray amplitudeCustomColors = new com.google.gson.JsonArray();
        amplitudeCustomColors.add(0xAA0000);
        amplitudeCustomColors.add(0x00AA00);
        json.add("amplitudeCustomPrideColors", amplitudeCustomColors);
        json.addProperty("voidWarningMode", VoidWarningSettings.MODE_SIMPLE_HEIGHT);
        json.addProperty("voidWarningSimpleWarningBlocks", 96);
        json.addProperty("voidWarningColorRgb", 0xF05500);
        json.addProperty("voidWarningPrideEnabled", true);
        json.addProperty("voidWarningPrideFlag", "progress");
        com.google.gson.JsonArray voidWarningCustomColors = new com.google.gson.JsonArray();
        voidWarningCustomColors.add(0x101010);
        voidWarningCustomColors.add(0x202020);
        json.add("voidWarningCustomPrideColors", voidWarningCustomColors);
        json.addProperty("voidWarningToleranceOverride", false);
        json.addProperty("voidWarningCustomToleranceDegrees", 12.5f);
        json.addProperty("voidWarningMaxOffsetOverride", false);
        json.addProperty("voidWarningCustomMaxOffsetPixels", 72);
        return json;
    }
}
