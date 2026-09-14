package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingsRegistry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PaletteColorBindingTest {
    @Test
    void colorEditsAfterConfigReplacementReadAndSaveTheCurrentProfile() {
        Host host = new Host();
        Config original = host.config;
        int originalColor = original.profile(0).line().colorRgb();
        openColor(host);

        host.config = original.copy();
        Profile restored = host.config.profile(0);
        host.config.replaceProfile(0, restored.withLine(restored.line()
                .withColorRgb(0x123456).withLengthPixels(75).withWidthPixels(3)));

        assertEquals(0x123456, host.binding.value().color());
        assertEquals(75, host.binding.previewLineLength());
        assertEquals(3, host.binding.previewLineWidth());
        host.binding.setColor(0x654321);

        assertEquals(0x654321, host.config.profile(0).line().colorRgb());
        assertEquals(75, host.config.profile(0).line().lengthPixels());
        assertEquals(originalColor, original.profile(0).line().colorRgb());
        assertEquals(0x654321, host.saved.profile(0).line().colorRgb());
        assertEquals("line_color", host.savedOption);
    }

    @Test
    void prideEditsAfterConfigReplacementPreserveRestoredColor() {
        Host host = new Host();
        openColor(host);
        host.config = host.config.copy();
        Profile restored = host.config.profile(0);
        host.config.replaceProfile(0, restored.withLine(restored.line().withColorRgb(0x123456)));

        host.binding.setPride(true, "trans", new int[0]);

        assertTrue(host.binding.value().prideEnabled());
        assertEquals("trans", host.binding.value().prideFlagId());
        assertEquals(0x123456, host.binding.value().color());
        assertTrue(host.saved.profile(0).line().prideEnabled());
        assertFalse(host.binding.previewCuePeak());
    }

    private static void openColor(Host host) {
        assertTrue(new ConfigScreenPaletteController(host).openSettingScreen(
                new SettingsSearch.Entry(SettingsRegistry.byId("line_color"))));
    }

    private static final class Host implements ConfigScreenPaletteController.Host {
        Config config = new Config();
        ColorSettingBinding binding;
        Config saved;
        String savedOption;

        @Override public Config config() { return config; }
        @Override public int paletteCurrentProfileIndex() { return 0; }
        @Override public boolean historyRestoring() { return false; }
        @Override public boolean deleteMode() { return false; }
        @Override public void copyPaletteUsageToHistoryBaseline() {}
        @Override public void scheduleSave() {}
        @Override public boolean openCommandPaletteAppearanceScreen() { return false; }
        @Override public boolean openChildScreen(Screen screen) { return true; }

        @Override
        public void saveProfileChange(String optionName) {
            savedOption = optionName;
            saved = config.copy();
        }

        @Override
        public ColorEditorScreen createColorEditor(Component label, ColorSettingBinding binding) {
            this.binding = binding;
            return null;
        }
    }
}
