package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.client.ClientConfigStore;
import com.asphyxiamywife.elytrapitchhelper.config.CommandPaletteAppearanceSettings;
import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import net.minecraft.client.gui.screens.Screen;

record PaletteState(Screen parent, Config config, ConfigScreen configScreen) {
    static PaletteState capture(Screen parent) {
        if (parent instanceof ConfigScreen configScreen) {
            return new PaletteState(parent, configScreen.paletteConfig().copy(), configScreen);
        }
        return capture(parent, ClientConfigStore.get());
    }

    static PaletteState capture(Screen parent, Config publishedConfig) {
        ConfigScreen configScreen = parent instanceof ConfigScreen screen ? screen : null;
        Config config = configScreen == null ? publishedConfig : configScreen.paletteConfig().copy();
        return new PaletteState(parent, config, configScreen);
    }

    boolean hasProfiles() {
        return config != null && config.profileCount() > 0;
    }

    boolean deleteMode() {
        return configScreen != null && configScreen.paletteDeleteMode();
    }

    int currentProfileIndex() {
        if (!hasProfiles()) {
            return 0;
        }
        if (configScreen != null) {
            return configScreen.paletteCurrentProfileIndex();
        }
        return config.clampProfileIndex(config.activeProfileIndex);
    }

    Profile currentProfile() {
        return hasProfiles() ? config.profile(currentProfileIndex()) : null;
    }

    CommandPaletteAppearanceSettings appearance() {
        Profile profile = currentProfile();
        if (profile == null) {
            return new CommandPaletteAppearanceSettings();
        }
        return profile.commandPaletteAppearance();
    }
}
