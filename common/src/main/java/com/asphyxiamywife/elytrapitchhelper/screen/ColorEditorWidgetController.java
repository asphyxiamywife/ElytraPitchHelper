package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.PrideFlag;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.FlatEditBox;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.FlatButton;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.DropdownButton;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.SliderRow;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.StepperRow;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.ToggleRow;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.function.IntConsumer;

final class ColorEditorWidgetController {
    static final int CONTROL_HEIGHT = 20;
    static final int RGB_SLIDER_GAP = 4;

    interface Host {
        Font editorFont();

        ColorEditorLayout colorEditorLayout();

        int editorWidth();

        int color();

        int red();

        int green();

        int blue();

        boolean prideControlsVisible();

        PrideFlag prideFlag();

        Component prideModeMessage();

        Component customStripeMessage();

        int[] stripeColorsForDropdown(PrideFlag flag);

        void onHexChanged(String value);

        void togglePrideMode();

        void selectPrideFlag(PrideFlag flag);

        void cycleCustomStripe(int direction);

        void addCustomStripe();

        void removeCustomStripe();

        SliderRow rgbSlider(Component label, int current, IntConsumer onValueChanged);

        Component customStripeValue();

        boolean prideEnabled();

        boolean canAddCustomStripe();

        boolean canRemoveCustomStripe();

        <T extends AbstractWidget> T tooltip(T widget, String translationKey);

        void setRed(int value);

        void setGreen(int value);

        void setBlue(int value);

        void addWidget(AbstractWidget widget);

        void closeEditor();
    }

    record Widgets(EditBox hexBox, SliderRow redSlider, SliderRow greenSlider, SliderRow blueSlider,
            AbstractWidget customStripeButton, DropdownButton<PrideFlag> prideFlagDropdown) {
    }

    Widgets init(Host host) {
        ColorEditorLayout layout = host.colorEditorLayout();
        EditBox hexBox = new FlatEditBox(host.editorFont(), layout.controlX() + layout.controlWidth() - 82,
                layout.hexY(), 82, CONTROL_HEIGHT, Component.translatable("option.elytrapitchhelper.color.hex"));
        hexBox.setMaxLength(7);
        hexBox.setValue(ScreenText.hexColor(host.color()));
        hexBox.setResponder(host::onHexChanged);
        host.addWidget(hexBox);

        DropdownButton<PrideFlag> prideFlagDropdown = null;
        AbstractWidget customStripeButton = null;
        if (host.prideControlsVisible()) {
            int prideButtonWidth = Math.max(58, (layout.controlWidth() - RGB_SLIDER_GAP) / 2);
            AbstractWidget prideModeButton = new ToggleRow(
                    Component.translatable("option.elytrapitchhelper.color.pride"),
                    host.editorFont(), 0, host::prideEnabled, enabled -> host.togglePrideMode(),
                    Component.translatable("option.elytrapitchhelper.color.pride.on"),
                    Component.translatable("option.elytrapitchhelper.color.pride.off"));
            prideModeButton.setPosition(layout.controlX(), layout.prideY());
            prideModeButton.setSize(prideButtonWidth, CONTROL_HEIGHT);
            host.addWidget(prideModeButton);

            prideFlagDropdown = new DropdownButton<>(layout.controlX() + prideButtonWidth + RGB_SLIDER_GAP,
                    layout.prideY(), layout.controlWidth() - prideButtonWidth - RGB_SLIDER_GAP, CONTROL_HEIGHT,
                    null, Arrays.asList(PrideFlag.values()), host.prideFlag(),
                    flag -> Component.translatable(flag.translationKey()), host::stripeColorsForDropdown,
                    host::selectPrideFlag);
            prideFlagDropdown.setRowStyle(0);
            host.addWidget(prideFlagDropdown);

            customStripeButton = new StepperRow(
                    Component.translatable("option.elytrapitchhelper.color.custom.stripe"),
                    host.editorFont(), 0, host::customStripeValue,
                    () -> host.cycleCustomStripe(-1), () -> host.cycleCustomStripe(1),
                    host::removeCustomStripe, host::addCustomStripe,
                    host::canRemoveCustomStripe, host::canAddCustomStripe);
            customStripeButton.setPosition(layout.controlX(), layout.customY());
            customStripeButton.setSize(layout.controlWidth(), CONTROL_HEIGHT);
            host.addWidget(host.tooltip(customStripeButton,
                    "tooltip.elytrapitchhelper.color.custom.stripe"));
        }

        SliderRow redSlider = host.rgbSlider(Component.translatable("option.elytrapitchhelper.color.red"),
                host.red(), host::setRed);
        SliderRow greenSlider = host.rgbSlider(Component.translatable("option.elytrapitchhelper.color.green"),
                host.green(), host::setGreen);
        SliderRow blueSlider = host.rgbSlider(Component.translatable("option.elytrapitchhelper.color.blue"),
                host.blue(), host::setBlue);
        int labelWidth = 0;
        for (String channel : new String[] { "red", "green", "blue" }) {
            labelWidth = Math.max(labelWidth, host.editorFont().width(
                    Component.translatable("option.elytrapitchhelper.color." + channel)));
        }
        int valueWidth = host.editorFont().width("255");
        redSlider.setColumnWidths(labelWidth, valueWidth);
        greenSlider.setColumnWidths(labelWidth, valueWidth);
        blueSlider.setColumnWidths(labelWidth, valueWidth);
        addSlider(host, redSlider, layout.controlX(), layout.sliderY(), layout.controlWidth());
        addSlider(host, greenSlider, layout.controlX(), layout.sliderY() + CONTROL_HEIGHT + RGB_SLIDER_GAP,
                layout.controlWidth());
        addSlider(host, blueSlider, layout.controlX(), layout.sliderY() + (CONTROL_HEIGHT + RGB_SLIDER_GAP) * 2,
                layout.controlWidth());

        int buttonWidth = Math.min(120, host.editorWidth() - 40);
        host.addWidget(FlatButton.of(CommonComponents.GUI_DONE, layout.doneX(), layout.doneY(),
                buttonWidth, CONTROL_HEIGHT, button -> host.closeEditor()));
        return new Widgets(hexBox, redSlider, greenSlider, blueSlider, customStripeButton, prideFlagDropdown);
    }

    private static void addSlider(Host host, AbstractWidget widget, int x, int y, int width) {
        widget.setSize(width, CONTROL_HEIGHT);
        widget.setX(x);
        widget.setY(y);
        host.addWidget(widget);
    }
}
