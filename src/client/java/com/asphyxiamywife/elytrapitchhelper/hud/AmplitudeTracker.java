package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;

public final class AmplitudeTracker {
    private static final float VERTICAL_EPSILON = 0.01f;
    private static final float CUE_START_RATIO = 0.72f;
    private static final float MIN_VELOCITY_CUE_SPAN = 0.1f;
    private static final long FLASH_MILLIS = 520L;

    private AmplitudeLeg leg = AmplitudeLeg.NONE;
    private double anchorY;
    private double lastPlayerY;
    private boolean hasSample;
    private float previousDistance;
    private boolean hasPreviousDistance;
    private float previousHorizontalSpeed;
    private boolean hasPreviousHorizontalSpeed;
    private float velocityAnchorSpeed;
    private boolean hasTriggeredCue;
    private AmplitudeLeg flashLeg = AmplitudeLeg.NONE;
    private long flashStartedMillis = -FLASH_MILLIS;

    AmplitudeCue update(Config config, double currentY, double currentHorizontalSpeed) {
        if (!config.amplitude.enabled) {
            reset();
            return AmplitudeCue.NONE;
        }

        float horizontalSpeed = (float) currentHorizontalSpeed;
        if (captureFirstSampleIfNeeded(currentY, horizontalSpeed)) {
            return AmplitudeCue.NONE;
        }

        boolean stateMachineMode = usesStateMachine(config);
        advanceLeg(currentY, stateMachineMode, horizontalSpeed);
        lastPlayerY = currentY;
        if (leg == AmplitudeLeg.NONE) {
            return AmplitudeCue.NONE;
        }

        return applyCue(calculateCue(config, currentY, horizontalSpeed), currentY, horizontalSpeed, stateMachineMode);
    }

    public void reset() {
        leg = AmplitudeLeg.NONE;
        anchorY = 0.0;
        lastPlayerY = 0.0;
        hasSample = false;
        previousDistance = 0.0f;
        hasPreviousDistance = false;
        previousHorizontalSpeed = 0.0f;
        hasPreviousHorizontalSpeed = false;
        velocityAnchorSpeed = 0.0f;
        hasTriggeredCue = false;
        flashLeg = AmplitudeLeg.NONE;
        flashStartedMillis = -FLASH_MILLIS;
    }

    private boolean captureFirstSampleIfNeeded(double currentY, float horizontalSpeed) {
        if (hasSample) {
            return false;
        }

        hasSample = true;
        anchorY = currentY;
        lastPlayerY = currentY;
        velocityAnchorSpeed = horizontalSpeed;
        return true;
    }

    private AmplitudeLeg detectLeg(double currentY) {
        double deltaY = currentY - lastPlayerY;
        if (deltaY < -VERTICAL_EPSILON) {
            return AmplitudeLeg.DESCENDING;
        }
        if (deltaY > VERTICAL_EPSILON) {
            return AmplitudeLeg.ASCENDING;
        }
        return AmplitudeLeg.NONE;
    }

    private void advanceLeg(double currentY, boolean stateMachineMode, float horizontalSpeed) {
        AmplitudeLeg detectedLeg = detectLeg(currentY);
        if (leg == AmplitudeLeg.NONE) {
            beginLeg(detectedLeg == AmplitudeLeg.NONE ? AmplitudeLeg.DESCENDING : detectedLeg, lastPlayerY,
                    horizontalSpeed);
        } else if (shouldFollowDetectedLeg(stateMachineMode, detectedLeg)) {
            beginLeg(detectedLeg, lastPlayerY, horizontalSpeed);
        }
    }

    private TriggerCue calculateCue(Config config, double currentY, float horizontalSpeed) {
        TriggerCue heightCue = heightCue(config, currentY);
        TriggerCue velocityCue = velocityCue(config, horizontalSpeed);
        return new TriggerCue(selectedCueAmount(config, heightCue.amount(), velocityCue.amount()),
                selectedTriggerReached(config, heightCue.triggered(), velocityCue.triggered()));
    }

    private AmplitudeCue applyCue(TriggerCue cue, double currentY, float horizontalSpeed, boolean stateMachineMode) {
        AmplitudeLeg cueLeg = leg;
        float cueAmount = cue.amount();
        if (cue.triggered()) {
            flashCurrentLeg();
            hasTriggeredCue = true;
            if (stateMachineMode) {
                beginLeg(oppositeLeg(leg), currentY, horizontalSpeed);
                cueLeg = leg;
                cueAmount = 0.0f;
            }
        }

        return new AmplitudeCue(cueLeg, cueAmount, flashLeg, computeFlash());
    }

    private void flashCurrentLeg() {
        flashLeg = leg;
        flashStartedMillis = System.currentTimeMillis();
    }

    private TriggerCue heightCue(Config config, double currentY) {
        if (!usesHeightTrigger(config)) {
            return TriggerCue.NONE;
        }

        float target = leg == AmplitudeLeg.DESCENDING ? config.amplitude.downBlocks : config.amplitude.upBlocks;
        float distance = (float) (leg == AmplitudeLeg.DESCENDING ? anchorY - currentY : currentY - anchorY);
        distance = Math.max(0.0f, distance);

        float fullCueDistance = Math.max(1.0f, target - config.amplitude.toleranceBlocks);
        float progress = MathUtil.clamp(distance / fullCueDistance, 0.0f, 1.0f);
        boolean triggered = distance >= target && (!hasPreviousDistance || previousDistance < target);
        previousDistance = distance;
        hasPreviousDistance = true;
        return new TriggerCue(cueFromProgress(progress), triggered);
    }

