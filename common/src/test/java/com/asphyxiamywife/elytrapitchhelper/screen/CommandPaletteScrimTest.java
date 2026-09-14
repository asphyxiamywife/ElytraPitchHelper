package com.asphyxiamywife.elytrapitchhelper.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CommandPaletteScrimTest {
    @Test
    void aSettingsParentRecedesFurtherThanGameplay() {
        int overWorld = CommandPaletteScreen.scrimAlpha(60, false);
        int overSettings = CommandPaletteScreen.scrimAlpha(60, true);

        assertTrue(overSettings > overWorld,
                () -> "expected a stronger scrim over settings, got " + overSettings + " vs " + overWorld);
    }

    @Test
    void noShadowMeansNoScrimWhateverIsBehind() {
        assertEquals(0, CommandPaletteScreen.scrimAlpha(0, false));
        assertEquals(0, CommandPaletteScreen.scrimAlpha(0, true));
    }

    @Test
    void aFullShadowIsAlreadyOpaqueAndCannotBePushedFurther() {
        assertEquals(255, CommandPaletteScreen.scrimAlpha(100, false));
        assertEquals(255, CommandPaletteScreen.scrimAlpha(100, true));
    }

    @Test
    void theBoostNeverRunsPastOpaque() {
        for (int step = 0; step <= 100; step++) {
            int shadow = step;
            int alpha = CommandPaletteScreen.scrimAlpha(shadow, true);
            assertTrue(alpha >= 0 && alpha <= 255, () -> "shadow " + shadow + " gave alpha " + alpha);
        }
    }

    @Test
    void outOfRangeShadowIsClamped() {
        assertEquals(0, CommandPaletteScreen.scrimAlpha(-20, true));
        assertEquals(255, CommandPaletteScreen.scrimAlpha(400, false));
    }
}
