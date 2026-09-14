package com.asphyxiamywife.elytrapitchhelper.screen.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.BooleanSupplier;

public abstract class SettingRow extends AbstractWidget {
    public static final int INDENT = SettingRowPainter.INDENT;
    protected static final int LABEL_COLOR = SettingRowPainter.LABEL_COLOR;
    protected static final int VALUE_COLOR = SettingRowPainter.VALUE_COLOR;
    protected static final int DISABLED_COLOR = SettingRowPainter.DISABLED_COLOR;
    protected static final int ACCENT_COLOR = SettingRowPainter.ACCENT_COLOR;

    private static java.util.function.BooleanSupplier overlayOpen = () -> false;

    private final Font font;
    private final Component label;
    private int depth;
    private boolean resetGutter;
    private BooleanSupplier resetModified;
    private Runnable onReset;

    protected SettingRow(Component label, Font font, int depth) {
        super(0, 0, 10, 20, label);
        this.font = font;
        this.label = label;
        this.depth = depth;
    }

    public static void setOverlayOpen(java.util.function.BooleanSupplier supplier) {
        overlayOpen = supplier == null ? () -> false : supplier;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public void setReset(BooleanSupplier modified, Runnable reset) {
        resetGutter = true;
        this.resetModified = modified;
        this.onReset = reset;
    }

    public void reserveResetGutter() {
        resetGutter = true;
    }

    protected Font font() {
        return font;
    }

    protected int rowRight() {
        return getX() + getWidth();
    }

    protected int valueRight() {
        return rowRight() - (resetGutter ? SettingRowPainter.RESET_GUTTER : 0);
    }

    protected int contentRight() {
        return valueRight() - SettingRowPainter.ROW_PADDING;
    }

    protected int faded(int argb) {
        return SettingRowPainter.faded(argb, alpha);
    }

    protected int labelColor() {
        return SettingRowPainter.labelColor(active, depth);
    }

    protected int valueColor() {
        return active ? VALUE_COLOR : DISABLED_COLOR;
    }

    protected void drawRightAligned(GuiGraphicsExtractor context, Component text, int color) {
        SettingRowPainter.paintRightAligned(context, font, text, valueRight(), getY(), getHeight(), color, alpha);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY,
            float delta) {
        SettingRowPainter.paintBackground(context, getX(), getY(), getWidth(), getHeight(),
                active && isHovered() && !overlayOpen.getAsBoolean(), isFocused(), alpha);
        SettingRowPainter.paintLabel(context, font, label, getX(), getY(), getHeight(), depth,
                labelColor(), alpha);
        renderControl(context, mouseX, mouseY, delta);
        if (isResetModified()) {
            boolean resetHovered = active && !overlayOpen.getAsBoolean()
                    && resetZoneContains(getX(), getWidth(), mouseX)
                    && mouseY >= getY() && mouseY < getBottom();
            SettingRowPainter.paintReset(context, font, rowRight(), getY(), getHeight(), resetHovered,
                    active, alpha);
        }
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        boolean backwards = (event.modifiers() & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0;
        if (active && isResetKey(event.key()) && isResetModified()) {
            playClick();
            onReset.run();
            return true;
        }
        if (active && isActivationKey(event.key()) && onActivate(backwards)) {
            playClick();
            return true;
        }
        return super.keyPressed(event);
    }

    public static boolean isActivationKey(int key) {
        return key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER
                || key == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
    }

    public static boolean isResetKey(int key) {
        return key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE;
    }

    public static boolean resetZoneContains(int rowX, int rowWidth, double mouseX) {
        return mouseX >= rowX + rowWidth - SettingRowPainter.RESET_GUTTER
                && mouseX < rowX + rowWidth;
    }

    private boolean isResetModified() {
        return resetModified != null && resetModified.getAsBoolean();
    }

    private boolean handleResetClick(net.minecraft.client.input.MouseButtonEvent event) {
        if (resetModified == null || !resetZoneContains(getX(), getWidth(), event.x())) {
            return false;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && active && isResetModified()) {
            playClick();
            onReset.run();
        }
        return true;
    }

    @Override
    public final void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (!handleResetClick(event)) {
            onRowClick(event, doubleClick);
        }
    }

    protected abstract void onRowClick(net.minecraft.client.input.MouseButtonEvent event,
            boolean doubleClick);

    protected boolean onActivate() {
        return false;
    }

    protected boolean onActivate(boolean backwards) {
        return onActivate();
    }

    protected void playClick() {
        playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());
    }

    protected abstract void renderControl(GuiGraphicsExtractor context, int mouseX, int mouseY,
            float delta);

    protected abstract Component narratedValue();

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    @Override
    public Component getMessage() {
        Component value = narratedValue();
        Component message = value == null || value.getString().isEmpty()
                ? label
                : CommonComponents.optionNameValue(label, value);
        return isResetModified()
                ? Component.empty().append(message).append(" ")
                        .append(Component.translatable("narration.elytrapitchhelper.setting_modified"))
                : message;
    }
}
