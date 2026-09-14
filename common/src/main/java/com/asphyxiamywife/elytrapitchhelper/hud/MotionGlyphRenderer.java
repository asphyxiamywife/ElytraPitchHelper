package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;

final class MotionGlyphRenderer {
    private static final int CORE_SIZE = 2;
    private static final int GLOW_SIZE = 4;
    private static final int SAMPLE_SPACING = 3;
    private static final int ARROW_HEAD_SPREAD = 4;
    private static final float GLOW_ALPHA_SCALE = 0.22f;

    void draw(GuideLineRenderer.Filler filler, int centerX, int centerY, int guideLength,
            MotionGlyphCue cue, HudRenderState.Color color) {
        AmplitudeLeg leg = cue.afterLeg();
        float progress = cue.progressNow();
        float opacity = cue.opacityNow();
        if (leg == AmplitudeLeg.NONE || progress < 0.0f || opacity <= 0.01f) {
            return;
        }

        boolean upward = leg == AmplitudeLeg.DESCENDING;
        float eased = MathUtil.smoothstep(progress);
        int extent = upward
                ? 9 + Math.round(7.0f * eased)
                : 7 + Math.round(21.0f * eased);
        float appear = MathUtil.smoothstep(Math.min(1.0f, progress / (upward ? 0.08f : 0.18f)));
        float alpha = appear * opacity * (upward ? 0.95f : 0.82f);
        if (alpha <= 0.01f) {
            return;
        }

        int anchorX = centerX + guideLength / 2 + 9;
        int tailY = centerY + (upward ? 5 : -5);
        int direction = upward ? -1 : 1;
        int samples = Math.max(3, extent / SAMPLE_SPACING + 1);
        for (int i = 0; i < samples; i++) {
            float along = i / (float) (samples - 1);
            int x = anchorX + Math.round(3.0f * (4.0f * along * (1.0f - along)));
            int y = tailY + direction * Math.round(extent * along);
            drawPoint(filler, x, y, alpha, color, along);
        }

        int tipX = anchorX;
        int tipY = tailY + direction * extent;
        drawPoint(filler, tipX - ARROW_HEAD_SPREAD, tipY - direction * ARROW_HEAD_SPREAD,
                alpha, color, 0.9f);
        drawPoint(filler, tipX + ARROW_HEAD_SPREAD, tipY - direction * ARROW_HEAD_SPREAD,
                alpha, color, 1.0f);
    }

    private static void drawPoint(GuideLineRenderer.Filler filler, int x, int y, float alpha,
            HudRenderState.Color color, float position) {
        int rgb = color.colorAt(position);
        fillCentered(filler, x, y, GLOW_SIZE, MathUtil.argb(alpha * GLOW_ALPHA_SCALE, rgb));
        fillCentered(filler, x, y, CORE_SIZE, MathUtil.argb(alpha, rgb));
    }

    private static void fillCentered(GuideLineRenderer.Filler filler, int x, int y, int size, int argb) {
        int half = size / 2;
        filler.fill(x - half, y - half, x - half + size, y - half + size, argb);
    }
}
