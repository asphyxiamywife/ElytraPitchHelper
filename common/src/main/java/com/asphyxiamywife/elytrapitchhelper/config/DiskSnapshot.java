package com.asphyxiamywife.elytrapitchhelper.config;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;

record DiskSnapshot(boolean exists, long modifiedAtMillis, String fingerprint) {
    static final DiskSnapshot MISSING = new DiskSnapshot(false, 0L, null);


    static DiskSnapshot capture(ConfigFileSystem fileSystem, Path path) throws IOException {
        if (!fileSystem.exists(path, true)) {
            return MISSING;
        }
        if (!fileSystem.exists(path, false)) {
            throw new NoSuchFileException(path.toString(), null,
                    "path is a symbolic link that does not resolve");
        }
        return new DiskSnapshot(true, fileSystem.modifiedAtMillis(path),
                ConfigFiles.fileFingerprint(fileSystem, path));
    }

    boolean sameContent(DiskSnapshot other) {
        return exists == other.exists && Objects.equals(fingerprint, other.fingerprint);
    }
}
