package com.asphyxiamywife.elytrapitchhelper.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.FittingMultiLineTextWidget;
import net.minecraft.network.chat.Component;

final class FlatTextArea extends FittingMultiLineTextWidget {
    private static final int BACKGROUND = 0x90000000;

    FlatTextArea(int x, int y, int width, int height, Component message, Font font) {
        super(x, y, width, height, message, font);
    }

    @Override
    protected void extractBackground(GuiGraphicsExtractor context) {
        context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), BACKGROUND);
    }

    @Override
    protected void extractScrollbar(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        if (!scrollable()) {
            return;
        }
        ScrollbarPainter.paint(context, scrollBarX(), scrollbarWidth(), getY(), getHeight(),
                scrollBarY(), scrollerHeight(), isOverScrollbar(mouseX, mouseY), false);
    }
}
