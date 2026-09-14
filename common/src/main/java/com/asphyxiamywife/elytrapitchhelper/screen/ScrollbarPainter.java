package com.asphyxiamywife.elytrapitchhelper.screen;

import net.minecraft.client.gui.GuiGraphics;

public final class ScrollbarPainter {
    public static final int BAR_WIDTH = 4;
    private static final int TRACK_COLOR = 0xB4141414;
    private static final int THUMB_COLOR = 0xC8BEBEBE;
    private static final int THUMB_HOVER_COLOR = 0xDCE1E1E1;
    private static final int THUMB_DRAG_COLOR = 0xE6C7C1FF;

    private ScrollbarPainter() {
    }

    public static void paint(GuiGraphics context, int laneX, int laneWidth, int trackY,
            int trackHeight, int thumbY, int thumbHeight, boolean hovered, boolean dragging) {
        int width = Math.min(BAR_WIDTH, laneWidth);
        if (width <= 0 || trackHeight <= 0) {
            return;
        }

        int x = laneX + (laneWidth - width) / 2;
        bar(context, x, trackY, width, trackHeight, TRACK_COLOR);
        bar(context, x, thumbY, width, thumbHeight,
                dragging ? THUMB_DRAG_COLOR : hovered ? THUMB_HOVER_COLOR : THUMB_COLOR);
    }

    private static void bar(GuiGraphics context, int x, int y, int width, int height, int color) {
        if (height <= 0) {
            return;
        }
        if (width < 3 || height < 3) {
            context.fill(x, y, x + width, y + height, color);
            return;
        }
        context.fill(x + 1, y, x + width - 1, y + 1, color);
        context.fill(x, y + 1, x + width, y + height - 1, color);
        context.fill(x + 1, y + height - 1, x + width - 1, y + height, color);
    }
}
