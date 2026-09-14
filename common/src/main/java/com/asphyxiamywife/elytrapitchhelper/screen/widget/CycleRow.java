package com.asphyxiamywife.elytrapitchhelper.screen.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Supplier;

public final class CycleRow extends SettingRow {
    private static final String BEFORE = "‹ ";
    private static final String AFTER = " ›";

    private final Supplier<Component> value;
    private final Runnable next;
    private final Runnable previous;

    public CycleRow(Component label, Font font, int depth, Supplier<Component> value, Runnable next,
            Runnable previous) {
        super(label, font, depth);
        this.value = value;
        this.next = next;
        this.previous = previous;
    }

    @Override
    protected void renderControl(GuiGraphics context, int mouseX, int mouseY, float delta) {
        drawRightAligned(context, Component.literal(BEFORE).append(narratedValue()).append(AFTER),
                valueColor());
    }

    @Override
    protected Component narratedValue() {
        return value.get();
    }

    @Override
    protected boolean isValidClickButton(net.minecraft.client.input.MouseButtonInfo button) {
        return button.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                || button.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!active || scrollY == 0.0) {
            return false;
        }
        playClick();
        if (scrollY > 0.0) {
            next.run();
        } else {
            previous.run();
        }
        return true;
    }

    @Override
    protected boolean onActivate(boolean backwards) {
        if (backwards) {
            previous.run();
        } else {
            next.run();
        }
        return true;
    }

    @Override
    protected void onRowClick(MouseButtonEvent event, boolean doubleClick) {
        playClick();
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            previous.run();
        } else {
            next.run();
        }
    }
}
