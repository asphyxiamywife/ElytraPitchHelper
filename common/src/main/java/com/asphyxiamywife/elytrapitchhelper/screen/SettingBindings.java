package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingSpec;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingReset;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingsRegistry;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.CycleRow;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.ToggleRow;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.ScreenRow;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.SettingRow;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

final class SettingBindings {
    private final Host host;
    private final java.util.function.Supplier<Profile> current;
    private final java.util.function.Consumer<Profile> replace;

    private final Profile defaults = Config.defaultProfileTemplate();
    private final Runnable syncFloatSliders;

    SettingBindings(Host host, java.util.function.Supplier<Profile> current,
            java.util.function.Consumer<Profile> replace, Runnable syncFloatSliders) {
        this.host = host;
        this.current = current;
        this.replace = replace;
        this.syncFloatSliders = syncFloatSliders;
    }

    private Profile currentProfile() {
        return current.get();
    }

    private void replaceProfile(Profile profile) {
        replace.accept(profile);
    }

    AbstractWidget createControl(SettingSpec<?> spec, Profile profile) {
        Component label = Component.translatable(spec.labelKey());
        AbstractWidget widget;
        if (spec.control() instanceof SettingSpec.Toggle) {
            widget = createToggle(spec, label);
        } else if (spec.control() instanceof SettingSpec.IntSlider slider) {
            widget = createIntSlider(spec, slider, label, profile);
        } else if (spec.control() instanceof SettingSpec.FloatSlider slider) {
            widget = createFloatSlider(spec, slider, label, profile);
        } else if (spec.control() instanceof SettingSpec.Cycle) {
            widget = switch (spec.id()) {
                case "amplitude_trigger_mode" ->
                        host.amplitudeTriggerModeButton(label, spec.id(), spec.actionKey());
                case "void_mode" -> host.voidWarningModeButton(label, spec.id(), spec.actionKey());
                default -> throw new IllegalStateException("No cycle widget for " + spec.id());
            };
        } else if (spec.control() instanceof SettingSpec.Color color) {
            widget = createColorButton(spec, color, label, profile);
        } else if (spec.control() instanceof SettingSpec.Screen) {
            if (!"command_palette_appearance".equals(spec.id())) {
                throw new IllegalStateException("No screen widget for " + spec.id());
            }
            widget = host.commandPaletteAppearanceButton();
        } else {
            throw new IllegalStateException("No widget for " + spec.id());
        }
        boolean resettable = false;
        if (widget instanceof SettingRow row) {
            row.setDepth(SettingsRegistry.depth(spec));
            SettingReset.Unit unit = SettingReset.forSetting(spec.id()).orElse(null);
            if (unit != null) {
                resettable = true;
                row.setReset(() -> SettingReset.isModified(unit, currentProfile(), defaults),
                        () -> resetSetting(spec, unit));
            }
        }
        return resettable
                ? host.controls().resetTooltip(widget, spec.tooltipKey())
                : host.controls().tooltip(widget, spec.tooltipKey());
    }

    private void resetSetting(SettingSpec<?> spec, SettingReset.Unit unit) {
        var replacement = SettingResetPlan.setting(currentProfile(), unit);
        if (replacement.isEmpty()) {
            return;
        }
        host.setPendingSettingFocus(new SettingsSearch.Entry(spec));
        host.performProfileChange(spec.id(), "reset-" + spec.id(),
                () -> replaceProfile(replacement.get()));
        host.rebuildWidgets();
    }

    private AbstractWidget createToggle(SettingSpec<?> raw, Component label) {
        SettingSpec<Boolean> spec = booleanSpec(raw);
        boolean flightDetection = "flight_detection".equals(spec.id());
        Component on = flightDetection
                ? Component.translatable("option.elytrapitchhelper.flight_detection.any_elytra_glide")
                : CommonComponents.OPTION_ON;
        Component off = flightDetection
                ? Component.translatable("option.elytrapitchhelper.flight_detection.equipment_check")
                : CommonComponents.OPTION_OFF;
        return new ToggleRow(label, host.font(), SettingsRegistry.depth(spec),
                () -> spec.get().apply(currentProfile()),
                value -> host.performProfileChange(spec.id(), spec.actionKey(), () -> apply(spec, value)),
                on, off);
    }

    private AbstractWidget createIntSlider(SettingSpec<?> raw, SettingSpec.IntSlider slider,
            Component label, Profile profile) {
        SettingSpec<Integer> spec = integerSpec(raw);
        return host.controls().intSlider(label, spec.get().apply(profile), slider.min(), slider.max(), slider.step(),
                slider.unit(), value -> {
            apply(spec, value);
            host.saveProfileChange(spec.id());
        });
    }

    private AbstractWidget createFloatSlider(SettingSpec<?> raw, SettingSpec.FloatSlider slider,
            Component label, Profile profile) {
        SettingSpec<Float> spec = floatSpec(raw);
        return host.controls().floatSlider(label, spec.get().apply(profile), slider.min(), slider.max(), slider.step(),
                slider.unit(), value -> {
                    apply(spec, (float) value);
                    syncFloatSliders.run();
                    host.saveProfileChange(spec.id());
                });
    }

    private AbstractWidget createColorButton(SettingSpec<?> raw, SettingSpec.Color color,
            Component label, Profile profile) {
        ColorSettingBinding binding = new ColorSettingBinding(raw, this::currentProfile,
                this::replaceProfile, () -> host.saveProfileChange(raw.id()));
        SettingSpec.ColorValue state = color.state().apply(profile);
        return host.controls().colorButton(label, state.color(), state.prideEnabled(), state.prideFlagId(),
                state.customPrideColors(), () -> host.createColorEditor(label, binding));
    }

    private <T> void apply(SettingSpec<T> spec, T value) {
        replaceProfile(spec.apply(currentProfile(), value));
    }

    @SuppressWarnings("unchecked")
    private static SettingSpec<Boolean> booleanSpec(SettingSpec<?> spec) {
        return (SettingSpec<Boolean>) spec;
    }

    @SuppressWarnings("unchecked")
    private static SettingSpec<Integer> integerSpec(SettingSpec<?> spec) {
        return (SettingSpec<Integer>) spec;
    }

    @SuppressWarnings("unchecked")
    private static SettingSpec<Float> floatSpec(SettingSpec<?> spec) {
        return (SettingSpec<Float>) spec;
    }

    interface Host {
        Font font();
        SettingControlFactory controls();
        void rebuildWidgets();
        void setPendingSettingFocus(SettingsSearch.Entry entry);
        void saveProfileChange(String optionName);
        void performProfileChange(String optionName, String actionKey, Runnable change);
        ColorEditorScreen createColorEditor(Component label, ColorSettingBinding binding);

        CycleRow amplitudeTriggerModeButton(Component label, String optionName,
                String actionKey);

        CycleRow voidWarningModeButton(Component label, String optionName,
                String actionKey);

        ScreenRow commandPaletteAppearanceButton();

    }
}
