package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingSpec;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingsRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SettingDependencyTest {
    @Test
    void everyParentResolvesToARealSettingInTheSameSection() {
        for (SettingSpec<?> spec : SettingsRegistry.all()) {
            if (spec.parent() == null) {
                continue;
            }
            SettingSpec<?> parent = SettingsRegistry.byId(spec.parent());
            assertNotNull(parent, () -> spec.id() + " hangs off unknown setting " + spec.parent());
            assertEquals(spec.category(), parent.category(),
                    () -> spec.id() + " indents under a setting in another section");
        }
    }

    @Test
    void dependencyChainsTerminateWithinTheIndentBudget() {
        for (SettingSpec<?> spec : SettingsRegistry.all()) {
            int depth = SettingsRegistry.depth(spec);
            assertTrue(depth <= SettingsRegistry.MAX_DEPENDENCY_DEPTH,
                    () -> spec.id() + " nests deeper than the list can indent");
            if (depth == SettingsRegistry.MAX_DEPENDENCY_DEPTH) {
                SettingSpec<?> grandparent = SettingsRegistry.byId(
                        SettingsRegistry.byId(spec.parent()).parent());
                assertTrue(grandparent == null || grandparent.parent() == null,
                        () -> spec.id() + " is in a parent cycle");
            }
        }
    }

    @Test
    void switchingOffTheCueDisablesEverythingUnderIt() {
        Profile on = new Profile();
        Profile off = on.withAmplitude(on.amplitude().withEnabled(false));
        SettingSpec<?> helper = SettingsRegistry.byId("amplitude_helper");
        SettingSpec<?> child = SettingsRegistry.byId("amplitude_trigger_mode");
        SettingSpec<?> color = SettingsRegistry.byId("amplitude_color");

        assertTrue(SettingsRegistry.isEnabled(child, on));
        assertFalse(SettingsRegistry.isEnabled(child, off));
        assertFalse(SettingsRegistry.isEnabled(color, off));
        assertTrue(SettingsRegistry.isEnabled(helper, off));
    }

    @Test
    void overrideChildrenFollowBothTheirOverrideAndTheVoidMaster() {
        Profile defaults = new Profile();
        Profile profile = defaults.withVoidWarning(
                defaults.voidWarning().withToleranceOverride(true));
        SettingSpec<?> customTolerance = SettingsRegistry.byId("void_custom_tolerance");

        assertEquals(2, SettingsRegistry.depth(customTolerance));
        assertTrue(SettingsRegistry.isEnabled(customTolerance, profile));

        Profile overrideOff = profile.withVoidWarning(
                profile.voidWarning().withToleranceOverride(false));
        assertFalse(SettingsRegistry.isEnabled(customTolerance, overrideOff));

        Profile voidOff = profile.withVoidWarning(profile.voidWarning().withEnabled(false));
        assertTrue(voidOff.voidWarning().toleranceOverride());
        assertFalse(SettingsRegistry.isEnabled(customTolerance, voidOff));
    }

    @Test
    void glyphTemposAreChildrenOfTheGlyphToggleAndFollowBothAncestors() {
        Profile profile = new Profile();
        SettingSpec<?> pullUp = SettingsRegistry.byId("pull_up_glyph_duration");
        SettingSpec<?> release = SettingsRegistry.byId("release_down_glyph_duration");

        assertEquals("motion_glyphs", pullUp.parent());
        assertEquals("motion_glyphs", release.parent());
        assertEquals(2, SettingsRegistry.depth(pullUp));
        assertEquals(2, SettingsRegistry.depth(release));
        assertTrue(SettingsRegistry.isEnabled(pullUp, profile));
        assertTrue(SettingsRegistry.isEnabled(release, profile));

        Profile glyphsOff = profile.withAmplitude(profile.amplitude().withMotionGlyphsEnabled(false));
        assertFalse(SettingsRegistry.isEnabled(pullUp, glyphsOff));
        assertFalse(SettingsRegistry.isEnabled(release, glyphsOff));

        Profile helperOff = profile.withAmplitude(profile.amplitude().withEnabled(false));
        assertFalse(SettingsRegistry.isEnabled(pullUp, helperOff));
        assertFalse(SettingsRegistry.isEnabled(release, helperOff));
    }

    @Test
    void everyColourIsTheLastRowOfItsSection() {
        for (String id : new String[] {"line_color", "amplitude_color", "void_color"}) {
            SettingSpec<?> colour = SettingsRegistry.byId(id);
            List<SettingSpec<?>> section = SettingsRegistry.visible(colour.category(), new Profile());

            assertEquals(id, section.get(section.size() - 1).id(),
                    () -> id + " should close " + colour.category());
        }
    }

    @Test
    void aMasterToggleLeadsItsSection() {
        for (String id : new String[] {"amplitude_helper", "void_warning"}) {
            SettingSpec<?> master = SettingsRegistry.byId(id);
            List<SettingSpec<?>> section = SettingsRegistry.visible(master.category(), new Profile());

            assertEquals(id, section.get(0).id(), () -> id + " should lead " + master.category());
        }
    }

    @Test
    void topLevelSettingsAreNeverIndented() {
        for (String id : new String[] {"show_only_with_firework", "target_up", "line_length",
                "amplitude_helper", "void_warning", "command_palette_appearance"}) {
            SettingSpec<?> spec = SettingsRegistry.byId(id);
            assertEquals(0, SettingsRegistry.depth(spec), () -> id + " should sit at the top level");
        }
    }
}
