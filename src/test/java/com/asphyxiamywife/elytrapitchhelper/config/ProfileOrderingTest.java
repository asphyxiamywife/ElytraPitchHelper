package com.asphyxiamywife.elytrapitchhelper.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ProfileOrderingTest {
    @Test
    void sortByNamePreservesSelectedProfileByFileName() {
        Profile bravo = profile("Bravo", "bravo.json");
        Profile alpha = profile("Alpha", "alpha.json");
        ProfileMetadataStore metadata = new ProfileMetadataStore();
        metadata.profile(bravo.fileName).markCreated(100L, "Test");
        metadata.profile(alpha.fileName).markCreated(200L, "Test");

        ProfileOrdering.Selection selection = ProfileOrdering.sort(new ArrayList<>(List.of(bravo, alpha)), 0,
                "bravo.json", Config.PROFILE_SORT_NAME, metadata);

        assertEquals("alpha.json", selection.profiles().get(0).fileName);
        assertEquals("bravo.json", selection.profiles().get(1).fileName);
        assertEquals(1, selection.activeIndex());
        assertEquals("bravo.json", selection.activeFile());
    }

    @Test
    void sortByModifiedUsesNewestFirst() {
        Profile oldProfile = profile("Old", "old.json");
        Profile newProfile = profile("New", "new.json");
        ProfileMetadataStore metadata = new ProfileMetadataStore();
        metadata.profile(oldProfile.fileName).markModified(100L, "Test");
        metadata.profile(newProfile.fileName).markModified(300L, "Test");

        ProfileOrdering.Selection selection = ProfileOrdering.sort(new ArrayList<>(List.of(oldProfile, newProfile)),
                0, "old.json", Config.PROFILE_SORT_MODIFIED, metadata);

        assertEquals("new.json", selection.profiles().get(0).fileName);
        assertEquals("old.json", selection.profiles().get(1).fileName);
        assertEquals(1, selection.activeIndex());
    }

    private static Profile profile(String name, String fileName) {
        Profile profile = new Profile();
        profile.name = name;
        profile.fileName = fileName;
        return profile;
    }
}
