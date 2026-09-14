package com.asphyxiamywife.elytrapitchhelper.screen;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EditorHintsTest {
    private static Map<EditorHints.Hint, Integer> tally(boolean usedSearch, EditorHints.Hint previous,
            int draws) {
        RandomGenerator random = new java.util.Random(1234L);
        Map<EditorHints.Hint, Integer> counts = new EnumMap<>(EditorHints.Hint.class);
        for (int i = 0; i < draws; i++) {
            counts.merge(EditorHints.pick(usedSearch, previous, random), 1, Integer::sum);
        }
        return counts;
    }

    @Test
    void theShortcutIsAboutHalfOfOpeningsBeforeAnyoneHasSearched() {
        int draws = 4000;
        int shortcut = tally(false, null, draws).getOrDefault(EditorHints.Hint.SEARCH_SHORTCUT, 0);

        assertTrue(shortcut > draws * 0.45 && shortcut < draws * 0.55,
                () -> "expected roughly half, got " + shortcut + " of " + draws);
    }

    @Test
    void theShortcutBacksOffOnceTheFieldHasExplainedItself() {
        int draws = 4000;
        int before = tally(false, null, draws).getOrDefault(EditorHints.Hint.SEARCH_SHORTCUT, 0);
        int after = tally(true, null, draws).getOrDefault(EditorHints.Hint.SEARCH_SHORTCUT, 0);

        assertTrue(after < before / 2, () -> "expected a clear drop, got " + before + " then " + after);
        assertTrue(after > 0, "the prompt should not disappear entirely");
    }

    @Test
    void aHintNeverFollowsItself() {
        RandomGenerator random = new java.util.Random(99L);
        for (EditorHints.Hint previous : EditorHints.Hint.values()) {
            for (int i = 0; i < 500; i++) {
                assertNotEquals(previous, EditorHints.pick(false, previous, random));
                assertNotEquals(previous, EditorHints.pick(true, previous, random));
            }
        }
    }

    @Test
    void everyTipIsReachable() {
        Map<EditorHints.Hint, Integer> counts = tally(true, null, 6000);

        assertEquals(EditorHints.Hint.values().length, counts.size(),
                () -> "some hints never come up: " + counts.keySet());
    }

    @Test
    void everyHintHasItsOwnTranslationKey() {
        for (EditorHints.Hint hint : EditorHints.Hint.values()) {
            assertTrue(hint.translationKey != null && !hint.translationKey.isBlank());
            for (EditorHints.Hint other : EditorHints.Hint.values()) {
                if (other != hint) {
                    assertNotEquals(hint.translationKey, other.translationKey);
                }
            }
        }
    }
}
