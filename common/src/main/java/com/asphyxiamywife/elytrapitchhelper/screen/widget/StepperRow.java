package com.asphyxiamywife.elytrapitchhelper.screen.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class StepperRow extends SettingRow {
    private static final int STEP_WIDTH = 13;
    private static final int GAP = 6;

    private final Supplier<Component> value;
    private final Runnable onPrevious;
    private final Runnable onNext;
    private final Runnable onRemove;
    private final Runnable onAdd;
    private final BooleanSupplier canRemove;
    private final BooleanSupplier canAdd;

    public StepperRow(Component label, Font font, int depth, Supplier<Component> value,
            Runnable onPrevious, Runnable onNext, Runnable onRemove, Runnable onAdd,
            BooleanSupplier canRemove, BooleanSupplier canAdd) {
        super(label, font, depth);
        this.value = value;
        this.onPrevious = onPrevious;
        this.onNext = onNext;
        this.onRemove = onRemove;
        this.onAdd = onAdd;
        this.canRemove = canRemove;
        this.canAdd = canAdd;
    }

    private int addX() {
        return contentRight() - STEP_WIDTH;
    }

    private int removeX() {
        return addX() - GAP - font().width(narratedValue()) - GAP - STEP_WIDTH;
    }

    @Override
    protected void renderControl(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        Component reading = narratedValue();
        int baseline = SettingRowPainter.baseline(getY(), getHeight(), font());
        drawSegment(context, removeX(), mouseX, mouseY, Component.literal("−"), canRemove.getAsBoolean());
        context.text(font(), reading, removeX() + STEP_WIDTH + GAP, baseline, faded(valueColor()), true);
        drawSegment(context, addX(), mouseX, mouseY, Component.literal("+"), canAdd.getAsBoolean());
    }

    private void drawSegment(GuiGraphicsExtractor context, int x, int mouseX, int mouseY, Component glyph,
            boolean usable) {
        boolean hovered = mouseX >= x && mouseX < x + STEP_WIDTH
                && mouseY >= getY() && mouseY < getY() + getHeight();
        SettingRowPainter.paintSegment(context, font(), glyph, x, getY(), STEP_WIDTH, getHeight(),
                hovered, usable && active, alpha);
    }

    @Override
    protected Component narratedValue() {
        return value.get();
    }

    @Override
    protected boolean onActivate(boolean backwards) {
        if (backwards) {
            onPrevious.run();
        } else {
            onNext.run();
        }
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (active) {
            boolean add = event.key() == GLFW.GLFW_KEY_EQUAL || event.key() == GLFW.GLFW_KEY_KP_ADD;
            boolean remove = event.key() == GLFW.GLFW_KEY_MINUS || event.key() == GLFW.GLFW_KEY_KP_SUBTRACT;
            if (add && canAdd.getAsBoolean()) {
                playClick();
                onAdd.run();
                return true;
            }
            if (remove && canRemove.getAsBoolean()) {
                playClick();
                onRemove.run();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    protected boolean isValidClickButton(net.minecraft.client.input.MouseButtonInfo button) {
        return button.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                || button.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
    }

    @Override
    protected void onRowClick(MouseButtonEvent event, boolean doubleClick) {
        if (event.x() >= addX()) {
            step(canAdd.getAsBoolean(), onAdd);
            return;
        }
        if (event.x() >= removeX() && event.x() < removeX() + STEP_WIDTH) {
            step(canRemove.getAsBoolean(), onRemove);
            return;
        }
        playClick();
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            onPrevious.run();
        } else {
            onNext.run();
        }
    }

    private void step(boolean usable, Runnable action) {
        if (!usable) {
            return;
        }
        playClick();
        action.run();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!active || scrollY == 0.0) {
            return false;
        }
        playClick();
        if (scrollY > 0.0) {
            onNext.run();
        } else {
            onPrevious.run();
        }
        return true;
    }
}
