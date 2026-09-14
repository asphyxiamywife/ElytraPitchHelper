package com.asphyxiamywife.elytrapitchhelper.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class MonotonicClockTest {
    @Test
    void readingsNeverGoBackwards() {
        long previous = MonotonicClock.millis();
        for (int i = 0; i < 100_000; i++) {
            long current = MonotonicClock.millis();
            assertTrue(current >= previous, "monotonic clock stepped backwards");
            previous = current;
        }
    }

    @Test
    void theSharedSupplierReadsTheSameClock() {
        long before = MonotonicClock.millis();
        long supplied = MonotonicClock.MILLIS.getAsLong();
        long after = MonotonicClock.millis();

        assertTrue(supplied >= before && supplied <= after);
    }
}
