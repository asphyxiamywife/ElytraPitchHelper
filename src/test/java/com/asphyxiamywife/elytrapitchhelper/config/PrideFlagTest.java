package com.asphyxiamywife.elytrapitchhelper.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PrideFlagTest {
    @Test
    void idsAreCaseInsensitiveAndAcceptUnderscoreAliases() {
        assertTrue(PrideFlag.isValidId("TRANS"));
        assertTrue(PrideFlag.isValidId("gay_men"));
        assertFalse(PrideFlag.isValidId("not-a-flag"));
        assertEquals("gay-men", PrideFlag.sanitizeId("gay_men"));
    }

    @Test
    void colorAtClampsPositionIntoAvailableStripes() {
        int[] colors = { 0x111111, 0x222222, 0x333333 };

        assertEquals(0x111111, PrideFlag.colorAt(colors, -1.0));
        assertEquals(0x222222, PrideFlag.colorAt(colors, 0.5));
        assertEquals(0x333333, PrideFlag.colorAt(colors, 1.0));
    }

    @Test
    void emptyColorListFallsBackToWhite() {
        assertEquals(0xFFFFFF, PrideFlag.colorAt(null, 0.5));
        assertEquals(0xFFFFFF, PrideFlag.colorAt(new int[0], 0.5));
    }
}
