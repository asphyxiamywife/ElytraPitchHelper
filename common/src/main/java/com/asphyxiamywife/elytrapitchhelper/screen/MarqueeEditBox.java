package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.screen.widget.FlatEditBox;
import com.asphyxiamywife.elytrapitchhelper.util.MonotonicClock;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

final class MarqueeEditBox extends FlatEditBox {
    private static final long EDGE_HOLD_MILLIS = 850L;
    private static final int PIXELS_PER_SECOND = 18;
    private static final int TRANSPARENT_TEXT_COLOR = 0x00000000;

    private final Font textRenderer;
    private final Runnable onFocusLost;
    private final boolean hoverToMarquee;
    private int textColor = EditBox.DEFAULT_TEXT_COLOR;
    private Component marqueeHint = Component.empty();
    private String marqueeValue = "";
    private int marqueeInnerWidth = -1;
    private long marqueeStartedMillis = MonotonicClock.millis();
    private boolean marqueeWasActive;

    MarqueeEditBox(Font font, int x, int y, int width, int height, Component message, Runnable onFocusLost) {
        this(font, x, y, width, height, message, onFocusLost, true);
    }

    MarqueeEditBox(Font font, int x, int y, int width, int height, Component message,
            Runnable onFocusLost, boolean hoverToMarquee) {
        super(font, x, y, width, height, message);
        this.textRenderer = font;
        this.onFocusLost = onFocusLost;
        this.hoverToMarquee = hoverToMarquee;
    }

    @Override
    public void setFocused(boolean focused) {
        boolean wasFocused = isFocused();
        super.setFocused(focused);
        if (wasFocused && !focused && onFocusLost != null) {
            onFocusLost.run();
        }
    }

    @Override
    public void setTextColor(int textColor) {
        super.setTextColor(textColor);
        this.textColor = textColor;
    }

    @Override
    public void setHint(Component hint) {
        marqueeHint = styledHint(hint);
        super.setHint(marqueeHint);
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        String value = getValue();
        boolean showingHint = value.isEmpty() && !isFocused() && !marqueeHint.getString().isEmpty();
        String displayedText = showingHint ? marqueeHint.getString() : value;
        int innerWidth = getInnerWidth();
        int textWidth = textRenderer.width(displayedText);
        boolean hovered = isHovered();
        boolean marqueeActive = !hoverToMarquee || hovered;
        if (!marqueeActive || isFocused() || displayedText.isEmpty() || textWidth <= innerWidth) {
            marqueeWasActive = false;
            super.extractWidgetRenderState(context, mouseX, mouseY, delta);
            return;
        }

        updateMarqueeStart(displayedText, innerWidth, !marqueeWasActive);
        marqueeWasActive = true;
        if (showingHint) {
            super.setHint(Component.empty());
        } else {
            super.setTextColor(TRANSPARENT_TEXT_COLOR);
        }
        try {
            super.extractWidgetRenderState(context, mouseX, mouseY, delta);
        } finally {
            if (showingHint) {
                super.setHint(marqueeHint);
            } else {
                super.setTextColor(textColor);
            }
        }

        int textX = textLeft();
        int textY = textTop();
        int overflow = textWidth - innerWidth;
        int offset = marqueeOffset(overflow);
        context.enableScissor(textX, getY(), textX + innerWidth, getY() + getHeight());
        Component renderedText = showingHint ? marqueeHint : Component.literal(displayedText);
        context.text(textRenderer, renderedText, textX - offset, textY, textColor, true);
        context.disableScissor();
    }

    private void updateMarqueeStart(String value, int innerWidth, boolean forceRestart) {
        if (forceRestart || !value.equals(marqueeValue) || innerWidth != marqueeInnerWidth) {
            marqueeValue = value;
            marqueeInnerWidth = innerWidth;
            marqueeStartedMillis = MonotonicClock.millis();
        }
    }

    private int marqueeOffset(int overflow) {
        long travelMillis = Math.max(1L, overflow * 1000L / PIXELS_PER_SECOND);
        long cycleMillis = travelMillis * 2L + EDGE_HOLD_MILLIS * 2L;
        long elapsed = Math.floorMod(MonotonicClock.millis() - marqueeStartedMillis, cycleMillis);
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
