package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.ConfigTestFixtures;
import com.asphyxiamywife.elytrapitchhelper.config.VoidWarningSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class VoidProximityTrackerTest {
    private static final float EPSILON = 0.0001f;

    @Test
    void shortLookaheadPulsesAtEntryAndOneSecondWithoutRepeatingSpentThresholds() {
        Config config = voidConfig();
        ConfigTestFixtures.updateActiveProfile(config,
                p -> p.withVoidWarning(p.voidWarning().withLookaheadSeconds(2.0f)));
        MutableClock clock = new MutableClock();
        VoidProximityTracker tracker = new VoidProximityTracker(clock::now);
        tracker.update(config, 60.0, -1.0, 0);
        assertEquals(1.0f, tracker.update(config, 40.0, -1.0, 0).pulse, EPSILON);
        clock.advance(260L);
        assertEquals(0.5f, tracker.update(config, 30.0, -1.0, 0).pulse, EPSILON);
        clock.advance(260L);
        assertEquals(1.0f, tracker.update(config, 20.0, -1.0, 0).pulse, EPSILON);
        clock.advance(520L);
        assertEquals(0.0f, tracker.update(config, 10.0, -1.0, 0).pulse, EPSILON);
        tracker.update(config, 60.0, -1.0, 0);
        assertEquals(1.0f, tracker.update(config, 40.0, -1.0, 0).pulse, EPSILON);
    }

    @Test
    void skippingSeveralThresholdsSpendsThemTogether() {
        Config config = voidConfig();
        MutableClock clock = new MutableClock();
        VoidProximityTracker tracker = new VoidProximityTracker(clock::now);
        tracker.update(config, 120.0, -1.0, 0);
        assertEquals(1.0f, tracker.update(config, 10.0, -1.0, 0).pulse, EPSILON);
        clock.advance(520L);
        assertEquals(0.0f, tracker.update(config, 9.0, -1.0, 0).pulse, EPSILON);
    }

    @Test
    void overflowingContactTimeDoesNotFirePulses() {
        Config config = voidConfig();
        VoidProximityTracker tracker = new VoidProximityTracker(() -> 0L);
        tracker.update(config, 100.0, -Float.MIN_VALUE, 0);
        assertEquals(0.0f, tracker.update(config, 100.0, -Float.MIN_VALUE, 0).pulse, EPSILON);
        assertEquals(0.0f, tracker.update(config, -100.0, -Float.MIN_VALUE, 0).pulse, EPSILON);
    }

    @Test
    void flatGlideNoWarning() {
        Config config = voidConfig();
        VoidProximityTracker tracker = new VoidProximityTracker();
        tracker.update(config, 100.0, 0.0, 0);
        assertSame(VoidProximity.NONE, tracker.update(config, 100.0, 0.0, 0));
    }

    @Test
    void descendingFarAwayNoWarning() {
        Config config = voidConfig();
        VoidProximityTracker tracker = new VoidProximityTracker();
        tracker.update(config, 1000.0, -0.5, 0);
        assertSame(VoidProximity.NONE, tracker.update(config, 999.5, -0.5, 0));
    }

    @Test
    void descendingWithinLookaheadUsesBlocksPerSecondPrediction() {
        Config config = voidConfig();
        VoidProximityTracker tracker = new VoidProximityTracker();
        tracker.update(config, 100.0, -1.0, 0);

        VoidProximity result = tracker.update(config, 80.0, -1.0, 0);

        assertTrue(result.warning > 0.0f, "warning should use predicted seconds to contact");
        assertTrue(tracker.isWarningRelevant(), "warning should be relevant inside lookahead");
    }

    @Test
    void descendingCloseWarningRamps() {
        Config config = voidConfig();
        VoidProximityTracker tracker = new VoidProximityTracker();
        tracker.update(config, 4.0, -1.0, 0);
        VoidProximity result = tracker.update(config, 3.0, -1.0, 0);
        assertTrue(result.warning > 0.0f, "warning should be > 0 when contact is inside lookahead");
    }

    @Test
    void disabledReturnsNone() {
        Config config = voidConfig();
        ConfigTestFixtures.updateActiveProfile(config, p -> p.withVoidWarning(p.voidWarning().withEnabled(false)));
        VoidProximityTracker tracker = new VoidProximityTracker();
        tracker.update(config, 5.0, -1.0, 0);
        assertSame(VoidProximity.NONE, tracker.update(config, 4.0, -1.0, 0));
    }

    @Test
    void simpleHeightModeWarnsImmediatelyInsideBlockThreshold() {
        Config config = voidConfig();
        ConfigTestFixtures.updateActiveProfile(config, p -> p.withVoidWarning(p.voidWarning().withMode(VoidWarningSettings.MODE_SIMPLE_HEIGHT)));
        ConfigTestFixtures.updateActiveProfile(config, p -> p.withVoidWarning(p.voidWarning().withSimpleWarningBlocks(50)));
        VoidProximityTracker tracker = new VoidProximityTracker();

        VoidProximity result = tracker.update(config, 25.0, 0.0, 0);

        assertTrue(result.warning > 0.0f, "simple mode should warn from height without waiting for velocity samples");
        assertTrue(tracker.isWarningRelevant(), "simple mode should become relevant inside the configured height");
    }

    @Test
    void simpleHeightModeIgnoresVerticalVelocity() {
        Config config = voidConfig();
        ConfigTestFixtures.updateActiveProfile(config, p -> p.withVoidWarning(p.voidWarning().withMode(VoidWarningSettings.MODE_SIMPLE_HEIGHT)));
        ConfigTestFixtures.updateActiveProfile(config, p -> p.withVoidWarning(p.voidWarning().withSimpleWarningBlocks(20)));
        VoidProximityTracker tracker = new VoidProximityTracker();

        VoidProximity result = tracker.update(config, 5.0, 1.0, 0);

        assertTrue(result.warning > 0.0f, "simple mode should warn based on height even while moving upward");
    }

    @Test
    void simpleHeightModeStaysInactiveAboveBlockThreshold() {
        Config config = voidConfig();
        ConfigTestFixtures.updateActiveProfile(config, p -> p.withVoidWarning(p.voidWarning().withMode(VoidWarningSettings.MODE_SIMPLE_HEIGHT)));
        ConfigTestFixtures.updateActiveProfile(config, p -> p.withVoidWarning(p.voidWarning().withSimpleWarningBlocks(20)));
        VoidProximityTracker tracker = new VoidProximityTracker();

        VoidProximity result = tracker.update(config, 30.0, -4.0, 0);

        assertSame(VoidProximity.NONE, result);
        assertFalse(tracker.isWarningRelevant(), "simple mode should ignore velocity when height is safe");
    }

    @Test
    void intensityDecaysSlowlyOnClimbback() {
        Config config = voidConfig();
        VoidProximityTracker tracker = new VoidProximityTracker();
        tracker.update(config, 2.0, -1.0, 0);
        tracker.update(config, 1.0, -1.0, 0);
        VoidProximity after = tracker.update(config, 2.0, 0.5, 0);
        assertTrue(after.warning > 0.0f, "intensity should decay slowly, not snap to 0");
    }

    @Test
    void relevanceClearsBeforeFadeCompletes() {
        Config config = voidConfig();
        VoidProximityTracker tracker = new VoidProximityTracker();
        tracker.update(config, 2.0, -1.0, 0);
        tracker.update(config, 1.0, -1.0, 0);

        VoidProximity after = VoidProximity.NONE;
        for (int i = 0; i < 4; i++) {
            after = tracker.update(config, 2.0 + i, 0.5, 0);
        }

        assertTrue(after.isActive(), "warning should still be fading");
        assertFalse(tracker.isWarningRelevant(), "terrain scan should not run after relevance clears");
    }

    @Test
    void resetClearsState() {
        Config config = voidConfig();
        VoidProximityTracker tracker = new VoidProximityTracker();
        tracker.update(config, 2.0, -1.0, 0);
        tracker.update(config, 1.0, -1.0, 0);
        tracker.reset();
        assertSame(VoidProximity.NONE, tracker.update(config, 1.0, -1.0, 0));
    }

    @Test
    void pulseFadesAccordingToInjectedClock() {
        Config config = voidConfig();
        MutableClock clock = new MutableClock();
        VoidProximityTracker tracker = new VoidProximityTracker(clock::now);
        tracker.update(config, 100.0, -1.0, 0);

        VoidProximity started = tracker.update(config, 80.0, -1.0, 0);
        assertEquals(1.0f, started.pulse, EPSILON);

        clock.advance(260L);
        VoidProximity halfway = tracker.update(config, 80.0, -1.0, 0);
        assertEquals(0.5f, halfway.pulse, EPSILON);

        clock.advance(260L);
        VoidProximity expired = tracker.update(config, 80.0, -1.0, 0);
        assertEquals(0.0f, expired.pulse, EPSILON);
    }

    @Test
    void pulseDoesNotStartBeforeConfiguredLookaheadWindow() {
        Config config = voidConfig();
        ConfigTestFixtures.updateActiveProfile(config,
                p -> p.withVoidWarning(p.voidWarning().withLookaheadSeconds(2.0f)));
        VoidProximityTracker tracker = new VoidProximityTracker(() -> 0L);
        tracker.update(config, 100.0, -1.0, 0);

        VoidProximity outsideWindow = tracker.update(config, 80.0, -1.0, 0);

        assertEquals(0.0f, outsideWindow.pulse, EPSILON);
        assertEquals(0.0f, tracker.pulseStrength().value(), EPSILON);
        assertFalse(tracker.isWarningRelevant());
    }

    @Test
    void zeroLookaheadIsSanitizedBeforeTracking() {
        Config config = voidConfig();
        ConfigTestFixtures.updateActiveProfile(config,
                p -> p.withVoidWarning(p.voidWarning().withLookaheadSeconds(0.0f)));

        assertTrue(config.voidWarning().lookaheadSeconds() > 0.0f);
    }

    @Test
    void pulseIsNotRetriggeredAfterPassingVoidLine() {
        Config config = voidConfig();
        MutableClock clock = new MutableClock();
        VoidProximityTracker tracker = new VoidProximityTracker(clock::now);
        tracker.update(config, 1.0, -1.0, 0);
        tracker.update(config, 0.5, -1.0, 0);
        clock.advance(520L);

        VoidProximity belowVoid = tracker.update(config, -1.0, -1.0, 0);

        assertEquals(0.0f, belowVoid.pulse, EPSILON);
    }

    @Test
    void aNonFiniteVerticalSpeedDoesNotPoisonTheFilter() {
        Config config = voidConfig();
        VoidProximityTracker tracker = new VoidProximityTracker();
        tracker.update(config, 100.0, -1.0, 0);

        assertSame(VoidProximity.NONE, tracker.update(config, 90.0, Double.NaN, 0));
        assertSame(VoidProximity.NONE, tracker.update(config, 85.0, Double.POSITIVE_INFINITY, 0));

        VoidProximity recovered = tracker.update(config, 80.0, -1.0, 0);

        assertTrue(recovered.warning > 0.0f, "the warning must re-engage after a non-finite sample");
        assertTrue(tracker.isWarningRelevant());
    }

    @Test
    void suppressingForTerrainKeepsTheDescentFilterWarm() {
        Config config = voidConfig();
        VoidProximityTracker suppressed = new VoidProximityTracker();
        VoidProximityTracker restarted = new VoidProximityTracker();
        for (VoidProximityTracker tracker : new VoidProximityTracker[] {suppressed, restarted}) {
            tracker.update(config, 100.0, -1.0, 0);
            tracker.update(config, 80.0, -1.0, 0);
        }

        suppressed.suppress();
        restarted.reset();

        assertTrue(suppressed.update(config, 79.0, -1.0, 0).warning > 0.0f,
                "the warning re-engages on the first tick without ground");
        assertSame(VoidProximity.NONE, restarted.update(config, 79.0, -1.0, 0),
                "a full reset spends that tick priming the filter instead");
    }

    @Test
    void suppressingClearsEverythingTheWarningIsDrawnFrom() {
        Config config = voidConfig();
        VoidProximityTracker tracker = new VoidProximityTracker();
        tracker.update(config, 100.0, -1.0, 0);
        assertTrue(tracker.update(config, 80.0, -1.0, 0).isActive());

        tracker.suppress();

        assertFalse(tracker.isWarningRelevant());
        assertEquals(0.0f, tracker.pulseStrength().value(), EPSILON);
    }

    @Test
    void suppressingDoesNotSpendThePulseThresholds() {
        Config config = voidConfig();
        MutableClock clock = new MutableClock();
        VoidProximityTracker tracker = new VoidProximityTracker(clock::now);
        tracker.update(config, 100.0, -1.0, 0);
        assertEquals(1.0f, tracker.update(config, 80.0, -1.0, 0).pulse, EPSILON);

        tracker.suppress();
        clock.advance(520L);

        assertEquals(1.0f, tracker.update(config, 79.0, -1.0, 0).pulse, EPSILON,
                "a threshold crossed while the terrain hid the warning has to be able to fire again");
    }

    private static Config voidConfig() {
        Config config = new Config();
        ConfigTestFixtures.updateActiveProfile(config, p -> p.withVoidWarning(p.voidWarning().withEnabled(true)));
        ConfigTestFixtures.updateActiveProfile(config, p -> p.withVoidWarning(p.voidWarning().withLookaheadSeconds(5.0f)));
        return config;
    }

    private static final class MutableClock {
        private long millis;

        long now() {
            return millis;
        }

        void advance(long elapsedMillis) {
            millis += elapsedMillis;
        }
    }
}
