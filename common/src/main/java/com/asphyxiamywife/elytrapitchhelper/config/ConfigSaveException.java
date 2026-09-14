package com.asphyxiamywife.elytrapitchhelper.config;

public class ConfigSaveException extends RuntimeException {
    private final boolean retryable;

    public ConfigSaveException(String message) {
        this(message, null, false);
    }

    public ConfigSaveException(String message, Throwable cause) {
        this(message, cause, false);
    }

    ConfigSaveException(String message, boolean retryable) {
        this(message, null, retryable);
    }

    ConfigSaveException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
