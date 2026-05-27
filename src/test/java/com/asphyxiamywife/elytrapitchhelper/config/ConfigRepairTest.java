package com.asphyxiamywife.elytrapitchhelper.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ConfigRepairTest {
    @Test
    void repairFloatUsesFallbackForOutOfRangeAndNonFiniteValues() {
        assertEquals(4.0f, ConfigRepair.repairFloat(null, "value", 4.0f, 2.0f, 0.0f, 10.0f));
        assertEquals(2.0f, ConfigRepair.repairFloat(null, "value", Float.NaN, 2.0f, 0.0f, 10.0f));
        assertEquals(10.0f, ConfigRepair.repairFloat(null, "value", 20.0f, 12.0f, 0.0f, 10.0f));
    }

    @Test
    void repairIntClampsFallbackWhenCurrentValueIsInvalid() {
        assertEquals(5, ConfigRepair.repairInt(null, "value", 5, 20, 0, 10));
        assertEquals(10, ConfigRepair.repairInt(null, "value", 50, 20, 0, 10));
        assertEquals(0, ConfigRepair.repairInt(null, "value", -50, -20, 0, 10));
    }

    @Test
    void repairColorKeepsValidRgbAndFallsBackForInvalidValues() {
        assertEquals(0x12ABEF, ConfigRepair.repairColor(null, "color", 0x12ABEF, 0xFFFFFF));
        assertEquals(0x345678, ConfigRepair.repairColor(null, "color", -1, 0x12345678));
        assertEquals(0x345678, ConfigRepair.repairColor(null, "color", 0x1000000, 0x12345678));
    }

    @Test
    void repairPrideFlagNormalizesKnownIdsAndFallsBackForUnknownIds() {
        assertEquals("gay-men", ConfigRepair.repairPrideFlag(null, "flag", "gay_men", "rainbow"));
        assertEquals("trans", ConfigRepair.repairPrideFlag(null, "flag", "missing", "trans"));
        assertEquals("rainbow", ConfigRepair.repairPrideFlag(null, "flag", null, "missing"));
    }
}
