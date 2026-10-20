package com.asphyxiamywife.elytrapitchhelper.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ColorPaletteProbeScreen extends Screen {
    private final ColorPaletteRenderer palette = new ColorPaletteRenderer();
    private int frames;

    public ColorPaletteProbeScreen() {
        super(Component.literal("EPH palette probe"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics) {
        int size = frames < 3 ? 128 : 160;
        double hue = frames < 2 ? 0.25 : 0.75;
        var layout = new ColorEditorLayout(20, 20, size, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        palette.render(graphics, layout, hue, 0.5, 0.5, frames == 4);
        frames++;
    }

    public boolean complete() {
        return frames >= 5;
    }

    @Override
    public void removed() {
        palette.close();
        palette.close();
    }
}
