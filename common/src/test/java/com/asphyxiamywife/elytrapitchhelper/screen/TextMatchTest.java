package com.asphyxiamywife.elytrapitchhelper.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TextMatchTest {
    @Test
    void normalizationAndSluggingCollapsePunctuation() {
        assertEquals("void y override", TextMatch.normalize("  Void-Y__Override!  "));
        assertEquals("void-y-override", TextMatch.slug("  Void-Y__Override!  "));
    }

    @Test
    void allQueryTokensMustMatch() {
        assertTrue(TextMatch.matchesAllTokens("Void warning Y override", "void-y"));
        assertFalse(TextMatch.matchesAllTokens("Void warning", "void-y"));
    }

    @Test
    void nonLatinScriptsSurviveNormalization() {
        assertEquals("虚空警告", TextMatch.normalize("  虚空警告！ "));
        assertEquals("предупреждение", TextMatch.normalize("Предупреждение"));
    }

    @Test
    void nonLatinQueriesFilterInsteadOfMatchingEverything() {
        assertTrue(TextMatch.matchesAllTokens("虚空警告", "虚空"));
        assertFalse(TextMatch.matchesAllTokens("音量", "虚空"));
    }

    @Test
    void accentsFoldSoUnaccentedQueriesMatch() {
        assertEquals("cafe", TextMatch.normalize("Café"));
        assertTrue(TextMatch.matchesAllTokens("Café", "cafe"));
        assertEquals("uber", TextMatch.normalize("über"));
    }

    @Test
    void distinctLabelsSlugDistinctlyOutsideAscii() {
        assertNotEquals(TextMatch.slug("虚空警告"), TextMatch.slug("音量"));
    }
}
