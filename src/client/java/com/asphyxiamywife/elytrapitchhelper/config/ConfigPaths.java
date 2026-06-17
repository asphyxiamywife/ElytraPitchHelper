package com.asphyxiamywife.elytrapitchhelper.config;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

final class ConfigPaths {
    private static final String FILE_NAME = "elytra-pitch-helper.json";
    private static final String CONFIG_DIRECTORY_NAME = "elytra-pitch-helper";
    private static final String PROFILE_DIRECTORY_NAME = "profiles";
    private static final String INTERNAL_DIRECTORY_NAME = ".internal";
    private static final String PROFILE_METADATA_FILE_NAME = "profile-metadata.json";

    private static Path configRootOverride;

    private ConfigPaths() {
    }

    static Path configPath() {
        return configDirectory().resolve(FILE_NAME);
    }

    static Path legacyConfigPath() {
        return loaderConfigDirectory().resolve(FILE_NAME);
    }

    static Path configDirectory() {
        return loaderConfigDirectory().resolve(CONFIG_DIRECTORY_NAME);
    }

    static Path profileDirectory() {
        return configDirectory().resolve(PROFILE_DIRECTORY_NAME);
    }

    static Path profileMetadataPath() {
        return configDirectory().resolve(INTERNAL_DIRECTORY_NAME).resolve(PROFILE_METADATA_FILE_NAME);
    }

    static void setConfigRootOverrideForTests(Path configRoot) {
        configRootOverride = configRoot;
    }

    private static Path loaderConfigDirectory() {
        return configRootOverride == null ? FabricLoader.getInstance().getConfigDir() : configRootOverride;
    }
}
