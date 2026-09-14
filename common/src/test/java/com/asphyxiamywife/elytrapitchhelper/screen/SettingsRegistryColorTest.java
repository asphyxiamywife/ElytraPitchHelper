package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingSpec;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingsRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SettingsRegistryColorTest {
    @Test
    void eachOptionReadsAndWritesOnlyItsOwnProfileSection() {
        for (SettingSpec<Integer> target : colorSpecs()) {
            Profile profile = distinctProfile();
            SettingSpec.Color control = (SettingSpec.Color) target.control();
            SettingSpec.ColorValue before = control.state().apply(profile);
            int[] colors = {0x010203, 0xA0B0C0};

            profile = target.apply(profile, 0x123456);
            profile = control.setPride().set(
                    profile, !before.prideEnabled(), "custom", colors);

            SettingSpec.ColorValue after = control.state().apply(profile);
            assertEquals(0x123456, after.color(), target.id());
            assertEquals(!before.prideEnabled(), after.prideEnabled(), target.id());
            assertEquals("custom", after.prideFlagId(), target.id());
            assertArrayEquals(colors, after.customPrideColors(), target.id());

            for (SettingSpec<Integer> other : colorSpecs()) {
                if (other == target) {
                    continue;
                }
                SettingSpec.Color otherControl = (SettingSpec.Color) other.control();
                SettingSpec.ColorValue untouched = otherControl.state().apply(profile);
                SettingSpec.ColorValue expected = otherControl.state().apply(distinctProfile());
                String message = target.id() + " leaked into " + other.id();
                assertEquals(expected.color(), untouched.color(), message);
                assertEquals(expected.prideEnabled(), untouched.prideEnabled(), message);
                assertEquals(expected.prideFlagId(), untouched.prideFlagId(), message);
                assertArrayEquals(expected.customPrideColors(), untouched.customPrideColors(), message);
            }
        }
    }

    @Test
    void optionLookupAndPreviewModesAreStable() {
        assertFalse(colorControl("line_color").previewCuePeak());
        assertTrue(colorControl("amplitude_color").previewCuePeak());
        assertTrue(colorControl("void_color").previewCuePeak());
    }

    private static Profile distinctProfile() {
        Profile profile = new Profile();
        return profile
                .withLine(profile.line().withColorRgb(1).withPrideFlag("line"))
                .withAmplitude(profile.amplitude().withCueColorRgb(2)
                        .withCuePrideEnabled(true).withCuePrideFlag("amplitude"))
                .withVoidWarning(profile.voidWarning().withWarningColorRgb(3)
                        .withWarningPrideFlag("void"));
    }

    private static SettingSpec.Color colorControl(String id) {
        return (SettingSpec.Color) SettingsRegistry.byId(id).control();
    }

    @SuppressWarnings("unchecked")
    private static List<SettingSpec<Integer>> colorSpecs() {
        return SettingsRegistry.all().stream()
                .filter(spec -> spec.control() instanceof SettingSpec.Color)
                .map(spec -> (SettingSpec<Integer>) spec)
                .toList();
    }
}
