package com.asphyxiamywife.elytrapitchhelper.client;

import com.asphyxiamywife.elytrapitchhelper.ModConstants;
import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.flight.ElytraDetector;
import com.asphyxiamywife.elytrapitchhelper.hud.PitchGuideHud;
import com.asphyxiamywife.elytrapitchhelper.platform.PlatformRuntime;
import com.asphyxiamywife.elytrapitchhelper.platform.PlatformServices;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public final class ClientBootstrap {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModConstants.MOD_ID);
    private static volatile ClientBootstrap active;

    private final PitchGuideHud hud = new PitchGuideHud(new ElytraDetector());
    private KeyBindings keyBindings;
    private boolean missingKeyBindingsLogged;

    private ClientBootstrap() {
    }

    public static ClientBootstrap start(PlatformRuntime runtime) {
        PlatformServices.setRuntime(runtime);
        ClientConfigStore.initialize();
        ConfigWatcher.start();
        ClientBootstrap bootstrap = new ClientBootstrap();
        active = bootstrap;
        return bootstrap;
    }

    public void onClientTick(Minecraft client) {
        if (keyBindings != null) {
            keyBindings.handleClientTick(client);
        } else if (!missingKeyBindingsLogged) {
            missingKeyBindingsLogged = true;
            LOGGER.warn("Client tick started before Elytra Pitch Helper key bindings were registered");
        }
        hud.tick(client);
    }

    public void onClientStopping() {
        if (active == this) {
            active = null;
        }
        ConfigWatcher.stop();
        if (!ClientConfigStore.shutdownPersistenceCoordinator()) {
            LOGGER.error("Could not save or preserve pending Elytra Pitch Helper changes during shutdown");
        }
    }

    public PitchGuideHud hud() {
        return hud;
    }

    public static void onClientBlockChanged(BlockPos pos) {
        ClientBootstrap bootstrap = active;
        if (bootstrap != null) {
            bootstrap.hud.invalidateGroundColumn(pos.getX(), pos.getZ());
        }
    }

    public static void onClientChunkChanged(ChunkPos pos) {
        ClientBootstrap bootstrap = active;
        if (bootstrap != null) {
            bootstrap.hud.invalidateGroundChunk(pos.x, pos.z);
        }
    }

    public void registerKeys(Consumer<KeyMapping> registrar) {
        keyBindings = KeyBindings.register(keyMapping -> {
            registrar.accept(keyMapping);
            return keyMapping;
        });
    }

    public static Config config() {
        return ClientConfigStore.get();
    }

    public static void reload() {
        ClientConfigStore.reloadFromDisk();
    }

    public static Identifier id(String path) {
        return ModConstants.id(path);
    }
}
