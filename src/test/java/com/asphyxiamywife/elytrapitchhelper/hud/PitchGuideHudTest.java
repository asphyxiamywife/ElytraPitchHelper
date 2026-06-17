package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.VoidWarningSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PitchGuideHudTest {
    @Test
    void predictedVoidWarningHidesAscendingLine() {
        Config config = new Config();
        config.voidWarning.mode = VoidWarningSettings.MODE_PREDICTED_TIME;

        assertTrue(PitchGuideHud.hidesAscendingLineForVoidWarning(config, new VoidProximity(0.5f, 0.0f)));
    }

    @Test
    void simpleVoidWarningKeepsAscendingLineVisible() {
        Config config = new Config();
        config.voidWarning.mode = VoidWarningSettings.MODE_SIMPLE_HEIGHT;

        assertFalse(PitchGuideHud.hidesAscendingLineForVoidWarning(config, new VoidProximity(0.5f, 0.0f)));
    }

    @Test
    void simpleVoidWarningUsesTerrainClearCheckWhenRelevant() {
        Config config = new Config();
        config.voidWarning.mode = VoidWarningSettings.MODE_SIMPLE_HEIGHT;
        config.voidWarning.simpleWarningBlocks = 20;
        VoidProximityTracker tracker = new VoidProximityTracker();
        VoidProximity proximity = tracker.update(config, 5.0, 0.0, 0);

        assertTrue(PitchGuideHud.shouldClearVoidWarningForTerrain(tracker, proximity));
    }
}
