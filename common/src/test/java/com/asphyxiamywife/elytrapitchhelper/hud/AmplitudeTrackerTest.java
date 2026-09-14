package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.ConfigTestFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AmplitudeTrackerTest {
    private static final float EPSILON = 0.0001f;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {
            Config.AMPLITUDE_TRIGGER_VELOCITY, Config.AMPLITUDE_TRIGGER_EITHER})
    void startingBeyondDiveTargetWaitsForAnActualCrossing(int mode) {
        Config config = amplitudeConfig(mode);
        AmplitudeTracker tracker = new AmplitudeTracker();
        tracker.update(config, 100.0, 2.5);
        assertEquals(AmplitudeLeg.NONE, tracker.update(config, 99.0, 2.6).flashLeg);
        assertEquals(AmplitudeLeg.NONE, tracker.update(config, 98.0, 2.0).flashLeg);
        assertEquals(AmplitudeLeg.NONE, tracker.update(config, 97.0, 1.9).flashLeg);
        assertEquals(AmplitudeLeg.DESCENDING, tracker.update(config, 96.0, 2.0).flashLeg);
    }

    @Test
    void startingBelowClimbTargetWaitsForSpeedGainAndCrossing() {
        Config config = amplitudeConfig(Config.AMPLITUDE_TRIGGER_VELOCITY);
        AmplitudeTracker tracker = new AmplitudeTracker();
        tracker.update(config, 100.0, 0.1);
        assertEquals(AmplitudeLeg.NONE, tracker.update(config, 101.0, 0.1).flashLeg);
        assertEquals(AmplitudeLeg.NONE, tracker.update(config, 102.0, 0.5).flashLeg);
        assertEquals(AmplitudeLeg.ASCENDING, tracker.update(config, 103.0, 0.2).flashLeg);
    }

    @Test
    void firstDetectedLegPreservesCrossingsBetweenTheFirstTwoSamples() {
        Config config = amplitudeConfig(Config.AMPLITUDE_TRIGGER_VELOCITY);
        AmplitudeTracker dive = new AmplitudeTracker();
        dive.update(config, 100.0, 1.9);
        assertEquals(AmplitudeLeg.DESCENDING, dive.update(config, 99.0, 2.0).flashLeg);

        AmplitudeTracker climb = new AmplitudeTracker();
        climb.update(config, 100.0, 0.5);
        assertEquals(AmplitudeLeg.ASCENDING, climb.update(config, 101.0, 0.2).flashLeg);
    }

    @Test
    void detectedTurnPreservesARealCrossingButDoesNotInventOne() {
        Config config = amplitudeConfig(Config.AMPLITUDE_TRIGGER_VELOCITY);
        AmplitudeTracker tracker = new AmplitudeTracker();
        tracker.update(config, 100.0, 2.5);
        tracker.update(config, 101.0, 2.5);
        assertEquals(AmplitudeLeg.NONE, tracker.update(config, 100.0, 2.5).flashLeg);
        assertEquals(AmplitudeLeg.ASCENDING, tracker.update(config, 101.0, 0.2).flashLeg);
        assertEquals(AmplitudeLeg.DESCENDING, tracker.update(config, 100.0, 2.0).flashLeg);
    }

    @Test
    void heightCueBuildsBeforeTriggerAndFlashesOnCrossing() {
        Config config = amplitudeConfig(Config.AMPLITUDE_TRIGGER_HEIGHT);
        AmplitudeTracker tracker = new AmplitudeTracker();

        assertSame(AmplitudeCue.NONE, tracker.update(config, 100.0, 0.8));

        AmplitudeCue earlyDive = tracker.update(config, 95.0, 0.9);
        assertEquals(AmplitudeLeg.DESCENDING, earlyDive.leg);
        assertEquals(0.0f, earlyDive.amount, EPSILON);
        assertEquals(AmplitudeLeg.NONE, earlyDive.flashLeg);

        AmplitudeCue readyDive = tracker.update(config, 87.0, 1.0);
        assertEquals(AmplitudeLeg.DESCENDING, readyDive.leg);
        assertEquals(1.0f, readyDive.amount, EPSILON);
        assertEquals(AmplitudeLeg.NONE, readyDive.flashLeg);

        AmplitudeCue triggeredDive = tracker.update(config, 85.0, 1.0);
        assertEquals(AmplitudeLeg.DESCENDING, triggeredDive.leg);
        assertEquals(1.0f, triggeredDive.amount, EPSILON);
        assertEquals(AmplitudeLeg.DESCENDING, triggeredDive.flashLeg);
        assertTrue(tracker.flashStrength().value() > 0.0f);

        AmplitudeCue afterCrossing = tracker.update(config, 84.0, 1.0);
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
        assertTrue(tracker.flashStrength().value() > 0.0f);
        assertEquals(AmplitudeLeg.DESCENDING, tracker.motionGlyphCue().afterLeg());

        AmplitudeCue climbTriggered = tracker.update(config, 98.5, 0.2);
        assertEquals(AmplitudeLeg.DESCENDING, climbTriggered.leg);
        assertEquals(0.0f, climbTriggered.amount, EPSILON);
        assertEquals(AmplitudeLeg.ASCENDING, climbTriggered.flashLeg);
        assertTrue(tracker.flashStrength().value() > 0.0f);
        assertEquals(AmplitudeLeg.ASCENDING, tracker.motionGlyphCue().afterLeg());
    }

    @Test
    void motionGlyphUsesTheConfiguredTargetForEachLeg() {
        Config config = amplitudeConfig(Config.AMPLITUDE_TRIGGER_VELOCITY);
        ConfigTestFixtures.updateActiveProfile(config, profile -> profile.withAmplitude(
                profile.amplitude()
                        .withPullUpGlyphMillis(1_000)
                        .withReleaseDownGlyphMillis(1_200)));
        MutableClock clock = new MutableClock();
        AmplitudeTracker tracker = new AmplitudeTracker(clock::now);

        tracker.update(config, 100.0, 0.5);
        tracker.update(config, 99.0, 1.5);
        tracker.update(config, 98.0, 2.0);
        clock.advance(500L);
        assertEquals(0.5f, tracker.motionGlyphCue().progress().value(), EPSILON);

        tracker.update(config, 98.5, 0.2);
        clock.advance(600L);
        assertEquals(0.5f, tracker.motionGlyphCue().progress().value(), EPSILON);
    }

    @Test
    void motionGlyphOnlyStartsWhenItsGuideIsVisibleAtTheCueCrossing() {
        Config config = amplitudeConfig(Config.AMPLITUDE_TRIGGER_VELOCITY);
        AmplitudeTracker tracker = new AmplitudeTracker();

        tracker.update(config, 100.0, 0.5, false, true);
        tracker.update(config, 99.0, 1.5, false, true);
        AmplitudeCue diveCue = tracker.update(config, 98.0, 2.0, false, true);

        assertEquals(AmplitudeLeg.DESCENDING, diveCue.flashLeg,
                "the amplitude cue itself still triggers");
        assertEquals(AmplitudeLeg.NONE, diveCue.glyphStartedLeg,
                "telemetry must see that visibility suppressed the glyph");
        assertEquals(AmplitudeLeg.NONE, tracker.motionGlyphCue().afterLeg(),
                "an off-screen guide must not produce a detached glyph");

        tracker.update(config, 98.25, 1.0, true, true);
        assertEquals(AmplitudeLeg.NONE, tracker.motionGlyphCue().afterLeg(),
                "becoming visible after the crossing must not start the skipped glyph late");

        AmplitudeCue climbCue = tracker.update(config, 98.5, 0.2, true, true);
        assertEquals(AmplitudeLeg.ASCENDING, climbCue.glyphStartedLeg);
        assertEquals(AmplitudeLeg.ASCENDING, tracker.motionGlyphCue().afterLeg(),
                "a later cue whose guide is visible still starts normally");
    }

    @Test
    void skippedGlyphClearsThePreviousLegsGlyph() {
        Config config = amplitudeConfig(Config.AMPLITUDE_TRIGGER_VELOCITY);
        AmplitudeTracker tracker = new AmplitudeTracker();

        tracker.update(config, 100.0, 0.5, true, false);
        tracker.update(config, 99.0, 1.5, true, false);
        tracker.update(config, 98.0, 2.0, true, false);
        assertEquals(AmplitudeLeg.DESCENDING, tracker.motionGlyphCue().afterLeg());

        tracker.update(config, 98.5, 0.2, true, false);
        assertEquals(AmplitudeLeg.NONE, tracker.motionGlyphCue().afterLeg(),
                "an invisible next-leg cue must not leave the opposite instruction running");
    }

    @Test
    void startedMotionGlyphKeepsRunningAfterItsGuideLeavesView() {
        Config config = amplitudeConfig(Config.AMPLITUDE_TRIGGER_VELOCITY);
        MutableClock clock = new MutableClock();
        AmplitudeTracker tracker = new AmplitudeTracker(clock::now);

        tracker.update(config, 100.0, 0.5, true, true);
        tracker.update(config, 99.0, 1.5, true, true);
        tracker.update(config, 98.0, 2.0, true, true);
        clock.advance(350L);
        tracker.update(config, 98.1, 1.5, false, false);

        assertEquals(AmplitudeLeg.DESCENDING, tracker.motionGlyphCue().afterLeg());
        assertTrue(tracker.motionGlyphCue().progressNow() > 0.0f,
                "visibility is only a start gate, not a continuing render condition");
    }

    @Test
    void velocityModeAcceptsAnotherDiveCueAfterPreviousDiveFlashEnds() {
        Config config = amplitudeConfig(Config.AMPLITUDE_TRIGGER_VELOCITY);
        MutableClock clock = new MutableClock();
        AmplitudeTracker tracker = new AmplitudeTracker(clock::now);

        assertSame(AmplitudeCue.NONE, tracker.update(config, 100.0, 0.5));
        tracker.update(config, 99.0, 1.5);

        AmplitudeCue firstDiveCue = tracker.update(config, 98.0, 2.0);
        assertEquals(AmplitudeLeg.ASCENDING, firstDiveCue.leg);
        assertEquals(AmplitudeLeg.DESCENDING, firstDiveCue.flashLeg);
        assertTrue(tracker.flashStrength().value() > 0.0f);

        clock.advance(521L);

        AmplitudeCue expiredCue = tracker.update(config, 97.5, 1.8);
        assertEquals(AmplitudeLeg.DESCENDING, expiredCue.leg);
        assertEquals(AmplitudeLeg.NONE, expiredCue.flashLeg);
        assertEquals(0.0f, tracker.flashStrength().value(), EPSILON);

        AmplitudeCue secondDiveCue = tracker.update(config, 97.0, 2.1);
        assertEquals(AmplitudeLeg.ASCENDING, secondDiveCue.leg);
        assertEquals(AmplitudeLeg.DESCENDING, secondDiveCue.flashLeg);
        assertTrue(tracker.flashStrength().value() > 0.0f);
    }

    @Test
    void velocityModeCanKeepDiveCueLockedOutAfterPreviousDiveFlashEnds() {
        Config config = amplitudeConfig(Config.AMPLITUDE_TRIGGER_VELOCITY);
        ConfigTestFixtures.updateActiveProfile(config, p -> p.withAmplitude(p.amplitude().withRepeatDiveCue(false)));
        MutableClock clock = new MutableClock();
        AmplitudeTracker tracker = new AmplitudeTracker(clock::now);

        assertSame(AmplitudeCue.NONE, tracker.update(config, 100.0, 0.5));
        tracker.update(config, 99.0, 1.5);
        AmplitudeCue firstDiveCue = tracker.update(config, 98.0, 2.0);
        assertEquals(AmplitudeLeg.DESCENDING, firstDiveCue.flashLeg);

        clock.advance(521L);

        AmplitudeCue expiredCue = tracker.update(config, 97.5, 1.8);
        assertEquals(AmplitudeLeg.ASCENDING, expiredCue.leg);
        assertEquals(AmplitudeLeg.NONE, expiredCue.flashLeg);

        AmplitudeCue lockedOutDive = tracker.update(config, 97.0, 2.1);
        assertEquals(AmplitudeLeg.ASCENDING, lockedOutDive.leg);
        assertEquals(AmplitudeLeg.NONE, lockedOutDive.flashLeg);
        assertEquals(0.0f, tracker.flashStrength().value(), EPSILON);
    }

    @Test
    void aLockedOutLegIsReleasedOnceTheFlightHasPlainlyGoneTheOtherWay() {
        Config config = amplitudeConfig(Config.AMPLITUDE_TRIGGER_VELOCITY);
        ConfigTestFixtures.updateActiveProfile(config, p -> p.withAmplitude(p.amplitude().withRepeatDiveCue(false)));
        MutableClock clock = new MutableClock();
        AmplitudeTracker tracker = new AmplitudeTracker(clock::now);

        assertSame(AmplitudeCue.NONE, tracker.update(config, 100.0, 0.5));
        tracker.update(config, 99.0, 1.5);
        AmplitudeCue firstDiveCue = tracker.update(config, 98.0, 2.0);
        assertEquals(AmplitudeLeg.DESCENDING, firstDiveCue.flashLeg);
        assertEquals(AmplitudeLeg.ASCENDING, firstDiveCue.leg);

        clock.advance(521L);

        AmplitudeCue partway = tracker.update(config, 92.0, 1.8);
        assertEquals(AmplitudeLeg.ASCENDING, partway.leg);

        AmplitudeCue resynced = tracker.update(config, 87.0, 1.9);
        assertEquals(AmplitudeLeg.DESCENDING, resynced.leg);

        AmplitudeCue diveCueAgain = tracker.update(config, 86.0, 2.1);
        assertEquals(AmplitudeLeg.DESCENDING, diveCueAgain.flashLeg);
        assertTrue(tracker.flashStrength().value() > 0.0f);
    }

    @Test
    void velocityModeAcceptsAnotherClimbCueAfterPreviousClimbFlashEnds() {
        Config config = amplitudeConfig(Config.AMPLITUDE_TRIGGER_VELOCITY);
        MutableClock clock = new MutableClock();
        AmplitudeTracker tracker = new AmplitudeTracker(clock::now);

        assertSame(AmplitudeCue.NONE, tracker.update(config, 100.0, 0.5));
        tracker.update(config, 99.0, 1.5);
        tracker.update(config, 98.0, 2.0);

        AmplitudeCue firstClimbCue = tracker.update(config, 98.5, 0.2);
        assertEquals(AmplitudeLeg.DESCENDING, firstClimbCue.leg);
        assertEquals(AmplitudeLeg.ASCENDING, firstClimbCue.flashLeg);
        assertTrue(tracker.flashStrength().value() > 0.0f);

        clock.advance(521L);

        AmplitudeCue expiredCue = tracker.update(config, 99.0, 0.8);
        assertEquals(AmplitudeLeg.ASCENDING, expiredCue.leg);
        assertEquals(AmplitudeLeg.NONE, expiredCue.flashLeg);
        assertEquals(0.0f, tracker.flashStrength().value(), EPSILON);

        AmplitudeCue secondClimbCue = tracker.update(config, 99.5, 0.19);
        assertEquals(AmplitudeLeg.DESCENDING, secondClimbCue.leg);
        assertEquals(AmplitudeLeg.ASCENDING, secondClimbCue.flashLeg);
        assertTrue(tracker.flashStrength().value() > 0.0f);
    }

    @Test
    void velocityModeCanKeepClimbCueLockedOutAfterPreviousClimbFlashEnds() {
        Config config = amplitudeConfig(Config.AMPLITUDE_TRIGGER_VELOCITY);
        ConfigTestFixtures.updateActiveProfile(config, p -> p.withAmplitude(p.amplitude().withRepeatClimbCue(false)));
        MutableClock clock = new MutableClock();
        AmplitudeTracker tracker = new AmplitudeTracker(clock::now);

        assertSame(AmplitudeCue.NONE, tracker.update(config, 100.0, 0.5));
        tracker.update(config, 99.0, 1.5);
        tracker.update(config, 98.0, 2.0);
        AmplitudeCue firstClimbCue = tracker.update(config, 98.5, 0.2);
        assertEquals(AmplitudeLeg.ASCENDING, firstClimbCue.flashLeg);

        clock.advance(521L);

        AmplitudeCue expiredCue = tracker.update(config, 99.0, 0.8);
        assertEquals(AmplitudeLeg.DESCENDING, expiredCue.leg);
        assertEquals(AmplitudeLeg.NONE, expiredCue.flashLeg);

        AmplitudeCue lockedOutClimb = tracker.update(config, 99.5, 0.19);
        assertEquals(AmplitudeLeg.DESCENDING, lockedOutClimb.leg);
        assertEquals(AmplitudeLeg.NONE, lockedOutClimb.flashLeg);
        assertEquals(0.0f, tracker.flashStrength().value(), EPSILON);
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
        assertTrue(tracker.flashStrength().value() > 0.0f);
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
        assertTrue(tracker.flashStrength().value() > 0.0f);
    }

    @Test
    void disabledHelperResetsTrackerState() {
        Config config = amplitudeConfig(Config.AMPLITUDE_TRIGGER_HEIGHT);
        AmplitudeTracker tracker = new AmplitudeTracker();

        tracker.update(config, 100.0, 1.0);
        tracker.update(config, 90.0, 1.0);
        AmplitudeCue triggered = tracker.update(config, 80.0, 1.0);
        assertEquals(AmplitudeLeg.DESCENDING, triggered.flashLeg);

        ConfigTestFixtures.updateActiveProfile(config, p -> p.withAmplitude(p.amplitude().withEnabled(false)));
        assertSame(AmplitudeCue.NONE, tracker.update(config, 80.0, 1.0));
        assertEquals(AmplitudeLeg.NONE, tracker.motionGlyphCue().afterLeg());

        ConfigTestFixtures.updateActiveProfile(config, p -> p.withAmplitude(p.amplitude().withEnabled(true)));
        assertSame(AmplitudeCue.NONE, tracker.update(config, 70.0, 1.0));
    }

    @Test
    void changingTriggerModeStartsAFreshCycle() {
        Config config = amplitudeConfig(Config.AMPLITUDE_TRIGGER_HEIGHT);
        AmplitudeTracker tracker = new AmplitudeTracker();

        assertSame(AmplitudeCue.NONE, tracker.update(config, 100.0, 0.5));
        tracker.update(config, 95.0, 1.5);

        ConfigTestFixtures.updateActiveProfile(config,
                p -> p.withAmplitude(p.amplitude().withTriggerMode(Config.AMPLITUDE_TRIGGER_VELOCITY)));

        assertSame(AmplitudeCue.NONE, tracker.update(config, 94.0, 3.0),
                "the first sample in a new mode must establish new anchors rather than reuse old state");
        assertEquals(AmplitudeLeg.NONE, tracker.update(config, 93.0, 1.5).flashLeg);
        assertEquals(AmplitudeLeg.DESCENDING, tracker.update(config, 92.0, 2.1).flashLeg,
                "the new mode must trigger from its own freshly observed speed crossing");
    }

    @Test
    void nonFiniteHeightSampleDoesNotConsumeTheNextRealCrossing() {
        Config config = amplitudeConfig(Config.AMPLITUDE_TRIGGER_HEIGHT);
        AmplitudeTracker tracker = new AmplitudeTracker();

        tracker.update(config, 100.0, 1.0);
        tracker.update(config, 95.0, 1.0);
        assertSame(AmplitudeCue.NONE, tracker.update(config, Double.NaN, 1.0));

        AmplitudeCue recovered = tracker.update(config, 85.0, 1.0);
        assertEquals(AmplitudeLeg.DESCENDING, recovered.flashLeg);
        assertTrue(Float.isFinite(recovered.amount));
    }

    @Test
    void nonFiniteSpeedSampleDoesNotConsumeTheNextRealCrossing() {
        Config config = amplitudeConfig(Config.AMPLITUDE_TRIGGER_VELOCITY);
        AmplitudeTracker tracker = new AmplitudeTracker();

        tracker.update(config, 100.0, 0.5);
        tracker.update(config, 99.0, 1.5);
        assertSame(AmplitudeCue.NONE, tracker.update(config, 98.5, Double.POSITIVE_INFINITY));
        assertSame(AmplitudeCue.NONE, tracker.update(config, 98.5, Double.MAX_VALUE),
                "a finite double that overflows float must also be rejected");

        AmplitudeCue recovered = tracker.update(config, 98.0, 2.0);
        assertEquals(AmplitudeLeg.DESCENDING, recovered.flashLeg);
        assertTrue(Float.isFinite(recovered.amount));
    }

    private static Config amplitudeConfig(int triggerMode) {
        Config config = new Config();
        ConfigTestFixtures.updateActiveProfile(config, p -> p.withAmplitude(p.amplitude().withEnabled(true)));
        ConfigTestFixtures.updateActiveProfile(config, p -> p.withAmplitude(p.amplitude().withTriggerMode(triggerMode)));
        ConfigTestFixtures.updateActiveProfile(config, p -> p.withAmplitude(p.amplitude().withDownBlocks(10)));
        ConfigTestFixtures.updateActiveProfile(config, p -> p.withAmplitude(p.amplitude().withUpBlocks(10)));
        ConfigTestFixtures.updateActiveProfile(config, p -> p.withAmplitude(p.amplitude().withToleranceBlocks(2)));
        ConfigTestFixtures.updateActiveProfile(config, p -> p.withAmplitude(p.amplitude().withDownVelocity(2.0f)));
        ConfigTestFixtures.updateActiveProfile(config, p -> p.withAmplitude(p.amplitude().withUpVelocity(0.2f)));
        ConfigTestFixtures.updateActiveProfile(config, p -> p.withAmplitude(p.amplitude()
                .withRepeatDiveCue(true)
                .withRepeatClimbCue(true)));
        return config;
    }

    private static final class MutableClock {
        private long millis = 1_000L;

        long now() {
            return millis;
        }

        void advance(long elapsedMillis) {
            millis += elapsedMillis;
        }
    }
}
