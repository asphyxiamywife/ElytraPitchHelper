package com.asphyxiamywife.elytrapitchhelper.screen.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.GuiGraphics.HoveredTextEffects;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class CategoryTabButton extends Button {
    private static final int FOCUS_OUTLINE_COLOR = 0xFFFFE066;
    private static final int SELECTED_FILL_COLOR = 0xBB777777;
    private static final int SELECTED_FOCUSED_FILL_COLOR = 0xCC888888;
    private static final int IDLE_FILL_COLOR = 0x88454545;
    private static final int HIGHLIGHTED_FILL_COLOR = 0xAA565656;
    private static final int SELECTED_OUTLINE_COLOR = 0xFFFFFFFF;
    private static final int IDLE_OUTLINE_COLOR = 0xFF565656;
    private static final int HIGHLIGHTED_OUTLINE_COLOR = 0xFFE0E0E0;

    private final boolean selected;

    public CategoryTabButton(int x, int y, int width, int height, Component message, boolean selected,
            OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.selected = selected;
    }

    @Override
    protected void renderContents(GuiGraphics context, int mouseX, int mouseY, float delta) {
        boolean focused = isFocused();
        boolean highlighted = isHoveredOrFocused();
        int fillColor = fillColor(focused, highlighted);
        int outlineColor = outlineColor(highlighted);
        context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), fillColor);
        context.renderOutline(getX(), getY(), getWidth(), getHeight(), outlineColor);
        if (focused) {
            context.renderOutline(getX() + 1, getY() + 1, getWidth() - 2, getHeight() - 2, FOCUS_OUTLINE_COLOR);
        }
        if (selected) {
            context.fill(getX() + 2, getY() + getHeight() - 3, getX() + getWidth() - 2,
                    getY() + getHeight() - 1, 0xFFFFFFFF);
        }
        renderDefaultLabel(context.textRendererForWidget(this, HoveredTextEffects.NONE));
    }

    private int fillColor(boolean focused, boolean highlighted) {
        if (selected) {
            return focused ? SELECTED_FOCUSED_FILL_COLOR : SELECTED_FILL_COLOR;
        }
        return highlighted ? HIGHLIGHTED_FILL_COLOR : IDLE_FILL_COLOR;
    }

    private int outlineColor(boolean highlighted) {
        if (selected) {
            return SELECTED_OUTLINE_COLOR;
        }
        return highlighted ? HIGHLIGHTED_OUTLINE_COLOR : IDLE_OUTLINE_COLOR;
    }
}
