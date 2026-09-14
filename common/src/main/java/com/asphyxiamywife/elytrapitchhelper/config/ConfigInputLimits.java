package com.asphyxiamywife.elytrapitchhelper.config;

public final class ConfigInputLimits {
    public static final int MAX_FILE_BYTES = 4 * 1024 * 1024;
    public static final int MAX_NESTING = 64;
    public static final int MAX_ARRAY_ELEMENTS = 4096;
    public static final int MAX_TOKENS = 100_000;

    private ConfigInputLimits() {
    }
}
