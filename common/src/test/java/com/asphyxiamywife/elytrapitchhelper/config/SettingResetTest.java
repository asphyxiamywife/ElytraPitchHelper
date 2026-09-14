package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingReset;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingSpec;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingsRegistry;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SettingResetTest {
    @Test
    void modelConstructorsUseTheBundledSettings() {
        Profile defaults = Config.defaultProfileTemplate();
        Profile constructed = new Profile();

        assertEquals(defaults.visibility(), constructed.visibility());
        assertEquals(defaults.pitch(), constructed.pitch());
        assertEquals(defaults.line(), constructed.line());
        assertEquals(defaults.amplitude(), constructed.amplitude());
        assertEquals(defaults.voidWarning(), constructed.voidWarning());
        assertEquals(defaults.commandPaletteAppearance(), constructed.commandPaletteAppearance());
        assertEquals(defaults.diagnostics(), constructed.diagnostics());
    }

    @Test
    void bundledTemplateHasNoModifiedResetUnits() {
        Profile defaults = Config.defaultProfileTemplate();

        for (SettingSpec<?> spec : SettingsRegistry.all()) {
            SettingReset.forSetting(spec.id()).ifPresent(unit ->
                    assertFalse(SettingReset.isModified(unit, defaults, defaults), unit.id()));
        }
    }

    @Test
    void everyVisibleNonDynamicSettingHasAResetUnit() {
        for (SettingSpec<?> spec : SettingsRegistry.all()) {
            if (spec.searchable() && !(spec.control() instanceof SettingSpec.Dynamic<?>)) {
                assertTrue(SettingReset.forSetting(spec.id()).isPresent(), spec.id());
            }
        }
    }

    @Test
    void everyHiddenSettingIsClaimedExactlyOnce() {
        Set<String> hidden = new HashSet<>();
        Set<String> claimed = new HashSet<>();
        for (SettingSpec<?> spec : SettingsRegistry.all()) {
            if (spec.control() instanceof SettingSpec.Hidden<?>) {
                hidden.add(spec.id());
            }
            for (String companion : spec.companions()) {
                assertTrue(claimed.add(companion), companion + " is claimed more than once");
            }
        }

        assertEquals(hidden, claimed);
    }

    @Test
    void resettingEveryUnitAndDimensionKeyMatchesWholeProfileDefaults() {
        Profile defaults = Config.defaultProfileTemplate();
        Profile profile = defaults.withName("Kept name").withFileName("kept.json");
        for (SettingSpec<?> spec : SettingsRegistry.serialized()) {
            profile = modify(spec, profile);
        }

        for (SettingSpec<?> spec : SettingsRegistry.all()) {
            SettingReset.Unit unit = SettingReset.forSetting(spec.id()).orElse(null);
            if (unit != null) {
                profile = SettingReset.reset(unit, profile, defaults);
            }
        }
        for (String dimension : Set.copyOf(profile.voidWarning().dimensionYOverrides().keySet())) {
            Map<String, Integer> overrides = new HashMap<>(profile.voidWarning().dimensionYOverrides());
            overrides.remove(dimension);
            profile = profile.withVoidWarning(profile.voidWarning().withDimensionYOverrides(overrides));
        }

        assertEquals(defaults.withName("Kept name").withFileName("kept.json"), profile);
    }

    @Test
    void colorResetAlsoClearsItsPrideState() {
        Profile defaults = Config.defaultProfileTemplate();
        Profile modified = defaults.withLine(defaults.line()
                .withColorRgb(0x123456)
                .withPride(true, "trans", new int[] {0x111111, 0x222222}));
        SettingReset.Unit unit = SettingReset.forSetting("line_color").orElseThrow();

        Profile reset = SettingReset.reset(unit, modified, defaults);

        assertEquals(defaults.line(), reset.line());
        assertFalse(reset.line().prideEnabled());
    }

    @SuppressWarnings("unchecked")
    private static <T> Profile modify(SettingSpec<?> raw, Profile profile) {
        SettingSpec<T> spec = (SettingSpec<T>) raw;
        Object current = spec.get().apply(profile);
        Object changed;
        if (current instanceof Boolean value) {
            changed = !value;
        } else if (current instanceof Integer value) {
            changed = value + 1;
        } else if (current instanceof Float value) {
            changed = value + 0.25f;
        } else if (current instanceof String value) {
            changed = value + "-modified";
        } else if (current instanceof int[]) {
            changed = new int[] {0x123456, 0x654321};
        } else if (current instanceof Map<?, ?>) {
            changed = Map.of("test:dimension", -64);
        } else {
            throw new IllegalStateException("No test mutation for " + spec.id() + ": " + current);
        }
        return spec.apply(profile, (T) changed);
    }
}
