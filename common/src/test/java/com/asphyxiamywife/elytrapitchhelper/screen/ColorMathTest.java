package com.asphyxiamywife.elytrapitchhelper.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ColorMathTest {
    private static final double EPSILON = 1.0E-9;

    @Test
    void primaryColorsHaveCanonicalHsvValues() {
        assertArrayEquals(new double[] {0.0, 1.0, 1.0}, ColorMath.rgbToHsv(255, 0, 0), EPSILON);
        assertArrayEquals(new double[] {1.0 / 3.0, 1.0, 1.0}, ColorMath.rgbToHsv(0, 255, 0), EPSILON);
        assertArrayEquals(new double[] {2.0 / 3.0, 1.0, 1.0}, ColorMath.rgbToHsv(0, 0, 255), EPSILON);
        assertEquals(0xFF0000, ColorMath.hsvToRgb(0.0, 1.0, 1.0));
        assertEquals(0x00FF00, ColorMath.hsvToRgb(1.0 / 3.0, 1.0, 1.0));
        assertEquals(0x0000FF, ColorMath.hsvToRgb(2.0 / 3.0, 1.0, 1.0));
    }

    @Test
    void squareDiskMappingRoundTripsCornersAndOrigin() {
        assertArrayEquals(new double[] {0.0, 0.0}, ColorMath.squareToDisk(0.0, 0.0), EPSILON);
        for (double[] point : new double[][] {{1.0, 1.0}, {-1.0, 1.0}, {-1.0, -1.0}, {1.0, -1.0}}) {
            double[] disk = ColorMath.squareToDisk(point[0], point[1]);
            assertEquals(1.0, Math.hypot(disk[0], disk[1]), EPSILON);
            assertArrayEquals(point, ColorMath.diskToSquare(disk[0], disk[1]), EPSILON);
        }
    }

    @Test
    void hexParsingIsStrictAndCaseInsensitive() {
        assertEquals(0xA1B2C3, ColorMath.parseHexColor("a1B2c3"));
        assertEquals(-1, ColorMath.parseHexColor("12345"));
        assertEquals(-1, ColorMath.parseHexColor("1234567"));
        assertEquals(-1, ColorMath.parseHexColor("GGGGGG"));
    }

    @Test
    void hexParsingTakesTheHashAndOnlyDigits() {
        assertEquals(0x123456, ColorMath.parseHexColor("#123456"));
        assertEquals(-1, ColorMath.parseHexColor("#12345"));
        assertEquals(-1, ColorMath.parseHexColor("##12345"));
        assertEquals(-1, ColorMath.parseHexColor("+12345F"));
        assertEquals(-1, ColorMath.parseHexColor("-12345F"));
    }

}
