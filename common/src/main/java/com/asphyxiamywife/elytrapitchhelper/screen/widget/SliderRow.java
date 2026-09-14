package com.asphyxiamywife.elytrapitchhelper.screen.widget;

import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.function.DoubleConsumer;

public final class SliderRow extends SettingRow {
    private static final int TRACK_WIDTH = 74;
    private static final int TRACK_HEIGHT = 2;
    private static final int HANDLE_WIDTH = 3;
    private static final int HANDLE_HEIGHT = 10;
    private static final int VALUE_GAP = 6;
    private static final int TRACK_COLOR = 0x60FFFFFF;
    private static final int TRACK_DISABLED = 0x30FFFFFF;
    private static final int VALUE_RESERVE = 52;
    private static final int GRAB_MARGIN = 4;

    private final double min;
    private final double max;
    private final double step;
    private final ValueFormatter formatter;
    private final DoubleConsumer onChange;
    private final String actionKey;
    private final InteractionListener interactions;
    private int valueReserve = VALUE_RESERVE;
    private int labelReserve;
    private double value;
    private boolean dragging;
    private boolean interactionOpen;
    private boolean interactionChanged;

    public SliderRow(Component label, Font font, int depth, double current, double min, double max,
            double step, ValueFormatter formatter, DoubleConsumer onChange,
            String actionKey, InteractionListener interactions) {
        super(label, font, depth);
        this.min = min;
        this.max = max;
        this.step = step;
        this.formatter = formatter;
        this.onChange = onChange;
        this.actionKey = actionKey;
        this.interactions = interactions;
        this.value = MathUtil.clamp(current, min, max);
    }

    public void setCurrent(double current) {
        value = MathUtil.clamp(current, min, max);
    }

    public void setColumnWidths(int labelWidth, int valueWidth) {
        labelReserve = labelWidth + SettingRowPainter.ROW_PADDING + VALUE_GAP;
        valueReserve = valueWidth;
    }

    private int trackWidth() {
        int available = contentRight() - valueReserve - VALUE_GAP - getX() - labelReserve;
        return Math.max(HANDLE_WIDTH, Math.min(TRACK_WIDTH, available));
    }

    private int trackLeft() {
        return contentRight() - valueReserve - VALUE_GAP - trackWidth();
    }

    public static int trackLeftForTesting(int rowX, int rowWidth) {
        return trackLeftForTesting(rowX, rowWidth, true);
    }

    public static int trackLeftForTesting(int rowX, int rowWidth, boolean resetGutter) {
        return rowX + rowWidth - (resetGutter ? SettingRowPainter.RESET_GUTTER : 0)
                - SettingRowPainter.ROW_PADDING
                - VALUE_RESERVE - VALUE_GAP - TRACK_WIDTH;
    }

    public static boolean onTrackForTesting(int rowX, int rowWidth, double mouseX) {
        return onTrackForTesting(rowX, rowWidth, mouseX, true);
    }

    public static boolean onTrackForTesting(int rowX, int rowWidth, double mouseX,
            boolean resetGutter) {
        int left = trackLeftForTesting(rowX, rowWidth, resetGutter);
        return mouseX >= left - GRAB_MARGIN && mouseX <= left + TRACK_WIDTH + GRAB_MARGIN;
    }

    private double fraction() {
        return max <= min ? 0.0 : (value - min) / (max - min);
    }

    @Override
    protected void renderControl(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int left = trackLeft();
        int trackWidth = trackWidth();
        int centreY = getY() + getHeight() / 2;
        int trackTop = centreY - TRACK_HEIGHT / 2;
        context.fill(left, trackTop, left + trackWidth, trackTop + TRACK_HEIGHT,
                faded(active ? TRACK_COLOR : TRACK_DISABLED));

        int filled = (int) Math.round(fraction() * trackWidth);
        if (filled > 0) {
            context.fill(left, trackTop, left + filled, trackTop + TRACK_HEIGHT,
                    faded(active ? ACCENT_COLOR : TRACK_DISABLED));
        }

        int handleX = MathUtil.clamp(left + filled - HANDLE_WIDTH / 2, left, left + trackWidth - HANDLE_WIDTH);
        context.fill(handleX, centreY - HANDLE_HEIGHT / 2, handleX + HANDLE_WIDTH,
                centreY + HANDLE_HEIGHT / 2, faded(active ? LABEL_COLOR : DISABLED_COLOR));

        drawRightAligned(context, narratedValue(), valueColor());
    }

    @Override
    protected Component narratedValue() {
        return Component.literal(formatter.format(value));
    }

    private void applyFromMouse(double mouseX) {
        int left = trackLeft();
        double fraction = MathUtil.clamp01((mouseX - left) / (double) trackWidth());
        setValue(min + fraction * (max - min));
    }

    private void setValue(double next) {
        double stepped = step > 0.0 ? Math.round(next / step) * step : next;
        double clamped = MathUtil.clamp(stepped, min, max);
        if (Double.compare(clamped, value) == 0) {
            return;
        }
        value = clamped;
        interactionChanged = true;
        onChange.accept(clamped);
    }

    private void openInteraction() {
        if (!interactionOpen) {
            interactionOpen = true;
            interactionChanged = false;
            interactions.begin(actionKey);
        }
    }

    private void closeInteraction() {
        if (interactionOpen) {
            interactionOpen = false;
            interactions.end(actionKey, interactionChanged, true);
            interactionChanged = false;
        }
    }

    private boolean onTrack(double mouseX) {
        int left = trackLeft();
        return mouseX >= left - GRAB_MARGIN && mouseX <= left + trackWidth() + GRAB_MARGIN;
    }

    @Override
    protected void onRowClick(MouseButtonEvent event, boolean doubleClick) {
        if (!onTrack(event.x())) {
            return;
        }
        playClick();
        openInteraction();
        dragging = true;
        applyFromMouse(event.x());
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        dragging = false;
        closeInteraction();
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging) {
            applyFromMouse(event.x());
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!active || scrollY == 0.0) {
            return false;
        }
        openInteraction();
        setValue(value + Math.signum(scrollY) * (step > 0.0 ? step : 1.0));
        closeInteraction();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int direction = switch (event.key()) {
            case InputConstants.KEY_LEFT -> -1;
            case InputConstants.KEY_RIGHT -> 1;
            default -> 0;
        };
        if (!active || direction == 0) {
            return super.keyPressed(event);
        }
        openInteraction();
        setValue(value + direction * (step > 0.0 ? step : 1.0));
        closeInteraction();
        return true;
    }

    public interface ValueFormatter {
        String format(double value);
    }

    public interface InteractionListener {
        InteractionListener NONE = new InteractionListener() {
            @Override
            public void begin(String actionKey) {
            }

            @Override
            public void end(String actionKey, boolean changed, boolean coalesce) {
            }
        };

        void begin(String actionKey);

        void end(String actionKey, boolean changed, boolean coalesce);
    }
}
