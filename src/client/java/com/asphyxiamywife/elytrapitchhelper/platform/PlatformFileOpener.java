package com.asphyxiamywife.elytrapitchhelper.platform;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

public final class PlatformFileOpener {
    private PlatformFileOpener() {
    }

    public static boolean open(Path path) {
        try {
            if (openWithPlatformCommand(path)) {
                return true;
            }
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(path.toFile());
                return true;
            }
        } catch (IOException | IllegalArgumentException | UnsupportedOperationException ignored) {
        }
        return false;
    }

    private static boolean openWithPlatformCommand(Path path) throws IOException {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("mac")) {
            new ProcessBuilder("/usr/bin/open", path.toAbsolutePath().toString()).start();
            return true;
        }
        if (osName.contains("win")) {
            new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", path.toAbsolutePath().toString()).start();
            return true;
        }
        if (osName.contains("linux") || osName.contains("unix")) {
            new ProcessBuilder("xdg-open", path.toAbsolutePath().toString()).start();
            return true;
        }
        return false;
    }
}
