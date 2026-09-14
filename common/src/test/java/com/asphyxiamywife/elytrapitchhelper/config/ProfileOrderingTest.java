package com.asphyxiamywife.elytrapitchhelper.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProfileOrderingTest {
    @Test
    void sortByNamePreservesSelectedProfileByFileName() {
        Profile bravo = profile("Bravo", "bravo.json");
        Profile alpha = profile("Alpha", "alpha.json");
        ProfileMetadataStore metadata = new ProfileMetadataStore();
        metadata.markCreated(bravo.fileName(), 100L, "Test");
        metadata.markCreated(alpha.fileName(), 200L, "Test");

        ProfileOrdering.Selection selection = ProfileOrdering.sort(new ArrayList<>(List.of(bravo, alpha)), 0,
                "bravo.json", Config.PROFILE_SORT_NAME, metadata);

        assertEquals("alpha.json", selection.profiles().get(0).fileName());
        assertEquals("bravo.json", selection.profiles().get(1).fileName());
        assertEquals(1, selection.activeIndex());
        assertEquals("bravo.json", selection.activeFile());
    }

    @Test
    void sortByModifiedUsesNewestFirst() {
        Profile oldProfile = profile("Old", "old.json");
        Profile newProfile = profile("New", "new.json");
        ProfileMetadataStore metadata = new ProfileMetadataStore();
        metadata.markModified(oldProfile.fileName(), 100L, "Test");
        metadata.markModified(newProfile.fileName(), 300L, "Test");
        List<ProfileMetadata> metadataBeforeSort = List.copyOf(metadata.profiles);

        ProfileOrdering.Selection selection = ProfileOrdering.sort(new ArrayList<>(List.of(oldProfile, newProfile)),
                0, "old.json", Config.PROFILE_SORT_MODIFIED, metadata);

        assertEquals("new.json", selection.profiles().get(0).fileName());
        assertEquals("old.json", selection.profiles().get(1).fileName());
        assertEquals(1, selection.activeIndex());
        assertEquals(metadataBeforeSort, metadata.profiles);
    }

    @Test
    void normalizeRepairsNullProfilesNamesAndCaseInsensitiveFileCollisions() {
        Profile first = profile(null, "same.json");
        Profile duplicate = profile("Duplicate", "SAME.JSON");
        List<Profile> profiles = new ArrayList<>();
        profiles.add(first);
        profiles.add(null);
        profiles.add(duplicate);

        ProfileOrdering.Selection selection = ProfileOrdering.normalize(profiles, 99, null,
                Config.PROFILE_SORT_CREATED, new ProfileMetadataStore());

        assertEquals(3, selection.profiles().size());
        for (Profile profile : selection.profiles()) {
            assertNotNull(profile);
            assertNotNull(profile.name());
            assertNotNull(profile.fileName());
        }
        assertNotEquals(selection.profiles().get(0).fileName(), selection.profiles().get(1).fileName());
        assertTrue(selection.profiles().stream().anyMatch(profile -> profile.fileName().equals("same.json")));
        assertTrue(selection.profiles().stream().anyMatch(profile -> profile.fileName().equals("duplicate.json")));
        assertEquals(3, selection.profiles().stream()
                .map(profile -> ProfileFileNames.comparisonKey(profile.fileName()))
                .distinct()
                .count());
        assertEquals(2, selection.activeIndex());
        assertEquals(selection.profiles().get(2).fileName(), selection.activeFile());
    }

    @Test
    void nameAllocationIgnoresNullProfileNames() {
        List<Profile> profiles = List.of(
                profile(null, "unnamed.json"),
                profile("Flight", "flight.json"));

        assertEquals("Profile", ProfileNameAllocator.uniqueName(profiles, "Profile"));
        assertEquals("Flight copy", ProfileNameAllocator.uniqueCopyName(profiles, "Flight"));
    }

    @Test
    void sortToleratesUnnormalizedNullNamesAndFileNames() {
        Profile named = profile("Flight", "flight.json");
        Profile unnamed = profile(null, null);

        ProfileOrdering.Selection selection = ProfileOrdering.sort(
                new ArrayList<>(List.of(unnamed, named)), 1, "flight.json",
                Config.PROFILE_SORT_NAME, new ProfileMetadataStore());

        assertEquals(named, selection.profiles().get(0));
        assertEquals(unnamed, selection.profiles().get(1));
        assertEquals(0, selection.activeIndex());
    }

    private static Profile profile(String name, String fileName) {
        return ConfigTestFixtures.minimalProfile(name, fileName);
    }
}
