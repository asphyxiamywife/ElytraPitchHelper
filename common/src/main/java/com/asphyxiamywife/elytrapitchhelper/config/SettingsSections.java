package com.asphyxiamywife.elytrapitchhelper.config;

import java.util.function.Supplier;

final class SettingsSections {
    private SettingsSections() {
    }

    static void ensureAll(Config config) {
        config.commandPaletteUsage =
                orDefault(config.commandPaletteUsage, CommandPaletteUsageSettings::new);
        config.sectionCollapse = orDefault(config.sectionCollapse, SectionCollapseSettings::new);
    }

    private static <T> T orDefault(T value, Supplier<T> factory) {
        return value != null ? value : factory.get();
    }
}
