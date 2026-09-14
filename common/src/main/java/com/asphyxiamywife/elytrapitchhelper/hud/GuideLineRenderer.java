package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.config.LineSettings;
import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;

final class GuideLineRenderer {
    @FunctionalInterface
    interface Filler {
        void fill(int x, int y, int x2, int y2, int argb);
    }

    private static final float VISIBLE_EPSILON = 0.01f;
    private static final float WARNING_EPSILON = 0.001f;

    private static final float FLASH_COLOR_MIX_WEIGHT = 0.65f;

    private static final int GLOW_MAX_WARNING_GROWTH = 8;
    private static final int GLOW_INNER_EXTRA_LENGTH = 10;
    private static final int GLOW_INNER_EXTRA_THICKNESS = 4;
    private static final int GLOW_OUTER_EXTRA_LENGTH = 18;
    private static final int GLOW_OUTER_EXTRA_THICKNESS = 6;
    private static final float GLOW_OUTER_ALPHA_SCALE = 0.45f;
    private static final float GLOW_CUE_ALPHA_WEIGHT = 0.18f;
    private static final float GLOW_FLASH_ALPHA_WEIGHT = 0.45f;
    private static final float GLOW_MAX_ALPHA = 0.85f;

    private static final int FLASH_EXTRA_LENGTH = 26;
    private static final int FLASH_EXTRA_THICKNESS = 8;
    private static final float FLASH_ALPHA_SCALE = 0.28f;
    private static final int FLASH_RGB = 0xFFFFFF;

    private static final float MIX_EPSILON = 0.0001f;
    private static final double BOUNDARY_EPSILON = 0.000001;

    void draw(Filler filler, int cx, int guideY, float alpha,
            float cueAmount, float flash, float warnAmount, float warnPulse,
            LineSettings line, HudRenderState renderState) {
        int length = line.lengthPixels();
        int thickness = line.widthPixels();
        int x = cx - length / 2;
        int y = guideY - thickness / 2;
        HudRenderState.Color baseColor = renderState.line();
        HudRenderState.Color cueColor = renderState.cue();
        HudRenderState.Color warningColor = renderState.warning();
        boolean warningActive = warnAmount > WARNING_EPSILON || warnPulse > WARNING_EPSILON;

        float effectiveCue = Math.max(cueAmount, warnAmount);
        float effectiveFlash = Math.max(flash, warnPulse);
        float colorMix = warningActive
                ? MathUtil.clamp01(Math.max(warnAmount, warnPulse * FLASH_COLOR_MIX_WEIGHT))
                : MathUtil.clamp01(Math.max(cueAmount, flash * FLASH_COLOR_MIX_WEIGHT));

        if (effectiveCue > VISIBLE_EPSILON || effectiveFlash > VISIBLE_EPSILON) {
            HudRenderState.Color glowColor = glowLedByWarning(cueAmount, flash, warnAmount, warnPulse)
                    ? warningColor
                    : cueColor;
            int glowExtra = (int) (warnAmount * GLOW_MAX_WARNING_GROWTH);
            float glowAlpha = alpha * MathUtil.clamp(
                    GLOW_CUE_ALPHA_WEIGHT * effectiveCue + GLOW_FLASH_ALPHA_WEIGHT * effectiveFlash,
                    0.0f, GLOW_MAX_ALPHA);
            drawCenteredCueRect(filler, cx, guideY,
                    length + GLOW_OUTER_EXTRA_LENGTH + glowExtra,
                    thickness + GLOW_OUTER_EXTRA_THICKNESS + glowExtra,
                    glowAlpha * GLOW_OUTER_ALPHA_SCALE, glowColor);
            drawCenteredCueRect(filler, cx, guideY,
                    length + GLOW_INNER_EXTRA_LENGTH + glowExtra,
                    thickness + GLOW_INNER_EXTRA_THICKNESS + glowExtra,
                    glowAlpha, glowColor);
        }

        float flashScale = line.cueFlashScale();
        if (effectiveFlash > VISIBLE_EPSILON && flashScale > VISIBLE_EPSILON) {
            drawCenteredRect(filler, cx, guideY,
                    length + FLASH_EXTRA_LENGTH, thickness + FLASH_EXTRA_THICKNESS,
                    MathUtil.argb(alpha * effectiveFlash * FLASH_ALPHA_SCALE * flashScale, FLASH_RGB));
        }

        HudRenderState.Color finalCueColor = warningActive ? warningColor : cueColor;
        drawBlendedRect(filler, x, y, length, thickness, alpha, baseColor, finalCueColor, colorMix);
    }

