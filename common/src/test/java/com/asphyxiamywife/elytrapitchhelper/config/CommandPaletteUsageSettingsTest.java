package com.asphyxiamywife.elytrapitchhelper.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CommandPaletteUsageSettingsTest {
    @Test
    void recordsUsageAndBoostsRecentActions() {
        CommandPaletteUsageSettings usage = new CommandPaletteUsageSettings();

        usage = usage.recordUse(
                "elytrapitchhelper:palette/settings/command_palette_appearance", 1_000L);

        assertEquals(1, usage.actions().get(
                "elytrapitchhelper:palette/settings/command_palette_appearance").count());
        assertTrue(usage.rankingBoost(
                "elytrapitchhelper:palette/settings/command_palette_appearance", 1_000L) > 0);
        assertEquals(0, usage.rankingBoost("elytrapitchhelper:palette/actions/missing", 1_000L));
    }

    @Test
    void recordsLocalEditsWithDecayingFrequency() {
        CommandPaletteUsageSettings usage = new CommandPaletteUsageSettings();
        String oldAction = "elytrapitchhelper:palette/settings/void_y_override";
        String recentAction = "elytrapitchhelper:palette/settings/tolerance";
        long seventyDays = 70L * 24L * 60L * 60L * 1_000L;

        for (int i = 0; i < 50; i++) {
            usage = usage.recordEdit(oldAction, 1_000L);
        }
        usage = usage.recordEdit(recentAction, seventyDays);

        assertTrue(usage.rankingBoost(recentAction, seventyDays)
                > usage.rankingBoost(oldAction, seventyDays));
    }

    @Test
    void sanitizeDropsInvalidActionIdsAndCopiesIndependently() {
        CommandPaletteUsageSettings usage = new CommandPaletteUsageSettings();
        usage = usage.recordUse("elytrapitchhelper:palette/actions/toggle_eph", 2_000L);
        java.util.Map<String, CommandPaletteUsageSettings.ActionUsage> actions =
                new java.util.LinkedHashMap<>(usage.actions());
        actions.put("bad\nid", new CommandPaletteUsageSettings.ActionUsage());
        usage = new CommandPaletteUsageSettings(actions).sanitized(null);

        CommandPaletteUsageSettings copy = usage.recordUse(
                "elytrapitchhelper:palette/actions/open_json", 3_000L);

        assertTrue(usage.actions().containsKey("elytrapitchhelper:palette/actions/toggle_eph"));
        assertFalse(usage.actions().containsKey("bad\nid"));
        assertFalse(usage.actions().containsKey("elytrapitchhelper:palette/actions/open_json"));
        assertTrue(copy.actions().containsKey("elytrapitchhelper:palette/actions/open_json"));
    }

    @Test
    void sanitizeMigratesLegacyToggleActionIdsToRegistryIds() {
        CommandPaletteUsageSettings usage = new CommandPaletteUsageSettings();
        usage = usage.recordUse(
                "elytrapitchhelper:palette/settings/toggle_show_only_with_firework", 2_000L)
                .sanitized(null);

        assertFalse(usage.actions().containsKey(
                "elytrapitchhelper:palette/settings/toggle_show_only_with_firework"));
        assertTrue(usage.actions().containsKey(
                "elytrapitchhelper:palette/settings/show_only_with_firework"));
    }
}
