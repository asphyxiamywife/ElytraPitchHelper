package com.asphyxiamywife.elytrapitchhelper.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class VoidWarningSettingsTest {
    @Test
    void sanitizeAllowsPostOneEighteenOverworldVoidFloor() {
        VoidWarningSettings settings = new VoidWarningSettings();
        settings.dimensionYOverrides.put("minecraft:overworld", -128);
        settings.dimensionYOverrides.put("minecraft:the_nether", -64);

        settings.sanitize(null, new VoidWarningSettings());

        assertEquals(-128, settings.dimensionYOverrides.get("minecraft:overworld"));
        assertEquals(-64, settings.dimensionYOverrides.get("minecraft:the_nether"));
    }

    @Test
    void sanitizeRepairsDimensionVoidFloorOutsideSupportedRange() {
        VoidWarningSettings settings = new VoidWarningSettings();
        settings.dimensionYOverrides.put("minecraft:overworld", VoidWarningSettings.MIN_VOID_Y - 1);
        settings.dimensionYOverrides.put("minecraft:sky", VoidWarningSettings.MAX_VOID_Y + 1);

        settings.sanitize(null, new VoidWarningSettings());

        assertEquals(0, settings.dimensionYOverrides.get("minecraft:overworld"));
        assertEquals(0, settings.dimensionYOverrides.get("minecraft:sky"));
    }

    @Test
    void sanitizeRepairsSimpleModeFieldsOutsideSupportedRange() {
        VoidWarningSettings settings = new VoidWarningSettings();
        settings.mode = 99;
        settings.simpleWarningBlocks = 0;

        settings.sanitize(null, new VoidWarningSettings());

        assertEquals(VoidWarningSettings.MODE_PREDICTED_TIME, settings.mode);
        assertEquals(48, settings.simpleWarningBlocks);
    }
}
