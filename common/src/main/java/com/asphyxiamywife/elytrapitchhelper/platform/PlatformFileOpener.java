package com.asphyxiamywife.elytrapitchhelper.platform;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class PlatformFileOpener {
    private PlatformFileOpener() {
    }

    public static CompletableFuture<Boolean> openAsync(Path path) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return open(path);
            } catch (RuntimeException failure) {
                return false;
            }
        });
    }

    private static boolean open(Path path) {
        try {
            if (openWithPlatformCommand(path)) {
                return true;
            }
        } catch (IOException | IllegalArgumentException | UnsupportedOperationException ignored) {
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(path.toFile());
                return true;
            }
        } catch (IOException | IllegalArgumentException | UnsupportedOperationException ignored) {
        }
        return false;
    }

    private static boolean openWithPlatformCommand(Path path) throws IOException, InterruptedException {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        ProcessBuilder command;
        if (osName.contains("mac")) {
            command = new ProcessBuilder("/usr/bin/open", path.toAbsolutePath().toString());
        } else if (osName.contains("win")) {
            command = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler",
                    path.toAbsolutePath().toString());
        } else if (osName.contains("linux") || osName.contains("unix")) {
            command = new ProcessBuilder("xdg-open", path.toAbsolutePath().toString());
        } else {
            return false;
        }
        Process process = command.redirectInput(ProcessBuilder.Redirect.PIPE)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        try {
            process.getOutputStream().close();
        } catch (IOException failure) {
            process.destroy();
            throw failure;
        }
        if (!process.waitFor(3L, TimeUnit.SECONDS)) {
            process.destroy();
            return false;
        }
        return process.exitValue() == 0;
    }
}
