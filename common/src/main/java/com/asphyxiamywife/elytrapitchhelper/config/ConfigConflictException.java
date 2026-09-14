package com.asphyxiamywife.elytrapitchhelper.config;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class ConfigConflictException extends ConfigSaveException {
    private final List<Path> conflictPaths;

    ConfigConflictException(Path conflictPath) {
        this(List.of(conflictPath));
    }

    ConfigConflictException(List<Path> conflictPaths) {
        this(conflictPaths, "Config files changed externally while they were being edited");
    }

    ConfigConflictException(List<Path> conflictPaths, String message) {
        super(message + ": "
                + conflictPaths.stream().map(Path::toString).collect(Collectors.joining(", ")));
        if (conflictPaths.isEmpty()) {
            throw new IllegalArgumentException("At least one conflicting config path is required");
        }
        this.conflictPaths = List.copyOf(conflictPaths);
    }

    public Path conflictPath() {
        return conflictPaths.get(0);
    }

    public List<Path> conflictPaths() {
        return conflictPaths;
    }
}
