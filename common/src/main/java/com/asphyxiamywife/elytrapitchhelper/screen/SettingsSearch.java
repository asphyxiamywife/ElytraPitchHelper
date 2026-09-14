package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingSpec;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingsRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

final class SettingsSearch {
    private SettingsSearch() {
    }

    static Entry entry(ConfigCategory category, String optionName) {
        SettingSpec<?> spec = SettingsRegistry.byId(optionName);
        if (spec == null || spec.category() != category || !spec.searchable()) {
            throw new IllegalArgumentException("Unknown setting " + category + "/" + optionName);
        }
        return new Entry(spec);
    }

    static List<Entry> entries(ConfigCategory category, Profile profile) {
        return SettingsRegistry.visible(category, profile).stream().map(Entry::new).toList();
    }

    static List<Entry> allEntries(Profile profile) {
        List<Entry> entries = new ArrayList<>();
        for (ConfigCategory category : ConfigCategory.values()) {
            entries.addAll(entries(category, profile));
        }
        return List.copyOf(entries);
    }

    static boolean matchesLabel(Entry entry, String query, Function<String, String> translator) {
        return TextMatch.matchesAllTokens(translator.apply(entry.labelKey()), query);
    }

    static boolean matches(Entry entry, String query, Function<String, String> translator) {
        String searchableText = translator.apply(entry.labelKey()) + " "
                + translator.apply(entry.tooltipKey());
        return TextMatch.matchesAllTokens(searchableText, query);
    }

    static boolean isActive(String query) {
        return query != null && !query.isBlank();
    }

    enum PaletteOpenMode {
        FOCUS_CONTROL,
        OPEN_SCREEN,
        TOGGLE_SETTING
    }

    record Entry(SettingSpec<?> spec) {
        Entry {
            if (!spec.searchable()) {
                throw new IllegalArgumentException("Hidden settings cannot be searched");
            }
        }

        ConfigCategory category() {
            return spec.category();
        }

        String labelKey() {
            return spec.labelKey();
        }

        String tooltipKey() {
            return spec.tooltipKey();
        }

        String optionName() {
            return spec.id();
        }

        List<String> paletteKeywords() {
            return spec.paletteKeywords();
        }

        PaletteOpenMode paletteOpenMode() {
            if (spec.control() instanceof SettingSpec.Toggle) {
                return PaletteOpenMode.TOGGLE_SETTING;
            }
            if (spec.control() instanceof SettingSpec.Cycle cycle && cycle.paletteToggle()) {
                return PaletteOpenMode.TOGGLE_SETTING;
            }
            if (spec.control() instanceof SettingSpec.Color
                    || spec.control() instanceof SettingSpec.Screen) {
                return PaletteOpenMode.OPEN_SCREEN;
            }
            return PaletteOpenMode.FOCUS_CONTROL;
        }

        ToggleBinding toggleBinding() {
            return paletteOpenMode() == PaletteOpenMode.TOGGLE_SETTING ? new ToggleBinding(spec) : null;
        }
    }
}
