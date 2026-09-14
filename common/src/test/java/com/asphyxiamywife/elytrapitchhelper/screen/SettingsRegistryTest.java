package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.VoidWarningSettings;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingSpec;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingsRegistry;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.SliderRow;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SettingsRegistryTest {
    @Test
    void everyEditorSettingHasItsDerivedLabelAndTooltip() {
        JsonObject language = language();

        for (SettingSpec<?> spec : SettingsRegistry.all()) {
            if (!spec.searchable()) {
                continue;
            }
            assertTrue(language.has(spec.labelKey()), spec.labelKey());
            assertTrue(language.has(spec.tooltipKey()), spec.tooltipKey());
        }
    }

    @Test
    void everyCategoryRendersExactlyItsVisibleRegistrySpecs() {
        Profile profile = new Profile();
        for (int mode : List.of(VoidWarningSettings.MODE_PREDICTED_TIME,
                VoidWarningSettings.MODE_SIMPLE_HEIGHT)) {
            profile = profile.withVoidWarning(profile.voidWarning().withMode(mode));
            for (ConfigCategory category : ConfigCategory.values()) {
                List<String> declared = SettingsRegistry.visible(category, profile).stream()
                        .map(SettingSpec::id)
                        .toList();
                List<String> rendered = SettingsSearch.entries(category, profile).stream()
                        .map(SettingsSearch.Entry::optionName)
                        .toList();
                assertEquals(declared, rendered, category + " mode " + mode);
            }
        }
    }

    @Test
    void idsAndJsonLocationsAreUnique() {
        Set<String> ids = new HashSet<>();
        Set<String> jsonLocations = new HashSet<>();
        for (SettingSpec<?> spec : SettingsRegistry.all()) {
            assertTrue(ids.add(spec.id()), spec.id());
            if (spec.json() != null) {
                String location = spec.json().section() + "." + spec.json().field();
                assertTrue(jsonLocations.add(location), location);
            }
        }
    }

    @Test
    void amplitudeVelocityControlsMoveTheirNeighbourAtTheHysteresisBoundary() {
        SettingSpec<Float> down = floatSpec("amplitude_down_velocity");
        SettingSpec<Float> up = floatSpec("amplitude_up_velocity");
        SettingSpec.FloatSlider downSlider = (SettingSpec.FloatSlider) down.control();
        SettingSpec.FloatSlider upSlider = (SettingSpec.FloatSlider) up.control();

        assertEquals(0.1f, downSlider.min());
        assertEquals(5.0f, downSlider.max());
        assertEquals(0.05f, downSlider.step());
        assertEquals(0.0f, upSlider.min());
        assertEquals(4.95f, upSlider.max());
        assertEquals(0.05f, upSlider.step());

        Profile profile = up.apply(new Profile(), 4.95f);
        assertTrue(profile.amplitude().upVelocity() < profile.amplitude().downVelocity());
        assertEquals(4.95f, profile.amplitude().upVelocity());
        assertEquals(5.0f, profile.amplitude().downVelocity(),
                "Dive speed should follow Reset speed at the upper boundary");

        profile = down.apply(profile, 0.1f);
        assertTrue(profile.amplitude().upVelocity() < profile.amplitude().downVelocity());
        assertEquals(0.1f, profile.amplitude().downVelocity());
        assertEquals(0.05f, profile.amplitude().upVelocity(),
                "Reset speed should follow Dive speed at the lower boundary");
    }

    @Test
    void coordinatedVelocityValuesAreSynchronizedIntoBothVisibleRows() {
        SettingSpec<Float> down = floatSpec("amplitude_down_velocity");
        SettingSpec<Float> up = floatSpec("amplitude_up_velocity");
        SliderRow downRow = sliderRow("Dive", 2.2f);
        SliderRow upRow = sliderRow("Reset", 0.5f);
        Profile coordinated = up.apply(new Profile(), 4.0f);

        ProfileEditorPanel.syncFloatSlider(coordinated, down, downRow);
        ProfileEditorPanel.syncFloatSlider(coordinated, up, upRow);

        assertTrue(downRow.getMessage().getString().contains("4.05"));
        assertTrue(upRow.getMessage().getString().contains("4.0"));
    }

    private static SliderRow sliderRow(String label, float current) {
        return new SliderRow(Component.literal(label), null, 0, current, 0.0, 10.0, 0.05,
                Double::toString, value -> {}, "test", SliderRow.InteractionListener.NONE);
    }

    @Test
    void registryFallbacksComeFromTheBundledDefault() {
        Profile defaults = Config.defaultProfileTemplate();

        for (SettingSpec<?> spec : SettingsRegistry.all()) {
            assertTrue(Objects.deepEquals(spec.get().apply(defaults), spec.defaultValue()), spec.id());
        }
    }

    @Test
    void glyphTempoControlsAdvanceInFiftyMillisecondSteps() {
        SettingSpec.IntSlider pullUp = (SettingSpec.IntSlider)
                SettingsRegistry.byId("pull_up_glyph_duration").control();
        SettingSpec.IntSlider release = (SettingSpec.IntSlider)
                SettingsRegistry.byId("release_down_glyph_duration").control();

        assertEquals(50, pullUp.step());
        assertEquals(50, release.step());
    }

    @Test
    void flightTelemetryIsAnOptInInterfaceToggle() {
        SettingSpec<?> raw = SettingsRegistry.byId("flight_telemetry_logging");
        assertNotNull(raw);
        assertEquals(ConfigCategory.INTERFACE, raw.category());
        assertTrue(raw.control() instanceof SettingSpec.Toggle);

        @SuppressWarnings("unchecked")
        SettingSpec<Boolean> telemetry = (SettingSpec<Boolean>) raw;
        Profile defaults = new Profile();
        assertFalse(telemetry.get().apply(defaults));
        assertTrue(telemetry.get().apply(telemetry.apply(defaults, true)));
    }

    @SuppressWarnings("unchecked")
    private static SettingSpec<Float> floatSpec(String id) {
        return (SettingSpec<Float>) SettingsRegistry.byId(id);
    }

    private static JsonObject language() {
        var stream = SettingsRegistryTest.class.getResourceAsStream(
                "/assets/elytrapitchhelper/lang/en_us.json");
        assertNotNull(stream);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }
}
