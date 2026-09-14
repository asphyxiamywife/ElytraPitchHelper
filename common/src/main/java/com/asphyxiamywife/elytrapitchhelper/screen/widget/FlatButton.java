package com.asphyxiamywife.elytrapitchhelper.screen.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class FlatButton extends Button {
    private static final int BACKGROUND = 0x90000000;
    private static final int BACKGROUND_QUIET = 0x40000000;
    private static final int HOVER_LIFT = 0x26FFFFFF;
    private static final int FOCUS_BORDER = 0xB3C7C1FF;

    private final Font font;
    private boolean quiet;

    protected FlatButton(int x, int y, int width, int height, Component label, OnPress onPress) {
        super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
        this.font = Minecraft.getInstance().font;
    }

    public static FlatButton of(Component label, int x, int y, int width, int height, OnPress onPress) {
        return new FlatButton(x, y, width, height, label, onPress);
    }

    public FlatButton quiet() {
        this.quiet = true;
        return this;
    }

    @Override
    protected void renderContents(GuiGraphics context, int mouseX, int mouseY,
            float delta) {
        float alpha = getAlpha();
        boolean hovered = active && isHovered();
        int background = quiet && !hovered && active ? BACKGROUND_QUIET : BACKGROUND;
        context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(),
                SettingRowPainter.faded(background, alpha));
        if (hovered) {
            context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(),
                    SettingRowPainter.faded(HOVER_LIFT, alpha));
        }
        if (active && isFocused()) {
            context.renderOutline(getX(), getY(), getWidth(), getHeight(),
                    SettingRowPainter.faded(FOCUS_BORDER, alpha));
        }

        Component label = getMessage();
        int color = active ? SettingRowPainter.LABEL_COLOR : SettingRowPainter.DISABLED_COLOR;
        context.drawString(font, label, getX() + (getWidth() - font.width(label)) / 2,
                SettingRowPainter.baseline(getY(), getHeight(), font),
                SettingRowPainter.faded(color, alpha), true);
    }
}
