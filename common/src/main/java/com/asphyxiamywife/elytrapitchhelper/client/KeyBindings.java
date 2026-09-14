package com.asphyxiamywife.elytrapitchhelper.client;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.screen.CommandPaletteScreen;
import com.asphyxiamywife.elytrapitchhelper.screen.ConfigScreen;
import com.asphyxiamywife.elytrapitchhelper.ModConstants;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

public final class KeyBindings {
    private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category
            .register(ModConstants.id("keys"));

    private final KeyMapping toggleGuides;
    private final KeyMapping openConfig;
    private final KeyMapping openProfiles;
    private final KeyMapping openPaletteModifier;
    private final KeyMapping openPalette;
    private static KeyBindings registered;
    private boolean paletteClickPending;

    KeyBindings(KeyMapping toggleGuides, KeyMapping openConfig, KeyMapping openProfiles,
            KeyMapping openPaletteModifier, KeyMapping openPalette) {
        this.toggleGuides = toggleGuides;
        this.openConfig = openConfig;
        this.openProfiles = openProfiles;
        this.openPaletteModifier = openPaletteModifier;
        this.openPalette = openPalette;
    }

    public static KeyBindings register(Function<KeyMapping, KeyMapping> registrar) {
        KeyMapping toggleGuides = registrar.apply(new KeyMapping(
                "key.elytrapitchhelper.toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                KEY_CATEGORY));

        KeyMapping openConfig = registrar.apply(new KeyMapping(
                "key.elytrapitchhelper.open_config",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                KEY_CATEGORY));

        KeyMapping openProfiles = registrar.apply(new KeyMapping(
                "key.elytrapitchhelper.open_profiles",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                KEY_CATEGORY));

        KeyMapping openPaletteModifier = registrar.apply(new KeyMapping(
                "key.elytrapitchhelper.open_palette_modifier",
                InputConstants.Type.KEYSYM,
                defaultPaletteModifierKey(),
                KEY_CATEGORY));

        KeyMapping openPalette = registrar.apply(new KeyMapping(
                "key.elytrapitchhelper.open_palette",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                KEY_CATEGORY));

        KeyBindings keyBindings = new KeyBindings(toggleGuides, openConfig, openProfiles,
                openPaletteModifier, openPalette);
        registered = keyBindings;
        return keyBindings;
    }

    public void handleClientTick(Minecraft client) {
        while (toggleGuides.consumeClick()) {
            toggleGuides(client);
        }
        while (openConfig.consumeClick()) {
            openConfig(client);
        }
        while (openProfiles.consumeClick()) {
            openProfiles(client);
        }
        consumeClicks(openPalette);
        consumeClicks(openPaletteModifier);
        if (consumePaletteClick() && client.screen == null) {
            openCommandPalette(client);
        }
    }

    public static void onKeyMappingClick(InputConstants.Key key) {
        KeyBindings bindings = registered;
        if (bindings != null) {
            bindings.recordPaletteClick(key, Minecraft.getInstance().screen == null);
        }
    }

    void recordPaletteClick(InputConstants.Key key, boolean inGame) {
        if (inGame && (key.equals(InputConstants.getKey(openPalette.saveString()))
                && openPaletteModifier.isDown()
                || key.equals(InputConstants.getKey(openPaletteModifier.saveString()))
                && openPalette.isDown())) {
            paletteClickPending = true;
        }
    }

    boolean consumePaletteClick() {
        boolean pending = paletteClickPending;
        paletteClickPending = false;
        return pending;
    }

    private static boolean consumeClicks(KeyMapping keyMapping) {
        boolean clicked = false;
        while (keyMapping.consumeClick()) {
            clicked = true;
        }
        return clicked;
    }

    public static boolean handleCommandPaletteShortcut(Minecraft client, KeyEvent event) {
        return handleCommandPaletteShortcut(client, event, () -> true);
    }

    public static boolean handleCommandPaletteShortcut(Minecraft client, KeyEvent event,
            BooleanSupplier beforeOpen) {
        KeyBindings keyBindings = registered;
        if (keyBindings == null) {
            return false;
        }
        return handleCommandPaletteShortcut(event, keyBindings.openPalette, keyBindings.openPaletteModifier,
                keyMapping -> isBoundKeyDown(client, keyMapping), beforeOpen, () -> openCommandPalette(client));
    }

    static boolean handleCommandPaletteShortcut(KeyEvent event, KeyMapping paletteKey, KeyMapping modifierKey,
            Predicate<KeyMapping> isDown, BooleanSupplier beforeOpen, Runnable openPalette) {
        if (!isCommandPaletteShortcut(event, paletteKey, modifierKey, isDown)) {
            return false;
        }
        if (beforeOpen.getAsBoolean()) {
            openPalette.run();
        }
        return true;
    }

    static boolean isCommandPaletteShortcut(KeyEvent event, KeyMapping paletteKey, KeyMapping modifierKey,
            Predicate<KeyMapping> isDown) {
        return paletteKey.matches(event) && isDown.test(modifierKey)
                || modifierKey.matches(event) && isDown.test(paletteKey);
    }

    private static boolean isBoundKeyDown(Minecraft client, KeyMapping keyMapping) {
        InputConstants.Key key = InputConstants.getKey(keyMapping.saveString());
        return switch (key.getType()) {
            case KEYSYM -> InputConstants.isKeyDown(client.getWindow(), key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(client.getWindow().handle(), key.getValue()) == GLFW.GLFW_PRESS;
            case SCANCODE -> keyMapping.isDown();
        };
    }

    public static void openCommandPalette(Minecraft client) {
        Screen currentScreen = client.screen;
        if (currentScreen instanceof CommandPaletteScreen palette) {
            palette.onClose();
            return;
        }
        client.setScreen(new CommandPaletteScreen(currentScreen));
    }

    public static void toggleGuides(Minecraft client) {
        ConfigStore store = ClientConfigStore.store();
        Config config = store.get();
        if (config.isReadOnly()) {
            if (client.player != null) {
                client.player.sendOverlayMessage(Component
                        .translatable("message.elytrapitchhelper.config.save_failed"));
            }
            return;
        }
        config.enabled = !config.enabled;
        if (ClientConfigStore.store() != store) {
            return;
        }
        store.saveAsync(config).thenAcceptAsync(result -> {
            if (!result.isCurrentAtDelivery()) {
                return;
            }
            if (result.result().outcome() != ClientConfigStore.SaveOutcome.COMMITTED) {
                if (client.player != null) {
                    client.player.sendOverlayMessage(Component
                            .translatable("message.elytrapitchhelper.config.save_failed"));
                }
                return;
            }
            if (client.player != null) {
                client.player.sendOverlayMessage(Component
                        .translatable("message.elytrapitchhelper.toggle."
                                + (config.enabled ? "on" : "off")));
            }
        }, client::execute);
    }

    private static void openConfig(Minecraft client) {
        Screen currentScreen = client.screen;
        if (!(currentScreen instanceof ConfigScreen)) {
            client.setScreen(new ConfigScreen(currentScreen));
        }
    }

    private static void openProfiles(Minecraft client) {
        Screen currentScreen = client.screen;
        if (!(currentScreen instanceof ConfigScreen)) {
            client.setScreen(ConfigScreen.profiles(currentScreen));
        }
    }

    private static int defaultPaletteModifierKey() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return osName.contains("mac") ? GLFW.GLFW_KEY_LEFT_SUPER : GLFW.GLFW_KEY_LEFT_CONTROL;
    }
}
