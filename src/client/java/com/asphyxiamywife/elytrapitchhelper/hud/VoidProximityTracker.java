package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.VoidWarningSettings;
import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;

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
    private long pulseStartedMillis = -PULSE_MILLIS;
    private int activeMode = VoidWarningSettings.MODE_PREDICTED_TIME;

    VoidProximity update(Config config, double currentY, double rawVy, int voidY) {
        if (!config.voidWarning.enabled) {
            reset();
            return VoidProximity.NONE;
        }
        if (activeMode != config.voidWarning.mode) {
            reset();
            activeMode = config.voidWarning.mode;
        }
        if (config.voidWarning.mode == VoidWarningSettings.MODE_SIMPLE_HEIGHT) {
            return updateSimpleHeight(config, currentY, voidY);
        }
        return updatePredictedTime(config, currentY, rawVy, voidY);
    }

    private VoidProximity updatePredictedTime(Config config, double currentY, double rawVy, int voidY) {
        if (!hasSample) {
            smoothVy = (float) rawVy;
            hasSample = true;
            warningRelevant = false;
            return VoidProximity.NONE;
        }
        smoothVy = LP_KEEP * smoothVy + LP_NEW * (float) rawVy;

        if (smoothVy >= 0.0f) {
            warningRelevant = false;
            intensity = Math.max(0.0f, intensity - MAX_DECAY);
            for (int i = 0; i < pulseFired.length; i++) pulseFired[i] = false;
            return buildResult();
        }

        float timeToContact = (float) ((currentY - voidY) / (-smoothVy * TICKS_PER_SECOND));
        warningRelevant = timeToContact <= config.voidWarning.lookaheadSeconds;
        float target = warningRelevant
                ? MathUtil.smoothstep(MathUtil.clamp(
                        1.0f - timeToContact / config.voidWarning.lookaheadSeconds, 0.0f, 1.0f))
                : 0.0f;
        intensity = target >= intensity ? target : Math.max(target, intensity - MAX_DECAY);

        for (int i = 0; i < PULSE_THRESHOLDS.length; i++) {
            if (!pulseFired[i] && timeToContact <= PULSE_THRESHOLDS[i]) {
                pulseFired[i] = true;
                pulseStartedMillis = System.currentTimeMillis();
            }
            if (pulseFired[i] && timeToContact > PULSE_THRESHOLDS[i] + PULSE_RESET_MARGIN) {
                pulseFired[i] = false;
            }
        }
        return buildResult();
    }

    private VoidProximity updateSimpleHeight(Config config, double currentY, int voidY) {
        hasSample = false;
        smoothVy = 0.0f;
        for (int i = 0; i < pulseFired.length; i++) pulseFired[i] = false;
        pulseStartedMillis = -PULSE_MILLIS;

        float warningBlocks = Math.max(1.0f, config.voidWarning.simpleWarningBlocks);
        float blocksAboveVoid = (float) (currentY - voidY);
        warningRelevant = blocksAboveVoid <= warningBlocks;
        float target = warningRelevant
                ? MathUtil.smoothstep(MathUtil.clamp(1.0f - blocksAboveVoid / warningBlocks, 0.0f, 1.0f))
                : 0.0f;
        intensity = target >= intensity ? target : Math.max(target, intensity - MAX_DECAY);
        return buildResult();
    }

    public void reset() {
        smoothVy = 0.0f;
        hasSample = false;
        warningRelevant = false;
        intensity = 0.0f;
        for (int i = 0; i < pulseFired.length; i++) pulseFired[i] = false;
        pulseStartedMillis = -PULSE_MILLIS;
    }

    boolean isWarningRelevant() {
        return warningRelevant;
    }

    private VoidProximity buildResult() {
        float pulse = computePulse();
        if (intensity <= 0.01f && pulse <= 0.01f) return VoidProximity.NONE;
        return new VoidProximity(intensity, pulse);
    }

    private float computePulse() {
        long age = System.currentTimeMillis() - pulseStartedMillis;
        if (age >= 0L && age < PULSE_MILLIS) {
            return 1.0f - MathUtil.smoothstep(age / (float) PULSE_MILLIS);
        }
        return 0.0f;
    }
}
