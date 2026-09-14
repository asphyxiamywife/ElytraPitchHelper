package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SettingResetPlanTest {
    @Test
    void colorResetIncludesCompanionSettingsAndPreservesUnrelatedValues() {
        Profile defaults = Config.defaultProfileTemplate();
        int changedColor = defaults.line().colorRgb() ^ 0xFFFFFF;
        Profile current = defaults.withName("Kept name").withLine(defaults.line()
                .withColorRgb(changedColor).withPride(true, "trans", new int[0]).withLengthPixels(75));

        Profile reset = SettingResetPlan.setting(current, "line_color").orElseThrow();

        assertEquals(defaults.line().colorRgb(), reset.line().colorRgb());
        assertEquals(defaults.line().prideEnabled(), reset.line().prideEnabled());
        assertEquals(defaults.line().prideFlag(), reset.line().prideFlag());
        assertArrayEquals(defaults.line().customPrideColors(), reset.line().customPrideColors());
        assertEquals(75, reset.line().lengthPixels());
        assertEquals("Kept name", reset.name());
        assertEquals(changedColor, current.line().colorRgb());
    }

    @Test
    void dimensionResetRemovesOnlyTheSelectedOverrideWithoutMutatingTheSource() {
        Profile defaults = Config.defaultProfileTemplate();
        Profile current = defaults.withVoidWarning(defaults.voidWarning()
                .withDimensionYOverrides(Map.of("minecraft:overworld", -80, "custom:moon", -20)));

        Profile reset = SettingResetPlan.voidDimension(current, "custom:moon").orElseThrow();

        assertEquals(Map.of("minecraft:overworld", -80), reset.voidWarning().dimensionYOverrides());
        assertEquals(2, current.voidWarning().dimensionYOverrides().size());
        assertEquals(current.line(), reset.line());
        assertEquals(current.voidWarning().mode(), reset.voidWarning().mode());
    }

    @Test
    void defaultUnsupportedAndMissingDimensionResetsHaveNoWorkToApply() {
        Profile defaults = Config.defaultProfileTemplate();
        assertTrue(SettingResetPlan.setting(defaults, "line_color").isEmpty());
        assertTrue(SettingResetPlan.setting(defaults, "not-a-setting").isEmpty());
        assertTrue(SettingResetPlan.voidDimension(defaults, "custom:missing").isEmpty());
        assertTrue(SettingResetPlan.voidDimension(defaults, null).isEmpty());
    }
}
