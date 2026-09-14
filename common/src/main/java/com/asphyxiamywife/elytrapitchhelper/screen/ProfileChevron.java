package com.asphyxiamywife.elytrapitchhelper.screen;

import net.minecraft.client.gui.GuiGraphics;

final class ProfileChevron {
    static final int SIZE = 7;
    private static final int STEPS = (SIZE + 1) / 2;
    private static final int CLOSED_COLOR = 0xFFB0B0B0;
    private static final int OPEN_COLOR = 0xFFFFFFFF;

    private ProfileChevron() {
    }

    static void paint(GuiGraphics context, ProfileRowLayout row, int rowY, boolean open) {
        int x = row.chevronX() + (row.chevronWidth() - SIZE) / 2;
        int y = rowY + (ConfigScreen.CONTROL_HEIGHT - SIZE) / 2;
        int color = open ? OPEN_COLOR : CLOSED_COLOR;
        if (open) {
            for (int step = 0; step < STEPS; step++) {
                context.fill(x + step, y + step, x + SIZE - step, y + step + 1, color);
            }
        } else {
            for (int step = 0; step < STEPS; step++) {
                context.fill(x + step, y + step, x + step + 1, y + SIZE - step, color);
            }
        }
    }
}
