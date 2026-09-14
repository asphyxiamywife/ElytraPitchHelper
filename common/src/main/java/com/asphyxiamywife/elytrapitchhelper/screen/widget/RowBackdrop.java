package com.asphyxiamywife.elytrapitchhelper.screen.widget;

import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.CommonComponents;

import java.util.function.BooleanSupplier;

public final class RowBackdrop extends AbstractWidget {
    private final BooleanSupplier focusWithin;

    public RowBackdrop(int x, int y, int width, int height, BooleanSupplier focusWithin) {
        super(x, y, width, height, CommonComponents.EMPTY);
        this.focusWithin = focusWithin;
        this.active = false;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY,
            float delta) {
        boolean hovered = mouseX >= getX() && mouseX < getX() + getWidth()
                && mouseY >= getY() && mouseY < getY() + getHeight();
        SettingRowPainter.paintBackground(context, getX(), getY(), getWidth(), getHeight(),
                hovered, focusWithin.getAsBoolean(), getAlpha());
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return false;
    }

    @Override
    public ComponentPath nextFocusPath(FocusNavigationEvent event) {
        return null;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
