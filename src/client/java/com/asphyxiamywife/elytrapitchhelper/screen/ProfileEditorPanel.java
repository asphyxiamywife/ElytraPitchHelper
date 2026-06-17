package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.PrideFlag;
import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import com.asphyxiamywife.elytrapitchhelper.config.VoidWarningSettings;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.CategoryTabButton;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.DropdownButton;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.NumberSlider;
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
        } else if (host.category() == ConfigCategory.VOID) {
            addVoidWarningControls(index, startX, startY, columns, controlWidth, profile);
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
                host.tooltip(CycleButton.onOffBuilder(profile.visibility.showOnlyWithFirework)
                        .create(0, 0, controlWidth, ConfigScreen.CONTROL_HEIGHT,
                                Component.translatable("option.elytrapitchhelper.show_only_with_firework"),
                                (button, value) -> {
                                    profile.visibility.showOnlyWithFirework = value;
                                    host.saveProfileChange();
                                }), "tooltip.elytrapitchhelper.show_only_with_firework"));

        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(CycleButton.onOffBuilder(profile.visibility.showInThirdPerson)
                        .create(0, 0, controlWidth, ConfigScreen.CONTROL_HEIGHT,
                                Component.translatable("option.elytrapitchhelper.show_in_third_person"),
                                (button, value) -> {
                                    profile.visibility.showInThirdPerson = value;
                                    host.saveProfileChange();
                                }), "tooltip.elytrapitchhelper.show_in_third_person"));
        return index;
    }

    private int addPitchControls(int index, int startX, int startY, int columns, int controlWidth, Profile profile) {
        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(host.floatSlider(Component.translatable("option.elytrapitchhelper.target_up"),
                        profile.pitch.targetUpMinecraft, -90.0, 0.0, 1.0, "deg", value -> {
                            profile.pitch.targetUpMinecraft = (float) value;
                            host.saveProfileChange();
                        }), "tooltip.elytrapitchhelper.target_up"));

        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(host.floatSlider(Component.translatable("option.elytrapitchhelper.target_down"),
                        profile.pitch.targetDownMinecraft, 0.0, 90.0, 1.0, "deg", value -> {
                            profile.pitch.targetDownMinecraft = (float) value;
                            host.saveProfileChange();
                        }), "tooltip.elytrapitchhelper.target_down"));

        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(host.floatSlider(Component.translatable("option.elytrapitchhelper.tolerance"),
                        profile.pitch.toleranceDegrees, 1.0, 45.0, 0.5, "deg", value -> {
                            profile.pitch.toleranceDegrees = (float) value;
                            host.saveProfileChange();
                        }), "tooltip.elytrapitchhelper.tolerance"));

        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(host.intSlider(Component.translatable("option.elytrapitchhelper.max_offset"),
                        profile.pitch.maxOffsetPixels, 0, 200, "px", value -> {
                            profile.pitch.maxOffsetPixels = value;
                            host.saveProfileChange();
                        }), "tooltip.elytrapitchhelper.max_offset"));

        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(host.floatSlider(Component.translatable("option.elytrapitchhelper.offset_per_degree"),
                        profile.pitch.offsetPerDegree, 0.25, 10.0, 0.25, "px/deg", value -> {
                            profile.pitch.offsetPerDegree = (float) value;
                            host.saveProfileChange();
                        }), "tooltip.elytrapitchhelper.offset_per_degree"));
        return index;
    }

    private int addAmplitudeControls(int index, int startX, int startY, int columns, int controlWidth,
            Profile profile) {
        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(CycleButton.onOffBuilder(profile.amplitude.enabled)
                        .create(0, 0, controlWidth, ConfigScreen.CONTROL_HEIGHT,
                                Component.translatable("option.elytrapitchhelper.amplitude_helper"),
                                (button, value) -> {
                                    profile.amplitude.enabled = value;
                                    host.saveProfileChange();
                                }), "tooltip.elytrapitchhelper.amplitude_helper"));

        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(host.amplitudeTriggerModeButton(
                        Component.translatable("option.elytrapitchhelper.amplitude_trigger_mode"), profile),
                        "tooltip.elytrapitchhelper.amplitude_trigger_mode"));

        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(host.intSlider(Component.translatable("option.elytrapitchhelper.amplitude_down"),
                        profile.amplitude.downBlocks, 10, 300, "blocks", value -> {
                            profile.amplitude.downBlocks = value;
                            host.saveProfileChange();
                        }), "tooltip.elytrapitchhelper.amplitude_down"));

        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(host.intSlider(Component.translatable("option.elytrapitchhelper.amplitude_up"),
                        profile.amplitude.upBlocks, 10, 300, "blocks", value -> {
                            profile.amplitude.upBlocks = value;
                            host.saveProfileChange();
                        }), "tooltip.elytrapitchhelper.amplitude_up"));

        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(host.intSlider(Component.translatable("option.elytrapitchhelper.amplitude_tolerance"),
                        profile.amplitude.toleranceBlocks, 0, 30, "blocks", value -> {
                            profile.amplitude.toleranceBlocks = value;
                            host.saveProfileChange();
                        }), "tooltip.elytrapitchhelper.amplitude_tolerance"));

        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(host.floatSlider(Component.translatable("option.elytrapitchhelper.amplitude_down_velocity"),
                        profile.amplitude.downVelocity, 0.5, 5.0, 0.1, "b/t", value -> {
                            profile.amplitude.downVelocity = (float) value;
                            host.saveProfileChange();
                        }), "tooltip.elytrapitchhelper.amplitude_down_velocity"));

        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(host.floatSlider(Component.translatable("option.elytrapitchhelper.amplitude_up_velocity"),
                        profile.amplitude.upVelocity, 0.0, 2.0, 0.1, "b/t", value -> {
                            profile.amplitude.upVelocity = (float) value;
                            host.saveProfileChange();
                        }), "tooltip.elytrapitchhelper.amplitude_up_velocity"));
        return index;
    }

    private void addVisualControls(int index, int startX, int startY, int columns, int controlWidth, Profile profile) {
        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(host.intSlider(Component.translatable("option.elytrapitchhelper.line_length"),
                        profile.line.lengthPixels, 2, 200, "px", value -> {
                            profile.line.lengthPixels = value;
                            host.saveProfileChange();
                        }), "tooltip.elytrapitchhelper.line_length"));

        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(host.intSlider(Component.translatable("option.elytrapitchhelper.line_width"),
                        profile.line.widthPixels, 1, 20, "px", value -> {
                            profile.line.widthPixels = value;
                            host.saveProfileChange();
                        }), "tooltip.elytrapitchhelper.line_width"));

        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(host.colorButton(Component.translatable("option.elytrapitchhelper.line_color"),
                        profile.line.colorRgb, profile.line.prideEnabled, profile.line.prideFlag,
                        profile.line.customPrideColors, () -> profile.line.lengthPixels,
                        () -> profile.line.widthPixels, false, value -> {
                            profile.line.colorRgb = value;
                            host.saveProfileChange();
                        }, (enabled, flagId, customColors) -> {
                            profile.line.prideEnabled = enabled;
                            profile.line.prideFlag = flagId;
                            profile.line.customPrideColors = customColors;
                            host.saveProfileChange();
                        }), "tooltip.elytrapitchhelper.line_color"));

        addControl(index, startX, startY, columns, controlWidth,
                host.tooltip(host.colorButton(Component.translatable("option.elytrapitchhelper.amplitude_color"),
                        profile.amplitude.cueColorRgb, profile.amplitude.cuePrideEnabled,
                        profile.amplitude.cuePrideFlag, profile.amplitude.customPrideColors,
                        () -> profile.line.lengthPixels,
                        () -> profile.line.widthPixels, true, value -> {
                            profile.amplitude.cueColorRgb = value;
                            host.saveProfileChange();
                        }, (enabled, flagId, customColors) -> {
                            profile.amplitude.cuePrideEnabled = enabled;
                            profile.amplitude.cuePrideFlag = flagId;
                            profile.amplitude.customPrideColors = customColors;
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

    private static final String[] KNOWN_DIMENSIONS = {
        "minecraft:overworld", "minecraft:the_end", "minecraft:the_nether"
    };

    private void addVoidWarningControls(int index, int startX, int startY, int columns, int controlWidth,
            Profile profile) {
        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(CycleButton.onOffBuilder(profile.voidWarning.enabled)
                        .create(0, 0, controlWidth, ConfigScreen.CONTROL_HEIGHT,
                                Component.translatable("option.elytrapitchhelper.void_warning"),
                                (button, value) -> {
                                    profile.voidWarning.enabled = value;
                                    host.saveProfileChange();
                                }), "tooltip.elytrapitchhelper.void_warning"));

        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(host.voidWarningModeButton(
                        Component.translatable("option.elytrapitchhelper.void_mode"), profile),
                        "tooltip.elytrapitchhelper.void_mode"));

        if (profile.voidWarning.mode == VoidWarningSettings.MODE_SIMPLE_HEIGHT) {
            addControl(index++, startX, startY, columns, controlWidth,
                    host.tooltip(host.intSlider(
                            Component.translatable("option.elytrapitchhelper.void_simple_blocks"),
                            profile.voidWarning.simpleWarningBlocks, 1, 512, "blocks", value -> {
                                profile.voidWarning.simpleWarningBlocks = value;
                                host.saveProfileChange();
                            }), "tooltip.elytrapitchhelper.void_simple_blocks"));
        } else {
            addControl(index++, startX, startY, columns, controlWidth,
                    host.tooltip(host.floatSlider(
                            Component.translatable("option.elytrapitchhelper.void_lookahead"),
                            profile.voidWarning.lookaheadSeconds, 1.0, 15.0, 0.5, "s", value -> {
                                profile.voidWarning.lookaheadSeconds = (float) value;
                                host.saveProfileChange();
                            }), "tooltip.elytrapitchhelper.void_lookahead"));
        }

        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(host.colorButton(
                        Component.translatable("option.elytrapitchhelper.void_color"),
                        profile.voidWarning.warningColorRgb, profile.voidWarning.warningPrideEnabled,
                        profile.voidWarning.warningPrideFlag, profile.voidWarning.customPrideColors,
                        () -> profile.line.lengthPixels, () -> profile.line.widthPixels,
                        true, value -> {
                            profile.voidWarning.warningColorRgb = value;
                            host.saveProfileChange();
                        }, (enabled, flagId, customColors) -> {
                            profile.voidWarning.warningPrideEnabled = enabled;
                            profile.voidWarning.warningPrideFlag = flagId;
                            profile.voidWarning.customPrideColors = customColors;
                            host.saveProfileChange();
                        }),
                        "tooltip.elytrapitchhelper.void_color"));

        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(CycleButton.onOffBuilder(profile.voidWarning.toleranceOverride)
                        .create(0, 0, controlWidth, ConfigScreen.CONTROL_HEIGHT,
                                Component.translatable("option.elytrapitchhelper.void_tolerance_override"),
                                (button, value) -> {
                                    profile.voidWarning.toleranceOverride = value;
                                    host.saveProfileChange();
                                }), "tooltip.elytrapitchhelper.void_tolerance_override"));

        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(host.floatSlider(
                        Component.translatable("option.elytrapitchhelper.void_custom_tolerance"),
                        profile.voidWarning.customToleranceDegrees, 1.0, 45.0, 0.5, "deg", value -> {
                            profile.voidWarning.customToleranceDegrees = (float) value;
                            host.saveProfileChange();
                        }), "tooltip.elytrapitchhelper.void_custom_tolerance"));

        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(CycleButton.onOffBuilder(profile.voidWarning.maxOffsetOverride)
                        .create(0, 0, controlWidth, ConfigScreen.CONTROL_HEIGHT,
                                Component.translatable("option.elytrapitchhelper.void_max_offset_override"),
                                (button, value) -> {
                                    profile.voidWarning.maxOffsetOverride = value;
                                    host.saveProfileChange();
                                }), "tooltip.elytrapitchhelper.void_max_offset_override"));

        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(host.intSlider(
                        Component.translatable("option.elytrapitchhelper.void_custom_max_offset"),
                        profile.voidWarning.customMaxOffsetPixels, 0, 200, "px", value -> {
                            profile.voidWarning.customMaxOffsetPixels = value;
                            host.saveProfileChange();
                        }), "tooltip.elytrapitchhelper.void_custom_max_offset"));

        java.util.List<String> dims = new java.util.ArrayList<>(
                java.util.Arrays.asList(KNOWN_DIMENSIONS));
        for (String key : profile.voidWarning.dimensionYOverrides.keySet()) {
            if (!dims.contains(key)) dims.add(key);
        }
        String currentDimension = host.currentDimensionKey();
        if (currentDimension != null && !dims.contains(currentDimension)) {
            dims.add(currentDimension);
        }
        String selectedDimension = host.selectedVoidWarningDimensionKey();
        if (selectedDimension != null && !dims.contains(selectedDimension)) {
            dims.add(selectedDimension);
        }
        String[] dimArray = dims.toArray(new String[0]);
        java.util.List<String> dimValues = java.util.Arrays.asList(dimArray);
        String preferredDimension = selectedDimension != null ? selectedDimension : currentDimension;
        int selected = preferredDimension == null ? 0 : dimValues.indexOf(preferredDimension);
        if (selected < 0) {
            selected = 0;
        }
        host.setSelectedVoidWarningDimensionKey(dimArray[selected]);
        int[] selectedIdx = {selected};
        int[] pendingY = {profile.voidWarning.dimensionYOverrides.getOrDefault(dimArray[selected], 0)};
        NumberSlider[] ySliderRef = {null};

        DropdownButton<String> dimButton = new DropdownButton<>(0, 0, controlWidth, ConfigScreen.CONTROL_HEIGHT,
                Component.translatable("option.elytrapitchhelper.void_dimension"), dimValues, dimArray[selected],
                dim -> Component.literal(shortDimName(dim)), dim -> {
                    selectedIdx[0] = dimValues.indexOf(dim);
                    host.setSelectedVoidWarningDimensionKey(dim);
                    pendingY[0] = profile.voidWarning.dimensionYOverrides.getOrDefault(dim, 0);
                    if (ySliderRef[0] != null) ySliderRef[0].setCurrent(pendingY[0]);
                });
        addControl(index++, startX, startY, columns, controlWidth,
                host.tooltip(dimButton, "tooltip.elytrapitchhelper.void_dimension"));

        NumberSlider ySlider = (NumberSlider) host.intSlider(
                Component.translatable("option.elytrapitchhelper.void_y_override"),
                pendingY[0], VoidWarningSettings.MIN_VOID_Y, VoidWarningSettings.MAX_VOID_Y, "", value -> {
                    pendingY[0] = value;
                    profile.voidWarning.dimensionYOverrides.put(dimArray[selectedIdx[0]], value);
                    host.saveProfileChange();
                });
        ySliderRef[0] = ySlider;
        addControl(index, startX, startY, columns, controlWidth, ySlider);
    }

    private static String shortDimName(String id) {
        int colon = id.indexOf(':');
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        String[] words = path.replace('_', ' ').split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(w.charAt(0)));
                if (w.length() > 1) sb.append(w.substring(1));
            }
        }
        return sb.toString();
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

        String currentDimensionKey();

        String selectedVoidWarningDimensionKey();

        void setSelectedVoidWarningDimensionKey(String dimensionKey);

        Button colorButton(Component label, int current, boolean prideEnabled, String prideFlagId,
                int[] customPrideColors,
                java.util.function.IntSupplier previewLineLength, java.util.function.IntSupplier previewLineWidth,
                boolean previewCuePeak, java.util.function.IntConsumer onChange,
                ColorEditorScreen.PrideSettingsConsumer onPrideChange);

        Button amplitudeTriggerModeButton(Component label, Profile profile);

        Button voidWarningModeButton(Component label, Profile profile);

        void saveProfileChange();

        void openResetProfileConfirmation();

        void closeEditor();
    }
}
