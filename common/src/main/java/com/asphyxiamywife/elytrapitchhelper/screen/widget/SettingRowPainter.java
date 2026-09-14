package com.asphyxiamywife.elytrapitchhelper.screen.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class SettingRowPainter {
    public static final int INDENT = 12;
    public static final int LABEL_COLOR = 0xFFFFFFFF;
    public static final int CHILD_LABEL_COLOR = 0xFFC6C6C6;
    public static final int GRANDCHILD_LABEL_COLOR = 0xFFAEAEAE;
    public static final int VALUE_COLOR = 0xFFE8E8E8;
    public static final int DISABLED_COLOR = 0xFFA2A2A2;
    public static final int DERIVED_COLOR = 0xFF8C8C8C;
    public static final int ACCENT_COLOR = 0xFFC7C1FF;
    private static final int HOVER_BACKGROUND = 0xE0000000;
    private static final int FOCUS_BACKGROUND = 0xEA000000;
    private static final int FOCUS_MARKER_COLOR = 0xFFFFE066;
    private static final int FOCUS_MARKER_WIDTH = 2;
    private static final int SEGMENT_BACKGROUND = 0x18FFFFFF;
    private static final int SEGMENT_HOVER = 0x38FFFFFF;
    public static final int ROW_PADDING = 5;
    public static final int RESET_GUTTER = 12;
    private static final Component RESET_GLYPH = Component.literal("\u21BA");

    private SettingRowPainter() {
    }

    public static int faded(int argb, float alpha) {
        int existing = (argb >>> 24) & 0xFF;
        int scaled = Math.round(existing * Math.clamp(alpha, 0.0f, 1.0f));
        return (scaled << 24) | (argb & 0x00FFFFFF);
    }

    public static int labelColor(boolean active, int depth) {
        if (!active) {
            return DISABLED_COLOR;
        }
        if (depth <= 0) {
            return LABEL_COLOR;
        }
        return depth == 1 ? CHILD_LABEL_COLOR : GRANDCHILD_LABEL_COLOR;
    }

    public static int baseline(int y, int height, Font font) {
        return y + (height - font.lineHeight) / 2 + 1;
    }

    public static void paintBackground(GuiGraphics context, int x, int y, int width, int height,
            boolean hovered, boolean focused, float alpha) {
        if (hovered || focused) {
            context.fill(x, y, x + width, y + height,
                    faded(focused ? FOCUS_BACKGROUND : HOVER_BACKGROUND, alpha));
        }
        if (focused) {
            paintFocusMarker(context, x, y, height, alpha);
        }
    }

    public static void paintFocusMarker(GuiGraphics context, int x, int y, int height,
            float alpha) {
        context.fill(x, y + 1, x + FOCUS_MARKER_WIDTH, y + height - 1,
                faded(FOCUS_MARKER_COLOR, alpha));
    }

    public static void paintLabel(GuiGraphics context, Font font, Component label, int x, int y,
            int height, int depth, int color, float alpha) {
        if (label == null) {
            return;
        }
        context.drawString(font, label, x + ROW_PADDING + depth * INDENT, baseline(y, height, font),
                faded(color, alpha), true);
    }

    public static void paintSegment(GuiGraphics context, Font font, Component glyph, int x, int y,
            int width, int height, boolean hovered, boolean usable, float alpha) {
        context.fill(x, y + 2, x + width, y + height - 2,
                faded(hovered && usable ? SEGMENT_HOVER : SEGMENT_BACKGROUND, alpha));
        context.drawString(font, glyph, x + (width - font.width(glyph)) / 2, baseline(y, height, font),
                faded(usable ? VALUE_COLOR : DISABLED_COLOR, alpha), true);
    }

    public static void paintReset(GuiGraphics context, Font font, int rowRight, int y, int height,
            boolean hovered, boolean usable, float alpha) {
        paintSegment(context, font, RESET_GLYPH, rowRight - RESET_GUTTER, y, RESET_GUTTER, height,
                hovered, usable, alpha);
    }

    public static void paintCentered(GuiGraphics context, Font font, Component text, int x,
            int width, int y, int height, int color, float alpha) {
        context.drawString(font, text, x + (width - font.width(text)) / 2, baseline(y, height, font),
                faded(color, alpha), true);
    }

    public static void paintRightAligned(GuiGraphics context, Font font, Component text, int right,
            int y, int height, int color, float alpha) {
        context.drawString(font, text, right - ROW_PADDING - font.width(text), baseline(y, height, font),
                faded(color, alpha), true);
    }
}
