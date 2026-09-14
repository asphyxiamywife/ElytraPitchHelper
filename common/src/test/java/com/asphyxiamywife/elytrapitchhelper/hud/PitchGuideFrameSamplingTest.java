package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.flight.ElytraDetector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PitchGuideFrameSamplingTest {
    @Test
    void theSameFrameStateDrawsAtDifferentHeightsForDifferentPitches() {
        PitchGuideHud hud = new PitchGuideHud(new ElytraDetector());
        HudFrameState state = frameState();

        int twoDegreesAway = midpointY(draw(hud, state, 38.0f));
        int oneDegreeAway = midpointY(draw(hud, state, 39.0f));
        int onTarget = midpointY(draw(hud, state, 40.0f));

        assertNotEquals(twoDegreesAway, oneDegreeAway,
                "a one-degree pitch change must move the guide within the same tick");
        assertNotEquals(oneDegreeAway, onTarget);
        assertEquals(oneDegreeAway - twoDegreesAway, onTarget - oneDegreeAway);
    }

    @Test
    void cueFlashIsSampledPerFrameRatherThanPerTick() {
        Config config = new Config();
        float[] clock = {1.0f};
        HudFrameState state = new HudFrameState(config.activeProfile(), HudRenderState.from(config),
                new AmplitudeCue(AmplitudeLeg.DESCENDING, 1.0f, AmplitudeLeg.DESCENDING),
                VoidProximity.NONE, () -> clock[0], Strength.ZERO);

        assertEquals(1.0f, state.flashNow());
        clock[0] = 0.25f;
        assertEquals(0.25f, state.flashNow(),
                "flash must follow the clock between ticks, not the value the tick captured");

        PitchGuideHud hud = new PitchGuideHud(new ElytraDetector());
        clock[0] = 1.0f;
        int fullGlow = draw(hud, state, 40.0f).size();
        clock[0] = 0.0f;
        int noGlow = draw(hud, state, 40.0f).size();
        assertNotEquals(fullGlow, noGlow, "a decayed flash must stop drawing its glow layers");
    }

    @Test
    void voidPulseIsSampledPerFrameRatherThanPerTick() {
        Config config = new Config();
        float[] clock = {1.0f};
        HudFrameState state = new HudFrameState(config.activeProfile(), HudRenderState.from(config),
                AmplitudeCue.NONE, new VoidProximity(0.0f, 1.0f), Strength.ZERO, () -> clock[0]);

        assertTrue(state.voidPulseNow() > 0.01f);
        clock[0] = 0.0f;
        assertFalse(state.voidPulseNow() > 0.01f,
                "a pulse that decayed to zero between ticks must stop reading as active");
    }

    @Test
    void frameStateCarriesNoPitch() {
        for (RecordComponent component : HudFrameState.class.getRecordComponents()) {
            assertFalse(component.getName().toLowerCase(java.util.Locale.ROOT).contains("pitch"),
                    "HudFrameState must not carry pitch; it is sampled per frame in render");
        }
    }

    private static HudFrameState frameState() {
        Config config = new Config();
        return new HudFrameState(config.activeProfile(), HudRenderState.from(config),
                AmplitudeCue.NONE, VoidProximity.NONE, Strength.ZERO, Strength.ZERO);
    }

    private static List<int[]> draw(PitchGuideHud hud, HudFrameState state, float pitch) {
        List<int[]> fills = new ArrayList<>();
        hud.renderGuides((x, y, x2, y2, argb) -> fills.add(new int[] {x, y, x2, y2}),
                640, 360, state, pitch);
        assertTrue(!fills.isEmpty(), "expected the descending guide to draw at pitch " + pitch);
        return fills;
    }

    private static int midpointY(List<int[]> fills) {
        int[] lowest = fills.get(0);
        for (int[] fill : fills) {
            if (fill[1] > lowest[1]) {
                lowest = fill;
            }
        }
        return lowest[1] + lowest[3];
    }
}
