package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.VoidWarningSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class VoidProximityTrackerTest {

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
        // timeToContact ≈ 999.5 / (0.5 * 20) = 99.95s >> 5s lookahead
        assertSame(VoidProximity.NONE, tracker.update(config, 999.5, -0.5, 0));
    }

    @Test
    void descendingWithinLookaheadUsesBlocksPerSecondPrediction() {
        Config config = voidConfig();
        VoidProximityTracker tracker = new VoidProximityTracker();
        tracker.update(config, 100.0, -1.0, 0);

        VoidProximity result = tracker.update(config, 80.0, -1.0, 0);

        // 80 blocks at -1 block/tick is 4s, so a 5s lookahead should already warn.
        assertTrue(result.warning > 0.0f, "warning should use predicted seconds to contact");
        assertTrue(tracker.isWarningRelevant(), "warning should be relevant inside lookahead");
    }

    @Test
    void descendingCloseWarningRamps() {
        Config config = voidConfig();
        VoidProximityTracker tracker = new VoidProximityTracker();
        tracker.update(config, 4.0, -1.0, 0);
        VoidProximity result = tracker.update(config, 3.0, -1.0, 0);
        // timeToContact ≈ 0.15s < 5s lookahead
        assertTrue(result.warning > 0.0f, "warning should be > 0 when contact is inside lookahead");
    }

    @Test
    void disabledReturnsNone() {
        Config config = voidConfig();
        config.voidWarning.enabled = false;
        VoidProximityTracker tracker = new VoidProximityTracker();
        tracker.update(config, 5.0, -1.0, 0);
        assertSame(VoidProximity.NONE, tracker.update(config, 4.0, -1.0, 0));
    }

    @Test
    void simpleHeightModeWarnsImmediatelyInsideBlockThreshold() {
        Config config = voidConfig();
        config.voidWarning.mode = VoidWarningSettings.MODE_SIMPLE_HEIGHT;
        config.voidWarning.simpleWarningBlocks = 50;
        VoidProximityTracker tracker = new VoidProximityTracker();

        VoidProximity result = tracker.update(config, 25.0, 0.0, 0);

        assertTrue(result.warning > 0.0f, "simple mode should warn from height without waiting for velocity samples");
        assertTrue(tracker.isWarningRelevant(), "simple mode should become relevant inside the configured height");
    }

    @Test
    void simpleHeightModeIgnoresVerticalVelocity() {
        Config config = voidConfig();
        config.voidWarning.mode = VoidWarningSettings.MODE_SIMPLE_HEIGHT;
        config.voidWarning.simpleWarningBlocks = 20;
        VoidProximityTracker tracker = new VoidProximityTracker();

        VoidProximity result = tracker.update(config, 5.0, 1.0, 0);

        assertTrue(result.warning > 0.0f, "simple mode should warn based on height even while moving upward");
    }

    @Test
    void simpleHeightModeStaysInactiveAboveBlockThreshold() {
        Config config = voidConfig();
        config.voidWarning.mode = VoidWarningSettings.MODE_SIMPLE_HEIGHT;
        config.voidWarning.simpleWarningBlocks = 20;
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
        // climb: vy positive — intensity should decay slowly, not snap to 0
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

    private static Config voidConfig() {
        Config config = new Config();
        config.voidWarning.enabled = true;
        config.voidWarning.lookaheadSeconds = 5.0f;
        return config;
    }
}
