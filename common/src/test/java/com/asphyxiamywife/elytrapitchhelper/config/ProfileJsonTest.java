package com.asphyxiamywife.elytrapitchhelper.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProfileJsonTest {
    @Test
    void nullJsonKeepsDefaultsAndDerivesNameFromFileNameWhenNeeded() {
        Profile defaults = defaults();
        defaults = defaults.withName("");

        Profile profile = ProfileJson.parse(null, "speed_run", defaults, null);

        assertEquals("speed_run.json", profile.fileName());
        assertEquals("Speed Run", profile.name());
        assertEquals(defaults.pitch().targetUpMinecraft(), profile.pitch().targetUpMinecraft());
        assertEquals(defaults.amplitude().downVelocity(), profile.amplitude().downVelocity());
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

        assertEquals(defaults.name(), profile.name());
        assertEquals(defaults.visibility().showInThirdPerson(), profile.visibility().showInThirdPerson());
        assertEquals(defaults.pitch().targetUpMinecraft(), profile.pitch().targetUpMinecraft());
        assertEquals(defaults.pitch().maxOffsetPixels(), profile.pitch().maxOffsetPixels());
        assertEquals("progress", profile.line().prideFlag());
    }

    @Test
    void populatedGroupedJsonOverridesDefaults() {
        Profile profile = ProfileJson.parse(groupedJson(), "profiles/tuned", defaults(), null);

        assertPopulatedProfile(profile);
        assertFalse(profile.amplitude().motionGlyphsEnabled());
        assertEquals(850, profile.amplitude().pullUpGlyphMillis());
        assertEquals(1_750, profile.amplitude().releaseDownGlyphMillis());
        ProfileInvariants.assertValid(profile);
    }

    @Test
    void legacyFlatJsonOverridesDefaults() {
        Profile profile = ProfileJson.parse(legacyFlatJson(), "profiles/tuned", defaults(), null);

        assertPopulatedProfile(profile);
        assertEquals(defaults().amplitude().pullUpGlyphMillis(), profile.amplitude().pullUpGlyphMillis());
        assertEquals(defaults().amplitude().releaseDownGlyphMillis(),
                profile.amplitude().releaseDownGlyphMillis());
    }

    @Test
    void registryWriterRoundTripsEveryProfileSetting() {
        Profile source = ProfileJson.parse(groupedJson(), "profiles/tuned", defaults(), null);

        Profile reloaded = ProfileJson.parse(ProfileJson.write(source), source.fileName(), defaults(), null);

        assertEquals(ProfileJson.write(source), ProfileJson.write(reloaded));
    }

    @Test
    void invalidDimensionOverrideNumberIsIgnored() {
        JsonObject json = JsonParser.parseString("""
                {"voidWarning":{"dimensionYOverrides":{"huge":9e999990,"minecraft:overworld":-64}}}
                """).getAsJsonObject();

        Profile profile = ProfileJson.parse(json, "custom.json", defaults(), null);

        assertFalse(profile.voidWarning().dimensionYOverrides().containsKey("huge"));
        assertEquals(-64, profile.voidWarning().dimensionYOverrides().get("minecraft:overworld"));
    }

    @Test
    void integerFieldsRejectOverflowAndFractionsWithoutWrapping() {
        Profile defaults = defaults();
        JsonObject json = JsonParser.parseString("""
                {
                  "pitch":{"maxOffsetPixels":4294967296},
                  "line":{"customPrideColors":[4294967296,16777215]},
                  "amplitude":{"toleranceBlocks":1.5},
                  "voidWarning":{"dimensionYOverrides":{
                    "mod:overflow":4294967296,
                    "mod:fraction":1.5,
                    "mod:integral-exponent":1e2
                  }}
                }
                """).getAsJsonObject();

        Profile profile = ProfileJson.parse(json, "custom.json", defaults, null);

        assertEquals(defaults.pitch().maxOffsetPixels(), profile.pitch().maxOffsetPixels());
        assertArrayEquals(defaults.line().customPrideColors(), profile.line().customPrideColors());
        assertEquals(defaults.amplitude().toleranceBlocks(), profile.amplitude().toleranceBlocks());
        assertFalse(profile.voidWarning().dimensionYOverrides().containsKey("mod:overflow"));
        assertFalse(profile.voidWarning().dimensionYOverrides().containsKey("mod:fraction"));
        assertEquals(100, profile.voidWarning().dimensionYOverrides().get("mod:integral-exponent"));
    }

    @Test
    void malformedDimensionIdsAreIgnored() {
        JsonObject json = JsonParser.parseString("""
                {"voidWarning":{"dimensionYOverrides":{
                  "Bad Dimension":5,
                  "minecraft:":5,
                  "mod:valid/path":-64
                }}}
                """).getAsJsonObject();

        Profile profile = ProfileJson.parse(json, "custom.json", defaults(), null);

        assertFalse(profile.voidWarning().dimensionYOverrides().containsKey("Bad Dimension"));
        assertFalse(profile.voidWarning().dimensionYOverrides().containsKey("minecraft:"));
        assertEquals(-64, profile.voidWarning().dimensionYOverrides().get("mod:valid/path"));
    }

    private static void assertPopulatedProfile(Profile profile) {
        assertEquals("tuned.json", profile.fileName());
        assertEquals("Tuned", profile.name());
        assertTrue(profile.visibility().showOnlyWithFirework());
        assertFalse(profile.visibility().showInThirdPerson());
        assertEquals(-35.0f, profile.pitch().targetUpMinecraft());
        assertEquals(45.0f, profile.pitch().targetDownMinecraft());
        assertEquals(8.0f, profile.pitch().toleranceDegrees());
        assertEquals(60, profile.pitch().maxOffsetPixels());
        assertEquals(3.0f, profile.pitch().offsetPerDegree());
        assertEquals(36, profile.line().lengthPixels());
        assertEquals(3, profile.line().widthPixels());
        assertEquals(0x00AAFF, profile.line().colorRgb());
        assertTrue(profile.line().prideEnabled());
        assertEquals("trans", profile.line().prideFlag());
        assertArrayEquals(new int[] { 0x111111, 0x222222, 0x333333 }, profile.line().customPrideColors());
        assertFalse(profile.amplitude().enabled());
        assertEquals(Config.AMPLITUDE_TRIGGER_EITHER, profile.amplitude().triggerMode());
        assertEquals(70, profile.amplitude().downBlocks());
        assertEquals(55, profile.amplitude().upBlocks());
        assertEquals(6, profile.amplitude().toleranceBlocks());
        assertEquals(2.4f, profile.amplitude().downVelocity());
        assertEquals(0.4f, profile.amplitude().upVelocity());
        assertFalse(profile.amplitude().repeatDiveCue());
        assertTrue(profile.amplitude().repeatClimbCue());
        assertEquals(0xFF3300, profile.amplitude().cueColorRgb());
        assertTrue(profile.amplitude().cuePrideEnabled());
        assertEquals("rainbow", profile.amplitude().cuePrideFlag());
        assertArrayEquals(new int[] { 0xAA0000, 0x00AA00 }, profile.amplitude().customPrideColors());
        assertEquals(VoidWarningSettings.MODE_SIMPLE_HEIGHT, profile.voidWarning().mode());
        assertEquals(96, profile.voidWarning().simpleWarningBlocks());
        assertEquals(0xF05500, profile.voidWarning().warningColorRgb());
        assertTrue(profile.voidWarning().warningPrideEnabled());
        assertEquals("progress", profile.voidWarning().warningPrideFlag());
        assertArrayEquals(new int[] { 0x101010, 0x202020 }, profile.voidWarning().customPrideColors());
        assertFalse(profile.voidWarning().toleranceOverride());
        assertEquals(12.5f, profile.voidWarning().customToleranceDegrees());
        assertFalse(profile.voidWarning().maxOffsetOverride());
        assertEquals(72, profile.voidWarning().customMaxOffsetPixels());
        assertTrue(profile.visibility().anyElytraGlide());
        assertEquals(75, profile.commandPaletteAppearance().shadowOpacity());
        assertEquals(40, profile.commandPaletteAppearance().blurAmount());
        assertEquals(0x3366FF, profile.commandPaletteAppearance().accentColorRgb());
        assertEquals(0x101820, profile.commandPaletteAppearance().baseColorRgb());
    }

    private static Profile defaults() {
        Profile profile = ConfigTestFixtures.tunedProfile("Default", null);
        return profile
                .withVisibility(profile.visibility().withShowInThirdPerson(true))
                .withAmplitude(profile.amplitude().withUpBlocks(50).withRepeatClimbCue(false))
                .withCommandPaletteAppearance(new CommandPaletteAppearanceSettings());
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
        amplitude.addProperty("repeatDiveCue", false);
        amplitude.addProperty("repeatClimbCue", true);
        amplitude.addProperty("motionGlyphsEnabled", false);
        amplitude.addProperty("pullUpGlyphMillis", 850);
        amplitude.addProperty("releaseDownGlyphMillis", 1_750);
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
        JsonObject commandPaletteAppearance = new JsonObject();
        commandPaletteAppearance.addProperty("shadowOpacity", 75);
        commandPaletteAppearance.addProperty("blurAmount", 40);
        commandPaletteAppearance.addProperty("accentColorRgb", 0x3366FF);
        commandPaletteAppearance.addProperty("baseColorRgb", 0x101820);
        json.add("visibility", visibility);
        json.add("pitch", pitch);
        json.add("line", line);
        json.add("amplitude", amplitude);
        json.add("voidWarning", voidWarning);
        json.add("commandPaletteAppearance", commandPaletteAppearance);
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
        json.addProperty("amplitudeRepeatDiveCue", false);
        json.addProperty("amplitudeRepeatClimbCue", true);
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
        json.addProperty("commandPaletteShadowOpacity", 75);
        json.addProperty("commandPaletteBlurAmount", 40);
        json.addProperty("commandPaletteAccentColorRgb", 0x3366FF);
        json.addProperty("commandPaletteBaseColorRgb", 0x101820);
        return json;
    }
}
