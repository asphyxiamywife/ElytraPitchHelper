package com.asphyxiamywife.elytrapitchhelper.screen.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

public final class ColorRow extends SettingRow {
    private static final int SWATCH = 10;
    private static final int SWATCH_GAP = 5;


    private final Supplier<Component> value;
    private final Supplier<int[]> stripes;
    private final Supplier<Integer> color;
    private final Runnable onOpen;

    public ColorRow(Component label, Font font, int depth, Supplier<Integer> color,
            Supplier<int[]> stripes, Supplier<Component> value, Runnable onOpen) {
        super(label, font, depth);
        this.color = color;
        this.stripes = stripes;
        this.value = value;
        this.onOpen = onOpen;
    }

    @Override
    protected void renderControl(GuiGraphics context, int mouseX, int mouseY, float delta) {
        Component reading = narratedValue();
        int swatchX = contentRight() - SWATCH;
        SettingRowPainter.paintRightAligned(context, font(), reading, swatchX - SWATCH_GAP
                + SettingRowPainter.ROW_PADDING, getY(), getHeight(), valueColor(), alpha);
        int swatchY = getY() + (getHeight() - SWATCH) / 2;
        int[] flag = stripes.get();
        if (flag == null || flag.length == 0) {
            context.fill(swatchX, swatchY, swatchX + SWATCH, swatchY + SWATCH,
                    faded(0xFF000000 | (color.get() & 0x00FFFFFF)));
        } else {
            int segments = Math.min(SWATCH, flag.length);
            for (int i = 0; i < segments; i++) {
                int startX = swatchX + i * SWATCH / segments;
                int endX = swatchX + (i + 1) * SWATCH / segments;
                if (endX > startX) {
                    context.fill(startX, swatchY, endX, swatchY + SWATCH,
                            faded(0xFF000000 | (flag[i * flag.length / segments] & 0x00FFFFFF)));
                }
            }
        }
        context.renderOutline(swatchX - 1, swatchY - 1, SWATCH + 2, SWATCH + 2, faded(0xFF000000));
        context.renderOutline(swatchX, swatchY, SWATCH, SWATCH, faded(0x80FFFFFF));
    }

    @Override
    protected Component narratedValue() {
        return value.get();
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
