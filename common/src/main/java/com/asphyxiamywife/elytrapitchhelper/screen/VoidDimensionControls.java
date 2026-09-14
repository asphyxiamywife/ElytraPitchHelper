package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import com.asphyxiamywife.elytrapitchhelper.config.VoidWarningSettings;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingSpec;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingsRegistry;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.DerivedRow;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.CycleRow;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.SliderRow;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.DropdownButton;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class VoidDimensionControls {
    private final Host host;
    private final java.util.function.Supplier<Profile> current;
    private final java.util.function.Consumer<Profile> replace;

    private static final String[] KNOWN_DIMENSIONS = {
        "minecraft:overworld", "minecraft:the_end", "minecraft:the_nether"
    };

    VoidDimensionControls(Host host, java.util.function.Supplier<Profile> current,
            java.util.function.Consumer<Profile> replace) {
        this.host = host;
        this.current = current;
        this.replace = replace;
    }

    private Profile currentProfile() {
        return current.get();
    }

    private void replaceProfile(Profile profile) {
        replace.accept(profile);
    }

    Map<String, AbstractWidget> createControls(Profile profile) {
        SettingSpec<?> dimensionSpec = requireSpec("void_dimension");
        SettingSpec<?> modeSpec = requireSpec("void_y_mode");
        SettingSpec<?> overrideSpec = requireSpec("void_y_override");
        String currentDimension = host.currentDimensionKey();
        String selectedDimension = host.selectedVoidWarningDimensionKey();
        List<String> dimensions = dimensions(profile, currentDimension, selectedDimension);
        String preferredDimension = selectedDimension != null ? selectedDimension : currentDimension;
        int selected = preferredDimension == null ? 0 : dimensions.indexOf(preferredDimension);
        if (selected < 0) {
            selected = 0;
        }
        String selectedValue = dimensions.get(selected);
        host.setSelectedVoidWarningDimensionKey(selectedValue);
        int[] selectedIndex = {selected};
        boolean[] customVoidY = {profile.voidWarning().dimensionYOverrides().containsKey(selectedValue)};
        int[] pendingY = {displayVoidY(profile, selectedValue)};

        Map<String, AbstractWidget> widgets = new LinkedHashMap<>();
        DropdownButton<String> dimensionButton = new DropdownButton<>(0, 0,
                ProfileEditorLayout.MAX_ROW_WIDTH,
                ConfigScreen.CONTROL_HEIGHT, Component.translatable(dimensionSpec.labelKey()),
                dimensions, selectedValue, value -> Component.literal(shortDimName(value)), value -> {
                    selectedIndex[0] = dimensions.indexOf(value);
                    host.setSelectedVoidWarningDimensionKey(value);
                    host.rebuildWidgets();
                });
        dimensionButton.setRowStyle(SettingsRegistry.depth(dimensionSpec));
        dimensionButton.reserveResetGutter();
        widgets.put("void_dimension", host.controls().tooltip(dimensionButton, dimensionSpec.tooltipKey()));

        Runnable cycleMode = () -> cycleVoidYMode(dimensions.get(selectedIndex[0]), customVoidY,
                pendingY, modeSpec);
        CycleRow modeRow = new CycleRow(Component.translatable(modeSpec.labelKey()), host.font(),
                SettingsRegistry.depth(modeSpec), () -> voidYModeValue(customVoidY[0]), cycleMode, cycleMode);
        modeRow.setReset(
                () -> currentProfile().voidWarning().dimensionYOverrides().containsKey(selectedValue),
                () -> resetVoidDimension(modeSpec, selectedValue));
        widgets.put("void_y_mode", host.controls().resetTooltip(modeRow, modeSpec.tooltipKey()));

        AbstractWidget yRow;
        if (customVoidY[0]) {
            SliderRow slider = host.controls().intSlider(Component.translatable(overrideSpec.labelKey()), pendingY[0],
                    VoidWarningSettings.MIN_VOID_Y, VoidWarningSettings.MAX_VOID_Y, 1, "", value -> {
                        pendingY[0] = value;
                        updateVoidYOverride(dimensions.get(selectedIndex[0]), value);
                        host.saveProfileChange(overrideSpec.id());
                    });
            slider.setDepth(SettingsRegistry.depth(overrideSpec));
            slider.setReset(
                    () -> currentProfile().voidWarning().dimensionYOverrides().containsKey(selectedValue),
                    () -> resetVoidDimension(modeSpec, selectedValue));
            yRow = slider;
        } else {
            DerivedRow derived = new DerivedRow(Component.translatable("option.elytrapitchhelper.void_y_detected"),
                    host.font(), SettingsRegistry.depth(overrideSpec),
                    () -> Component.literal(Integer.toString(pendingY[0])));
            derived.reserveResetGutter();
            yRow = derived;
        }
        widgets.put("void_y_override", customVoidY[0]
                ? host.controls().resetTooltip(yRow, overrideSpec.tooltipKey())
                : host.controls().tooltip(yRow, overrideSpec.tooltipKey()));
        return widgets;
    }

    static List<String> dimensions(Profile profile, String currentDimension, String selectedDimension) {
        List<String> dimensions = new ArrayList<>(Arrays.asList(KNOWN_DIMENSIONS));
        for (String key : profile.voidWarning().dimensionYOverrides().keySet()) {
            if (!dimensions.contains(key)) {
                dimensions.add(key);
            }
        }
        if (currentDimension != null && !dimensions.contains(currentDimension)) {
            dimensions.add(currentDimension);
        }
        if (selectedDimension != null && !dimensions.contains(selectedDimension)) {
            dimensions.add(selectedDimension);
        }
        return dimensions;
    }

    private void resetVoidDimension(SettingSpec<?> focusSpec, String dimension) {
        var replacement = SettingResetPlan.voidDimension(currentProfile(), dimension);
        if (replacement.isEmpty()) {
            return;
        }
        host.setPendingSettingFocus(new SettingsSearch.Entry(focusSpec));
        host.performProfileChange(focusSpec.id(), "reset-void-y:" + dimension,
                () -> replaceProfile(replacement.get()));
        host.rebuildWidgets();
    }

    private void cycleVoidYMode(String dimension, boolean[] customVoidY,
            int[] pendingY, SettingSpec<?> spec) {
        host.performProfileChange(spec.id(), spec.actionKey(), () -> {
            customVoidY[0] = !customVoidY[0];
            if (customVoidY[0]) {
                updateVoidYOverride(dimension, pendingY[0]);
            } else {
                updateVoidYOverride(dimension, null);
                pendingY[0] = displayVoidY(currentProfile(), dimension);
            }
        });
        host.rebuildWidgets();
    }

    private int displayVoidY(Profile profile, String dimensionKey) {
        Integer override = profile.voidWarning().dimensionYOverrides().get(dimensionKey);
        if (override != null) {
            return override;
        }
        Integer detected = host.detectedDefaultVoidY(dimensionKey);
        return detected != null ? detected : 0;
    }

    private void updateVoidYOverride(String dimension, Integer value) {
        Profile profile = currentProfile();
        Map<String, Integer> overrides = new HashMap<>(
                profile.voidWarning().dimensionYOverrides());
        if (value == null) {
            overrides.remove(dimension);
        } else {
            overrides.put(dimension, value);
        }
        replaceProfile(profile.withVoidWarning(
                profile.voidWarning().withDimensionYOverrides(overrides)));
    }

    private static Component voidYModeValue(boolean custom) {
        return Component.translatable(custom
                ? "option.elytrapitchhelper.void_y_mode.custom"
                : "option.elytrapitchhelper.void_y_mode.auto");
    }

    private static String shortDimName(String id) {
        int colon = id.indexOf(':');
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        String[] words = path.replace('_', ' ').split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                if (!result.isEmpty()) {
                    result.append(' ');
                }
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1));
                }
            }
        }
        return result.toString();
    }

    private static SettingSpec<?> requireSpec(String id) {
        SettingSpec<?> spec = SettingsRegistry.byId(id);
        if (spec == null) {
            throw new IllegalStateException("Missing setting " + id);
        }
        return spec;
    }

    interface Host {
        Font font();
        SettingControlFactory controls();
        void rebuildWidgets();
        void setPendingSettingFocus(SettingsSearch.Entry entry);
        void saveProfileChange(String optionName);
        void performProfileChange(String optionName, String actionKey, Runnable change);
        String currentDimensionKey();
        String selectedVoidWarningDimensionKey();
        void setSelectedVoidWarningDimensionKey(String dimensionKey);
        Integer detectedDefaultVoidY(String dimensionKey);
    }
}
