package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.PrideFlag;
import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class GuideLineRenderer {
    void draw(GuiGraphicsExtractor ctx, int cx, int guideY, float alpha, AmplitudeCue amplitudeCue, Config config) {
        int length = config.lineLengthPixels;
        int thickness = config.lineWidthPixels;
        int x = cx - length / 2;
        int y = guideY - thickness / 2;
        int baseRgb = config.lineColorRgb & 0x00FFFFFF;
        int cueRgb = config.amplitudeCueColorRgb & 0x00FFFFFF;
        int[] basePrideColors = config.linePrideEnabled ? PrideFlag.byId(config.linePrideFlag).colors() : null;
        int[] cuePrideColors = config.amplitudeCuePrideEnabled
                ? PrideFlag.byId(config.amplitudeCuePrideFlag).colors()
                : null;
        float cueAmount = amplitudeCue.amount;
        float flash = amplitudeCue.flash;
        float colorMix = MathUtil.clamp(Math.max(cueAmount, flash * 0.65f), 0.0f, 1.0f);

        if (cueAmount > 0.01f || flash > 0.01f) {
            float glowAlpha = alpha * MathUtil.clamp(0.18f * cueAmount + 0.45f * flash, 0.0f, 0.85f);
            drawCenteredCueRect(ctx, cx, guideY, length + 18, thickness + 6, glowAlpha * 0.45f, cueRgb,
                    cuePrideColors);
            drawCenteredCueRect(ctx, cx, guideY, length + 10, thickness + 4, glowAlpha, cueRgb, cuePrideColors);
        }

        if (flash > 0.01f) {
            drawCenteredRect(ctx, cx, guideY, length + 26, thickness + 8, MathUtil.argb(alpha * flash * 0.28f,
                    0xFFFFFF));
        }

        drawBlendedRect(ctx, x, y, length, thickness, alpha, baseRgb, basePrideColors, cueRgb, cuePrideColors,
                colorMix);
    }

    private static void drawCenteredRect(GuiGraphicsExtractor ctx, int cx, int cy, int width, int height, int color) {
        int x = cx - width / 2;
        int y = cy - height / 2;
        ctx.fill(x, y, x + width, y + height, color);
    }

    private static void drawCenteredCueRect(GuiGraphicsExtractor ctx, int cx, int cy, int width, int height,
            float alpha, int cueRgb, int[] cuePrideColors) {
        int x = cx - width / 2;
        int y = cy - height / 2;
        drawStripedRect(ctx, x, y, width, height, alpha, cueRgb, cuePrideColors);
    }

    private static void drawStripedRect(GuiGraphicsExtractor ctx, int x, int y, int width, int height, float alpha,
            int fallbackRgb, int[] prideColors) {
        if (prideColors == null || prideColors.length == 0) {
            ctx.fill(x, y, x + width, y + height, MathUtil.argb(alpha, fallbackRgb));
            return;
        }

        int segments = Math.min(width, prideColors.length);
        for (int i = 0; i < segments; i++) {
            int startX = x + i * width / segments;
            int endX = x + (i + 1) * width / segments;
            if (endX > startX) {
                double position = (i + 0.5) / segments;
                ctx.fill(startX, y, endX, y + height, MathUtil.argb(alpha,
                        PrideFlag.colorAt(prideColors, position)));
            }
        }
    }

    private static void drawBlendedRect(GuiGraphicsExtractor ctx, int x, int y, int width, int height, float alpha,
            int baseRgb, int[] basePrideColors, int cueRgb, int[] cuePrideColors, float mix) {
        mix = MathUtil.clamp(mix, 0.0f, 1.0f);
        if (mix <= 0.0001f) {
            drawStripedRect(ctx, x, y, width, height, alpha, baseRgb, basePrideColors);
            return;
        }
        if (mix >= 0.9999f) {
            drawStripedRect(ctx, x, y, width, height, alpha, cueRgb, cuePrideColors);
            return;
        }
        if ((basePrideColors == null || basePrideColors.length == 0)
                && (cuePrideColors == null || cuePrideColors.length == 0)) {
            ctx.fill(x, y, x + width, y + height, MathUtil.argb(alpha, MathUtil.blendRgb(baseRgb, cueRgb, mix)));
            return;
        }

        int baseSegments = basePrideColors == null ? 1 : basePrideColors.length;
        int cueSegments = cuePrideColors == null ? 1 : cuePrideColors.length;
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
            int base = basePrideColors == null ? baseRgb : PrideFlag.colorAt(basePrideColors, position);
            int cue = cuePrideColors == null ? cueRgb : PrideFlag.colorAt(cuePrideColors, position);
            ctx.fill(lastEndX, y, endX, y + height, MathUtil.argb(alpha, MathUtil.blendRgb(base, cue, mix)));

            lastEndX = endX;
            while (baseBoundary < baseSegments && baseBoundary / (double) baseSegments <= nextBoundary + 0.000001) {
                baseBoundary++;
            }
            while (cueBoundary < cueSegments && cueBoundary / (double) cueSegments <= nextBoundary + 0.000001) {
                cueBoundary++;
            }
        }
    }
}
