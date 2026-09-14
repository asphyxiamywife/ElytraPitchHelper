package com.asphyxiamywife.elytrapitchhelper.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class ConfigVersionGuard {
    private ConfigVersionGuard() {
    }


    static Result detectNewerFiles(ConfigFileSystem fileSystem, Path mainConfigPath,
            Path profileDirectory, Path profileMetadataPath) {
        List<NewerFile> newerFiles = new ArrayList<>();
        detect(fileSystem, mainConfigPath, newerFiles);
        detect(fileSystem, profileMetadataPath, newerFiles);
        if (fileSystem.isDirectory(profileDirectory)) {
            try {
                fileSystem.list(profileDirectory).stream()
                        .filter(path -> ConfigFiles.isJsonFile(fileSystem, path))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .forEach(path -> detect(fileSystem, path, newerFiles));
            } catch (IOException ignored) {
            }
        }
        return new Result(List.copyOf(newerFiles));
    }

    private static void detect(
            ConfigFileSystem fileSystem, Path path, List<NewerFile> newerFiles) {
        if (!fileSystem.isRegularFile(path, false)) {
            return;
        }
        JsonObject root = ConfigJsonFiles.read(fileSystem, path, JsonObject.class, failure -> null);
        BigDecimal version = version(root);
        if (version.compareTo(BigDecimal.valueOf(Config.CURRENT_VERSION)) > 0) {
            newerFiles.add(new NewerFile(path, version));
        }
    }

    private static BigDecimal version(JsonObject root) {
        if (root == null) {
            return BigDecimal.valueOf(Config.CURRENT_VERSION);
        }
        JsonElement version = root.get("version");
        if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()) {
            return BigDecimal.valueOf(Config.CURRENT_VERSION);
        }
        try {
            return version.getAsBigDecimal();
        } catch (ArithmeticException | NumberFormatException ignored) {
            return BigDecimal.valueOf(Config.CURRENT_VERSION);
        }
    }

    record Result(List<NewerFile> newerFiles) {
        boolean foundNewerVersion() {
            return !newerFiles.isEmpty();
        }

        String warning() {
            return newerFiles.stream()
                    .map(file -> file.path().getFileName() + " (version " + file.version() + ")")
                    .collect(java.util.stream.Collectors.joining(", "));
        }
    }

    record NewerFile(Path path, BigDecimal version) {
    }
}
