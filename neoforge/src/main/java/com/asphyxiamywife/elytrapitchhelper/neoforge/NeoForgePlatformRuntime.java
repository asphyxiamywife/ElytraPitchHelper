package com.asphyxiamywife.elytrapitchhelper.neoforge;

import com.asphyxiamywife.elytrapitchhelper.ModConstants;
import com.asphyxiamywife.elytrapitchhelper.platform.PlatformRuntime;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.List;

public final class NeoForgePlatformRuntime implements PlatformRuntime {
    @Override
    public Path configDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public List<Path> modRootPaths() {
        var modFile = ModList.get().getModFileById(ModConstants.MOD_ID);
        return modFile == null ? List.of() : List.copyOf(modFile.getFile().getContents().getContentRoots());
    }

    @Override
    public String modVersion() {
        return ModList.get().getModContainerById(ModConstants.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }
}
