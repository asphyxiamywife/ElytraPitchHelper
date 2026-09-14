package com.asphyxiamywife.elytrapitchhelper.screen;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

final class ScreenTextTest {
    @Test
    void builtInProfileOriginsUseTranslationKeys() {
        assertTranslationKey("screen.elytrapitchhelper.profile.based_on.custom",
                ScreenText.profileBasedOn("Custom"));
        assertTranslationKey("screen.elytrapitchhelper.profile.based_on.bundled_default",
                ScreenText.profileBasedOn("Bundled default"));
    }

    @Test
    void arbitraryProfileOriginsRemainLiteral() {
        assertEquals("Speedrun", ScreenText.profileBasedOn("Speedrun").getString());
    }

    @Test
    void unknownRelativeTimeUsesTranslationKey() {
        assertTranslationKey("screen.elytrapitchhelper.relative_time.unknown", ScreenText.relativeTime(0L));
    }

    @Test
    void truncationReturnsEmptyWhenTheMarkerDoesNotFit() {
        assertEquals("", ScreenText.truncate("abcdef", 3, String::length,
                (text, width) -> text.substring(0, Math.min(text.length(), width))));
        assertEquals("ab...", ScreenText.truncate("abcdef", 5, String::length,
                (text, width) -> text.substring(0, Math.min(text.length(), width))));
    }

    @Test
    void sharedNumericFormattersPreserveSliderLabels() {
        assertEquals("12", ScreenText.formatInteger(11.6, ""));
        assertEquals("12 px", ScreenText.formatInteger(11.6, "px"));
        assertEquals("12%", ScreenText.formatPercent(11.6));
        assertEquals("2.05", ScreenText.formatDecimal(2.05, 0.05));
        assertEquals("2.1", ScreenText.formatDecimal(2.1, 0.05));
        assertEquals("2", ScreenText.formatDecimal(2.0, 0.05));
        assertEquals("2.05", ScreenText.formatDecimal((double) 2.05f, (double) 0.05f));
    }

    private static void assertTranslationKey(String expected, Component component) {
        TranslatableContents contents = assertInstanceOf(TranslatableContents.class, component.getContents());
        assertEquals(expected, contents.getKey());
    }
}
