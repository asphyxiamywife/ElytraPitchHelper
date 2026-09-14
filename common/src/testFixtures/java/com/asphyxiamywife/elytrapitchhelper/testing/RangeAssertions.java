package com.asphyxiamywife.elytrapitchhelper.testing;

import static org.junit.jupiter.api.Assertions.assertTrue;

public final class RangeAssertions {
    private RangeAssertions() {
    }

    public static void assertBetween(int value, int minimum, int maximum) {
        assertTrue(value >= minimum && value <= maximum,
                () -> value + " is outside [" + minimum + ", " + maximum + "]");
    }

    public static void assertBetween(float value, float minimum, float maximum) {
        assertTrue(Float.isFinite(value) && value >= minimum && value <= maximum,
                () -> value + " is outside [" + minimum + ", " + maximum + "]");
    }

    public static void assertUnit(float value) {
        assertTrue(Float.isFinite(value) && value >= 0.0f && value <= 1.0f,
                () -> value + " is outside [0, 1]");
    }

    public static void assertUnit(double value) {
        assertTrue(Double.isFinite(value) && value >= 0.0 && value <= 1.0,
                () -> value + " is outside [0, 1]");
    }

    public static void assertFiniteUnit(double value) {
        assertTrue(Double.isFinite(value) && value >= -1.0 && value <= 1.0,
                () -> value + " is outside [-1, 1]");
    }
}
