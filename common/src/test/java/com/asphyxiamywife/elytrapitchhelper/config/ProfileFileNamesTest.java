package com.asphyxiamywife.elytrapitchhelper.config;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProfileFileNamesTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "CON.json", "nul.extra.json", "PrN.json", "AUX.json", "COM1.json", "COM9.json",
            "LPT1.json", "lpt9.json", "COM¹.json", "LPT².json", "COM³.json", "con .json",
            "bad:name.json", "bad<name.json", "bad>name.json", "bad\"name.json", "bad|name.json",
            "bad?name.json", "bad*name.json", "folder/name.json", "folder\\name.json", "name.json.", "name.json "
    })
    void rejectsIncompatibleDiskNamesWithoutChangingTheirIdentity(String name) {
        assertFalse(ProfileFileNames.isPortable(name));
    }

    @Test
    void rejectsControlCharactersButAcceptsPortableUnicodeNames() {
        for (int i = 0; i < 32; i++) {
            assertFalse(ProfileFileNames.isPortable("name" + (char) i + ".json"));
        }
        for (String name : Set.of("COM0.json", "COM10.json", "console.json", "nul-profile.json",
                "flight plan.json", "Übung.json", ".hidden.json")) {
            assertTrue(ProfileFileNames.isPortable(name), name);
        }
        assertEquals("CON.json", ProfileFileNames.normalize("CON.json"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"CON", "NUL", "aux", "PRN", "com1", "lpt9", "nul.extra"})
    void generatedNamesAvoidDevicesAndRemainUnique(String name) {
        Set<String> used = new HashSet<>();
        String first = ProfileFileNames.unique(name, used);
        String second = ProfileFileNames.unique(name, used);
        assertTrue(first.startsWith("profile-"));
        assertTrue(ProfileFileNames.isPortable(first));
        assertTrue(ProfileFileNames.isPortable(second));
        assertFalse(first.equals(second));
    }

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
        used.add("FLIGHT-CYCLE.JSON");
        used.add("flight-cycle-2.json");

        assertEquals("flight-cycle-3.json", ProfileFileNames.unique("Flight Cycle", used));
        assertEquals("profile.json", ProfileFileNames.unique("   ", used));
    }

    @Test
    void uniqueBoundsLongNamesAndLeavesRoomForCollisionSuffixes() {
        Set<String> used = new HashSet<>();
        String longName = "A".repeat(512);

        String first = ProfileFileNames.unique(longName, used);
        String second = ProfileFileNames.unique(longName, used);

        assertEquals(ProfileFileNames.MAX_FILE_NAME_LENGTH, first.length());
        assertEquals(ProfileFileNames.MAX_FILE_NAME_LENGTH, second.length());
        assertTrue(first.endsWith(".json"));
        assertTrue(second.endsWith("-2.json"));
    }

    @Test
    void comparisonKeyNormalizesPathAndCase() {
        assertEquals("foo.json", ProfileFileNames.comparisonKey("profiles/Foo.JSON"));
    }

    @Test
    void displayNameTurnsFileNameIntoHumanReadableName() {
        assertEquals("Flight Cycle", ProfileFileNames.displayName("flight-cycle.json"));
        assertEquals("Velocity Practice", ProfileFileNames.displayName("profiles/velocity_practice"));
        assertEquals("Profile", ProfileFileNames.displayName(null));
    }
}