    private static boolean glowLedByWarning(float cueAmount, float flash, float warnAmount, float warnPulse) {
        float warningWeight = GLOW_CUE_ALPHA_WEIGHT * warnAmount + GLOW_FLASH_ALPHA_WEIGHT * warnPulse;
        if (warningWeight <= WARNING_EPSILON) {
            return false;
        }
        return warningWeight >= GLOW_CUE_ALPHA_WEIGHT * cueAmount + GLOW_FLASH_ALPHA_WEIGHT * flash;
    }

    private static void drawCenteredRect(Filler filler, int cx, int cy, int width, int height, int color) {
        int x = cx - width / 2;
        int y = cy - height / 2;
        filler.fill(x, y, x + width, y + height, color);
    }

    private static void drawCenteredCueRect(Filler filler, int cx, int cy, int width, int height,
            float alpha, HudRenderState.Color cueColor) {
        int x = cx - width / 2;
        int y = cy - height / 2;
        drawStripedRect(filler, x, y, width, height, alpha, cueColor);
    }

    private static void drawStripedRect(Filler filler, int x, int y, int width, int height, float alpha,
            HudRenderState.Color color) {
        if (!color.striped()) {
            filler.fill(x, y, x + width, y + height, MathUtil.argb(alpha, color.rgb()));
            return;
        }

        int segments = Math.min(width, color.segmentCount());
        for (int i = 0; i < segments; i++) {
            int startX = x + i * width / segments;
            int endX = x + (i + 1) * width / segments;
            if (endX > startX) {
                double position = (i + 0.5) / segments;
                filler.fill(startX, y, endX, y + height, MathUtil.argb(alpha, color.colorAt(position)));
            }
        }
    }

    private static void drawBlendedRect(Filler filler, int x, int y, int width, int height, float alpha,
            HudRenderState.Color baseColor, HudRenderState.Color cueColor, float mix) {
        mix = MathUtil.clamp01(mix);
        if (mix <= MIX_EPSILON) {
            drawStripedRect(filler, x, y, width, height, alpha, baseColor);
            return;
        }
        if (mix >= 1.0f - MIX_EPSILON) {
            drawStripedRect(filler, x, y, width, height, alpha, cueColor);
            return;
        }
        if (!baseColor.striped() && !cueColor.striped()) {
            filler.fill(x, y, x + width, y + height,
                    MathUtil.argb(alpha, MathUtil.blendRgb(baseColor.rgb(), cueColor.rgb(), mix)));
            return;
        }

        int baseSegments = baseColor.segmentCount();
        int cueSegments = cueColor.segmentCount();
        int lastEndX = x;
        int baseBoundary = 1;
        int cueBoundary = 1;
        while (lastEndX < x + width) {
            double nextBaseBoundary = baseBoundary / (double) baseSegments;
            double nextCueBoundary = cueBoundary / (double) cueSegments;
            double nextBoundary = Math.min(nextBaseBoundary, nextCueBoundary);
            int endX = x + (int) Math.round(nextBoundary * width);
            if (endX <= lastEndX) {
                endX = lastEndX + 1;
            }
            endX = Math.min(endX, x + width);

            double position = (lastEndX + endX - x * 2) / (double) (width * 2);
            int base = baseColor.colorAt(position);
            int cue = cueColor.colorAt(position);
            filler.fill(lastEndX, y, endX, y + height,
                    MathUtil.argb(alpha, MathUtil.blendRgb(base, cue, mix)));

            lastEndX = endX;
            while (baseBoundary < baseSegments && baseBoundary / (double) baseSegments <= nextBoundary + BOUNDARY_EPSILON) {
                baseBoundary++;
            }
            while (cueBoundary < cueSegments && cueBoundary / (double) cueSegments <= nextBoundary + BOUNDARY_EPSILON) {
                cueBoundary++;
            }
        }
    }

}
