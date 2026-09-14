package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.ConfigTestFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class FlightDiagnosticsTest {
    @Test
    void countsOnlyGlyphsThatActuallyStarted() {
        Config config = ConfigTestFixtures.configWith(
                ConfigTestFixtures.minimalProfile("Default", "default.json"));
        ConfigTestFixtures.updateActiveProfile(config, profile -> profile
                .withDiagnostics(profile.diagnostics().withFlightTelemetryLogging(true))
                .withAmplitude(profile.amplitude().withMotionGlyphsEnabled(true)));
        FlightDiagnostics diagnostics = new FlightDiagnostics();
        diagnostics.configure(config);

        diagnostics.record(config,
                new AmplitudeCue(AmplitudeLeg.DESCENDING, 1.0f,
                        AmplitudeLeg.DESCENDING, AmplitudeLeg.NONE),
                MotionGlyphCue.NONE, 20.0f, -0.1, 100.0, 1.0);
        assertEquals(0, diagnostics.glyphAttemptCount());

        diagnostics.record(config,
                new AmplitudeCue(AmplitudeLeg.DESCENDING, 1.0f,
                        AmplitudeLeg.DESCENDING, AmplitudeLeg.DESCENDING),
                new MotionGlyphCue(AmplitudeLeg.DESCENDING, Strength.ZERO),
                20.0f, -0.1, 99.0, 1.1);
        assertEquals(1, diagnostics.glyphAttemptCount());
    }
}
