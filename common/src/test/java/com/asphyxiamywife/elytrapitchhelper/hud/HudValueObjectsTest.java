package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.ConfigTestFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HudValueObjectsTest {
    @Test
    void cueFilteringKeepsOnlyTheRequestedLegWithoutCreatingFilteredObjects() {
        AmplitudeCue cue = new AmplitudeCue(AmplitudeLeg.ASCENDING, 0.6f, AmplitudeLeg.DESCENDING);

        assertEquals(0.6f, PitchGuideHud.cueAmountForLeg(cue, AmplitudeLeg.ASCENDING));
        assertEquals(0.0f, PitchGuideHud.flashForLeg(cue, AmplitudeLeg.ASCENDING, 0.8f));
        assertEquals(0.0f, PitchGuideHud.cueAmountForLeg(cue, AmplitudeLeg.DESCENDING));
        assertEquals(0.8f, PitchGuideHud.flashForLeg(cue, AmplitudeLeg.DESCENDING, 0.8f));
    }

    @Test
    void voidProximityUsesTheDocumentedActivityThreshold() {
        assertFalse(new VoidProximity(0.01f, 0.01f).isActive());
        assertTrue(new VoidProximity(0.0101f, 0.0f).isActive());
        assertTrue(new VoidProximity(0.0f, 0.0101f).isActive());
    }

    @Test
    void renderStateResolvesCustomStripeColorsImmutably() {
        Config config = new Config();
        int[] colors = { 0xFF112233, 0x445566 };
        ConfigTestFixtures.updateActiveProfile(config, profile -> profile.withLine(
                profile.line().withColorRgb(0x223344).withPride(true, "custom", colors)));

        HudRenderState renderState = HudRenderState.from(config);
        colors[0] = 0x000000;

        assertEquals(0x223344, renderState.line().rgb());
        assertArrayEquals(new int[] { 0x112233, 0x445566 }, renderState.line().stripeColorsForTests());
        assertEquals(0x112233, renderState.line().colorAt(0.25));
    }

    @Test
    void renderStateKeepsDisabledPrideAsSolidColor() {
        Config config = new Config();
        ConfigTestFixtures.updateActiveProfile(config, profile -> profile.withLine(
                profile.line().withColorRgb(0xABCDEF)
                        .withPride(false, "custom", new int[] { 0x111111, 0x222222 })));

        HudRenderState renderState = HudRenderState.from(config);

        assertFalse(renderState.line().striped());
        assertEquals(0xABCDEF, renderState.line().rgb());
        assertEquals(0xABCDEF, renderState.line().colorAt(0.75));
    }

    @Test
    void absentFrameStateCarriesNoRenderableSimulation() {
        assertFalse(HudFrameState.NONE.visible());
        assertSame(AmplitudeCue.NONE, HudFrameState.NONE.amplitudeCue());
        assertSame(VoidProximity.NONE, HudFrameState.NONE.voidProximity());
    }
}
