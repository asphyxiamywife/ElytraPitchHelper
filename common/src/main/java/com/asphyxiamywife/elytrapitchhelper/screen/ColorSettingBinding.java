package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingSpec;

import java.util.function.Consumer;
import java.util.function.Supplier;

final class ColorSettingBinding {
    private final SettingSpec<Integer> spec;
    private final SettingSpec.Color color;
    private final Supplier<Profile> current;
    private final Consumer<Profile> replace;
    private final Runnable save;

    @SuppressWarnings("unchecked")
    ColorSettingBinding(SettingSpec<?> spec, Supplier<Profile> current,
            Consumer<Profile> replace, Runnable save) {
        if (!(spec.control() instanceof SettingSpec.Color color)) {
            throw new IllegalArgumentException("Not a color setting: " + spec.id());
        }
        this.spec = (SettingSpec<Integer>) spec;
        this.color = color;
        this.current = current;
        this.replace = replace;
        this.save = save;
    }

    SettingSpec.ColorValue value() {
        return color.state().apply(current.get());
    }

    int previewLineLength() {
        return current.get().line().lengthPixels();
    }

    int previewLineWidth() {
        return current.get().line().widthPixels();
    }

    boolean previewCuePeak() {
        return color.previewCuePeak();
    }

    void setColor(int value) {
        replace.accept(spec.apply(current.get(), value));
        save.run();
    }

    void setPride(boolean enabled, String flagId, int[] customColors) {
        replace.accept(color.setPride().set(current.get(), enabled, flagId, customColors));
        save.run();
    }
}
