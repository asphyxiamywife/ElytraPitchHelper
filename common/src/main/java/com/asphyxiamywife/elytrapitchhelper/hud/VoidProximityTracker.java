package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.VoidWarningSettings;
import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;
import com.asphyxiamywife.elytrapitchhelper.util.MonotonicClock;

import java.util.Arrays;
import java.util.function.LongSupplier;

final class VoidProximityTracker {
    private static final float LP_KEEP = 0.75f;
    private static final float LP_NEW = 0.25f;
    private static final float MAX_DECAY = 0.05f;
    private static final float TICKS_PER_SECOND = 20.0f;
    private static final float[] PULSE_THRESHOLDS = {5.0f, 3.0f, 1.0f};
    private static final float PULSE_RESET_MARGIN = 0.5f;
    private static final long PULSE_MILLIS = 520L;

    private float smoothVy = 0.0f;
    private boolean hasSample = false;
    private boolean warningRelevant = false;
    private float intensity = 0.0f;
    private final boolean[] pulseFired = new boolean[PULSE_THRESHOLDS.length];
    private int activeMode = VoidWarningSettings.MODE_PREDICTED_TIME;
    private final DecayingPulse pulse;

    VoidProximityTracker() {
        this(MonotonicClock.MILLIS);
    }

    VoidProximityTracker(LongSupplier currentTimeMillis) {
        this.pulse = new DecayingPulse(PULSE_MILLIS, currentTimeMillis);
    }

    VoidProximity update(Config config, double currentY, double rawVy, int voidY) {
        if (!config.voidWarning().enabled()) {
            reset();
            return VoidProximity.NONE;
        }
        if (activeMode != config.voidWarning().mode()) {
            reset();
            activeMode = config.voidWarning().mode();
        }
        if (config.voidWarning().mode() == VoidWarningSettings.MODE_SIMPLE_HEIGHT) {
            return updateSimpleHeight(config, currentY, voidY);
        }
        return updatePredictedTime(config, currentY, rawVy, voidY);
    }

    private VoidProximity updatePredictedTime(Config config, double currentY, double rawVy, int voidY) {
        float sample = (float) rawVy;
        if (!Float.isFinite(sample)) {
            return hasSample ? buildResult() : VoidProximity.NONE;
        }
        if (!hasSample) {
            smoothVy = sample;
            hasSample = true;
            warningRelevant = false;
            return VoidProximity.NONE;
        }
        smoothVy = LP_KEEP * smoothVy + LP_NEW * sample;

        if (smoothVy >= 0.0f) {
            warningRelevant = false;
            intensity = Math.max(0.0f, intensity - MAX_DECAY);
            clearPulses();
            return buildResult();
        }

        float timeToContact = (float) ((currentY - voidY) / (-smoothVy * TICKS_PER_SECOND));
        float lookaheadSeconds = config.voidWarning().lookaheadSeconds();
        if (!(lookaheadSeconds > 0.0f)) {
            warningRelevant = false;
            decayIntensityToward(0.0f);
            clearPulses();
            return buildResult();
        }
        warningRelevant = timeToContact <= lookaheadSeconds;
        float target = warningRelevant
                ? MathUtil.smoothstep(MathUtil.clamp(
                        1.0f - timeToContact / lookaheadSeconds, 0.0f, 1.0f))
                : 0.0f;
        decayIntensityToward(target);

        if (timeToContact > 0.0f) {
            boolean triggerPulse = false;
            for (int i = 0; i < PULSE_THRESHOLDS.length; i++) {
                float threshold = Math.min(PULSE_THRESHOLDS[i], lookaheadSeconds);
                if (warningRelevant && !pulseFired[i] && timeToContact <= threshold) {
                    pulseFired[i] = true;
                    triggerPulse = true;
                }
                if (pulseFired[i] && timeToContact > threshold + PULSE_RESET_MARGIN) {
                    pulseFired[i] = false;
                }
            }
            if (triggerPulse) {
                pulse.trigger();
            }
        }
        return buildResult();
    }

    private VoidProximity updateSimpleHeight(Config config, double currentY, int voidY) {
        float warningBlocks = Math.max(1.0f, config.voidWarning().simpleWarningBlocks());
        float blocksAboveVoid = (float) (currentY - voidY);
        warningRelevant = blocksAboveVoid <= warningBlocks;
        float target = warningRelevant
                ? MathUtil.smoothstep(MathUtil.clamp(1.0f - blocksAboveVoid / warningBlocks, 0.0f, 1.0f))
                : 0.0f;
        decayIntensityToward(target);
        return buildResult();
    }

    Strength pulseStrength() {
        return pulse;
    }

    void suppress() {
        warningRelevant = false;
        intensity = 0.0f;
        clearPulses();
    }

    public void reset() {
        suppress();
        smoothVy = 0.0f;
        hasSample = false;
    }

    private void clearPulses() {
        Arrays.fill(pulseFired, false);
        pulse.reset();
    }

    private void decayIntensityToward(float target) {
        intensity = target >= intensity ? target : Math.max(target, intensity - MAX_DECAY);
    }

    boolean isWarningRelevant() {
        return warningRelevant;
    }

    private VoidProximity buildResult() {
        float pulseAmount = pulse.value();
        if (intensity <= 0.01f && pulseAmount <= 0.01f) return VoidProximity.NONE;
        return new VoidProximity(intensity, pulseAmount);
    }

}
