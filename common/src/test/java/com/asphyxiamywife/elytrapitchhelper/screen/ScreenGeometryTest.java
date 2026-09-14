package com.asphyxiamywife.elytrapitchhelper.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ScreenGeometryTest {
    @Test
    void farEdgesAreExclusive() {
        assertTrue(ScreenGeometry.contains(10, 20, 30, 40, 10, 20));
        assertTrue(ScreenGeometry.contains(10, 20, 30, 40, 39.999, 59.999));
        assertFalse(ScreenGeometry.contains(10, 20, 30, 40, 40, 30));
        assertFalse(ScreenGeometry.contains(10, 20, 30, 40, 20, 60));
    }
}
