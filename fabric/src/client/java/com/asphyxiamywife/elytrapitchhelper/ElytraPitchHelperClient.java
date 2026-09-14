package com.asphyxiamywife.elytrapitchhelper;

import com.asphyxiamywife.elytrapitchhelper.client.ClientBootstrap;
import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.fabric.FabricPlatformRuntime;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

public final class ElytraPitchHelperClient implements ClientModInitializer {

    private ClientBootstrap bootstrap;

    @Override
    public void onInitializeClient() {
        bootstrap = ClientBootstrap.start(new FabricPlatformRuntime());
        registerHud();
        bootstrap.registerKeys(KeyBindingHelper::registerKeyBinding);
        ClientTickEvents.END_CLIENT_TICK.register(bootstrap::onClientTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> bootstrap.onClientStopping());
    }

    public static void reloadConfig() {
        ClientBootstrap.reload();
    }

    public static Config getConfig() {
        return ClientBootstrap.config();
    }

    public static Identifier id(String path) {
        return ClientBootstrap.id(path);
    }

    private void registerHud() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, id("pitch_guides"),
                bootstrap.hud()::render);
    }
}
