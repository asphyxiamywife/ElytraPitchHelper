package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingSpec;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingsRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SettingsRegistryCoverageTest {
    private static final Map<String, Class<?>> SECTIONS = Map.of(
            "visibility", VisibilitySettings.class,
            "pitch", PitchSettings.class,
            "line", LineSettings.class,
            "amplitude", AmplitudeSettings.class,
            "voidWarning", VoidWarningSettings.class,
            "commandPaletteAppearance", CommandPaletteAppearanceSettings.class,
            "diagnostics", DiagnosticsSettings.class);

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"2.5,3.0,2.45", "2.5,2.5,2.45", "0.1,0.2,0.05", "4.95,4.95,4.9"})
    void invalidUpVelocityClampsOneSliderStepBelowDown(float down, float up, float expected) {
        Profile defaults = ProfileDefaults.template();
        Profile invalid = defaults.withAmplitude(defaults.amplitude()
                .withDownVelocity(down).withUpVelocity(up));
        Profile repaired = SettingsRegistry.sanitize(invalid, defaults, null);
        assertEquals(down, repaired.amplitude().downVelocity());
        assertEquals(expected, repaired.amplitude().upVelocity(), 0.00001f);
        assertEquals(repaired, SettingsRegistry.sanitize(repaired, defaults, null));
    }

    @Test
    void velocitiesAboveTheCapsAreRepairedWithCoordinatedReset() {
        Profile defaults = ProfileDefaults.template();
        Profile invalid = defaults.withAmplitude(defaults.amplitude()
                .withDownVelocity(10.0f).withUpVelocity(9.95f));
        Profile repaired = SettingsRegistry.sanitize(invalid, defaults, null);
        assertEquals(defaults.amplitude().downVelocity(), repaired.amplitude().downVelocity());
        assertEquals(defaults.amplitude().downVelocity() - 0.05f,
                repaired.amplitude().upVelocity(), 0.00001f);
        assertEquals(repaired, SettingsRegistry.sanitize(repaired, defaults, null));
    }

    @Test
    void validHandEditedVelocityIsPreservedWithoutSnappingOrIncreasingTheGap() {
        Profile defaults = ProfileDefaults.template();
        Profile valid = defaults.withAmplitude(defaults.amplitude()
                .withDownVelocity(2.5f).withUpVelocity(2.487f));
        Profile sanitized = SettingsRegistry.sanitize(valid, defaults, null);
        assertEquals(2.5f, sanitized.amplitude().downVelocity());
        assertEquals(2.487f, sanitized.amplitude().upVelocity());
    }

    @Test
    void everyPersistedSettingsFieldHasExactlyOneRegistryBinding() {
        Set<String> modelFields = new HashSet<>();
        SECTIONS.forEach((section, type) -> {
            for (RecordComponent component : type.getRecordComponents()) {
                modelFields.add(section + "." + component.getName());
            }
        });

        Set<String> registeredFields = new HashSet<>();
        for (SettingSpec<?> spec : SettingsRegistry.serialized()) {
            registeredFields.add(spec.json().section() + "." + spec.json().field());
        }

        assertEquals(modelFields, registeredFields);
    }
}
