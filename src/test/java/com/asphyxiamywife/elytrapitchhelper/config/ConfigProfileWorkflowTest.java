package com.asphyxiamywife.elytrapitchhelper.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfigProfileWorkflowTest {
    @Test
    void selectingProfileSavesCurrentSettingsBeforeApplyingNextProfile() {
        Config config = configWithProfiles(profile("Cruise", "cruise.json", -40.0f),
                profile("Dive", "dive.json", -20.0f));
        config.selectProfile(0);
        config.targetUpMinecraft = -33.0f;
        config.lineColorRgb = 0x123456;

        config.selectProfile(1);

        assertEquals(-33.0f, config.profile(0).targetUpMinecraft);
        assertEquals(0x123456, config.profile(0).lineColorRgb);
        assertEquals(-20.0f, config.targetUpMinecraft);
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

    private static Config configWithProfiles(Profile... profiles) {
        Config config = new Config();
        config.profiles = new ArrayList<>(List.of(profiles));
        config.activeProfileIndex = 0;
        config.activeProfileFile = profiles[0].fileName;
        profiles[0].applyTo(config);
        return config;
    }

    private static Profile profile(String name, String fileName, float targetUpMinecraft) {
        Profile profile = new Profile();
        profile.name = name;
        profile.fileName = fileName;
        profile.showOnlyWithFirework = false;
        profile.showInThirdPerson = false;
        profile.targetUpMinecraft = targetUpMinecraft;
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
        profile.amplitudeUpBlocks = 45;
        profile.amplitudeToleranceBlocks = 4;
        profile.amplitudeDownVelocity = 2.0f;
        profile.amplitudeUpVelocity = 0.2f;
        profile.amplitudeCueColorRgb = 0xFF0000;
        profile.amplitudeCuePrideEnabled = false;
        profile.amplitudeCuePrideFlag = "trans";
        return profile;
    }
}
