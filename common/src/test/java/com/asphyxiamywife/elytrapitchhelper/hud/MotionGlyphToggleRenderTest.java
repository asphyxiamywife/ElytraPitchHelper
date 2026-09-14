package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import com.asphyxiamywife.elytrapitchhelper.flight.ElytraDetector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class MotionGlyphToggleRenderTest {
    private final PitchGuideHud hud = new PitchGuideHud(new ElytraDetector());

    @Test
    void glyphsDefaultOnAndCanBeHiddenWithoutDisablingAmplitudeCues() {
        Config config = new Config();
        Profile enabled = config.activeProfile();
        Profile disabled = enabled.withAmplitude(
                enabled.amplitude().withMotionGlyphsEnabled(false));

        assertTrue(enabled.amplitude().motionGlyphsEnabled());
        assertTrue(draw(config, disabled).isEmpty(),
                "a pitch away from both guides should draw nothing when glyphs are off");
        assertTrue(draw(config, enabled).size() > draw(config, disabled).size(),
                "the enabled setting should add the motion glyph fills");
    }

    private List<int[]> draw(Config config, Profile profile) {
        HudFrameState state = new HudFrameState(profile, HudRenderState.from(config),
                AmplitudeCue.NONE, VoidProximity.NONE, Strength.ZERO, Strength.ZERO,
                new MotionGlyphCue(AmplitudeLeg.DESCENDING, () -> 0.25f));
        List<int[]> fills = new ArrayList<>();
        hud.renderGuides((x, y, x2, y2, argb) -> fills.add(new int[] {x, y, x2, y2}),
                640, 360, state, 0.0f);
        return fills;
    }
}
