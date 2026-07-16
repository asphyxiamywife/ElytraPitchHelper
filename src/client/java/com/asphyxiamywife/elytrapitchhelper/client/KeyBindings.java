package com.asphyxiamywife.elytrapitchhelper.client;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.screen.ConfigScreen;
import com.asphyxiamywife.elytrapitchhelper.ModConstants;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class KeyBindings {
    private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category
            .register(ModConstants.id("keys"));

    private final KeyMapping toggleGuides;
    private final KeyMapping openConfig;

    private KeyBindings(KeyMapping toggleGuides, KeyMapping openConfig) {
        this.toggleGuides = toggleGuides;
        this.openConfig = openConfig;
    }

    public static KeyBindings register() {
        KeyMapping toggleGuides = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.elytrapitchhelper.toggle",
                InputConstants.Type.KEYBOARD,
                InputConstants.UNKNOWN.getValue(),
                KEY_CATEGORY));

        KeyMapping openConfig = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.elytrapitchhelper.open_config",
                InputConstants.Type.KEYBOARD,
                InputConstants.UNKNOWN.getValue(),
                KEY_CATEGORY));

        return new KeyBindings(toggleGuides, openConfig);
    }

    public void handleClientTick(Minecraft client) {
        while (toggleGuides.consumeClick()) {
            toggleGuides(client);
        }
        while (openConfig.consumeClick()) {
            openConfig(client);
        }
    }

    private static void toggleGuides(Minecraft client) {
        Config config = ClientConfigStore.get().copy();
        config.enabled = !config.enabled;
        ClientConfigStore.set(config);
        if (client.player != null) {
            Component message = Component
                    .translatable("message.elytrapitchhelper.toggle." + (config.enabled ? "on" : "off"));
            client.player.sendOverlayMessage(message);
        }
    }

    private static void openConfig(Minecraft client) {
        Screen currentScreen = client.gui.screen();
        if (!(currentScreen instanceof ConfigScreen)) {
            client.gui.setScreen(new ConfigScreen(currentScreen));
        }
    }
}
