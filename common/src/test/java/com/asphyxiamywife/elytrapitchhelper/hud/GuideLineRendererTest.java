package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.ConfigTestFixtures;
import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GuideLineRendererTest {
    @Test
    void solidLineWithoutCuesUsesOneExactFill() {
        Config config = new Config();
        ConfigTestFixtures.updateActiveProfile(config, profile -> profile.withLine(
                profile.line()
                        .withLengthPixels(10)
                        .withWidthPixels(4)
                        .withColorRgb(0x112233)
                        .withPrideEnabled(false)));
        List<Fill> fills = draw(config, 20, 10, 0.5f, AmplitudeCue.NONE, VoidProximity.NONE);

        assertEquals(List.of(new Fill(15, 8, 25, 12, 0x7F112233)), fills);
    }

    @Test
    void differentlyStripedColorsWalkBothBoundarySetsWithoutGaps() {
        Config config = new Config();
        ConfigTestFixtures.updateActiveProfile(config, profile -> profile
                .withLine(profile.line()
                        .withLengthPixels(12)
                        .withWidthPixels(2)
                        .withPride(true, "custom", new int[] {0x100000, 0x200000}))
                .withAmplitude(profile.amplitude()
                        .withCuePride(true, "custom", new int[] {0x001000, 0x002000, 0x003000})));

        List<Fill> mainLine = draw(config, 50, 20, 1.0f,
                new AmplitudeCue(AmplitudeLeg.DESCENDING, 0.5f, AmplitudeLeg.NONE),
                VoidProximity.NONE).stream()
                .filter(fill -> fill.y() == 19 && fill.y2() == 21)
                .toList();

        assertEquals(List.of(
                new Fill(44, 19, 48, 21, 0xFF080800),
                new Fill(48, 19, 50, 21, 0xFF081000),
                new Fill(50, 19, 52, 21, 0xFF101000),
                new Fill(52, 19, 56, 21, 0xFF101800)),
                mainLine);
    }

    @Test
    void fullVoidPulseDrawsOuterGlowInnerGlowFlashAndWarningLine() {
        Config config = new Config();
        ConfigTestFixtures.updateActiveProfile(config, profile -> profile
                .withLine(profile.line()
                        .withLengthPixels(10)
                        .withWidthPixels(2)
                        .withColorRgb(0x112233)
                        .withPrideEnabled(false))
                .withVoidWarning(profile.voidWarning()
                        .withWarningColorRgb(0xFF4400)
                        .withWarningPrideEnabled(false)));

        List<Fill> fills = draw(config, 50, 20, 1.0f,
                AmplitudeCue.NONE, new VoidProximity(1.0f, 1.0f));

        assertEquals(List.of(
                new Fill(32, 12, 68, 28, MathUtil.argb(0.63f * 0.45f, 0xFF4400)),
                new Fill(36, 13, 64, 27, MathUtil.argb(0.63f, 0xFF4400)),
                new Fill(32, 15, 68, 25, MathUtil.argb(0.28f, 0xFFFFFF)),
                new Fill(45, 19, 55, 21, 0xFFFF4400)),
                fills);
    }

    @Test
    void cueFlashIntensityScalesOnlyTheFlashAndCanRemoveIt() {
        Config half = voidPulseConfig(50);
        List<Fill> halfFills = draw(half, 50, 20, 1.0f, AmplitudeCue.NONE, new VoidProximity(1.0f, 1.0f));

        assertEquals(List.of(
                new Fill(32, 12, 68, 28, MathUtil.argb(0.63f * 0.45f, 0xFF4400)),
                new Fill(36, 13, 64, 27, MathUtil.argb(0.63f, 0xFF4400)),
                new Fill(32, 15, 68, 25, MathUtil.argb(0.28f * 0.5f, 0xFFFFFF)),
                new Fill(45, 19, 55, 21, 0xFFFF4400)),
                halfFills);

        Config off = voidPulseConfig(0);
        List<Fill> offFills = draw(off, 50, 20, 1.0f, AmplitudeCue.NONE, new VoidProximity(1.0f, 1.0f));

        assertEquals(List.of(
                new Fill(32, 12, 68, 28, MathUtil.argb(0.63f * 0.45f, 0xFF4400)),
                new Fill(36, 13, 64, 27, MathUtil.argb(0.63f, 0xFF4400)),
                new Fill(45, 19, 55, 21, 0xFFFF4400)),
                offFills);
    }

    @Test
    void amplitudeFlashUnderASustainedWarningStaysInTheCueColour() {
        Config config = new Config();
        ConfigTestFixtures.updateActiveProfile(config, profile -> profile
                .withLine(profile.line()
                        .withLengthPixels(10)
                        .withWidthPixels(2)
                        .withColorRgb(0x112233)
                        .withPrideEnabled(false))
                .withAmplitude(profile.amplitude()
                        .withCueColorRgb(0x00FF00)
                        .withCuePrideEnabled(false))
                .withVoidWarning(profile.voidWarning()
                        .withWarningColorRgb(0xFF4400)
                        .withWarningPrideEnabled(false)));

        List<Fill> fills = draw(config, 50, 20, 1.0f,
                new AmplitudeCue(AmplitudeLeg.DESCENDING, 0.0f, AmplitudeLeg.DESCENDING), 1.0f,
                new VoidProximity(0.5f, 0.0f));

        assertEquals(List.of(
                new Fill(34, 14, 66, 26, MathUtil.argb(0.54f * 0.45f, 0x00FF00)),
                new Fill(38, 15, 62, 25, MathUtil.argb(0.54f, 0x00FF00)),
                new Fill(32, 15, 68, 25, MathUtil.argb(0.28f, 0xFFFFFF)),
                new Fill(45, 19, 55, 21, MathUtil.argb(1.0f, MathUtil.blendRgb(0x112233, 0xFF4400, 0.5f)))),
                fills);
    }

    @Test
    void voidPulseOutranksASimultaneousAmplitudeFlash() {
        Config config = new Config();
        ConfigTestFixtures.updateActiveProfile(config, profile -> profile
                .withLine(profile.line()
                        .withLengthPixels(10)
                        .withWidthPixels(2)
                        .withColorRgb(0x112233)
                        .withPrideEnabled(false))
                .withAmplitude(profile.amplitude()
                        .withCueColorRgb(0x00FF00)
                        .withCuePrideEnabled(false))
                .withVoidWarning(profile.voidWarning()
                        .withWarningColorRgb(0xFF4400)
                        .withWarningPrideEnabled(false)));

        List<Fill> fills = draw(config, 50, 20, 1.0f,
                new AmplitudeCue(AmplitudeLeg.DESCENDING, 1.0f, AmplitudeLeg.DESCENDING), 1.0f,
                new VoidProximity(1.0f, 1.0f));

        assertEquals(List.of(
                new Fill(32, 12, 68, 28, MathUtil.argb(0.63f * 0.45f, 0xFF4400)),
                new Fill(36, 13, 64, 27, MathUtil.argb(0.63f, 0xFF4400)),
                new Fill(32, 15, 68, 25, MathUtil.argb(0.28f, 0xFFFFFF)),
                new Fill(45, 19, 55, 21, 0xFFFF4400)),
                fills);
    }

    private static Config voidPulseConfig(int cueFlashIntensity) {
        Config config = new Config();
        ConfigTestFixtures.updateActiveProfile(config, profile -> profile
                .withLine(profile.line()
                        .withLengthPixels(10)
                        .withWidthPixels(2)
                        .withColorRgb(0x112233)
                        .withPrideEnabled(false)
                        .withCueFlashIntensity(cueFlashIntensity))
                .withVoidWarning(profile.voidWarning()
                        .withWarningColorRgb(0xFF4400)
                        .withWarningPrideEnabled(false)));
        return config;
    }

    private static List<Fill> draw(Config config, int centerX, int guideY, float alpha,
            AmplitudeCue cue, VoidProximity proximity) {
        return draw(config, centerX, guideY, alpha, cue, 0.0f, proximity);
    }

    private static List<Fill> draw(Config config, int centerX, int guideY, float alpha,
            AmplitudeCue cue, float flash, VoidProximity proximity) {
        List<Fill> fills = new ArrayList<>();
        new GuideLineRenderer().draw(
                (x, y, x2, y2, argb) -> fills.add(new Fill(x, y, x2, y2, argb)),
                centerX, guideY, alpha, cue.amount, flash, proximity.warning, proximity.pulse,
                config.line(), HudRenderState.from(config));
        return fills;
    }

    private record Fill(int x, int y, int x2, int y2, int argb) {
    }
}
