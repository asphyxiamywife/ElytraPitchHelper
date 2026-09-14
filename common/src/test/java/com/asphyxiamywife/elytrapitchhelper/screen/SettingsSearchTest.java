package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import com.asphyxiamywife.elytrapitchhelper.config.VoidWarningSettings;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SettingsSearchTest {
    private static final Function<String, String> TRANSLATOR = key -> Map.of(
            "option.elytrapitchhelper.show_only_with_firework", "Firework only",
            "tooltip.elytrapitchhelper.show_only_with_firework", "Only draws guides while a rocket is held",
            "option.elytrapitchhelper.line_color", "Line color",
            "tooltip.elytrapitchhelper.line_color", "Base color of the pitch guide line",
            "option.elytrapitchhelper.amplitude_color", "Cue color",
            "tooltip.elytrapitchhelper.amplitude_color", "Color used for the cue",
            "option.elytrapitchhelper.void_color", "Warning color",
            "tooltip.elytrapitchhelper.void_color", "Color used for the warning",
            "option.elytrapitchhelper.command_palette_appearance", "Command palette appearance",
            "tooltip.elytrapitchhelper.command_palette_appearance",
            "Edit command palette shadow, blur, accent color, and base color"
    ).getOrDefault(key, "");

    @Test
    void matchesLocalizedLabelsAndTooltipsCaseInsensitively() {
        SettingsSearch.Entry firework = SettingsSearch.entry(ConfigCategory.GENERAL,
                "show_only_with_firework");

        assertTrue(SettingsSearch.matches(firework, "FIREWORK", TRANSLATOR));
        assertTrue(SettingsSearch.matches(firework, "rocket guide", TRANSLATOR));
        assertFalse(SettingsSearch.matches(firework, "line color", TRANSLATOR));
        assertTrue(SettingsSearch.matches(firework, "   ", TRANSLATOR));
    }

    @Test
    void punctuatedQueriesUseTheSameTokenNormalizationAsThePalette() {
        SettingsSearch.Entry voidY = SettingsSearch.entry(ConfigCategory.VOID, "void_y_override");

        assertTrue(SettingsSearch.matches(voidY, "void-y",
                key -> key.endsWith("void_y_override") ? "Void Y override" : ""));
    }

    @Test
    void voidCatalogTracksTheControlVisibleForTheSelectedMode() {
        Profile profile = new Profile();
        assertEquals(3, SettingsSearch.entries(ConfigCategory.GENERAL, profile).size());
        assertEquals(5, SettingsSearch.entries(ConfigCategory.PITCH, profile).size());
        assertEquals(13, SettingsSearch.entries(ConfigCategory.AMPLITUDE, profile).size());
        assertEquals(4, SettingsSearch.entries(ConfigCategory.LINE, profile).size());
        assertEquals(11, SettingsSearch.entries(ConfigCategory.VOID, profile).size());
        assertEquals(2, SettingsSearch.entries(ConfigCategory.INTERFACE, profile).size());

        profile = profile.withVoidWarning(
                profile.voidWarning().withMode(VoidWarningSettings.MODE_PREDICTED_TIME));
        assertTrue(SettingsSearch.entries(ConfigCategory.VOID, profile).stream()
                .anyMatch(entry -> entry.labelKey().endsWith("void_lookahead")));
        assertFalse(SettingsSearch.entries(ConfigCategory.VOID, profile).stream()
                .anyMatch(entry -> entry.labelKey().endsWith("void_simple_blocks")));

        profile = profile.withVoidWarning(
                profile.voidWarning().withMode(VoidWarningSettings.MODE_SIMPLE_HEIGHT));
        assertTrue(SettingsSearch.entries(ConfigCategory.VOID, profile).stream()
                .anyMatch(entry -> entry.labelKey().endsWith("void_simple_blocks")));
        assertFalse(SettingsSearch.entries(ConfigCategory.VOID, profile).stream()
                .anyMatch(entry -> entry.labelKey().endsWith("void_lookahead")));
        assertEquals(38, SettingsSearch.allEntries(profile).size());
    }

    @Test
    void commandPaletteAppearanceComponentNormalizesNull() {
        Profile profile = new Profile(2, null, null, null, null, null, null, null, null, null);

        var appearance = profile.commandPaletteAppearance();

        org.junit.jupiter.api.Assertions.assertSame(appearance, profile.commandPaletteAppearance());
    }

    @Test
    void nestedEditorSettingsOpenScreensFromThePalette() {
        Profile profile = new Profile();

        assertEquals(SettingsSearch.PaletteOpenMode.FOCUS_CONTROL,
                SettingsSearch.entries(ConfigCategory.LINE, profile).get(0).paletteOpenMode());
        assertEquals(SettingsSearch.PaletteOpenMode.OPEN_SCREEN,
                entry(ConfigCategory.LINE, profile, "line_color").paletteOpenMode());
        assertEquals(SettingsSearch.PaletteOpenMode.OPEN_SCREEN,
                entry(ConfigCategory.AMPLITUDE, profile, "amplitude_color").paletteOpenMode());
        assertEquals(SettingsSearch.PaletteOpenMode.OPEN_SCREEN,
                entry(ConfigCategory.VOID, profile, "void_color").paletteOpenMode());
        assertEquals(SettingsSearch.PaletteOpenMode.OPEN_SCREEN,
                entry(ConfigCategory.INTERFACE, profile, "command_palette_appearance").paletteOpenMode());
    }

    @Test
    void twoStateSettingsToggleFromThePalette() {
        Profile profile = new Profile();

        assertEquals(SettingsSearch.PaletteOpenMode.TOGGLE_SETTING,
                entry(ConfigCategory.GENERAL, profile, "show_only_with_firework").paletteOpenMode());
        assertEquals(SettingsSearch.PaletteOpenMode.TOGGLE_SETTING,
                entry(ConfigCategory.GENERAL, profile, "flight_detection").paletteOpenMode());
        assertEquals(SettingsSearch.PaletteOpenMode.TOGGLE_SETTING,
                entry(ConfigCategory.AMPLITUDE, profile, "amplitude_helper").paletteOpenMode());
        assertEquals(SettingsSearch.PaletteOpenMode.TOGGLE_SETTING,
                entry(ConfigCategory.AMPLITUDE, profile, "amplitude_repeat_dive").paletteOpenMode());
        assertEquals(SettingsSearch.PaletteOpenMode.TOGGLE_SETTING,
                entry(ConfigCategory.AMPLITUDE, profile, "amplitude_repeat_climb").paletteOpenMode());
        assertEquals(SettingsSearch.PaletteOpenMode.TOGGLE_SETTING,
                entry(ConfigCategory.AMPLITUDE, profile, "motion_glyphs").paletteOpenMode());
        assertEquals(SettingsSearch.PaletteOpenMode.TOGGLE_SETTING,
                entry(ConfigCategory.VOID, profile, "void_mode").paletteOpenMode());
        assertEquals(SettingsSearch.PaletteOpenMode.FOCUS_CONTROL,
                entry(ConfigCategory.VOID, profile, "void_y_mode").paletteOpenMode());
        assertEquals(SettingsSearch.PaletteOpenMode.FOCUS_CONTROL,
                entry(ConfigCategory.AMPLITUDE, profile, "amplitude_trigger_mode").paletteOpenMode());
    }

    private static SettingsSearch.Entry entry(ConfigCategory category, Profile profile, String optionName) {
        return SettingsSearch.entries(category, profile).stream()
                .filter(entry -> entry.optionName().equals(optionName))
                .findFirst()
                .orElseThrow();
    }
}
