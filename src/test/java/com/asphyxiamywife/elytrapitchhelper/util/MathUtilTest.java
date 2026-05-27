package com.asphyxiamywife.elytrapitchhelper.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MathUtilTest {
    @Test
    void clampBoundsValues() {
        assertEquals(0.0f, MathUtil.clamp(-2.0f, 0.0f, 1.0f));
        assertEquals(0.5f, MathUtil.clamp(0.5f, 0.0f, 1.0f));
        assertEquals(1.0f, MathUtil.clamp(2.0f, 0.0f, 1.0f));
    }

    @Test
    void argbClampsAlphaAndPreservesRgb() {
        assertEquals(0x00123456, MathUtil.argb(-1.0f, 0xFF123456));
        assertEquals(0x7F123456, MathUtil.argb(0.5f, 0xFF123456));
        assertEquals(0xFF123456, MathUtil.argb(2.0f, 0xFF123456));
    }

    @Test
    void blendRgbInterpolatesAndClampsAmount() {
        assertEquals(0x000000, MathUtil.blendRgb(0x000000, 0xFFFFFF, -1.0f));
        assertEquals(0x7F7F7F, MathUtil.blendRgb(0x000000, 0xFFFFFF, 0.5f));
        assertEquals(0xFFFFFF, MathUtil.blendRgb(0x000000, 0xFFFFFF, 2.0f));
    }

    @Test
    void smoothstepKeepsEndpoints() {
        assertEquals(0.0f, MathUtil.smoothstep(0.0f));
        assertEquals(0.5f, MathUtil.smoothstep(0.5f));
        assertEquals(1.0f, MathUtil.smoothstep(1.0f));
    }
}
