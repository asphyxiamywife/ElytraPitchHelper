package com.asphyxiamywife.elytrapitchhelper.config;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

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
        assertEquals(defaults.targetUpMinecraft, profile.targetUpMinecraft);
        assertEquals(defaults.amplitudeDownVelocity, profile.amplitudeDownVelocity);
    }

    @Test
    void invalidFieldTypesFallBackToDefaults() {
        Profile defaults = defaults();
        JsonObject json = new JsonObject();
        json.addProperty("name", "   ");
        json.addProperty("showInThirdPerson", "yes");
        json.addProperty("targetUpMinecraft", "fast");
        json.addProperty("maxOffsetPixels", "42");
        json.addProperty("linePrideFlag", "progress");

        Profile profile = ProfileJson.parse(json, "custom.json", defaults, null);

        assertEquals(defaults.name, profile.name);
        assertEquals(defaults.showInThirdPerson, profile.showInThirdPerson);
        assertEquals(defaults.targetUpMinecraft, profile.targetUpMinecraft);
        assertEquals(defaults.maxOffsetPixels, profile.maxOffsetPixels);
        assertEquals("progress", profile.linePrideFlag);
    }

    @Test
    void populatedJsonOverridesDefaults() {
        Profile profile = ProfileJson.parse(populatedJson(), "profiles/tuned", defaults(), null);

        assertEquals("tuned.json", profile.fileName);
        assertEquals("Tuned", profile.name);
        assertTrue(profile.showOnlyWithFirework);
        assertFalse(profile.showInThirdPerson);
        assertEquals(-35.0f, profile.targetUpMinecraft);
        assertEquals(45.0f, profile.targetDownMinecraft);
        assertEquals(8.0f, profile.toleranceDegrees);
        assertEquals(60, profile.maxOffsetPixels);
        assertEquals(3.0f, profile.offsetPerDegree);
        assertEquals(36, profile.lineLengthPixels);
        assertEquals(3, profile.lineWidthPixels);
        assertEquals(0x00AAFF, profile.lineColorRgb);
        assertTrue(profile.linePrideEnabled);
        assertEquals("trans", profile.linePrideFlag);
        assertFalse(profile.amplitudeHelperEnabled);
        assertEquals(Config.AMPLITUDE_TRIGGER_EITHER, profile.amplitudeTriggerMode);
        assertEquals(70, profile.amplitudeDownBlocks);
        assertEquals(55, profile.amplitudeUpBlocks);
        assertEquals(6, profile.amplitudeToleranceBlocks);
        assertEquals(2.4f, profile.amplitudeDownVelocity);
        assertEquals(0.4f, profile.amplitudeUpVelocity);
        assertEquals(0xFF3300, profile.amplitudeCueColorRgb);
        assertTrue(profile.amplitudeCuePrideEnabled);
        assertEquals("rainbow", profile.amplitudeCuePrideFlag);
    }

    private static Profile defaults() {
        Profile profile = new Profile();
        profile.name = "Default";
        profile.showOnlyWithFirework = false;
        profile.showInThirdPerson = true;
        profile.targetUpMinecraft = -40.0f;
        profile.targetDownMinecraft = 40.0f;
        profile.toleranceDegrees = 6.0f;
        profile.maxOffsetPixels = 42;
        profile.offsetPerDegree = 2.0f;
        profile.lineLengthPixels = 26;
        profile.lineWidthPixels = 2;
        profile.lineColorRgb = 0xFFFFFF;
        profile.linePrideEnabled = false;
        profile.linePrideFlag = "rainbow";
        profile.amplitudeHelperEnabled = true;
        profile.amplitudeTriggerMode = Config.AMPLITUDE_TRIGGER_HEIGHT;
        profile.amplitudeDownBlocks = 50;
        profile.amplitudeUpBlocks = 50;
        profile.amplitudeToleranceBlocks = 4;
        profile.amplitudeDownVelocity = 2.0f;
        profile.amplitudeUpVelocity = 0.2f;
        profile.amplitudeCueColorRgb = 0xFF0000;
        profile.amplitudeCuePrideEnabled = false;
        profile.amplitudeCuePrideFlag = "trans";
        return profile;
    }

    private static JsonObject populatedJson() {
        JsonObject json = new JsonObject();
        json.addProperty("version", Config.CURRENT_VERSION);
        json.addProperty("name", "Tuned");
        json.addProperty("showOnlyWithFirework", true);
        json.addProperty("showInThirdPerson", false);
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
        return json;
    }
}
