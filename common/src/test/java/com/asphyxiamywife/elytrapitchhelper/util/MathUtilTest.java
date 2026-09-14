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
    void clampBoundsIntValues() {
        assertEquals(0, MathUtil.clamp(-2, 0, 10));
        assertEquals(5, MathUtil.clamp(5, 0, 10));
        assertEquals(10, MathUtil.clamp(20, 0, 10));
        assertEquals(Integer.MAX_VALUE, MathUtil.clamp(Integer.MAX_VALUE, 0, Integer.MAX_VALUE));
        assertEquals(0, MathUtil.clamp(Integer.MIN_VALUE, 0, 10));
    }

    @Test
    void clampBoundsLongValues() {
        assertEquals(1L, MathUtil.clamp(0L, 1L, 50L));
        assertEquals(25L, MathUtil.clamp(25L, 1L, 50L));
        assertEquals(50L, MathUtil.clamp(Long.MAX_VALUE, 1L, 50L));
    }

    @Test
    void clampBoundsDoubleValues() {
        assertEquals(-1.0, MathUtil.clamp(-4.0, -1.0, 1.0));
        assertEquals(0.25, MathUtil.clamp(0.25, -1.0, 1.0));
        assertEquals(1.0, MathUtil.clamp(4.0, -1.0, 1.0));
    }

    @Test
    void clamp01BoundsToUnitRange() {
        assertEquals(0.0f, MathUtil.clamp01(-0.5f));
        assertEquals(0.25f, MathUtil.clamp01(0.25f));
        assertEquals(1.0f, MathUtil.clamp01(1.5f));

        assertEquals(0.0, MathUtil.clamp01(-0.5));
        assertEquals(0.25, MathUtil.clamp01(0.25));
        assertEquals(1.0, MathUtil.clamp01(1.5));
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

    @Test
    void smoothstepClampsInputToUnitInterval() {
        assertEquals(0.0f, MathUtil.smoothstep(-1.0f));
        assertEquals(1.0f, MathUtil.smoothstep(2.0f));
    }
}
