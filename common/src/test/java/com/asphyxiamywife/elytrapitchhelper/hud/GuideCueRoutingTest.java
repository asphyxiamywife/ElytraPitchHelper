package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.flight.ElytraDetector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GuideCueRoutingTest {
    private final PitchGuideHud hud = new PitchGuideHud(new ElytraDetector());
    private final Config config = new Config();

    @Test
    void theDiveCueReachesTheDiveGuideAndNotTheClimbGuide() {
        float dive = config.pitch().targetDownMinecraft();
        assertTrue(fills(AmplitudeLeg.DESCENDING, dive) > fills(AmplitudeLeg.NONE, dive),
                "a descending cue must show on the dive guide");
        assertEquals(fills(AmplitudeLeg.NONE, dive), fills(AmplitudeLeg.ASCENDING, dive),
                "a climbing cue must leave the dive guide alone");
    }

    @Test
    void theClimbCueReachesTheClimbGuideAndNotTheDiveGuide() {
        float climb = config.pitch().targetUpMinecraft();
        assertTrue(fills(AmplitudeLeg.ASCENDING, climb) > fills(AmplitudeLeg.NONE, climb),
                "an ascending cue must show on the climb guide");
        assertEquals(fills(AmplitudeLeg.NONE, climb), fills(AmplitudeLeg.DESCENDING, climb),
                "a dive cue must leave the climb guide alone");
    }

    private int fills(AmplitudeLeg leg, float pitch) {
        HudFrameState state = new HudFrameState(config.activeProfile(), HudRenderState.from(config),
                new AmplitudeCue(leg, leg == AmplitudeLeg.NONE ? 0.0f : 1.0f, AmplitudeLeg.NONE),
                VoidProximity.NONE, Strength.ZERO, Strength.ZERO);
        List<int[]> drawn = new ArrayList<>();
        hud.renderGuides((x, y, x2, y2, argb) -> drawn.add(new int[] {x, y, x2, y2}),
                640, 360, state, pitch);
        assertTrue(!drawn.isEmpty(), "expected a guide at pitch " + pitch);
        return drawn.size();
    }
}
