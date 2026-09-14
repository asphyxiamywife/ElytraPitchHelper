package com.asphyxiamywife.elytrapitchhelper.screen.widget;

import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

public final class DerivedRow extends SettingRow {
    private final Supplier<Component> value;

    public DerivedRow(Component label, Font font, int depth, Supplier<Component> value) {
        super(label, font, depth);
        this.value = value;
        this.active = false;
    }

    @Override
    protected void renderControl(GuiGraphics context, int mouseX, int mouseY, float delta) {
        drawRightAligned(context, narratedValue(), SettingRowPainter.DERIVED_COLOR);
    }

    @Override
    protected Component narratedValue() {
        return value.get();
    }

    @Override
    protected int labelColor() {
        return SettingRowPainter.DERIVED_COLOR;
    }

    @Override
    protected void onRowClick(MouseButtonEvent event, boolean doubleClick) {
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return false;
    }

    @Override
    public ComponentPath nextFocusPath(FocusNavigationEvent event) {
        return null;
    }
}
