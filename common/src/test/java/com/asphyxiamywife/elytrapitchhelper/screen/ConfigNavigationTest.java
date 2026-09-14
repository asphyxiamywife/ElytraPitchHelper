package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ConfigNavigationTest {
    @Test
    void retargetingEditorPreservesActiveProfileAndReturnDestination() {
        Config config = new Config();
        String active = config.activeProfile().fileName();
        String other = config.createProfile().fileName();
        config.selectProfile(config.profileIndexByFileName(active));
        ConfigNavigation navigation = new ConfigNavigation();
        navigation.rememberProfiles();
        navigation.openEditor(config, active);
        navigation.openEditor(config, other);

        assertTrue(navigation.editingProfile());
        assertEquals(other, navigation.editingProfileFile());
        assertEquals(active, config.activeProfile().fileName());
        assertEquals(new ConfigNavigation.Entry(false, null), navigation.back());
        assertNull(navigation.back());
    }

    @Test
    void nestedSurfaceVisitsUnwindInReverseOrder() {
        Config config = new Config();
        String profile = config.activeProfile().fileName();
        ConfigNavigation navigation = new ConfigNavigation();
        navigation.openEditor(config, profile);
        navigation.rememberCurrent();
        navigation.openProfiles();
        navigation.rememberProfiles();
        navigation.openEditor(config, profile);

        assertEquals(new ConfigNavigation.Entry(false, null), navigation.back());
        assertEquals(new ConfigNavigation.Entry(true, profile), navigation.back());
        assertNull(navigation.back());
    }

    @Test
    void deletedReturnDestinationFallsBackToActiveProfile() {
        Config config = new Config();
        String deleted = config.createProfile().fileName();
        ConfigNavigation navigation = new ConfigNavigation();
        navigation.openEditor(config, deleted);
        navigation.rememberCurrent();
        navigation.openProfiles();
        assertNotNull(config.stageDeleteProfile(config.profileIndexByFileName(deleted)));

        navigation.openEditor(config, navigation.back().profileFile());

        assertTrue(navigation.editingProfile());
        assertEquals(config.activeProfile().fileName(), navigation.editingProfileFile());
    }

    @Test
    void synchronizationResolvesIdentityAgainstAReplacementConfig() {
        Config config = new Config();
        String target = config.createProfile().fileName();
        ConfigNavigation navigation = new ConfigNavigation();
        navigation.openEditor(config, target);
        Config replacement = config.copy();
        replacement.setProfileName(replacement.profileIndexByFileName(target), "AAAA");
        replacement.cycleProfileSortMode();

        navigation.sync(replacement);

        assertEquals(target, navigation.editingProfileFile());
        assertEquals(replacement.profileIndexByFileName(target), navigation.editingProfileIndex());
    }

    @Test
    void restoringHistoryKeepsTheNavigationStackAndDoneClearsIt() {
        Config config = new Config();
        String first = config.activeProfile().fileName();
        String second = config.createProfile().fileName();
        ConfigNavigation navigation = new ConfigNavigation();
        navigation.openEditor(config, first);
        navigation.rememberCurrent();
        navigation.restore(true, second);
        navigation.sync(config);

        assertEquals(second, navigation.editingProfileFile());
        assertEquals(config.profileIndexByFileName(second), navigation.editingProfileIndex());
        assertEquals(new ConfigNavigation.Entry(true, first), navigation.back());
        navigation.rememberCurrent();
        navigation.clearBackStack();
        assertNull(navigation.back());
    }
}
