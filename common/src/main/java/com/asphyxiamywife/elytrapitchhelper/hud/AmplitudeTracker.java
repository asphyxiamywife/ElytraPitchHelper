package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;
import com.asphyxiamywife.elytrapitchhelper.util.MonotonicClock;

import java.util.function.LongSupplier;

public final class AmplitudeTracker {
    private static final float VERTICAL_EPSILON = 0.01f;
    private static final float CUE_START_RATIO = 0.72f;
    private static final float MIN_VELOCITY_CUE_SPAN = 0.1f;
    private static final long FLASH_MILLIS = 520L;
    private static final int NO_TRIGGER_MODE = Integer.MIN_VALUE;

    private AmplitudeLeg leg = AmplitudeLeg.NONE;
    private double anchorY;
    private double lastPlayerY;
    private boolean hasSample;
    private float previousDistance;
    private boolean hasPreviousDistance;
    private float previousHorizontalSpeed;
    private float velocityAnchorSpeed;
    private boolean hasTriggeredCue;
    private double desyncAnchorY;
    private int activeTriggerMode = NO_TRIGGER_MODE;
    private AmplitudeLeg flashLeg = AmplitudeLeg.NONE;
    private final DecayingPulse flash;
    private final MotionGlyphTimer motionGlyph;

    public AmplitudeTracker() {
        this(MonotonicClock.MILLIS);
    }

    AmplitudeTracker(LongSupplier currentTimeMillis) {
        this.flash = new DecayingPulse(FLASH_MILLIS, currentTimeMillis);
        this.motionGlyph = new MotionGlyphTimer(currentTimeMillis);
    }

    AmplitudeCue update(Config config, double currentY, double currentHorizontalSpeed) {
        return update(config, currentY, currentHorizontalSpeed, true, true);
    }

    AmplitudeCue update(Config config, double currentY, double currentHorizontalSpeed,
            boolean descendingGuideVisible, boolean ascendingGuideVisible) {
        if (!config.amplitude().enabled()) {
            reset();
            return AmplitudeCue.NONE;
        }
        int triggerMode = config.amplitude().triggerMode();
        if (activeTriggerMode != triggerMode) {
            reset();
            activeTriggerMode = triggerMode;
        }
        if (!Double.isFinite(currentY) || !Double.isFinite(currentHorizontalSpeed)) {
            return AmplitudeCue.NONE;
        }

        float horizontalSpeed = (float) currentHorizontalSpeed;
        if (!Float.isFinite(horizontalSpeed)) {
            return AmplitudeCue.NONE;
        }
        if (captureFirstSampleIfNeeded(currentY, horizontalSpeed)) {
            return AmplitudeCue.NONE;
        }

        boolean stateMachineMode = usesStateMachine(config);
        expireCompletedFlash(config);
        advanceLeg(config, currentY, stateMachineMode, horizontalSpeed);
        lastPlayerY = currentY;
        if (leg == AmplitudeLeg.NONE) {
            return AmplitudeCue.NONE;
        }

        return applyCue(config, calculateCue(config, currentY, horizontalSpeed), currentY,
                horizontalSpeed, stateMachineMode, descendingGuideVisible, ascendingGuideVisible);
    }

    Strength flashStrength() {
        return flash;
    }

    MotionGlyphCue motionGlyphCue() {
        return motionGlyph.cue();
    }

    public void reset() {
        leg = AmplitudeLeg.NONE;
        anchorY = 0.0;
        lastPlayerY = 0.0;
        hasSample = false;
        previousDistance = 0.0f;
        hasPreviousDistance = false;
        previousHorizontalSpeed = 0.0f;
        velocityAnchorSpeed = 0.0f;
        hasTriggeredCue = false;
        desyncAnchorY = 0.0;
        activeTriggerMode = NO_TRIGGER_MODE;
        flashLeg = AmplitudeLeg.NONE;
        flash.reset();
        motionGlyph.reset();
    }

