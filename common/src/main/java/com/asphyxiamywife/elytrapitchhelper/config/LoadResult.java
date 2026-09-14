package com.asphyxiamywife.elytrapitchhelper.config;

sealed interface LoadResult permits LoadResult.Loaded, LoadResult.ReadOnly {
    ConfigState state();

    default boolean readOnly() {
        return this instanceof ReadOnly;
    }

    default String warning() {
        return this instanceof ReadOnly readOnly ? readOnly.reason() : "";
    }

    record Loaded(ConfigState state) implements LoadResult {
        public Loaded {
            state = state == null ? new ConfigState(null, null) : state;
        }
    }

    record ReadOnly(ConfigState state, String reason) implements LoadResult {
        public ReadOnly {
            state = state == null ? new ConfigState(null, null) : state;
            reason = reason == null ? "" : reason;
        }
    }
}
