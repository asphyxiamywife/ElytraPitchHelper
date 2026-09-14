package com.asphyxiamywife.elytrapitchhelper.screen;

final class SmoothScroll {
    private double position;
    private double target;
    private long lastMillis;

    void reset(int value) {
        position = target = value;
        lastMillis = 0;
    }

    boolean wheel(double distance, int maximum, long nowMillis) {
        advance(nowMillis, maximum);
        double next = Math.max(0, Math.min(maximum, target + distance));
        boolean changed = next != target;
        target = next;
        return changed;
    }

    int advance(long nowMillis, int maximum) {
        target = Math.max(0, Math.min(maximum, target));
        position = Math.max(0, Math.min(maximum, position));
        long elapsed = lastMillis == 0 ? 0 : Math.max(0, nowMillis - lastMillis);
        lastMillis = nowMillis;
        position += (target - position) * -Math.expm1(-elapsed / 45.0);
        if (Math.abs(target - position) < 0.001) position = target;
        return (int) Math.round(position);
    }
}
