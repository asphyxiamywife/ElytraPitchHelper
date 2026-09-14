package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MotionGlyphRendererTest {
    private static final int CENTER_X = 320;
    private static final int CENTER_Y = 180;
    private static final int GUIDE_LENGTH = 26;
    private final MotionGlyphRenderer renderer = new MotionGlyphRenderer();
    private final HudRenderState.Color color = HudRenderState.from(new Config()).cue();

    @Test
    void aDiveFlashProducesACompactUpwardGesture() {
        List<Fill> fills = draw(new MotionGlyphCue(AmplitudeLeg.DESCENDING, () -> 0.25f));

        assertTrue(!fills.isEmpty());
        assertTrue(minY(fills) < CENTER_Y - 5);
        assertTrue(maxY(fills) <= CENTER_Y + 7);
        assertTrue(maxY(fills) - minY(fills) < 24,
                "the urgent pull-up should remain a compact pop, not a path to trace");
    }

    @Test
    void pullUpKeepsMovingAcrossItsConfiguredDuration() {
        List<Fill> early = draw(new MotionGlyphCue(AmplitudeLeg.DESCENDING, () -> 0.35f));
        List<Fill> late = draw(new MotionGlyphCue(AmplitudeLeg.DESCENDING, () -> 0.90f));

        assertTrue(minY(late) < minY(early),
                "the upward glyph should not reach full extent early in its configured duration");
    }

    @Test
    void aClimbFlashProducesALongerDownwardGestureThatUnfoldsSmoothly() {
        List<Fill> early = draw(new MotionGlyphCue(AmplitudeLeg.ASCENDING, () -> 0.20f));
        List<Fill> late = draw(new MotionGlyphCue(AmplitudeLeg.ASCENDING, () -> 0.65f));

        assertTrue(!early.isEmpty());
        assertTrue(maxY(early) > CENTER_Y + 3);
        assertTrue(maxY(late) > maxY(early) + 6,
                "the release glyph should stretch downward instead of popping to full length");
    }

    @Test
    void noLegAndCompletedAnimationsDrawNothing() {
        assertEquals(List.of(), draw(MotionGlyphCue.NONE));
        assertEquals(List.of(), draw(new MotionGlyphCue(AmplitudeLeg.DESCENDING, () -> 1.0f)));
        assertEquals(List.of(), draw(new MotionGlyphCue(AmplitudeLeg.ASCENDING, () -> 1.0f)));
    }

    @Test
    void glyphRemainsAttachedBeyondTheEndOfLongGuideLines() {
        int guideLength = 200;
        List<Fill> fills = draw(guideLength,
                new MotionGlyphCue(AmplitudeLeg.DESCENDING, () -> 0.5f));

        assertTrue(minX(fills) > CENTER_X + guideLength / 2,
                "the complete glyph should remain beyond the right endpoint of the guide line");
    }

    private List<Fill> draw(MotionGlyphCue cue) {
        return draw(GUIDE_LENGTH, cue);
    }

    private List<Fill> draw(int guideLength, MotionGlyphCue cue) {
        List<Fill> fills = new ArrayList<>();
        renderer.draw((x, y, x2, y2, argb) -> fills.add(new Fill(x, y, x2, y2, argb)),
                CENTER_X, CENTER_Y, guideLength, cue, color);
        return fills;
    }

    private static int minY(List<Fill> fills) {
        return fills.stream().mapToInt(Fill::y).min().orElseThrow();
    }

    private static int maxY(List<Fill> fills) {
        return fills.stream().mapToInt(Fill::y2).max().orElseThrow();
    }

    private static int minX(List<Fill> fills) {
        return fills.stream().mapToInt(Fill::x).min().orElseThrow();
    }

    private record Fill(int x, int y, int x2, int y2, int argb) {
    }
}
