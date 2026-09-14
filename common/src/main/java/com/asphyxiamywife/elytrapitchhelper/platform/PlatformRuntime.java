package com.asphyxiamywife.elytrapitchhelper.platform;

import java.nio.file.Path;
import java.util.List;

public interface PlatformRuntime {
    Path configDirectory();

    List<Path> modRootPaths();

    default String modVersion() {
        return "unknown";
    }
}
