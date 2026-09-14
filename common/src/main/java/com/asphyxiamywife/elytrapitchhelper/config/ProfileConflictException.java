package com.asphyxiamywife.elytrapitchhelper.config;

import java.nio.file.Path;
import java.util.List;

public final class ProfileConflictException extends ConfigConflictException {
    ProfileConflictException(Path profilePath) {
        this(List.of(profilePath));
    }

    ProfileConflictException(List<Path> profilePaths) {
        super(profilePaths, "Profiles changed externally while they were being edited");
    }

    public Path profilePath() {
        return conflictPath();
    }

    public List<Path> profilePaths() {
        return conflictPaths();
    }
}
