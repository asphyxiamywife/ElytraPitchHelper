package com.asphyxiamywife.elytrapitchhelper;

import com.asphyxiamywife.elytrapitchhelper.client.ClientConfigStore;
import com.asphyxiamywife.elytrapitchhelper.client.ConfigWatcher;
import com.asphyxiamywife.elytrapitchhelper.client.KeyBindings;
import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.flight.ElytraDetector;
import com.asphyxiamywife.elytrapitchhelper.hud.PitchGuideHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

public final class ElytraPitchHelperClient implements ClientModInitializer {
    public static final String MOD_ID = ModConstants.MOD_ID;

    private final PitchGuideHud pitchGuideHud = new PitchGuideHud(new ElytraDetector());

    @Override
    public void onInitializeClient() {
        ClientConfigStore.initialize();
        registerHud();
        registerKeys();
        ConfigWatcher.start();
    }

    public static void reloadConfig() {
        ClientConfigStore.reloadFromDisk();
    }

    public static Config getConfig() {
        return ClientConfigStore.get();
    }

    public static Identifier id(String path) {
        return ModConstants.id(path);
    }

    private void registerHud() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, id("pitch_guides"), pitchGuideHud::render);
    }

    private static void registerKeys() {
        KeyBindings keyBindings = KeyBindings.register();
        ClientTickEvents.END_CLIENT_TICK.register(keyBindings::handleClientTick);
    }
}
