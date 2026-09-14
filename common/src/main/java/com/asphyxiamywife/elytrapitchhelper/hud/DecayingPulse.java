package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;

import java.util.function.LongSupplier;

final class DecayingPulse implements Strength {
    private final long durationMillis;
    private final LongSupplier currentTimeMillis;
    private volatile long startedMillis;
    private volatile boolean triggered;

    DecayingPulse(long durationMillis, LongSupplier currentTimeMillis) {
        this.durationMillis = durationMillis;
        this.currentTimeMillis = currentTimeMillis;
    }

    void trigger() {
        startedMillis = currentTimeMillis.getAsLong();
        triggered = true;
    }

    void reset() {
        triggered = false;
    }

    boolean expired() {
        long age = age();
        return !triggered || age < 0L || age >= durationMillis;
    }

    @Override
    public float value() {
        long age = age();
        if (triggered && age >= 0L && age < durationMillis) {
            return 1.0f - MathUtil.smoothstep(age / (float) durationMillis);
        }
        return 0.0f;
    }

    private long age() {
        return currentTimeMillis.getAsLong() - startedMillis;
    }
}
