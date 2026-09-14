package com.asphyxiamywife.elytrapitchhelper.config;

public record DiagnosticsSettings(boolean flightTelemetryLogging) {
    public DiagnosticsSettings() {
        this(ProfileDefaults.template().diagnostics());
    }

    private DiagnosticsSettings(DiagnosticsSettings defaults) {
        this(defaults.flightTelemetryLogging);
    }

    public DiagnosticsSettings withFlightTelemetryLogging(boolean value) {
        return new DiagnosticsSettings(value);
    }
}
