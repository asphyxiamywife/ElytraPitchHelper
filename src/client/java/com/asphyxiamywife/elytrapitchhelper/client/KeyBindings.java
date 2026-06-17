package com.asphyxiamywife.elytrapitchhelper.client;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.ConfigSaveException;
import com.asphyxiamywife.elytrapitchhelper.screen.ConfigScreen;
import com.asphyxiamywife.elytrapitchhelper.ModConstants;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

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
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                KEY_CATEGORY));

        KeyMapping openConfig = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.elytrapitchhelper.open_config",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
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
        if (client.player != null) {
            try {
                ClientConfigStore.set(config);
            } catch (ConfigSaveException e) {
                client.player.sendOverlayMessage(Component
                        .translatable("message.elytrapitchhelper.config.save_failed"));
                return;
            }
            Component message = Component
                    .translatable("message.elytrapitchhelper.toggle." + (config.enabled ? "on" : "off"));
            client.player.sendOverlayMessage(message);
        } else {
            ClientConfigStore.set(config);
        }
    }

    private static void openConfig(Minecraft client) {
        Screen currentScreen = client.screen;
        if (!(currentScreen instanceof ConfigScreen)) {
            client.setScreen(new ConfigScreen(currentScreen));
        }
    }
}
