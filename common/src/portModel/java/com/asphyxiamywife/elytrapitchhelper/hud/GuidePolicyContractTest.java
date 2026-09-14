package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class GuidePolicyContractTest {
    @Test
    void guidesStayOrderedBoundedAndVisibleAtTheirTargets() {
        Profile profile = new Config().activeProfile();
        float downTarget = profile.pitch().targetDownMinecraft();
        float upTarget = profile.pitch().targetUpMinecraft();
        assertTrue(downTarget > upTarget);
        for (float pitch = -90; pitch <= 90; pitch += .25f) {
            int down = GuidePolicy.offset(profile, pitch, downTarget, false);
            int up = GuidePolicy.offset(profile, pitch, upTarget, false);
            assertTrue(down >= up, "Descending target must not appear above the ascending target");
            assertTrue(Math.abs(down) <= profile.pitch().maxOffsetPixels());
            assertTrue(Math.abs(up) <= profile.pitch().maxOffsetPixels());
        }
        assertEquals(0, GuidePolicy.offset(profile, downTarget, downTarget, false));
        assertEquals(0, GuidePolicy.offset(profile, upTarget, upTarget, false));
        assertTrue(GuidePolicy.isGuideVisible(profile, downTarget, AmplitudeLeg.DESCENDING));
        assertTrue(GuidePolicy.isGuideVisible(profile, upTarget, AmplitudeLeg.ASCENDING));
        assertEquals(1, GuidePolicy.computeAlpha(0, 0));
        assertEquals(0, GuidePolicy.computeAlpha(0, 1));
    }

    @Test
    void cueAndFlashStayOnTheirOwnLegs() {
        AmplitudeCue cue = new AmplitudeCue(AmplitudeLeg.ASCENDING, .75f, AmplitudeLeg.DESCENDING);
        assertEquals(.75f, GuidePolicy.cueAmountForLeg(cue, AmplitudeLeg.ASCENDING));
        assertEquals(0, GuidePolicy.cueAmountForLeg(cue, AmplitudeLeg.DESCENDING));
        assertEquals(.5f, GuidePolicy.flashForLeg(cue, AmplitudeLeg.DESCENDING, .5f));
        assertEquals(0, GuidePolicy.flashForLeg(cue, AmplitudeLeg.ASCENDING, .5f));
    }
}
