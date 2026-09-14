package com.asphyxiamywife.elytrapitchhelper.config;

import java.nio.file.Path;

final class ConfigPaths {
    private static final String FILE_NAME = "elytra-pitch-helper.json";
    private static final String CONFIG_DIRECTORY_NAME = "elytra-pitch-helper";
    private static final String PROFILE_DIRECTORY_NAME = "profiles";
    private static final String INTERNAL_DIRECTORY_NAME = ".internal";
    private static final String PROFILE_METADATA_FILE_NAME = "profile-metadata.json";

    private ConfigPaths() {
    }

    static Path configPath(ConfigFileSystem fileSystem) {
        return configDirectory(fileSystem).resolve(FILE_NAME);
    }

    static Path legacyConfigPath(ConfigFileSystem fileSystem) {
        return fileSystem.configRoot().resolve(FILE_NAME);
    }

    static Path configDirectory(ConfigFileSystem fileSystem) {
        return fileSystem.configRoot().resolve(CONFIG_DIRECTORY_NAME);
    }

    static Path profileDirectory(ConfigFileSystem fileSystem) {
        return configDirectory(fileSystem).resolve(PROFILE_DIRECTORY_NAME);
    }

    static Path internalDirectory(ConfigFileSystem fileSystem) {
        return configDirectory(fileSystem).resolve(INTERNAL_DIRECTORY_NAME);
    }

    static Path profileMetadataPath(ConfigFileSystem fileSystem) {
        return internalDirectory(fileSystem).resolve(PROFILE_METADATA_FILE_NAME);
    }
}
