package com.asphyxiamywife.elytrapitchhelper.screen.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class RowActionButton extends AbstractWidget {
    private final Font font;
    private final Runnable onPress;
    private final boolean takeMouseFocus;

    public RowActionButton(int x, int y, int width, int height, Component label, Font font,
            Runnable onPress) {
        this(x, y, width, height, label, font, onPress, true);
    }

    public RowActionButton(int x, int y, int width, int height, Component label, Font font,
            Runnable onPress, boolean takeMouseFocus) {
        super(x, y, width, height, label);
        this.font = font;
        this.onPress = onPress;
        this.takeMouseFocus = takeMouseFocus;
    }

    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY,
            float delta) {
        SettingRowPainter.paintSegment(context, font, getMessage(), getX(), getY(), getWidth(),
                getHeight(), isHovered(), active, getAlpha());
        if (isFocused()) {
            SettingRowPainter.paintFocusMarker(context, getX(), getY(), getHeight(), getAlpha());
        }
    }

    @Override
    public boolean shouldTakeFocusAfterInteraction() {
        return takeMouseFocus;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        playDownSound(Minecraft.getInstance().getSoundManager());
        onPress.run();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (active && SettingRow.isActivationKey(event.key())) {
            playDownSound(Minecraft.getInstance().getSoundManager());
            onPress.run();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
