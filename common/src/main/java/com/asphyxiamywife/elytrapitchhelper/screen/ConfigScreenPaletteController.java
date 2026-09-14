package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.CommandPaletteUsageSettings;
import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingSpec;
import net.minecraft.client.gui.screens.Screen;

final class ConfigScreenPaletteController {
    interface Host {
        Config config();

        int paletteCurrentProfileIndex();

        boolean historyRestoring();

        boolean deleteMode();

        void copyPaletteUsageToHistoryBaseline();

        void scheduleSave();

        void saveProfileChange(String optionName);

        ColorEditorScreen createColorEditor(net.minecraft.network.chat.Component label, ColorSettingBinding binding);

        boolean openCommandPaletteAppearanceScreen();

        boolean openChildScreen(Screen screen);
    }

    private final Host host;

    ConfigScreenPaletteController(Host host) {
        this.host = host;
    }

    void recordActionUse(String actionId, long nowMillis) {
        if (host.config().isReadOnly()) {
            return;
        }
        recordInteraction(actionId, nowMillis, true, true);
    }

    void recordLocalSettingEdit(String optionName) {
        Config config = host.config();
        if (config.isReadOnly() || host.historyRestoring() || optionName == null || optionName.isBlank()
                || config.profileCount() <= 0) {
            return;
        }
        SettingsSearch.Entry entry = entryForOption(optionName);
        if (entry != null) {
            recordInteraction(PaletteActions.settingActionId(entry), System.currentTimeMillis(), false, false);
        }
    }

    boolean openSettingScreen(SettingsSearch.Entry entry) {
        Config config = host.config();
        if (config.profileCount() <= 0) {
            return false;
        }
        if (entry.spec().control() instanceof SettingSpec.Color) {
            ColorSettingBinding binding = new ColorSettingBinding(entry.spec(),
                    () -> host.config().profile(host.paletteCurrentProfileIndex()),
                    replacement -> host.config().replaceProfile(host.paletteCurrentProfileIndex(), replacement),
                    () -> host.saveProfileChange(entry.spec().id()));
            return host.openChildScreen(host.createColorEditor(
                    net.minecraft.network.chat.Component.translatable(entry.spec().labelKey()), binding));
        }
        return "command_palette_appearance".equals(entry.optionName())
                && host.openCommandPaletteAppearanceScreen();
    }

    private SettingsSearch.Entry entryForOption(String optionName) {
        Profile profile = host.config().profile(host.paletteCurrentProfileIndex());
        for (SettingsSearch.Entry entry : SettingsSearch.allEntries(profile)) {
            if (entry.optionName().equals(optionName)) {
                return entry;
            }
        }
        return null;
    }

    private void recordInteraction(String actionId, long nowMillis, boolean actionUse, boolean scheduleSave) {
        Config config = host.config();
        if (config.commandPaletteUsage == null) {
            config.commandPaletteUsage = new CommandPaletteUsageSettings();
        }
        if (actionUse) {
            config.commandPaletteUsage = config.commandPaletteUsage.recordUse(actionId, nowMillis);
        } else {
            config.commandPaletteUsage = config.commandPaletteUsage.recordEdit(actionId, nowMillis);
        }
        host.copyPaletteUsageToHistoryBaseline();
        if (scheduleSave && !host.deleteMode()) {
            host.scheduleSave();
        }
    }
}
