package com.asphyxiamywife.elytrapitchhelper.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfigProfileWorkflowTest {
    @Test
    void selectingProfileSavesCurrentSettingsBeforeApplyingNextProfile() {
        Config config = configWithProfiles(profile("Cruise", "cruise.json", -40.0f),
                profile("Dive", "dive.json", -20.0f));
        config.selectProfile(0);
        config.pitch.targetUpMinecraft = -33.0f;
        config.line.colorRgb = 0x123456;

        assertSame(config.pitch, config.profile(0).pitch);
        assertSame(config.line, config.profile(0).line);

        config.selectProfile(1);

        assertEquals(-33.0f, config.profile(0).pitch.targetUpMinecraft);
        assertEquals(0x123456, config.profile(0).line.colorRgb);
        assertEquals(-20.0f, config.pitch.targetUpMinecraft);
        assertSame(config.pitch, config.profile(1).pitch);
        assertSame(config.line, config.profile(1).line);
        assertTrue(config.isActiveProfile(1));
        assertEquals("dive.json", config.activeProfileFile);
    }

    @Test
    void stagedDeleteCanRestoreRemovedActiveProfile() {
        Config config = configWithProfiles(profile("Cruise", "cruise.json", -40.0f),
                profile("Dive", "dive.json", -20.0f));
        config.selectProfile(1);

        StagedProfileDelete deleted = config.stageDeleteProfile(1);

        assertEquals(1, config.profileCount());
        assertEquals("cruise.json", config.activeProfileFile);
        assertEquals(-1, config.profileIndexByFileName("dive.json"));

        config.restoreStagedProfileDelete(deleted);

        assertEquals(2, config.profileCount());
        assertEquals("dive.json", config.activeProfileFile);
        assertEquals("Dive", config.activeProfileName());
        assertTrue(config.isActiveProfile(config.profileIndexByFileName("dive.json")));
    }

    @Test
    void copyBindsSettingsToCopiedActiveProfile() {
        Config config = configWithProfiles(profile("Cruise", "cruise.json", -40.0f),
                profile("Dive", "dive.json", -20.0f));
        config.pitch.targetUpMinecraft = -33.0f;

        Config copy = config.copy();

        assertEquals(-33.0f, copy.profile(0).pitch.targetUpMinecraft);
        assertSame(copy.pitch, copy.profile(0).pitch);
        assertNotSame(config.pitch, copy.pitch);
    }

    @Test
    void copyPreservesUnboundActiveSettings() {
        Config config = new Config();
        Profile profile = profile("Cruise", "cruise.json", -40.0f);
        config.profiles = new ArrayList<>(List.of(profile));
        config.activeProfileIndex = 0;
        config.activeProfileFile = profile.fileName;
        config.pitch.targetUpMinecraft = -33.0f;

        Config copy = config.copy();

        assertEquals(-33.0f, copy.profile(0).pitch.targetUpMinecraft);
        assertSame(copy.pitch, copy.profile(0).pitch);
    }

    private static Config configWithProfiles(Profile... profiles) {
        Config config = new Config();
        config.profiles = new ArrayList<>(List.of(profiles));
        config.activeProfileIndex = 0;
        config.activeProfileFile = profiles[0].fileName;
        profiles[0].bindTo(config);
        return config;
    }

    private static Profile profile(String name, String fileName, float targetUpMinecraft) {
        Profile profile = new Profile();
        profile.name = name;
        profile.fileName = fileName;
        profile.visibility.showOnlyWithFirework = false;
        profile.visibility.showInThirdPerson = false;
        profile.pitch.targetUpMinecraft = targetUpMinecraft;
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
        profile.amplitude.upBlocks = 45;
        profile.amplitude.toleranceBlocks = 4;
        profile.amplitude.downVelocity = 2.0f;
        profile.amplitude.upVelocity = 0.2f;
        profile.amplitude.cueColorRgb = 0xFF0000;
        profile.amplitude.cuePrideEnabled = false;
        profile.amplitude.cuePrideFlag = "trans";
        return profile;
    }
}
