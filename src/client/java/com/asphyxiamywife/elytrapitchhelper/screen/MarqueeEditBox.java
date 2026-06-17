package com.asphyxiamywife.elytrapitchhelper.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

final class MarqueeEditBox extends EditBox {
    private static final long EDGE_HOLD_MILLIS = 850L;
    private static final int PIXELS_PER_SECOND = 18;
    private static final int TRANSPARENT_TEXT_COLOR = 0x00000000;

    private final Font textRenderer;
    private int textColor = EditBox.DEFAULT_TEXT_COLOR;
    private String marqueeValue = "";
    private int marqueeInnerWidth = -1;
    private long marqueeStartedMillis = System.currentTimeMillis();
    private boolean marqueeHovered;

    MarqueeEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
        this.textRenderer = font;
    }

    @Override
    public void setTextColor(int textColor) {
        super.setTextColor(textColor);
        this.textColor = textColor;
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        String value = getValue();
        int innerWidth = getInnerWidth();
        int textWidth = textRenderer.width(value);
        boolean hovered = isHovered();
        if (!hovered || isFocused() || value.isEmpty() || textWidth <= innerWidth) {
            marqueeHovered = false;
            super.extractWidgetRenderState(context, mouseX, mouseY, delta);
            return;
        }

        updateMarqueeStart(value, innerWidth, !marqueeHovered);
        marqueeHovered = true;
        super.setTextColor(TRANSPARENT_TEXT_COLOR);
        try {
            super.extractWidgetRenderState(context, mouseX, mouseY, delta);
        } finally {
            super.setTextColor(textColor);
        }

        int textX = isBordered() ? getX() + 4 : getX();
        int textY = isBordered() ? getY() + (getHeight() - 8) / 2 : getY();
        int overflow = textWidth - innerWidth;
        int offset = marqueeOffset(overflow);
        context.enableScissor(textX, getY(), textX + innerWidth, getY() + getHeight());
        context.text(textRenderer, value, textX - offset, textY, textColor, true);
        context.disableScissor();
    }

    private void updateMarqueeStart(String value, int innerWidth, boolean forceRestart) {
        if (forceRestart || !value.equals(marqueeValue) || innerWidth != marqueeInnerWidth) {
            marqueeValue = value;
            marqueeInnerWidth = innerWidth;
            marqueeStartedMillis = System.currentTimeMillis();
        }
    }

    private int marqueeOffset(int overflow) {
        long travelMillis = Math.max(1L, overflow * 1000L / PIXELS_PER_SECOND);
        long cycleMillis = travelMillis * 2L + EDGE_HOLD_MILLIS * 2L;
        long elapsed = (System.currentTimeMillis() - marqueeStartedMillis) % cycleMillis;
        if (elapsed < EDGE_HOLD_MILLIS) {
            return 0;
        }

        elapsed -= EDGE_HOLD_MILLIS;
        if (elapsed < travelMillis) {
            return Math.round(overflow * (elapsed / (float) travelMillis));
        }

        elapsed -= travelMillis;
        if (elapsed < EDGE_HOLD_MILLIS) {
            return overflow;
        }

        elapsed -= EDGE_HOLD_MILLIS;
        return Math.round(overflow * (1.0F - elapsed / (float) travelMillis));
    }
}
