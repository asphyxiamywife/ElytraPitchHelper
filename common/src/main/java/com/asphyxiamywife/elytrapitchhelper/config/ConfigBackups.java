package com.asphyxiamywife.elytrapitchhelper.config;

import java.io.IOException;
import java.nio.file.Path;

final class ConfigBackups {
    private static final TimestampedBackupStore STORE = new TimestampedBackupStore(8);

    private ConfigBackups() {
    }


    static void saveMalformedMainOrMetadata(
            ConfigFileSystem fileSystem, Path source, String kind) throws IOException {
        STORE.saveRawOrThrow(fileSystem, directory(fileSystem, kind), source);
    }

    static Path directory(ConfigFileSystem fileSystem, String kind) {
        return Config.getConfigDirectory(fileSystem).resolve("backups").resolve(kind);
    }
}
