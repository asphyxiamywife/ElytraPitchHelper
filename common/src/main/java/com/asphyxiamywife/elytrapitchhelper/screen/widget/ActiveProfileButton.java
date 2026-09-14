package com.asphyxiamywife.elytrapitchhelper.screen.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class ActiveProfileButton extends Button {
    private final boolean selected;

    public ActiveProfileButton(int x, int y, int width, int height, boolean selected, Component narration,
            OnPress onPress) {
        super(x, y, width, height, narration, onPress, DEFAULT_NARRATION);
        this.selected = selected;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int size = 12;
        int x = getX() + (getWidth() - size) / 2;
        int y = getY() + (getHeight() - size) / 2;
        drawDisc(context, x, y, size, 0xFF000000);
        drawDisc(context, x + 1, y + 1, size - 2, 0xFFFFFFFF);
        drawDisc(context, x + 2, y + 2, size - 4, 0xFF202020);
        if (selected) {
            drawDisc(context, x + 4, y + 4, size - 8, 0xFFFFFFFF);
        }
    }

    private static void drawDisc(GuiGraphicsExtractor context, int x, int y, int size, int color) {
        if (size <= 0) {
            return;
        }
        if (size <= 4) {
            context.fill(x, y, x + size, y + size, color);
            return;
        }
        context.fill(x + 1, y, x + size - 1, y + size, color);
        context.fill(x, y + 1, x + size, y + size - 1, color);
    }
}
