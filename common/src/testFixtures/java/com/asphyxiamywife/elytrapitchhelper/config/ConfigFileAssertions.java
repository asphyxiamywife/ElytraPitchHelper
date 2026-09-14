package com.asphyxiamywife.elytrapitchhelper.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

public final class ConfigFileAssertions {
    private static boolean isJsonFile(java.nio.file.Path path) {
        return java.nio.file.Files.isRegularFile(path)
                && path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".json");
    }

    private ConfigFileAssertions() {
    }

    public static boolean backupContains(Path directory, byte[] expected) throws IOException {
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try (Stream<Path> paths = Files.list(directory)) {
            for (Path path : paths.filter(ConfigFileAssertions::isJsonFile).toList()) {
                if (Arrays.equals(expected, Files.readAllBytes(path))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static long jsonFileCount(Path directory) throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.filter(ConfigFileAssertions::isJsonFile).count();
        }
    }

    public static Map<String, byte[]> directoryContents(Path directory) throws IOException {
        Map<String, byte[]> contents = new LinkedHashMap<>();
        if (!Files.isDirectory(directory)) {
            return contents;
        }
        try (Stream<Path> paths = Files.list(directory)) {
            for (Path path : paths.sorted().toList()) {
                contents.put(path.getFileName().toString(), Files.readAllBytes(path));
            }
        }
        return contents;
    }
}
