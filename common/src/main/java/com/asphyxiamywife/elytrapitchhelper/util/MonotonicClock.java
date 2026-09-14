package com.asphyxiamywife.elytrapitchhelper.util;

import java.util.function.LongSupplier;

public final class MonotonicClock {
    public static final LongSupplier MILLIS = MonotonicClock::millis;

    private MonotonicClock() {
    }

    public static long millis() {
        return System.nanoTime() / 1_000_000L;
    }
}
