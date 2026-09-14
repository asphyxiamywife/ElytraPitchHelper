package com.asphyxiamywife.elytrapitchhelper.platform;

import java.nio.file.Path;
import java.util.List;

public final class PlatformServices {
    private static PlatformRuntime runtime = new DefaultPlatformRuntime();

    private PlatformServices() {
    }

    public static void setRuntime(PlatformRuntime runtime) {
        PlatformServices.runtime = runtime == null ? new DefaultPlatformRuntime() : runtime;
    }

    public static Path configDirectory() {
        return runtime.configDirectory();
    }

    public static List<Path> modRootPaths() {
        return runtime.modRootPaths();
    }

    public static String modVersion() {
        return runtime.modVersion();
    }

    private static final class DefaultPlatformRuntime implements PlatformRuntime {
        @Override
        public Path configDirectory() {
            return Path.of("config");
        }

        @Override
        public List<Path> modRootPaths() {
            return List.of();
        }
    }
}
