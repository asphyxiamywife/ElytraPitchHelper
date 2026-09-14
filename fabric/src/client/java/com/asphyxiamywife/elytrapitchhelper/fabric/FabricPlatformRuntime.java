package com.asphyxiamywife.elytrapitchhelper.fabric;

import com.asphyxiamywife.elytrapitchhelper.ModConstants;
import com.asphyxiamywife.elytrapitchhelper.platform.PlatformRuntime;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.List;

public final class FabricPlatformRuntime implements PlatformRuntime {
    @Override
    public Path configDirectory() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public List<Path> modRootPaths() {
        return FabricLoader.getInstance()
                .getModContainer(ModConstants.MOD_ID)
                .map(container -> List.copyOf(container.getRootPaths()))
                .orElse(List.of());
    }

    @Override
    public String modVersion() {
        return FabricLoader.getInstance().getModContainer(ModConstants.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
