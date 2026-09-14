package com.asphyxiamywife.elytrapitchhelper.config;

import static com.asphyxiamywife.elytrapitchhelper.config.ConfigTestFixtures.fs;

import com.asphyxiamywife.elytrapitchhelper.testing.IsolatedConfigRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UpgradeFromVersionTwoTest {
    @IsolatedConfigRoot
    Path configRoot;

    private static final String VERSION_TWO_PROFILE = """
            {
              "version": 2,
              "name": "Cruise",
              "visibility": {
                "showOnlyWithFirework": true,
                "showInThirdPerson": true,
                "anyElytraGlide": true
              },
              "pitch": {
                "targetUpMinecraft": -35.0,
                "targetDownMinecraft": 35.0,
                "toleranceDegrees": 3.0,
                "maxOffsetPixels": 120,
                "offsetPerDegree": 0.5
              },
              "line": {
                "lengthPixels": 24,
                "widthPixels": 3,
                "colorRgb": 3407718,
                "prideEnabled": true,
                "prideFlag": "trans",
                "customPrideColors": [1118481, 2236962]
              },
              "amplitude": {
                "enabled": true,
                "triggerMode": 1,
                "downBlocks": 12,
                "upBlocks": 18,
                "toleranceBlocks": 2,
                "downVelocity": 0.6,
                "upVelocity": 0.3,
                "cueColorRgb": 16711850,
                "cuePrideEnabled": true,
                "cuePrideFlag": "bisexual",
                "customPrideColors": [3355443, 4473924]
              },
              "voidWarning": {
                "enabled": true,
                "mode": 1,
                "lookaheadSeconds": 4.0,
                "simpleWarningBlocks": 30,
                "warningColorRgb": 16720384,
                "warningPrideEnabled": true,
                "warningPrideFlag": "lesbian",
                "customPrideColors": [5592405, 6710886],
                "toleranceOverride": true,
                "customToleranceDegrees": 8.0,
                "maxOffsetOverride": true,
                "customMaxOffsetPixels": 150,
                "dimensionYOverrides": {
                  "minecraft:the_end": 10
                }
              },
              "commandPaletteAppearance": {
                "shadowOpacity": 60,
                "blurAmount": 5,
                "accentColorRgb": 65450,
                "baseColorRgb": 1052688
              }
            }
            """;

    private static final String VERSION_TWO_MAIN_CONFIG = """
            {
              "version": 2,
              "enabled": false,
              "activeProfileIndex": 0,
              "activeProfileFile": "cruise.json",
              "profileSortMode": 1
            }
            """;

    @Test
    void everySettingV2WroteSurvivesTheUpgrade() throws IOException {
        seedVersionTwoInstall();

        Profile profile = Config.load(fs()).profile(0);

        assertEquals("Cruise", profile.name());
        assertTrue(profile.visibility().showOnlyWithFirework());
        assertTrue(profile.visibility().showInThirdPerson());
        assertTrue(profile.visibility().anyElytraGlide());

        assertEquals(-35.0f, profile.pitch().targetUpMinecraft());
        assertEquals(35.0f, profile.pitch().targetDownMinecraft());
        assertEquals(3.0f, profile.pitch().toleranceDegrees());
        assertEquals(120, profile.pitch().maxOffsetPixels());
        assertEquals(0.5f, profile.pitch().offsetPerDegree());

        assertEquals(24, profile.line().lengthPixels());
        assertEquals(3, profile.line().widthPixels());
        assertEquals(0x33FF66, profile.line().colorRgb());
        assertTrue(profile.line().prideEnabled());
        assertEquals("trans", profile.line().prideFlag());
        assertArrayEquals(new int[] {0x111111, 0x222222}, profile.line().customPrideColors());

        assertTrue(profile.amplitude().enabled());
        assertEquals(Config.AMPLITUDE_TRIGGER_VELOCITY, profile.amplitude().triggerMode());
        assertEquals(12, profile.amplitude().downBlocks());
        assertEquals(18, profile.amplitude().upBlocks());
        assertEquals(2, profile.amplitude().toleranceBlocks());
        assertEquals(0.6f, profile.amplitude().downVelocity());
        assertEquals(0.3f, profile.amplitude().upVelocity());
        assertEquals(0xFF00AA, profile.amplitude().cueColorRgb());
        assertTrue(profile.amplitude().cuePrideEnabled());
        assertEquals("bisexual", profile.amplitude().cuePrideFlag());
        assertArrayEquals(new int[] {0x333333, 0x444444}, profile.amplitude().customPrideColors());

        assertTrue(profile.voidWarning().enabled());
        assertEquals(VoidWarningSettings.MODE_SIMPLE_HEIGHT, profile.voidWarning().mode());
        assertEquals(4.0f, profile.voidWarning().lookaheadSeconds());
        assertEquals(30, profile.voidWarning().simpleWarningBlocks());
        assertEquals(0xFF2200, profile.voidWarning().warningColorRgb());
        assertTrue(profile.voidWarning().warningPrideEnabled());
        assertEquals("lesbian", profile.voidWarning().warningPrideFlag());
        assertArrayEquals(new int[] {0x555555, 0x666666}, profile.voidWarning().customPrideColors());
        assertTrue(profile.voidWarning().toleranceOverride());
        assertEquals(8.0f, profile.voidWarning().customToleranceDegrees());
        assertTrue(profile.voidWarning().maxOffsetOverride());
        assertEquals(150, profile.voidWarning().customMaxOffsetPixels());
        assertEquals(Map.of("minecraft:the_end", 10), profile.voidWarning().dimensionYOverrides());

        assertEquals(60, profile.commandPaletteAppearance().shadowOpacity());
        assertEquals(5, profile.commandPaletteAppearance().blurAmount());
        assertEquals(0x00FFAA, profile.commandPaletteAppearance().accentColorRgb());
        assertEquals(0x101010, profile.commandPaletteAppearance().baseColorRgb());
    }

    @Test
    void theMainConfigV2WroteSurvivesTheUpgrade() throws IOException {
        seedVersionTwoInstall();

        Config config = Config.load(fs());

        assertFalse(config.enabled);
        assertEquals(0, config.activeProfileIndex);
        assertEquals("cruise.json", config.activeProfileFile);
        assertEquals(Config.PROFILE_SORT_NAME, config.profileSortMode);
        assertEquals(1, config.profileCount());
    }

    @Test
    void settingsAddedSinceV2ArriveAtTheirDefaults() throws IOException {
        seedVersionTwoInstall();
        Profile defaults = Config.defaultProfileTemplate();

        Profile profile = Config.load(fs()).profile(0);

        assertEquals(defaults.amplitude().repeatClimbCue(), profile.amplitude().repeatClimbCue());
        assertEquals(defaults.amplitude().repeatDiveCue(), profile.amplitude().repeatDiveCue());
    }

    @Test
    void aSecondLaunchReadsBackWhatTheFirstOneWrote() throws IOException {
        seedVersionTwoInstall();

        Profile first = Config.load(fs()).profile(0);
        Profile second = Config.load(fs()).profile(0);

        assertEquals(first, second);
    }

    private void seedVersionTwoInstall() throws IOException {
        Path configDirectory = Config.getConfigDirectory(fs());
        Path profileDirectory = Config.getProfileDirectory(fs());
        Files.createDirectories(profileDirectory);
        Files.writeString(configDirectory.resolve("elytra-pitch-helper.json"), VERSION_TWO_MAIN_CONFIG,
                StandardCharsets.UTF_8);
        Files.writeString(profileDirectory.resolve("cruise.json"), VERSION_TWO_PROFILE,
                StandardCharsets.UTF_8);
    }
}
