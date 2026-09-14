package com.asphyxiamywife.elytrapitchhelper.screen.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public final class ScreenRow extends SettingRow {
    private static final String MARKER = "›";

    private final Runnable onOpen;

    public ScreenRow(Component label, Font font, int depth, Runnable onOpen) {
        super(label, font, depth);
        this.onOpen = onOpen;
    }

    @Override
    protected void renderControl(GuiGraphics context, int mouseX, int mouseY, float delta) {
        drawRightAligned(context, Component.literal(MARKER), valueColor());
    }

    @Override
    protected Component narratedValue() {
        return CommonComponents.EMPTY;
    }

    @Override
    protected boolean onActivate() {
        onOpen.run();
        return true;
    }

    @Override
    protected void onRowClick(MouseButtonEvent event, boolean doubleClick) {
        playClick();
        onOpen.run();
    }
}
