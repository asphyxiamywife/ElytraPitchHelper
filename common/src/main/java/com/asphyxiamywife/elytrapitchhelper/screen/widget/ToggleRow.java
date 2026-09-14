package com.asphyxiamywife.elytrapitchhelper.screen.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class ToggleRow extends SettingRow {
    private static final int ON_COLOR = 0xFF8FD98F;

    private final BooleanSupplier value;
    private final Consumer<Boolean> onChange;
    private final Component onLabel;
    private final Component offLabel;

    public ToggleRow(Component label, Font font, int depth, BooleanSupplier value,
            Consumer<Boolean> onChange, Component onLabel, Component offLabel) {
        super(label, font, depth);
        this.value = value;
        this.onChange = onChange;
        this.onLabel = onLabel;
        this.offLabel = offLabel;
    }

    private boolean on() {
        return value.getAsBoolean();
    }

    @Override
    protected void renderControl(GuiGraphics context, int mouseX, int mouseY, float delta) {
        drawRightAligned(context, narratedValue(),
                !active ? DISABLED_COLOR : on() ? ON_COLOR : VALUE_COLOR);
    }

    @Override
    protected Component narratedValue() {
        return on() ? onLabel : offLabel;
    }

    @Override
    protected boolean onActivate() {
        onChange.accept(!on());
        return true;
    }

    @Override
    protected void onRowClick(MouseButtonEvent event, boolean doubleClick) {
        playClick();
        onChange.accept(!on());
    }
}