    private TriggerCue velocityCue(Config config, float horizontalSpeed) {
        if (!usesVelocityTrigger(config)) {
            return TriggerCue.NONE;
        }

        float cueAmount;
        boolean triggered;
        if (leg == AmplitudeLeg.DESCENDING) {
            float target = config.amplitude.downVelocity;
            float progress = MathUtil.clamp(horizontalSpeed / target, 0.0f, 1.0f);
            cueAmount = cueFromProgress(progress);
            triggered = horizontalSpeed >= target && (!hasPreviousHorizontalSpeed || previousHorizontalSpeed < target);
        } else {
            if (horizontalSpeed > velocityAnchorSpeed) {
                velocityAnchorSpeed = horizontalSpeed;
            }
            float target = config.amplitude.upVelocity;
            if (velocityAnchorSpeed < target + MIN_VELOCITY_CUE_SPAN) {
                cueAmount = 0.0f;
                triggered = false;
            } else {
                float progress = MathUtil.clamp((velocityAnchorSpeed - horizontalSpeed)
                        / (velocityAnchorSpeed - target), 0.0f, 1.0f);
                cueAmount = cueFromProgress(progress);
                triggered = horizontalSpeed <= target
                        && (!hasPreviousHorizontalSpeed || previousHorizontalSpeed > target);
            }
        }

        previousHorizontalSpeed = horizontalSpeed;
        hasPreviousHorizontalSpeed = true;
        return new TriggerCue(cueAmount, triggered);
    }

    private void beginLeg(AmplitudeLeg nextLeg, double nextAnchorY, float anchorHorizontalSpeed) {
        leg = nextLeg;
        anchorY = nextAnchorY;
        velocityAnchorSpeed = anchorHorizontalSpeed;
        previousDistance = 0.0f;
        hasPreviousDistance = false;
        previousHorizontalSpeed = anchorHorizontalSpeed;
        hasPreviousHorizontalSpeed = false;
    }

    private static boolean usesHeightTrigger(Config config) {
        return config.amplitude.triggerMode == Config.AMPLITUDE_TRIGGER_HEIGHT
                || config.amplitude.triggerMode == Config.AMPLITUDE_TRIGGER_EITHER;
    }

    private static boolean usesVelocityTrigger(Config config) {
        return config.amplitude.triggerMode == Config.AMPLITUDE_TRIGGER_VELOCITY
                || config.amplitude.triggerMode == Config.AMPLITUDE_TRIGGER_EITHER;
    }

    private static boolean usesStateMachine(Config config) {
        return config.amplitude.triggerMode != Config.AMPLITUDE_TRIGGER_HEIGHT;
    }

    private boolean shouldFollowDetectedLeg(boolean stateMachineMode, AmplitudeLeg detectedLeg) {
        if (detectedLeg == AmplitudeLeg.NONE || detectedLeg == leg) {
            return false;
        }
        return !stateMachineMode || !hasTriggeredCue;
    }

    private static boolean selectedTriggerReached(Config config, boolean heightTriggered, boolean velocityTriggered) {
        if (config.amplitude.triggerMode == Config.AMPLITUDE_TRIGGER_HEIGHT) {
            return heightTriggered;
        }
        if (config.amplitude.triggerMode == Config.AMPLITUDE_TRIGGER_VELOCITY) {
            return velocityTriggered;
        }
        return heightTriggered || velocityTriggered;
    }

    private static float selectedCueAmount(Config config, float heightCue, float velocityCue) {
        if (config.amplitude.triggerMode == Config.AMPLITUDE_TRIGGER_HEIGHT) {
            return heightCue;
        }
        if (config.amplitude.triggerMode == Config.AMPLITUDE_TRIGGER_VELOCITY) {
            return velocityCue;
        }
        return Math.max(heightCue, velocityCue);
    }

    private static float cueFromProgress(float progress) {
        return MathUtil.smoothstep(MathUtil.clamp((progress - CUE_START_RATIO) / (1.0f - CUE_START_RATIO), 0.0f,
                1.0f));
    }

    private float computeFlash() {
        float flash = 0.0f;
        long flashAge = System.currentTimeMillis() - flashStartedMillis;
        if (flashAge >= 0L && flashAge < FLASH_MILLIS) {
            float normalizedAge = flashAge / (float) FLASH_MILLIS;
            flash = 1.0f - MathUtil.smoothstep(normalizedAge);
        } else if (flashAge >= FLASH_MILLIS) {
            flashLeg = AmplitudeLeg.NONE;
        }
        return flash;
    }

    private static AmplitudeLeg oppositeLeg(AmplitudeLeg currentLeg) {
        return currentLeg == AmplitudeLeg.DESCENDING ? AmplitudeLeg.ASCENDING : AmplitudeLeg.DESCENDING;
    }

    private record TriggerCue(float amount, boolean triggered) {
        private static final TriggerCue NONE = new TriggerCue(0.0f, false);
    }
}