    private boolean captureFirstSampleIfNeeded(double currentY, float horizontalSpeed) {
        if (hasSample) {
            return false;
        }

        hasSample = true;
        anchorY = currentY;
        lastPlayerY = currentY;
        desyncAnchorY = currentY;
        velocityAnchorSpeed = horizontalSpeed;
        previousHorizontalSpeed = horizontalSpeed;
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

    private void advanceLeg(Config config, double currentY, boolean stateMachineMode, float horizontalSpeed) {
        AmplitudeLeg detectedLeg = detectLeg(currentY);
        if (leg == AmplitudeLeg.NONE) {
            beginLeg(detectedLeg == AmplitudeLeg.NONE ? AmplitudeLeg.DESCENDING : detectedLeg, currentY,
                    horizontalSpeed);
        } else if (shouldFollowDetectedLeg(stateMachineMode, detectedLeg)) {
            beginLeg(detectedLeg, currentY, horizontalSpeed);
        } else {
            resyncIfLegAbandoned(config, currentY, detectedLeg, horizontalSpeed);
        }
    }

    private void resyncIfLegAbandoned(Config config, double currentY, AmplitudeLeg detectedLeg,
            float horizontalSpeed) {
        if (detectedLeg == AmplitudeLeg.NONE || detectedLeg == leg) {
            desyncAnchorY = currentY;
            return;
        }
        double travelled = detectedLeg == AmplitudeLeg.DESCENDING
                ? desyncAnchorY - currentY
                : currentY - desyncAnchorY;
        float legDistance = detectedLeg == AmplitudeLeg.DESCENDING
                ? config.amplitude().downBlocks()
                : config.amplitude().upBlocks();
        if (travelled >= Math.max(1.0f, legDistance)) {
            hasTriggeredCue = false;
            beginLeg(detectedLeg, currentY, horizontalSpeed);
        }
    }

    private TriggerCue calculateCue(Config config, double currentY, float horizontalSpeed) {
        TriggerCue heightCue = heightCue(config, currentY);
        TriggerCue velocityCue = velocityCue(config, horizontalSpeed);
        return new TriggerCue(selectedCueAmount(config, heightCue.amount(), velocityCue.amount()),
                selectedTriggerReached(config, heightCue.triggered(), velocityCue.triggered()));
    }

    private AmplitudeCue applyCue(Config config, TriggerCue cue, double currentY, float horizontalSpeed,
            boolean stateMachineMode, boolean descendingGuideVisible,
            boolean ascendingGuideVisible) {
        AmplitudeLeg cueLeg = leg;
        float cueAmount = cue.amount();
        AmplitudeLeg glyphStartedLeg = AmplitudeLeg.NONE;
        if (cue.triggered()) {
            glyphStartedLeg = flashCurrentLeg(config, descendingGuideVisible, ascendingGuideVisible);
            hasTriggeredCue = true;
            if (stateMachineMode) {
                beginLeg(oppositeLeg(leg), currentY, horizontalSpeed);
                cueLeg = leg;
                cueAmount = 0.0f;
            }
        }

        expireCompletedFlash(config);
        return new AmplitudeCue(cueLeg, cueAmount, flashLeg, glyphStartedLeg);
    }

    private AmplitudeLeg flashCurrentLeg(Config config, boolean descendingGuideVisible,
            boolean ascendingGuideVisible) {
        flashLeg = leg;
        flash.trigger();
        boolean guideVisible = leg == AmplitudeLeg.DESCENDING
                ? descendingGuideVisible
                : ascendingGuideVisible;
        if (!guideVisible) {
            motionGlyph.reset();
            return AmplitudeLeg.NONE;
        }
        int targetDuration = leg == AmplitudeLeg.DESCENDING
                ? config.amplitude().pullUpGlyphMillis()
                : config.amplitude().releaseDownGlyphMillis();
        motionGlyph.trigger(leg, targetDuration);
        return leg;
    }

    private TriggerCue heightCue(Config config, double currentY) {
        if (!usesHeightTrigger(config)) {
            return TriggerCue.NONE;
        }

        float target = leg == AmplitudeLeg.DESCENDING ? config.amplitude().downBlocks() : config.amplitude().upBlocks();
        float distance = (float) (leg == AmplitudeLeg.DESCENDING ? anchorY - currentY : currentY - anchorY);
        distance = Math.max(0.0f, distance);

        float fullCueDistance = Math.max(1.0f, target - config.amplitude().toleranceBlocks());
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
            float target = config.amplitude().downVelocity();
            float progress = MathUtil.clamp(horizontalSpeed / target, 0.0f, 1.0f);
            cueAmount = cueFromProgress(progress);
            triggered = horizontalSpeed >= target && previousHorizontalSpeed < target;
        } else {
            if (horizontalSpeed > velocityAnchorSpeed) {
                velocityAnchorSpeed = horizontalSpeed;
            }
            float target = config.amplitude().upVelocity();
            if (velocityAnchorSpeed < target + MIN_VELOCITY_CUE_SPAN) {
                cueAmount = 0.0f;
                triggered = false;
            } else {
                float progress = MathUtil.clamp((velocityAnchorSpeed - horizontalSpeed)
                        / (velocityAnchorSpeed - target), 0.0f, 1.0f);
                cueAmount = cueFromProgress(progress);
                triggered = horizontalSpeed <= target
                        && previousHorizontalSpeed > target;
            }
        }

        previousHorizontalSpeed = horizontalSpeed;
        return new TriggerCue(cueAmount, triggered);
    }

