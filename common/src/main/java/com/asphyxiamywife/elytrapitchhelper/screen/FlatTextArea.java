package com.asphyxiamywife.elytrapitchhelper.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.FittingMultiLineTextWidget;
import net.minecraft.network.chat.Component;

final class FlatTextArea extends FittingMultiLineTextWidget {
    private static final int BACKGROUND = 0x90000000;

    FlatTextArea(int x, int y, int width, int height, Component message, Font font) {
        super(x, y, width, height, message, font);
    }

    @Override
    protected void renderBackground(GuiGraphics context) {
        context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), BACKGROUND);
    }

    @Override
    protected void renderScrollbar(GuiGraphics context, int mouseX, int mouseY) {
        if (!scrollbarVisible()) {
            return;
        }
        ScrollbarPainter.paint(context, scrollBarX(), SCROLLBAR_WIDTH, getY(), getHeight(),
                scrollBarY(), scrollerHeight(), isOverScrollbar(mouseX, mouseY), false);
    }
}
