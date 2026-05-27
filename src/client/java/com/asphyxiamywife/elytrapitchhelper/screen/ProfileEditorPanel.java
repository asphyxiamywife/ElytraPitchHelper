package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.CategoryTabButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

final class ProfileEditorPanel {
    private final Host host;

    ProfileEditorPanel(Host host) {
        this.host = host;
    }

    void init() {
        int contentWidth = Math.max(120, Math.min(440, host.screenWidth() - 40));
        Profile profile = host.config().profile(host.editingProfileIndex());

        addCategoryTabs(contentWidth);

        int columns = host.screenWidth() >= 360 ? 2 : 1;
        int controlWidth = columns == 2 ? Math.min(210, (contentWidth - ConfigScreen.CONTROL_GAP) / 2)
                : Math.min(210, contentWidth);
        int totalWidth = columns * controlWidth + (columns - 1) * ConfigScreen.CONTROL_GAP;
        int startX = (host.screenWidth() - totalWidth) / 2;
        int startY = 84;
        int index = 0;

        if (host.category() == ConfigCategory.GENERAL) {
            index = addGeneralControls(index, startX, startY, columns, controlWidth, profile);
        } else if (host.category() == ConfigCategory.PITCH) {
            index = addPitchControls(index, startX, startY, columns, controlWidth, profile);
        } else if (host.category() == ConfigCategory.AMPLITUDE) {
            index = addAmplitudeControls(index, startX, startY, columns, controlWidth, profile);
        } else if (host.category() == ConfigCategory.VISUALS) {
            addVisualControls(index, startX, startY, columns, controlWidth, profile);
        }

        int footerY = host.screenHeight() - 28;
        int buttonWidth = Math.min(132, (host.screenWidth() - 46) / 2);
        int footerX = (host.screenWidth() - buttonWidth * 2 - ConfigScreen.CONTROL_GAP) / 2;
        host.addWidget(Button.builder(Component.translatable("screen.elytrapitchhelper.config.reset"),
                button -> host.openResetProfileConfirmation())
                .bounds(footerX, footerY, buttonWidth, ConfigScreen.CONTROL_HEIGHT).build());
        host.addWidget(Button.builder(Component.translatable("screen.elytrapitchhelper.profile.back_to_profiles"),
                button -> host.closeEditor())
                .bounds(footerX + buttonWidth + ConfigScreen.CONTROL_GAP, footerY, buttonWidth,
                        ConfigScreen.CONTROL_HEIGHT)
                .build());
    }

