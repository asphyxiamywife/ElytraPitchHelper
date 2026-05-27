package com.asphyxiamywife.elytrapitchhelper.client;

final class ConfigWatchDebouncer {
    private static final long NO_PENDING_RELOAD = Long.MIN_VALUE;

    private final long debounceMillis;
    private long reloadAtMillis = NO_PENDING_RELOAD;

    ConfigWatchDebouncer(long debounceMillis) {
        this.debounceMillis = Math.max(0L, debounceMillis);
    }

    void recordChange(long nowMillis) {
        reloadAtMillis = nowMillis + debounceMillis;
    }

    long waitMillis(long nowMillis) {
        if (reloadAtMillis == NO_PENDING_RELOAD) {
            return -1L;
        }
        return Math.max(0L, reloadAtMillis - nowMillis);
    }

    boolean shouldReload(long nowMillis) {
        if (reloadAtMillis == NO_PENDING_RELOAD || nowMillis < reloadAtMillis) {
            return false;
        }
        reloadAtMillis = NO_PENDING_RELOAD;
        return true;
    }
}
