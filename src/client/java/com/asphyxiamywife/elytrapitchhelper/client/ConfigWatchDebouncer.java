package com.asphyxiamywife.elytrapitchhelper.client;

final class ConfigWatchDebouncer {
    private final long debounceMillis;
    private long lastReloadMillis = Long.MIN_VALUE;

    ConfigWatchDebouncer(long debounceMillis) {
        this.debounceMillis = Math.max(0L, debounceMillis);
    }

    boolean shouldReload(long nowMillis) {
        if (lastReloadMillis != Long.MIN_VALUE && nowMillis - lastReloadMillis < debounceMillis) {
            return false;
        }
        lastReloadMillis = nowMillis;
        return true;
    }
}
