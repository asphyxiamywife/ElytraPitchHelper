package com.asphyxiamywife.elytrapitchhelper.screen.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.GuiGraphicsExtractor.HoveredTextEffects;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class CyclingOptionButton extends Button {
    private final Runnable onNext;
    private final Runnable onPrevious;

    public CyclingOptionButton(int x, int y, int width, int height, Component message,
            Runnable onNext, Runnable onPrevious) {
        super(x, y, width, height, message, button -> {}, DEFAULT_NARRATION);
        this.onNext = onNext;
        this.onPrevious = onPrevious;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        onNext.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        extractDefaultSprite(context);
        extractDefaultLabel(context.textRendererForWidget(this, HoveredTextEffects.NONE));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && active && visible && isMouseOver(event.x(), event.y())) {
            onPrevious.run();
            playDownSound(Minecraft.getInstance().getSoundManager());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0.0 || !active || !visible || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        if (scrollY > 0.0) {
            onPrevious.run();
        } else {
            onNext.run();
        }
        playDownSound(Minecraft.getInstance().getSoundManager());
        return true;
    }
}
