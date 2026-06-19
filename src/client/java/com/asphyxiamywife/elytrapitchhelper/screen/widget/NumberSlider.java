package com.asphyxiamywife.elytrapitchhelper.screen.widget;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;

public final class NumberSlider extends AbstractSliderButton {
    private final Component label;
    private final double min;
    private final double max;
    private final double step;
    private final ValueFormatter formatter;
    private final DoubleConsumer onChange;
    private final String actionKey;
    private final InteractionListener interactionListener;
    private double lastAppliedValue;
    private boolean interactionOpen;
    private boolean interactionChanged;

    public NumberSlider(int x, int y, int width, int height, Component label, double current, double min, double max,
            double step, ValueFormatter formatter, DoubleConsumer onChange) {
        this(x, y, width, height, label, current, min, max, step, formatter, onChange, "slider",
                InteractionListener.NONE);
    }

    public NumberSlider(int x, int y, int width, int height, Component label, double current, double min, double max,
            double step, ValueFormatter formatter, DoubleConsumer onChange, String actionKey,
            InteractionListener interactionListener) {
        super(x, y, width, height, Component.empty(), normalize(current, min, max));
        this.label = label;
        this.min = min;
        this.max = max;
        this.step = step;
        this.formatter = formatter;
        this.onChange = onChange;
        this.actionKey = actionKey;
        this.interactionListener = interactionListener;
        this.lastAppliedValue = currentValue();
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        setMessage(CommonComponents.optionNameValue(label, Component.literal(formatter.format(currentValue()))));
    }

    @Override
    protected void applyValue() {
        double current = currentValue();
        if (Double.compare(current, lastAppliedValue) != 0) {
            lastAppliedValue = current;
            interactionChanged = true;
            onChange.accept(current);
        }
        updateMessage();
    }

    public void setCurrent(double current) {
        value = normalize(current, min, max);
        lastAppliedValue = currentValue();
        updateMessage();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0.0 || !active || !visible || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        beginInteraction();
        double increment = step > 0.0 ? step : (max - min) / 100.0;
        double next = currentValue() + (scrollY > 0.0 ? increment : -increment);
        value = normalize(next, min, max);
        applyValue();
        finishInteraction(true);
        return true;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        beginInteraction();
        super.onClick(event, doubleClick);
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        super.onRelease(event);
        finishInteraction(false);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean valueKey = event.isLeft() || event.isRight();
        if (valueKey) {
            beginInteraction();
        }
        boolean handled = super.keyPressed(event);
        if (valueKey) {
            finishInteraction(true);
        }
        return handled;
    }

    private void beginInteraction() {
        if (interactionOpen) {
            return;
        }
        interactionOpen = true;
        interactionChanged = false;
        interactionListener.begin(actionKey);
    }

    private void finishInteraction(boolean coalesce) {
        if (!interactionOpen) {
            return;
        }
        interactionOpen = false;
        interactionListener.end(actionKey, interactionChanged, coalesce);
        interactionChanged = false;
    }

    private double currentValue() {
        double current = min + (max - min) * value;
        if (step > 0.0) {
            current = Math.round(current / step) * step;
        }
        return Math.max(min, Math.min(max, current));
    }

    private static double normalize(double current, double min, double max) {
        return Math.max(0.0, Math.min(1.0, (current - min) / (max - min)));
    }

    @FunctionalInterface
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
