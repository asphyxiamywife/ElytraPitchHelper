package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.PrideFlag;
import com.asphyxiamywife.elytrapitchhelper.config.VoidWarningSettings;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.ColorRow;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.CycleRow;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.SliderRow;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

final class SettingControlFactory {
    private final Supplier<Font> font;
    private final SliderRow.InteractionListener sliderHistory;
    private final Consumer<Screen> openScreen;
    private final Duration tooltipDelay;

    SettingControlFactory(Supplier<Font> font, SliderRow.InteractionListener sliderHistory,
            Consumer<Screen> openScreen, Duration tooltipDelay) {
        this.font = font;
        this.sliderHistory = sliderHistory;
        this.openScreen = openScreen;
        this.tooltipDelay = tooltipDelay;
    }

    <T extends AbstractWidget> T tooltip(T widget, String translationKey) {
        widget.setTooltip(Tooltip.create(Component.translatable(translationKey)));
        widget.setTooltipDelay(tooltipDelay);
        return widget;
    }

    <T extends AbstractWidget> T resetTooltip(T widget, String translationKey) {
        widget.setTooltip(Tooltip.create(Component.translatable(translationKey).append("\n")
                .append(Component.translatable("tooltip.elytrapitchhelper.reset_setting"))));
        widget.setTooltipDelay(tooltipDelay);
        return widget;
    }

    SliderRow floatSlider(Component label, double current, double min, double max, double step,
            String suffix, DoubleConsumer onChange) {
        return new SliderRow(label, font.get(), 0, current, min, max, step,
                value -> ScreenText.formatDecimal(value, step) + " " + suffix, onChange,
                TextMatch.slug(label.getString()), sliderHistory);
    }

    SliderRow intSlider(Component label, int current, int min, int max, int step, String suffix,
            IntConsumer onChange) {
        return new SliderRow(label, font.get(), 0, current, min, max, step,
                value -> ScreenText.formatInteger(value, suffix),
                value -> onChange.accept((int) Math.round(value)), TextMatch.slug(label.getString()), sliderHistory);
    }

    ColorRow colorButton(Component label, int current, boolean prideEnabled, String prideFlagId,
            int[] customPrideColors, Supplier<? extends Screen> editor) {
        int color = current & 0x00FFFFFF;
        PrideFlag prideFlag = PrideFlag.byId(prideFlagId);
        Component value = prideEnabled
                ? Component.translatable("option.elytrapitchhelper.color.pride_value",
                        Component.translatable(prideFlag.translationKey()))
                : ScreenText.colorComponent(color);
        int[] stripeColors = prideEnabled ? PrideFlag.colorsFor(prideFlag.id(), customPrideColors) : null;
        return new ColorRow(label, font.get(), 0, () -> color, () -> stripeColors, () -> value,
                () -> openScreen.accept(editor.get()));
    }

    CycleRow amplitudeTriggerModeButton(Component label, IntSupplier mode, Runnable forward, Runnable backward) {
        return new CycleRow(label, font.get(), 0,
                () -> amplitudeTriggerModeComponent(mode.getAsInt()), forward, backward);
    }

    CycleRow voidWarningModeButton(Component label, IntSupplier mode, Runnable forward, Runnable backward) {
        return new CycleRow(label, font.get(), 0,
                () -> voidWarningModeComponent(mode.getAsInt()), forward, backward);
    }

    private static Component amplitudeTriggerModeComponent(int mode) {
        if (mode == Config.AMPLITUDE_TRIGGER_VELOCITY) {
            return Component.translatable("option.elytrapitchhelper.amplitude_trigger.velocity");
        }
        if (mode == Config.AMPLITUDE_TRIGGER_EITHER) {
            return Component.translatable("option.elytrapitchhelper.amplitude_trigger.either");
        }
        return Component.translatable("option.elytrapitchhelper.amplitude_trigger.height");
    }

    private static Component voidWarningModeComponent(int mode) {
        if (mode == VoidWarningSettings.MODE_SIMPLE_HEIGHT) {
            return Component.translatable("option.elytrapitchhelper.void_mode.simple");
        }
        return Component.translatable("option.elytrapitchhelper.void_mode.predicted");
    }

}
