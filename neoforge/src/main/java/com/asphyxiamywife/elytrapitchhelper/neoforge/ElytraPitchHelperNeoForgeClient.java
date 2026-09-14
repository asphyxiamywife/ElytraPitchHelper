package com.asphyxiamywife.elytrapitchhelper.neoforge;

import com.asphyxiamywife.elytrapitchhelper.ModConstants;
import com.asphyxiamywife.elytrapitchhelper.client.ClientBootstrap;
import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.screen.ConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = ModConstants.MOD_ID, dist = Dist.CLIENT)
public final class ElytraPitchHelperNeoForgeClient {

    private final ClientBootstrap bootstrap;

    public ElytraPitchHelperNeoForgeClient(IEventBus modBus, ModContainer modContainer) {
        bootstrap = ClientBootstrap.start(new NeoForgePlatformRuntime());
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, (container, modListScreen) -> new ConfigScreen(modListScreen));
        modBus.addListener(this::registerHud);
        modBus.addListener(this::registerKeys);
        NeoForge.EVENT_BUS.addListener(this::handleClientTick);
        NeoForge.EVENT_BUS.addListener(this::handleClientStopping);
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

    private void registerHud(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, id("pitch_guides"), bootstrap.hud()::render);
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        bootstrap.registerKeys(event::register);
    }

    private void handleClientTick(ClientTickEvent.Post event) {
        bootstrap.onClientTick(Minecraft.getInstance());
    }

    private void handleClientStopping(ClientStoppingEvent event) {
        bootstrap.onClientStopping();
    }
}
