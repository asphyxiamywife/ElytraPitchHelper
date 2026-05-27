package com.asphyxiamywife.elytrapitchhelper.config;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class ProfileFileNamesTest {
    @Test
    void normalizeStripsPathsAndAddsJsonSuffix() {
        assertEquals("default.json", ProfileFileNames.normalize("default"));
        assertEquals("profile.json", ProfileFileNames.normalize("../profile.json"));
        assertEquals("custom.json", ProfileFileNames.normalize("folder\\custom"));
        assertNull(ProfileFileNames.normalize("   "));
    }

    @Test
    void uniqueUsesSlugAndSkipsExistingNamesCaseInsensitively() {
        Set<String> used = new HashSet<>();
        used.add("flight-cycle.json");
        used.add("flight-cycle-2.json");

        assertEquals("flight-cycle-3.json", ProfileFileNames.unique("Flight Cycle", used));
        assertEquals("profile.json", ProfileFileNames.unique("   ", used));
    }

    @Test
    void displayNameTurnsFileNameIntoHumanReadableName() {
        assertEquals("Flight Cycle", ProfileFileNames.displayName("flight-cycle.json"));
        assertEquals("Velocity Practice", ProfileFileNames.displayName("profiles/velocity_practice"));
        assertEquals("Profile", ProfileFileNames.displayName(null));
    }
}
