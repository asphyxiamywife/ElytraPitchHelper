package com.asphyxiamywife.elytrapitchhelper.screen;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

final class ProfileScrollBar extends AbstractScrollArea {
    private int contentHeight;
    private final BooleanSupplier dragging;

    private ProfileScrollBar(int x, int y, int width, int height, int contentHeight, int scroll,
            BooleanSupplier dragging, Component narration) {
        super(x, y, width, height, narration);
        this.contentHeight = contentHeight;
        this.dragging = dragging;
        setScrollPixels(scroll);
    }

    static ProfileScrollBar forProfiles(int x, int y, int width, int height, int profileCount, int scroll,
            BooleanSupplier dragging) {
        return new ProfileScrollBar(x, y, width, height, profileCount * ConfigScreen.ROW_HEIGHT, scroll,
                dragging,
                Component.translatable("screen.elytrapitchhelper.profile.scrollbar"));
    }

    static ProfileScrollBar forSettings(int x, int y, int width, int height, int contentHeight,
            int scroll, BooleanSupplier dragging) {
        return new ProfileScrollBar(x, y, width, height, contentHeight, scroll, dragging,
                Component.translatable("screen.elytrapitchhelper.config.scrollbar"));
    }

    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (!scrollbarVisible()) {
            return;
        }

        boolean held = dragging.getAsBoolean();
        boolean overBar = isOverScrollbar(mouseX, mouseY);
        ScrollbarPainter.paint(context, scrollBarX(), SCROLLBAR_WIDTH, getY(), getHeight(),
                scrollBarY(), scrollerHeight(), overBar, held);
        if (overBar) {
            context.requestCursor(held ? CursorTypes.RESIZE_NS : CursorTypes.POINTING_HAND);
        }
    }

    @Override
    protected int contentHeight() {
        return contentHeight;
    }

    @Override
    protected double scrollRate() {
        return ConfigScreen.ROW_HEIGHT;
    }

    @Override
    public net.minecraft.client.gui.ComponentPath nextFocusPath(
            net.minecraft.client.gui.navigation.FocusNavigationEvent event) {
        return null;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    void setContentHeight(int contentHeight) {
        this.contentHeight = contentHeight;
    }

    void setScrollPixels(int scroll) {
        super.setScrollAmount(scroll);
    }
}