    private void beginLeg(AmplitudeLeg nextLeg, double nextAnchorY, float anchorHorizontalSpeed) {
        leg = nextLeg;
        anchorY = nextAnchorY;
        desyncAnchorY = nextAnchorY;
        velocityAnchorSpeed = Math.max(anchorHorizontalSpeed, previousHorizontalSpeed);
        previousDistance = 0.0f;
        hasPreviousDistance = false;
    }

    private static boolean usesHeightTrigger(Config config) {
        return config.amplitude().triggerMode() == Config.AMPLITUDE_TRIGGER_HEIGHT
                || config.amplitude().triggerMode() == Config.AMPLITUDE_TRIGGER_EITHER;
    }

    private static boolean usesVelocityTrigger(Config config) {
        return config.amplitude().triggerMode() == Config.AMPLITUDE_TRIGGER_VELOCITY
                || config.amplitude().triggerMode() == Config.AMPLITUDE_TRIGGER_EITHER;
    }

    private static boolean usesStateMachine(Config config) {
        return config.amplitude().triggerMode() != Config.AMPLITUDE_TRIGGER_HEIGHT;
    }

    private boolean shouldFollowDetectedLeg(boolean stateMachineMode, AmplitudeLeg detectedLeg) {
        if (detectedLeg == AmplitudeLeg.NONE || detectedLeg == leg) {
            return false;
        }
        return !stateMachineMode || !hasTriggeredCue;
    }

    private static boolean selectedTriggerReached(Config config, boolean heightTriggered, boolean velocityTriggered) {
        if (config.amplitude().triggerMode() == Config.AMPLITUDE_TRIGGER_HEIGHT) {
            return heightTriggered;
        }
        if (config.amplitude().triggerMode() == Config.AMPLITUDE_TRIGGER_VELOCITY) {
            return velocityTriggered;
        }
        return heightTriggered || velocityTriggered;
    }

    private static float selectedCueAmount(Config config, float heightCue, float velocityCue) {
        if (config.amplitude().triggerMode() == Config.AMPLITUDE_TRIGGER_HEIGHT) {
            return heightCue;
        }
        if (config.amplitude().triggerMode() == Config.AMPLITUDE_TRIGGER_VELOCITY) {
            return velocityCue;
        }
        return Math.max(heightCue, velocityCue);
    }

    private static float cueFromProgress(float progress) {
        return MathUtil.smoothstep(MathUtil.clamp((progress - CUE_START_RATIO) / (1.0f - CUE_START_RATIO), 0.0f,
                1.0f));
    }

    private void expireCompletedFlash(Config config) {
        if (flash.expired()) {
            if (allowsRepeatCueAfterFlash(config, flashLeg)) {
                hasTriggeredCue = false;
            }
            flashLeg = AmplitudeLeg.NONE;
        }
    }

    private static boolean allowsRepeatCueAfterFlash(Config config, AmplitudeLeg triggeredLeg) {
        if (triggeredLeg == AmplitudeLeg.DESCENDING) {
            return config.amplitude().repeatDiveCue();
        }
        if (triggeredLeg == AmplitudeLeg.ASCENDING) {
            return config.amplitude().repeatClimbCue();
        }
        return false;
    }

    private static AmplitudeLeg oppositeLeg(AmplitudeLeg currentLeg) {
        return currentLeg == AmplitudeLeg.DESCENDING ? AmplitudeLeg.ASCENDING : AmplitudeLeg.DESCENDING;
    }

    private record TriggerCue(float amount, boolean triggered) {
        private static final TriggerCue NONE = new TriggerCue(0.0f, false);
    }
}
