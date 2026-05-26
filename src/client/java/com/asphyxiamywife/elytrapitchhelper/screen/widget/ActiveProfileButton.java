package com.asphyxiamywife.elytrapitchhelper.screen.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class ActiveProfileButton extends Button {
    private final boolean selected;

    public ActiveProfileButton(int x, int y, int width, int height, boolean selected, OnPress onPress) {
        super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
        this.selected = selected;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        extractDefaultSprite(context);

        int boxSize = 12;
        int boxX = getX() + (getWidth() - boxSize) / 2;
        int boxY = getY() + (getHeight() - boxSize) / 2;
        context.fill(boxX, boxY, boxX + boxSize, boxY + boxSize, 0xFF202020);
        context.outline(boxX - 1, boxY - 1, boxSize + 2, boxSize + 2, 0xFF000000);
        context.outline(boxX, boxY, boxSize, boxSize, 0xFFFFFFFF);
        if (selected) {
            context.fill(boxX + 3, boxY + 3, boxX + boxSize - 3, boxY + boxSize - 3, 0xFFFFFFFF);
        }
    }
}
