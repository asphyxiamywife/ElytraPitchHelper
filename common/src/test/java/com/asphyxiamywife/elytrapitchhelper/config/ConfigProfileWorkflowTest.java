package com.asphyxiamywife.elytrapitchhelper.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfigProfileWorkflowTest {
    @Test
    void copyingConfigPreservesUnrecoverableProfileSaveProtection() {
        Config source = new Config();
        source.skipNextProfileSave = true;

        Config copy = source.copy();
        Config restored = new Config();
        restored.restoreFrom(source);

        assertTrue(copy.skipNextProfileSave);
        assertTrue(restored.skipNextProfileSave);
    }

    @Test
    void selectingProfileReadsSettingsFromTheNewActiveProfile() {
        Config config = configWithProfiles(profile("Cruise", "cruise.json", -40.0f),
                profile("Dive", "dive.json", -20.0f));
        config.selectProfile(0);
        setTargetUp(config, 0, -33.0f);
        config.replaceProfile(0, config.profile(0).withLine(
                config.profile(0).line().withColorRgb(0x123456)));

        assertSame(config.pitch(), config.profile(0).pitch());
        assertSame(config.line(), config.profile(0).line());

        config.selectProfile(1);

        assertEquals(-33.0f, config.profile(0).pitch().targetUpMinecraft());
        assertEquals(0x123456, config.profile(0).line().colorRgb());
        assertEquals(-20.0f, config.pitch().targetUpMinecraft());
        assertSame(config.pitch(), config.profile(1).pitch());
        assertSame(config.line(), config.profile(1).line());
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
    void copyStructurallySharesImmutableActiveSettings() {
        Config config = configWithProfiles(profile("Cruise", "cruise.json", -40.0f),
                profile("Dive", "dive.json", -20.0f));
        setTargetUp(config, 0, -33.0f);
        config.replaceProfile(0, config.profile(0).withVisibility(
                config.profile(0).visibility().withAnyElytraGlide(true)));

        Config copy = config.copy();

        assertEquals(-33.0f, copy.profile(0).pitch().targetUpMinecraft());
        assertTrue(copy.visibility().anyElytraGlide());
        assertSame(copy.pitch(), copy.profile(0).pitch());
        assertSame(config.pitch(), copy.pitch());
        assertSame(config.visibility(), copy.visibility());
    }

    @Test
    void copyPreservesActiveSettings() {
        Config config = new Config();
        Profile profile = profile("Cruise", "cruise.json", -40.0f);
        config.profiles = new ArrayList<>(List.of(profile));
        config.activeProfileIndex = 0;
        config.activeProfileFile = profile.fileName();
        setTargetUp(config, 0, -33.0f);

        Config copy = config.copy();

        assertEquals(-33.0f, copy.profile(0).pitch().targetUpMinecraft());
        assertSame(copy.pitch(), copy.profile(0).pitch());
    }

    @Test
    void resettingProfileInvalidatesModifiedOrderingBeforeCopy() {
        Config config = configWithProfiles(profile("Older", "older.json", -40.0f),
                profile("Newer", "newer.json", -20.0f));
        config.profileMetadata.markModified("older.json", 100L, "Test");
        config.profileMetadata.markModified("newer.json", 200L, "Test");
        config.profileSortMode = Config.PROFILE_SORT_MODIFIED;
        config.profilesNormalized = false;
        ConfigProfileManager.ensureProfiles(config);

        config.resetProfileToDefaults(1);
        Config copy = config.copy();

        assertEquals("older.json", config.profile(0).fileName());
        assertTrue(config.hasSameState(copy));
    }

    @Test
    void profileStateComparisonIgnoresConfigMetadataButDetectsSettingChanges() {
        Config config = configWithProfiles(profile("Cruise", "cruise.json", -40.0f));
        Config reloaded = config.copy();
        reloaded.enabled = !config.enabled;
        reloaded.profileMetadata.markModified(
                "cruise.json", System.currentTimeMillis(), "Default");

        assertTrue(config.hasSameProfileState(reloaded, "cruise.json"));

        setTargetUp(reloaded, 0, -25.0f);

        assertFalse(config.hasSameProfileState(reloaded, "cruise.json"));
    }

    @Test
    void restoringProfileSnapshotKeepsCurrentDiskGeneration() {
        Config config = configWithProfiles(profile("Cruise", "cruise.json", -40.0f));
        config.profileMetadata.markCreated("cruise.json", 100L, "Hand Tuned");
        config.restoreProfileFileState(
                "cruise.json", LoadedFileState.existing(200L, "b".repeat(64)));
        Config snapshot = config.copy();

        setTargetUp(config, 0, -25.0f);
        config.profileMetadata.markModified("cruise.json", 300L, "Default");
        config.restoreProfileFileState(
                "cruise.json", LoadedFileState.existing(400L, "c".repeat(64)));

        config.restoreProfileStateFrom(snapshot, "cruise.json");

        assertEquals(-40.0f, config.profile(0).pitch().targetUpMinecraft());
        assertSame(config.pitch(), config.profile(0).pitch());
        ProfileMetadata restored = config.profileMetadata.profile("cruise.json");
        assertEquals("Hand Tuned", restored.basedOn());
        assertEquals(100L, restored.lastModifiedAtMillis());
        assertEquals(400L, config.profileFileState("cruise.json").modifiedAtMillis());
    }

    @Test
    void acceptingDiskConflictPreservesUnrelatedLocalEditsAndGlobalState() {
        Config local = configWithProfiles(profile("Alpha", "alpha.json", -40.0f),
                profile("Beta", "beta.json", -20.0f));
        Config disk = local.copy();
        setTargetUp(disk, 0, -10.0f);
        disk.restoreProfileFileState(
                "alpha.json", LoadedFileState.existing(500L, "a".repeat(64)));
        setTargetUp(local, 0, -30.0f);
        setTargetUp(local, 1, -15.0f);
        local.enabled = false;

        local.acceptProfileStatesFromDisk(disk, Set.of("alpha.json"));

        assertEquals(-10.0f, local.profile(local.profileIndexByFileName("alpha.json")).pitch().targetUpMinecraft());
        assertEquals(-15.0f, local.profile(local.profileIndexByFileName("beta.json")).pitch().targetUpMinecraft());
        assertFalse(local.enabled);
        assertEquals("a".repeat(64), local.profileFileState("alpha.json").fingerprint());
    }

    @Test
    void acceptingExternalDeletionKeepsOtherLocallyEditedProfiles() {
        Config local = configWithProfiles(profile("Alpha", "alpha.json", -40.0f),
                profile("Beta", "beta.json", -20.0f));
        setTargetUp(local, 1, -15.0f);
        Config disk = configWithProfiles(profile("Beta", "beta.json", -20.0f));

        local.acceptProfileStatesFromDisk(disk, Set.of("alpha.json"));

        assertEquals(1, local.profileCount());
        assertEquals("beta.json", local.activeProfileFile);
        assertEquals(-15.0f, local.pitch().targetUpMinecraft());
    }

    @Test
    void acceptingDiskConflictRestoresProfileMissingAfterStagedDeletion() {
        Config disk = configWithProfiles(profile("Alpha", "alpha.json", -40.0f),
                profile("Beta", "beta.json", -20.0f));
        Config local = disk.copy();
        local.stageDeleteProfile(local.profileIndexByFileName("alpha.json"));

        local.acceptProfileStatesFromDisk(disk, Set.of("alpha.json"));

        assertEquals(2, local.profileCount());
        assertEquals("Alpha", local.profileName(local.profileIndexByFileName("alpha.json")));
        assertEquals("alpha.json", local.activeProfileFile);
        assertSame(local.pitch(), local.profile(local.profileIndexByFileName("alpha.json")).pitch());
    }

    @Test
    void acceptingDiskConflictRestoresCaseOnlyProfileRename() {
        Config local = configWithProfiles(profile("Zulu", "Foo.json", -40.0f),
                profile("Alpha", "alpha.json", -20.0f));
        local.profileSortMode = Config.PROFILE_SORT_NAME;
        Config disk = configWithProfiles(profile("Zulu Disk", "foo.json", -10.0f),
                profile("Alpha", "alpha.json", -20.0f));
        disk.profileSortMode = Config.PROFILE_SORT_NAME;

        local.acceptProfileStatesFromDisk(disk, Set.of("Foo.json"));

        assertEquals(2, local.profileCount());
        assertEquals("foo.json", local.activeProfileFile);
        assertEquals("Zulu Disk", local.activeProfileName());
        assertEquals(-10.0f, local.pitch().targetUpMinecraft());
        assertEquals(1, local.profileIndexByFileName("foo.json"));
    }

    @Test
    void acceptingDiskConflictKeepsUnicodeDistinctProfilesSeparate() {
        Config local = configWithProfiles(profile("Dotted I", "İ.json", -40.0f),
                profile("Ascii I", "i.json", -20.0f));
        Config disk = configWithProfiles(profile("Dotted I Disk", "İ.json", -30.0f),
                profile("Ascii I Disk", "i.json", -10.0f));

        local.acceptProfileStatesFromDisk(disk, Set.of("i.json"));

        assertEquals(-40.0f,
                local.profile(local.profileIndexByFileName("İ.json")).pitch().targetUpMinecraft());
        assertEquals("Dotted I", local.profileName(local.profileIndexByFileName("İ.json")));
        assertEquals(-10.0f,
                local.profile(local.profileIndexByFileName("i.json")).pitch().targetUpMinecraft());
        assertEquals("Ascii I Disk", local.profileName(local.profileIndexByFileName("i.json")));
    }

    @Test
    void acceptingDeletionOfOnlyProfileUsesDiskFallbackWithoutResettingGlobals() {
        Config local = configWithProfiles(profile("Alpha", "alpha.json", -40.0f));
        local.enabled = false;
        Config disk = configWithProfiles(profile("Default", "default.json", -35.0f));

        local.acceptProfileStatesFromDisk(disk, Set.of("alpha.json"));

        assertEquals(1, local.profileCount());
        assertEquals("default.json", local.activeProfileFile);
        assertEquals(-35.0f, local.pitch().targetUpMinecraft());
        assertFalse(local.enabled);
    }

    @Test
    void acceptingSeveralConflictsReplacesAndDeletesOnlyThoseProfiles() {
        Config local = configWithProfiles(profile("Alpha", "alpha.json", -40.0f),
                profile("Beta", "beta.json", -20.0f),
                profile("Gamma", "gamma.json", -30.0f));
        local.selectProfile(1);
        setTargetUp(local, local.profileIndexByFileName("gamma.json"), -15.0f);
        Config disk = configWithProfiles(profile("Disk Alpha", "alpha.json", -10.0f),
                profile("Gamma", "gamma.json", -30.0f));

        local.acceptProfileStatesFromDisk(disk, Set.of("alpha.json", "beta.json"));

        assertEquals(2, local.profileCount());
        assertEquals(-1, local.profileIndexByFileName("beta.json"));
        assertEquals(-10.0f,
                local.profile(local.profileIndexByFileName("alpha.json")).pitch().targetUpMinecraft());
        assertEquals(-15.0f,
                local.profile(local.profileIndexByFileName("gamma.json")).pitch().targetUpMinecraft());
        assertEquals("alpha.json", local.activeProfileFile);
        assertSame(local.pitch(), local.profile(local.profileIndexByFileName("alpha.json")).pitch());
    }

    private static Config configWithProfiles(Profile... profiles) {
        return ConfigTestFixtures.configWith(profiles);
    }

    private static Profile profile(String name, String fileName, float targetUpMinecraft) {
        Profile profile = ConfigTestFixtures.tunedProfile(name, fileName);
        return profile.withPitch(profile.pitch().withTargetUpMinecraft(targetUpMinecraft));
    }

    private static void setTargetUp(Config config, int index, float value) {
        Profile profile = config.profile(index);
        config.replaceProfile(index, profile.withPitch(profile.pitch().withTargetUpMinecraft(value)));
    }
}
