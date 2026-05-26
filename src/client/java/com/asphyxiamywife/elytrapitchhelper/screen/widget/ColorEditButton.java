package com.asphyxiamywife.elytrapitchhelper.screen.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.GuiGraphics.HoveredTextEffects;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class ColorEditButton extends Button {
    private static final int CONTROL_HEIGHT = 20;

    private final int color;
    private final int[] stripeColors;

    public ColorEditButton(Component message, int color, OnPress onPress) {
        this(message, color, null, onPress);
    }

    public ColorEditButton(Component message, int color, int[] stripeColors, OnPress onPress) {
        super(0, 0, 1, CONTROL_HEIGHT, message, onPress, DEFAULT_NARRATION);
        this.color = color & 0x00FFFFFF;
        this.stripeColors = stripeColors;
    }

    @Override
    protected void renderContents(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderDefaultSprite(context);
        renderDefaultLabel(context.textRendererForWidget(this, HoveredTextEffects.NONE));

        int swatchSize = 10;
        int swatchX = getX() + 6;
        int swatchY = getY() + (getHeight() - swatchSize) / 2;
        if (stripeColors == null || stripeColors.length == 0) {
            context.fill(swatchX, swatchY, swatchX + swatchSize, swatchY + swatchSize, 0xFF000000 | color);
        } else {
            int segments = Math.min(swatchSize, stripeColors.length);
            for (int i = 0; i < segments; i++) {
                int startX = swatchX + i * swatchSize / segments;
                int endX = swatchX + (i + 1) * swatchSize / segments;
                if (endX > startX) {
                    int colorIndex = i * stripeColors.length / segments;
                    context.fill(startX, swatchY, endX, swatchY + swatchSize,
                            0xFF000000 | (stripeColors[colorIndex] & 0x00FFFFFF));
                }
            }
        }
        context.renderOutline(swatchX - 1, swatchY - 1, swatchSize + 2, swatchSize + 2, 0xFF000000);
        context.renderOutline(swatchX, swatchY, swatchSize, swatchSize, 0xFFFFFFFF);
    }
}
