package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.ModConstants;
import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.platform.PlatformServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

final class FlightDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModConstants.MOD_ID);

    private boolean enabled;
    private boolean sawDiveFlash;
    private boolean hasCycle;
    private double cycleStartY;
    private int cycleTicks;
    private double cycleDistance;
    private double cycleLowY;
    private double cycleHighY;
    private AmplitudeLeg pendingGlyph = AmplitudeLeg.NONE;
    private int glyphSequence;
    private int glyphResponseTicks;
    private float glyphStartPitch;
    private float glyphTargetPitch;
    private float glyphTolerance;
    private double glyphStartY;
    private double glyphStartSpeed;
    private int cycleGlyphAttempts;
    private int cycleGlyphReached;
    private int cycleGlyphResponseTicks;

    void configure(Config config) {
        boolean next = config.diagnostics().flightTelemetryLogging();
        if (next == enabled) {
            return;
        }
        reset();
        enabled = next;
        if (enabled) {
            LOGGER.info("diagnostics enabled version={} mode={}",
                    PlatformServices.modVersion(), triggerMode(config.amplitude().triggerMode()));
            LOGGER.info("config divePitch={} climbPitch={} downThreshold={} upThreshold={} glyphs={}",
                    fixed(config.pitch().targetDownMinecraft(), 1),
                    fixed(config.pitch().targetUpMinecraft(), 1),
                    activeDownThreshold(config), activeUpThreshold(config),
                    config.amplitude().motionGlyphsEnabled());
        }
    }

    boolean enabled() {
        return enabled;
    }

    int glyphAttemptCount() {
        return cycleGlyphAttempts;
    }

    void reset() {
        sawDiveFlash = false;
        hasCycle = false;
        cycleTicks = 0;
        cycleDistance = 0.0;
        pendingGlyph = AmplitudeLeg.NONE;
        glyphSequence = 0;
        glyphResponseTicks = 0;
        cycleGlyphAttempts = 0;
        cycleGlyphReached = 0;
        cycleGlyphResponseTicks = 0;
    }

    void record(Config config, AmplitudeCue amplitudeCue, MotionGlyphCue motionGlyphCue,
            float pitchDegrees,
            double verticalVelocity, double playerY, double horizontalSpeed) {
        if (!enabled) {
            return;
        }
        recordCycle(config, amplitudeCue, playerY, horizontalSpeed);
        recordGlyph(config, amplitudeCue, motionGlyphCue, pitchDegrees, verticalVelocity,
                playerY, horizontalSpeed);
    }

    private void recordCycle(Config config, AmplitudeCue amplitudeCue,
            double playerY, double horizontalSpeed) {
        boolean diveFlash = amplitudeCue.flashLeg == AmplitudeLeg.DESCENDING;
        boolean cueStarted = diveFlash && !sawDiveFlash;
        sawDiveFlash = diveFlash;

        if (hasCycle) {
            cycleTicks++;
            cycleDistance += horizontalSpeed;
            cycleLowY = Math.min(cycleLowY, playerY);
            cycleHighY = Math.max(cycleHighY, playerY);
        }

        if (!cueStarted) {
            return;
        }
        if (hasCycle) {
            double seconds = cycleTicks / 20.0;
            String glyphSummary = cycleGlyphAttempts == 0 ? "none"
                    : cycleGlyphReached + "/" + cycleGlyphAttempts + " reached, avg="
                    + (cycleGlyphReached == 0 ? "n/a"
                            : fixed(cycleGlyphResponseTicks / (double) cycleGlyphReached, 1) + "t");
            LOGGER.info("cycle dy={} ({}/s) dx={} ({}/s) over {}t ({}s) swing={} glyphs={} "
                            + "mode={} downThreshold={} upThreshold={}",
                    signed(playerY - cycleStartY, 2),
                    signed((playerY - cycleStartY) / seconds, 3),
                    fixed(cycleDistance, 0), fixed(cycleDistance / seconds, 2),
                    cycleTicks, fixed(seconds, 1), fixed(cycleHighY - cycleLowY, 1), glyphSummary,
                    triggerMode(config.amplitude().triggerMode()),
                    activeDownThreshold(config), activeUpThreshold(config));
        }
        hasCycle = true;
        cycleStartY = playerY;
        cycleTicks = 0;
        cycleDistance = 0.0;
        cycleLowY = playerY;
        cycleHighY = playerY;
        cycleGlyphAttempts = 0;
        cycleGlyphReached = 0;
        cycleGlyphResponseTicks = 0;
    }

    private void recordGlyph(Config config, AmplitudeCue amplitudeCue,
            MotionGlyphCue motionGlyphCue, float pitchDegrees,
            double verticalVelocity, double playerY, double horizontalSpeed) {
        if (!config.amplitude().motionGlyphsEnabled()) {
            pendingGlyph = AmplitudeLeg.NONE;
            return;
        }

        if (motionGlyphCue.afterLeg() == AmplitudeLeg.NONE) {
            pendingGlyph = AmplitudeLeg.NONE;
        }

        if (pendingGlyph != AmplitudeLeg.NONE) {
            glyphResponseTicks++;
            boolean reached = pendingGlyph == AmplitudeLeg.DESCENDING
                    ? pitchDegrees <= glyphTargetPitch + glyphTolerance
                    : pitchDegrees >= glyphTargetPitch - glyphTolerance;
            if (reached) {
                cycleGlyphReached++;
                cycleGlyphResponseTicks += glyphResponseTicks;
                LOGGER.info("glyph-result #{} action={} reached target in {}t ({}s) pitch {} -> {} "
                                + "dy={} speed {} -> {} vy={}",
                        glyphSequence, glyphAction(pendingGlyph), glyphResponseTicks,
                        fixed(glyphResponseTicks / 20.0, 2),
                        signed(glyphStartPitch, 1), signed(pitchDegrees, 1),
                        signed(playerY - glyphStartY, 2),
                        fixed(glyphStartSpeed, 3), fixed(horizontalSpeed, 3),
                        signed(verticalVelocity, 3));
                pendingGlyph = AmplitudeLeg.NONE;
            } else if (glyphResponseTicks >= 100) {
                LOGGER.info("glyph-result #{} action={} did not reach target within 100t; "
                                + "pitch {} -> {} dy={} speed {} -> {}",
                        glyphSequence, glyphAction(pendingGlyph),
                        signed(glyphStartPitch, 1), signed(pitchDegrees, 1),
                        signed(playerY - glyphStartY, 2),
                        fixed(glyphStartSpeed, 3), fixed(horizontalSpeed, 3));
                pendingGlyph = AmplitudeLeg.NONE;
            }
        }

        AmplitudeLeg startedLeg = amplitudeCue.glyphStartedLeg;
        if (startedLeg == AmplitudeLeg.NONE) {
            return;
        }
        if (pendingGlyph != AmplitudeLeg.NONE) {
            LOGGER.info("glyph-result #{} action={} superseded after {}t at pitch={}",
                    glyphSequence, glyphAction(pendingGlyph), glyphResponseTicks,
                    signed(pitchDegrees, 1));
        }

        pendingGlyph = startedLeg;
        glyphSequence++;
        glyphResponseTicks = 0;
        glyphStartPitch = pitchDegrees;
        glyphTargetPitch = startedLeg == AmplitudeLeg.DESCENDING
                ? config.pitch().targetUpMinecraft()
                : config.pitch().targetDownMinecraft();
        glyphTolerance = config.pitch().toleranceDegrees();
        glyphStartY = playerY;
        glyphStartSpeed = horizontalSpeed;
        cycleGlyphAttempts++;
        LOGGER.info("glyph-cue #{} action={} mode={} pitch={} target={}±{} y={} vy={} speed={} "
                        + "downThreshold={} upThreshold={}",
                glyphSequence, glyphAction(startedLeg), triggerMode(config.amplitude().triggerMode()),
                signed(pitchDegrees, 1), signed(glyphTargetPitch, 1), fixed(glyphTolerance, 1),
                fixed(playerY, 2), signed(verticalVelocity, 3), fixed(horizontalSpeed, 3),
                activeDownThreshold(config), activeUpThreshold(config));
    }

    private static String activeDownThreshold(Config config) {
        return activeThreshold(config, config.amplitude().downBlocks(), config.amplitude().downVelocity());
    }

    private static String activeUpThreshold(Config config) {
        return activeThreshold(config, config.amplitude().upBlocks(), config.amplitude().upVelocity());
    }

    private static String activeThreshold(Config config, int blocks, float velocity) {
        return switch (config.amplitude().triggerMode()) {
            case Config.AMPLITUDE_TRIGGER_HEIGHT -> blocks + "blocks";
            case Config.AMPLITUDE_TRIGGER_EITHER -> blocks + "blocks|" + fixed(velocity, 2) + "b/t";
            default -> fixed(velocity, 2) + "b/t";
        };
    }

    private static String glyphAction(AmplitudeLeg leg) {
        return leg == AmplitudeLeg.DESCENDING ? "pull-up" : "release-down";
    }

    private static String triggerMode(int mode) {
        return switch (mode) {
            case Config.AMPLITUDE_TRIGGER_HEIGHT -> "height";
            case Config.AMPLITUDE_TRIGGER_EITHER -> "either";
            default -> "velocity";
        };
    }

    private static String signed(double value, int decimals) {
        return String.format(Locale.ROOT, "%+." + decimals + "f", value);
    }

    private static String fixed(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }
}
