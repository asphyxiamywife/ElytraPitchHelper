package com.asphyxiamywife.elytrapitchhelper.screen.widget;

import net.minecraft.client.gui.components.AbstractSliderButton;
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
    private double lastAppliedValue;

    public NumberSlider(int x, int y, int width, int height, Component label, double current, double min, double max,
            double step, ValueFormatter formatter, DoubleConsumer onChange) {
        super(x, y, width, height, Component.empty(), normalize(current, min, max));
        this.label = label;
        this.min = min;
        this.max = max;
        this.step = step;
        this.formatter = formatter;
        this.onChange = onChange;
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
            onChange.accept(current);
        }
        updateMessage();
    }

    public void setCurrent(double current) {
        value = normalize(current, min, max);
        lastAppliedValue = currentValue();
        updateMessage();
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
}