    private int addGeneralControls(int index, int startX, int startY, int columns, int controlWidth, Profile profile) {
        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(CycleButton.onOffBuilder(profile.showOnlyWithFirework)
                        .create(0, 0, controlWidth, ConfigScreen.CONTROL_HEIGHT,
                                Component.translatable("option.elytrapitchhelper.show_only_with_firework"),
                                (button, value) -> {
                                    profile.showOnlyWithFirework = value;
                                    host.saveProfileChange();
                                }), "tooltip.elytrapitchhelper.show_only_with_firework"));

        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(CycleButton.onOffBuilder(profile.showInThirdPerson)
                        .create(0, 0, controlWidth, ConfigScreen.CONTROL_HEIGHT,
                                Component.translatable("option.elytrapitchhelper.show_in_third_person"),
                                (button, value) -> {
                                    profile.showInThirdPerson = value;
                                    host.saveProfileChange();
                                }), "tooltip.elytrapitchhelper.show_in_third_person"));
        return index;
    }

    private int addPitchControls(int index, int startX, int startY, int columns, int controlWidth, Profile profile) {
        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(host.floatSlider(Component.translatable("option.elytrapitchhelper.target_up"),
                        profile.targetUpMinecraft, -90.0, 0.0, 1.0, "deg", value -> {
                            profile.targetUpMinecraft = (float) value;
                            host.saveProfileChange();
                        }), "tooltip.elytrapitchhelper.target_up"));

        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(host.floatSlider(Component.translatable("option.elytrapitchhelper.target_down"),
                        profile.targetDownMinecraft, 0.0, 90.0, 1.0, "deg", value -> {
                            profile.targetDownMinecraft = (float) value;
                            host.saveProfileChange();
                        }), "tooltip.elytrapitchhelper.target_down"));

        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(host.floatSlider(Component.translatable("option.elytrapitchhelper.tolerance"),
                        profile.toleranceDegrees, 1.0, 45.0, 0.5, "deg", value -> {
                            profile.toleranceDegrees = (float) value;
                            host.saveProfileChange();
                        }), "tooltip.elytrapitchhelper.tolerance"));

        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(host.intSlider(Component.translatable("option.elytrapitchhelper.max_offset"),
                        profile.maxOffsetPixels, 0, 200, "px", value -> {
                            profile.maxOffsetPixels = value;
                            host.saveProfileChange();
                        }), "tooltip.elytrapitchhelper.max_offset"));

        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(host.floatSlider(Component.translatable("option.elytrapitchhelper.offset_per_degree"),
                        profile.offsetPerDegree, 0.25, 10.0, 0.25, "px/deg", value -> {
                            profile.offsetPerDegree = (float) value;
                            host.saveProfileChange();
                        }), "tooltip.elytrapitchhelper.offset_per_degree"));
        return index;
    }

    private int addAmplitudeControls(int index, int startX, int startY, int columns, int controlWidth,
            Profile profile) {
        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(CycleButton.onOffBuilder(profile.amplitudeHelperEnabled)
                        .create(0, 0, controlWidth, ConfigScreen.CONTROL_HEIGHT,
                                Component.translatable("option.elytrapitchhelper.amplitude_helper"),
                                (button, value) -> {
                                    profile.amplitudeHelperEnabled = value;
                                    host.saveProfileChange();
                                }), "tooltip.elytrapitchhelper.amplitude_helper"));

        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(host.amplitudeTriggerModeButton(
                        Component.translatable("option.elytrapitchhelper.amplitude_trigger_mode"), profile),
                        "tooltip.elytrapitchhelper.amplitude_trigger_mode"));

        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(host.intSlider(Component.translatable("option.elytrapitchhelper.amplitude_down"),
                        profile.amplitudeDownBlocks, 10, 300, "blocks", value -> {
                            profile.amplitudeDownBlocks = value;
                            host.saveProfileChange();
                        }), "tooltip.elytrapitchhelper.amplitude_down"));

        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(host.intSlider(Component.translatable("option.elytrapitchhelper.amplitude_up"),
                        profile.amplitudeUpBlocks, 10, 300, "blocks", value -> {
                            profile.amplitudeUpBlocks = value;
                            host.saveProfileChange();
                        }), "tooltip.elytrapitchhelper.amplitude_up"));

        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(host.intSlider(Component.translatable("option.elytrapitchhelper.amplitude_tolerance"),
                        profile.amplitudeToleranceBlocks, 0, 30, "blocks", value -> {
                            profile.amplitudeToleranceBlocks = value;
                            host.saveProfileChange();
                        }), "tooltip.elytrapitchhelper.amplitude_tolerance"));

        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(host.floatSlider(Component.translatable("option.elytrapitchhelper.amplitude_down_velocity"),
                        profile.amplitudeDownVelocity, 0.5, 5.0, 0.1, "b/t", value -> {
                            profile.amplitudeDownVelocity = (float) value;
                            host.saveProfileChange();
                        }), "tooltip.elytrapitchhelper.amplitude_down_velocity"));

        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(host.floatSlider(Component.translatable("option.elytrapitchhelper.amplitude_up_velocity"),
                        profile.amplitudeUpVelocity, 0.0, 2.0, 0.1, "b/t", value -> {
                            profile.amplitudeUpVelocity = (float) value;
                            host.saveProfileChange();
                        }), "tooltip.elytrapitchhelper.amplitude_up_velocity"));
        return index;
    }

    private void addVisualControls(int index, int startX, int startY, int columns, int controlWidth, Profile profile) {
        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(host.intSlider(Component.translatable("option.elytrapitchhelper.line_length"),
                        profile.lineLengthPixels, 2, 200, "px", value -> {
                            profile.lineLengthPixels = value;
                            host.saveProfileChange();
                        }), "tooltip.elytrapitchhelper.line_length"));

        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(host.intSlider(Component.translatable("option.elytrapitchhelper.line_width"),
                        profile.lineWidthPixels, 1, 20, "px", value -> {
                            profile.lineWidthPixels = value;
                            host.saveProfileChange();
                        }), "tooltip.elytrapitchhelper.line_width"));

        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(host.colorButton(Component.translatable("option.elytrapitchhelper.line_color"),
                        profile.lineColorRgb, profile.linePrideEnabled, profile.linePrideFlag,
                        () -> profile.lineLengthPixels, () -> profile.lineWidthPixels, false, value -> {
                            profile.lineColorRgb = value;
                            host.saveProfileChange();
                        }, (enabled, flagId) -> {
                            profile.linePrideEnabled = enabled;
                            profile.linePrideFlag = flagId;
                            host.saveProfileChange();
                        }), "tooltip.elytrapitchhelper.line_color"));

        addControl(index, startX, startY, columns, controlWidth,
                host.tooltip(host.colorButton(Component.translatable("option.elytrapitchhelper.amplitude_color"),
                        profile.amplitudeCueColorRgb, profile.amplitudeCuePrideEnabled,
                        profile.amplitudeCuePrideFlag, () -> profile.lineLengthPixels,
                        () -> profile.lineWidthPixels, true, value -> {
                            profile.amplitudeCueColorRgb = value;
                            host.saveProfileChange();
                        }, (enabled, flagId) -> {
                            profile.amplitudeCuePrideEnabled = enabled;
                            profile.amplitudeCuePrideFlag = flagId;
                            host.saveProfileChange();
                        }), "tooltip.elytrapitchhelper.amplitude_color"));
    }

    private void addCategoryTabs(int contentWidth) {
        int tabCount = ConfigCategory.values().length;
        int tabWidth = Math.max(62, Math.min(86, (contentWidth - (tabCount - 1) * ConfigScreen.CONTROL_GAP)
                / tabCount));
        int totalWidth = tabCount * tabWidth + (tabCount - 1) * ConfigScreen.CONTROL_GAP;
        int x = (host.screenWidth() - totalWidth) / 2;
        int y = 58;
        for (ConfigCategory tab : ConfigCategory.values()) {
            Button button = new CategoryTabButton(x, y, tabWidth, ConfigScreen.CONTROL_HEIGHT,
                    Component.translatable(tab.translationKey), tab == host.category(), pressed -> {
                host.setCategory(tab);
                host.rebuildWidgets();
            });
            host.addWidget(button);
            x += tabWidth + ConfigScreen.CONTROL_GAP;
        }
    }

    private void addControl(int index, int startX, int startY, int columns, int width, AbstractSliderButton widget) {
        addControl(index, startX, startY, columns, width, (AbstractWidget) widget);
    }

    private void addControl(int index, int startX, int startY, int columns, int width, AbstractWidget widget) {
        int x = startX + (index % columns) * (width + ConfigScreen.CONTROL_GAP);
        int y = startY + (index / columns) * ConfigScreen.ROW_HEIGHT;
        widget.setSize(width, ConfigScreen.CONTROL_HEIGHT);
        widget.setX(x);
        widget.setY(y);
        host.addWidget(widget);
    }

    interface Host {
        int screenWidth();

        int screenHeight();

        Config config();

        int editingProfileIndex();

        ConfigCategory category();

        void setCategory(ConfigCategory category);

        void rebuildWidgets();

        void addWidget(AbstractWidget widget);

        <T extends AbstractWidget> T tooltip(T widget, String translationKey);

        AbstractSliderButton floatSlider(Component label, double current, double min, double max, double step,
                String suffix, java.util.function.DoubleConsumer onChange);

        AbstractSliderButton intSlider(Component label, int current, int min, int max, String suffix,
                java.util.function.IntConsumer onChange);

        Button colorButton(Component label, int current, boolean prideEnabled, String prideFlagId,
                java.util.function.IntSupplier previewLineLength, java.util.function.IntSupplier previewLineWidth,
                boolean previewCuePeak, java.util.function.IntConsumer onChange,
                ColorEditorScreen.PrideSettingsConsumer onPrideChange);

        Button amplitudeTriggerModeButton(Component label, Profile profile);

        void saveProfileChange();

        void openResetProfileConfirmation();

        void closeEditor();
    }
}
