package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AmplitudeTrackerTest {
    private static final float EPSILON = 0.0001f;

    @Test
    void heightCueBuildsBeforeTriggerAndFlashesOnCrossing() {
        Config config = amplitudeConfig(Config.AMPLITUDE_TRIGGER_HEIGHT);
        AmplitudeTracker tracker = new AmplitudeTracker();

        assertSame(AmplitudeCue.NONE, tracker.update(config, 100.0, 0.8));

        AmplitudeCue earlyDive = tracker.update(config, 95.0, 0.9);
        assertEquals(AmplitudeLeg.DESCENDING, earlyDive.leg);
        assertEquals(0.0f, earlyDive.amount, EPSILON);
        assertEquals(AmplitudeLeg.NONE, earlyDive.flashLeg);

        AmplitudeCue readyDive = tracker.update(config, 92.0, 1.0);
        assertEquals(AmplitudeLeg.DESCENDING, readyDive.leg);
        assertEquals(1.0f, readyDive.amount, EPSILON);
        assertEquals(AmplitudeLeg.NONE, readyDive.flashLeg);

        AmplitudeCue triggeredDive = tracker.update(config, 90.0, 1.0);
        assertEquals(AmplitudeLeg.DESCENDING, triggeredDive.leg);
        assertEquals(1.0f, triggeredDive.amount, EPSILON);
        assertEquals(AmplitudeLeg.DESCENDING, triggeredDive.flashLeg);
        assertTrue(triggeredDive.flash > 0.0f);

        AmplitudeCue afterCrossing = tracker.update(config, 89.0, 1.0);
        assertEquals(AmplitudeLeg.DESCENDING, afterCrossing.leg);
        assertEquals(1.0f, afterCrossing.amount, EPSILON);
    }

    @Test
    void velocityModeArmsOppositeLegAfterTrigger() {
        Config config = amplitudeConfig(Config.AMPLITUDE_TRIGGER_VELOCITY);
        AmplitudeTracker tracker = new AmplitudeTracker();

        assertSame(AmplitudeCue.NONE, tracker.update(config, 100.0, 0.5));

        AmplitudeCue buildingSpeed = tracker.update(config, 99.0, 1.5);
        assertEquals(AmplitudeLeg.DESCENDING, buildingSpeed.leg);
        assertTrue(buildingSpeed.amount > 0.0f);

        AmplitudeCue diveTriggered = tracker.update(config, 98.0, 2.0);
        assertEquals(AmplitudeLeg.ASCENDING, diveTriggered.leg);
        assertEquals(0.0f, diveTriggered.amount, EPSILON);
        assertEquals(AmplitudeLeg.DESCENDING, diveTriggered.flashLeg);
        assertTrue(diveTriggered.flash > 0.0f);

        AmplitudeCue climbTriggered = tracker.update(config, 98.5, 0.2);
        assertEquals(AmplitudeLeg.DESCENDING, climbTriggered.leg);
        assertEquals(0.0f, climbTriggered.amount, EPSILON);
        assertEquals(AmplitudeLeg.ASCENDING, climbTriggered.flashLeg);
        assertTrue(climbTriggered.flash > 0.0f);
    }

    @Test
    void velocityModeTracksFirstAscendingWaveAfterGroundTakeoff() {
        Config config = amplitudeConfig(Config.AMPLITUDE_TRIGGER_VELOCITY);
        AmplitudeTracker tracker = new AmplitudeTracker();

        assertSame(AmplitudeCue.NONE, tracker.update(config, 64.0, 0.1));

        AmplitudeCue launchDip = tracker.update(config, 63.95, 0.4);
        assertEquals(AmplitudeLeg.DESCENDING, launchDip.leg);

        AmplitudeCue firstClimb = tracker.update(config, 64.5, 1.2);
        assertEquals(AmplitudeLeg.ASCENDING, firstClimb.leg);
        assertEquals(AmplitudeLeg.NONE, firstClimb.flashLeg);

        AmplitudeCue firstClimbTriggered = tracker.update(config, 65.0, 0.19);
        assertEquals(AmplitudeLeg.DESCENDING, firstClimbTriggered.leg);
        assertEquals(0.0f, firstClimbTriggered.amount, EPSILON);
        assertEquals(AmplitudeLeg.ASCENDING, firstClimbTriggered.flashLeg);
        assertTrue(firstClimbTriggered.flash > 0.0f);
    }

    @Test
    void velocityModeDoesNotCueTakeoffBeforeFirstClimbGainsSpeed() {
        Config config = amplitudeConfig(Config.AMPLITUDE_TRIGGER_VELOCITY);
        AmplitudeTracker tracker = new AmplitudeTracker();

        assertSame(AmplitudeCue.NONE, tracker.update(config, 64.0, 0.05));

        AmplitudeCue launchDip = tracker.update(config, 63.95, 0.05);
        assertEquals(AmplitudeLeg.DESCENDING, launchDip.leg);

        AmplitudeCue slowTakeoff = tracker.update(config, 64.5, 0.1);
        assertEquals(AmplitudeLeg.ASCENDING, slowTakeoff.leg);
        assertEquals(0.0f, slowTakeoff.amount, EPSILON);
        assertEquals(AmplitudeLeg.NONE, slowTakeoff.flashLeg);

        AmplitudeCue stillSlow = tracker.update(config, 65.0, 0.15);
        assertEquals(AmplitudeLeg.ASCENDING, stillSlow.leg);
        assertEquals(0.0f, stillSlow.amount, EPSILON);
        assertEquals(AmplitudeLeg.NONE, stillSlow.flashLeg);

        AmplitudeCue rocketAccelerated = tracker.update(config, 66.0, 0.8);
        assertEquals(AmplitudeLeg.ASCENDING, rocketAccelerated.leg);
        assertEquals(0.0f, rocketAccelerated.amount, EPSILON);

        AmplitudeCue firstClimbTriggered = tracker.update(config, 67.0, 0.19);
        assertEquals(AmplitudeLeg.DESCENDING, firstClimbTriggered.leg);
        assertEquals(AmplitudeLeg.ASCENDING, firstClimbTriggered.flashLeg);
        assertTrue(firstClimbTriggered.flash > 0.0f);
    }

    @Test
    void disabledHelperResetsTrackerState() {
        Config config = amplitudeConfig(Config.AMPLITUDE_TRIGGER_HEIGHT);
        AmplitudeTracker tracker = new AmplitudeTracker();

        tracker.update(config, 100.0, 1.0);
        AmplitudeCue triggered = tracker.update(config, 90.0, 1.0);
        assertEquals(AmplitudeLeg.DESCENDING, triggered.flashLeg);

        config.amplitude.enabled = false;
        assertSame(AmplitudeCue.NONE, tracker.update(config, 80.0, 1.0));

        config.amplitude.enabled = true;
        assertSame(AmplitudeCue.NONE, tracker.update(config, 70.0, 1.0));
    }

    private static Config amplitudeConfig(int triggerMode) {
        Config config = new Config();
        config.amplitude.enabled = true;
        config.amplitude.triggerMode = triggerMode;
        config.amplitude.downBlocks = 10;
        config.amplitude.upBlocks = 10;
        config.amplitude.toleranceBlocks = 2;
        config.amplitude.downVelocity = 2.0f;
        config.amplitude.upVelocity = 0.2f;
        return config;
    }
}
