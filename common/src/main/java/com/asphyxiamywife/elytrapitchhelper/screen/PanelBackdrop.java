package com.asphyxiamywife.elytrapitchhelper.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;

final class PanelBackdrop {
    static final int ROW_BLEED = 1;
    private static final int GROUP_COLOR = 0x40000000;
    private static final int HEADER_COLOR = 0x60000000;

    private PanelBackdrop() {
    }

    static void paintBand(GuiGraphicsExtractor context, int x, int width, int top, int bottom,
            boolean header) {
        if (bottom <= top || width <= 0) {
            return;
        }
        context.fill(x, top, x + width, bottom, header ? HEADER_COLOR : GROUP_COLOR);
    }
}
