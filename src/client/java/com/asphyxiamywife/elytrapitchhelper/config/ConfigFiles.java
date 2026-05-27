package com.asphyxiamywife.elytrapitchhelper.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

final class ConfigFiles {
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private ConfigFiles() {
    }

    static boolean isJsonFile(Path path) {
        return Files.isRegularFile(path)
                && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(ProfileFileNames.JSON_SUFFIX);
    }

    static void writeJsonAtomic(Path path, Object value) throws IOException {
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            GSON.toJson(value, writer);
        }
        moveAtomic(tmp, path);
    }

    static void writeBytesAtomic(Path path, byte[] value) throws IOException {
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.write(tmp, value);
        moveAtomic(tmp, path);
    }

    private static void moveAtomic(Path tmp, Path path) throws IOException {
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
