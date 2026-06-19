package com.asphyxiamywife.elytrapitchhelper.config;

import com.code_intelligence.jazzer.junit.FuzzTest;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProfileFileNamesFuzzTest {
    @FuzzTest(maxDuration = "30s")
    void generatedFileNamesStayLocalAndUnique(byte[] input) {
        String name = new String(input, StandardCharsets.UTF_8);
        String normalized = ProfileFileNames.normalize(name);
        if (normalized != null) {
            assertLocalJsonFile(normalized);
            assertEquals(normalized, ProfileFileNames.normalize(normalized));
        }

        String slug = ProfileFileNames.slug(name);
        assertFalse(slug.isBlank());
        assertTrue(slug.matches("[a-z0-9._-]+"));

        Set<String> used = new HashSet<>();
        String first = ProfileFileNames.unique(name, used);
        String second = ProfileFileNames.unique(name, used);
        assertLocalJsonFile(first);
        assertLocalJsonFile(second);
        assertNotEquals(first.toLowerCase(Locale.ROOT), second.toLowerCase(Locale.ROOT));
        assertTrue(used.contains(first.toLowerCase(Locale.ROOT)));
        assertTrue(used.contains(second.toLowerCase(Locale.ROOT)));
    }

    private static void assertLocalJsonFile(String fileName) {
        assertFalse(fileName.isBlank());
        assertFalse(fileName.contains("/"));
        assertFalse(fileName.contains("\\"));
        assertTrue(fileName.toLowerCase(Locale.ROOT).endsWith(ProfileFileNames.JSON_SUFFIX));
    }
}
