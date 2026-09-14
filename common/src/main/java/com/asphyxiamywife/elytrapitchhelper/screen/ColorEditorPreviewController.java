package com.asphyxiamywife.elytrapitchhelper.screen;

import static com.asphyxiamywife.elytrapitchhelper.util.MathUtil.clamp;

import com.asphyxiamywife.elytrapitchhelper.config.PrideFlag;
import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

final class ColorEditorPreviewController {
    private static final String CUSTOM_STRIPE_MARKER = "v";

    interface Host {
        Font editorFont();

        ColorEditorLayout colorEditorLayout();

        int previewHeight();

        int color();

        boolean prideEnabled();

        String prideFlagId();

        int[] customPrideColors();

        boolean editingCustomPride();

        int customStripeIndex();

        void selectCustomStripe(int stripeIndex);
    }

    private final Host host;
    private final int lineLength;
    private final int lineWidth;
    private final boolean cuePeak;

    ColorEditorPreviewController(Host host, int lineLength, int lineWidth, boolean cuePeak) {
        this.host = host;
        this.lineLength = lineLength;
        this.lineWidth = lineWidth;
        this.cuePeak = cuePeak;
    }

    void render(GuiGraphics context, int x, int y, int width, int height, int mouseX, int mouseY) {
        context.renderOutline(x, y, width, height, 0x99FFFFFF);
        PreviewLineGeometry line = geometry(x, y, width, height);
        int rgb = host.color() & 0x00FFFFFF;
        int[] prideColors = host.prideEnabled()
                ? PrideFlag.colorsFor(host.prideFlagId(), host.customPrideColors())
                : null;
        if (cuePeak) {
            float glowAlpha = 0.18f;
            drawCenteredRect(context, line.centerX(), line.centerY(), Math.min(line.length() + 18, width - 2),
                    Math.min(lineWidth + 6, height - 2), glowAlpha * 0.45f, rgb, prideColors);
            drawCenteredRect(context, line.centerX(), line.centerY(), Math.min(line.length() + 10, width - 2),
                    Math.min(lineWidth + 4, height - 2), glowAlpha, rgb, prideColors);
        }
        drawRect(context, line.x(), line.y(), line.length(), lineWidth, 1.0f, rgb, prideColors);
        drawStripeMarker(context, x, y, width, line);
        if (stripeIndexAt(x, y, width, height, mouseX, mouseY) >= 0) {
            context.requestCursor(CursorTypes.POINTING_HAND);
        }
    }

    boolean selectStripe(MouseButtonEvent event) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        ColorEditorLayout layout = host.colorEditorLayout();
        int stripe = stripeIndexAt(layout.controlX(), layout.previewY(), layout.controlWidth(), host.previewHeight(),
                event.x(), event.y());
        if (stripe < 0) {
            return false;
        }
        host.selectCustomStripe(stripe);
        return true;
    }

    private void drawStripeMarker(GuiGraphics context, int previewX, int previewY, int previewWidth,
            PreviewLineGeometry line) {
        if (!host.editingCustomPride()) {
            return;
        }
        int stripes = Math.max(1, host.customPrideColors().length);
        int markerX = line.x() + (int) Math.round((host.customStripeIndex() + 0.5) * line.length() / stripes);
        markerX = clamp(markerX, previewX + 2, previewX + previewWidth - 2);
        Font font = host.editorFont();
        int markerY = Math.max(previewY + 2, line.y() - font.lineHeight - 1);
        int markerWidth = font.width(CUSTOM_STRIPE_MARKER);
        context.drawString(font, CUSTOM_STRIPE_MARKER, markerX - markerWidth / 2, markerY, 0xFFFFFFFF, true);
    }

    private int stripeIndexAt(int previewX, int previewY, int previewWidth, int previewHeight, double mouseX,
            double mouseY) {
        if (!host.editingCustomPride()) {
            return -1;
        }
        PreviewLineGeometry line = geometry(previewX, previewY, previewWidth, previewHeight);
        if (mouseX < line.x() || mouseX >= line.x() + line.length()
                || mouseY < line.y() - 2 || mouseY >= line.y() + lineWidth + 2) {
            return -1;
        }
        int stripes = Math.max(1, host.customPrideColors().length);
        double position = (mouseX - line.x()) / Math.max(1.0, line.length());
        return clamp((int) Math.floor(position * stripes), 0, stripes - 1);
    }

    private PreviewLineGeometry geometry(int previewX, int previewY, int previewWidth, int previewHeight) {
        int renderedLength = Math.min(lineLength, Math.max(1, previewWidth - 8));
        int centerX = previewX + previewWidth / 2;
        int centerY = previewY + previewHeight / 2;
        return new PreviewLineGeometry(centerX - renderedLength / 2, centerY - lineWidth / 2,
                renderedLength, centerX, centerY);
    }

    private static void drawCenteredRect(GuiGraphics context, int centerX, int centerY, int width,
            int height, float alpha, int rgb, int[] prideColors) {
        drawRect(context, centerX - width / 2, centerY - height / 2, width, height, alpha, rgb, prideColors);
    }

    private static void drawRect(GuiGraphics context, int x, int y, int width, int height, float alpha,
            int rgb, int[] prideColors) {
        if (prideColors == null || prideColors.length == 0) {
            context.fill(x, y, x + width, y + height, MathUtil.argb(alpha, rgb));
            return;
        }
        int segments = Math.min(width, prideColors.length);
        for (int i = 0; i < segments; i++) {
            int startX = x + i * width / segments;
            int endX = x + (i + 1) * width / segments;
            if (endX > startX) {
                context.fill(startX, y, endX, y + height,
                        MathUtil.argb(alpha, PrideFlag.colorAt(prideColors, (i + 0.5) / segments)));
            }
        }
    }
}
