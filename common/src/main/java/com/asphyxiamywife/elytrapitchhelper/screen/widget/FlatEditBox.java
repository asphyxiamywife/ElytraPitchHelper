package com.asphyxiamywife.elytrapitchhelper.screen.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.joml.Matrix3x2fStack;

public class FlatEditBox extends EditBox {
    public static final int PAD_X = 4;
    private static final int LINE_HEIGHT = 8;
    private static final int BACKGROUND = 0x90000000;
    private static final int FOCUS_BORDER = 0xB3C7C1FF;
    protected static final Style DEFAULT_HINT_STYLE = Style.EMPTY.withColor(0xC6C6C6);

    private int background = BACKGROUND;
    private int focusBorder = FOCUS_BORDER;

    public FlatEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
        setBordered(false);
    }

    public FlatEditBox withColors(int background, int focusBorder) {
        this.background = background;
        this.focusBorder = focusBorder;
        return this;
    }

    @Override
    public void setHint(Component hint) {
        super.setHint(styledHint(hint));
    }

    protected static Component styledHint(Component hint) {
        return hint.getStyle().equals(Style.EMPTY) ? hint.copy().withStyle(DEFAULT_HINT_STYLE) : hint;
    }

    @Override
    public int getInnerWidth() {
        return Math.max(0, getWidth() - PAD_X * 2);
    }

    protected int textLeft() {
        return getX() + PAD_X;
    }

    protected int textTop() {
        return getY() + (getHeight() - LINE_HEIGHT) / 2;
    }

    @Override
    public void renderWidget(GuiGraphics context, int mouseX, int mouseY,
            float delta) {
        if (!isVisible()) {
            return;
        }

        context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), background);
        if (isFocused()) {
            context.renderOutline(getX(), getY(), getWidth(), getHeight(), focusBorder);
        }

        Matrix3x2fStack pose = context.pose();
        pose.pushMatrix();
        pose.translate(PAD_X, (getHeight() - LINE_HEIGHT) / 2.0f);
        super.renderWidget(context, mouseX, mouseY, delta);
        pose.popMatrix();
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        super.onClick(inset(event), doubleClick);
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dx, double dy) {
        super.onDrag(inset(event), dx, dy);
    }

    private static MouseButtonEvent inset(MouseButtonEvent event) {
        return new MouseButtonEvent(event.x() - PAD_X, event.y(), event.buttonInfo());
    }
}
