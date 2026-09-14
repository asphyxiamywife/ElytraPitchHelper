package com.asphyxiamywife.elytrapitchhelper.client;

final class ConfigWatchDebouncer {
    static final long MAX_DEFERRAL_MILLIS = 30_000L;
    private static final int MAX_BACKOFF_SHIFT = 16;

    private final long debounceMillis;
    private boolean reloadPending;
    private long reloadAtMillis;
    private int deferrals;

    ConfigWatchDebouncer(long debounceMillis) {
        this.debounceMillis = Math.max(0L, debounceMillis);
    }

    void recordChange(long nowMillis) {
        deferrals = 0;
        reloadPending = true;
        reloadAtMillis = nowMillis + debounceMillis;
    }

    void deferReload(long nowMillis) {
        if (deferrals < MAX_BACKOFF_SHIFT) {
            deferrals++;
        }
        reloadPending = true;
        reloadAtMillis = nowMillis + backoffMillis();
    }

    void clearBackoff() {
        deferrals = 0;
    }

    private long backoffMillis() {
        long scaled = Math.max(1L, debounceMillis) << Math.min(deferrals, MAX_BACKOFF_SHIFT);
        return Math.min(MAX_DEFERRAL_MILLIS, scaled);
    }

    long waitMillis(long nowMillis) {
        if (!reloadPending) {
            return -1L;
        }
        return Math.max(0L, reloadAtMillis - nowMillis);
    }

    boolean shouldReload(long nowMillis) {
        if (!reloadPending || nowMillis - reloadAtMillis < 0L) {
            return false;
        }
        reloadPending = false;
        return true;
    }
}
