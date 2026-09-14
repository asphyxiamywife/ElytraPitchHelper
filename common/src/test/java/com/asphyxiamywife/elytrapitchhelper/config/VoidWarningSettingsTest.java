package com.asphyxiamywife.elytrapitchhelper.config;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class VoidWarningSettingsTest {
    @Test
    void sanitizeAllowsPostOneEighteenOverworldVoidFloor() {
        VoidWarningSettings settings = sanitize(new VoidWarningSettings()
                .withDimensionYOverrides(Map.of(
                        "minecraft:overworld", -128,
                        "minecraft:the_nether", -64)));

        assertEquals(-128, settings.dimensionYOverrides().get("minecraft:overworld"));
        assertEquals(-64, settings.dimensionYOverrides().get("minecraft:the_nether"));
    }

    @Test
    void sanitizeRepairsDimensionVoidFloorOutsideSupportedRange() {
        VoidWarningSettings settings = sanitize(new VoidWarningSettings()
                .withDimensionYOverrides(Map.of(
                        "minecraft:overworld", VoidWarningSettings.MIN_VOID_Y - 1,
                        "minecraft:sky", VoidWarningSettings.MAX_VOID_Y + 1)));

        assertEquals(0, settings.dimensionYOverrides().get("minecraft:overworld"));
        assertEquals(0, settings.dimensionYOverrides().get("minecraft:sky"));
    }

    @Test
    void sanitizeRepairsSimpleModeFieldsOutsideSupportedRange() {
        VoidWarningSettings settings = sanitize(new VoidWarningSettings()
                .withMode(99)
                .withSimpleWarningBlocks(0));

        assertEquals(VoidWarningSettings.MODE_PREDICTED_TIME, settings.mode());
        assertEquals(48, settings.simpleWarningBlocks());
    }

    @Test
    void sanitizeAllowsWarningToleranceAcrossFullPitchRange() {
        VoidWarningSettings settings = sanitize(
                new VoidWarningSettings().withCustomToleranceDegrees(130.0f));

        assertEquals(130.0f, settings.customToleranceDegrees());
    }

    @Test
    void sanitizeRepairsNullValuesAndDropsMalformedDimensionIds() {
        Map<String, Integer> overrides = new HashMap<>();
        overrides.put("minecraft:overworld", null);
        overrides.put("Bad Dimension", -64);
        overrides.put("mod:valid/path", -64);
        VoidWarningSettings settings = sanitize(
                new VoidWarningSettings().withDimensionYOverrides(overrides));

        assertEquals(0, settings.dimensionYOverrides().get("minecraft:overworld"));
        assertFalse(settings.dimensionYOverrides().containsKey("Bad Dimension"));
        assertEquals(-64, settings.dimensionYOverrides().get("mod:valid/path"));
    }

    private static VoidWarningSettings sanitize(VoidWarningSettings settings) {
        return new Profile().withVoidWarning(settings)
                .sanitized(null, new Profile())
                .voidWarning();
    }
}
