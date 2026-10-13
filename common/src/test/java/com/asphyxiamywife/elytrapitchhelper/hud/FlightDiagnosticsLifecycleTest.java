package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.ModConstants;
import com.asphyxiamywife.elytrapitchhelper.client.ClientConfigStore;
import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.ConfigTestFixtures;
import com.asphyxiamywife.elytrapitchhelper.flight.ElytraDetector;
import com.asphyxiamywife.elytrapitchhelper.testing.IsolatedConfigRoot;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FlightDiagnosticsLifecycleTest {
    @IsolatedConfigRoot
    Path configRoot;

    @Test
    void missingPlayerClearsTelemetryBeforeNextFlight() throws ReflectiveOperationException {
        Config config = ClientConfigStore.get();
        config.enabled = true;
        ConfigTestFixtures.updateActiveProfile(config, profile -> profile.withDiagnostics(
                profile.diagnostics().withFlightTelemetryLogging(true)));
        ClientConfigStore.update(config);

        PitchGuideHud hud = new PitchGuideHud(new ElytraDetector());
        var diagnosticsField = PitchGuideHud.class.getDeclaredField("flightDiagnostics");
        diagnosticsField.setAccessible(true);
        FlightDiagnostics diagnostics = (FlightDiagnostics) diagnosticsField.get(hud);
        diagnostics.configure(config);

        List<String> cycles = new ArrayList<>();
        AbstractAppender appender = new AbstractAppender("flight-diagnostics-lifecycle", null,
                null, false, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                String message = event.getMessage().getFormattedMessage();
                if (message.startsWith("cycle ")) {
                    cycles.add(message);
                }
            }
        };
        Logger logger = (Logger) LogManager.getLogger(ModConstants.MOD_ID);
        Level previousLevel = logger.getLevel();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        try {
            AmplitudeCue dive = new AmplitudeCue(AmplitudeLeg.DESCENDING, 1.0f,
                    AmplitudeLeg.DESCENDING, AmplitudeLeg.NONE);
            diagnostics.record(config, dive, MotionGlyphCue.NONE, 20.0f, -0.1, 100.0, 1.0);
            diagnostics.record(config, AmplitudeCue.NONE, MotionGlyphCue.NONE,
                    20.0f, -0.1, 99.0, 1.0);

            hud.tick(null);
            assertTrue(diagnostics.enabled(), "Ending a flight must keep logging enabled");

            diagnostics.record(config, AmplitudeCue.NONE, MotionGlyphCue.NONE,
                    20.0f, -0.1, 200.0, 1.0);
            diagnostics.record(config, dive, MotionGlyphCue.NONE, 20.0f, -0.1, 200.0, 1.0);
            assertTrue(cycles.isEmpty(), "The first cue after restarting must begin a new cycle: " + cycles);

            diagnostics.record(config, AmplitudeCue.NONE, MotionGlyphCue.NONE,
                    20.0f, -0.1, 199.0, 2.0);
            diagnostics.record(config, dive, MotionGlyphCue.NONE, 20.0f, -0.1, 198.0, 3.0);
            assertEquals(1, cycles.size());
            assertTrue(cycles.getFirst().startsWith(
                    "cycle dy=-2.00 (-20.000/s) dx=5 (50.00/s) over 2t (0.1s) swing=2.0 "),
                    cycles.getFirst());
        } finally {
            logger.removeAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }
}
