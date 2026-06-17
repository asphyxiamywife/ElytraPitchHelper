package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.ModConstants;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class ConfigStorage {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModConstants.MOD_ID);

    private ConfigStorage() {
    }

    static Config load(boolean saveAfterLoad) {
        Path path = Config.getConfigPath();
        Config cfg = readMainConfig(path);
        boolean currentConfigExists = Files.exists(path);
        boolean migratedLegacyConfig = false;

        if (cfg == null && !currentConfigExists && !ProfilePersistence.hasProfileFiles()) {
            cfg = LegacyConfigMigration.load(ConfigPaths.legacyConfigPath(), path);
            migratedLegacyConfig = cfg != null;
        }

        if (cfg == null) {
            cfg = new Config();
        }

        cfg.profileMetadata = ProfileMetadataStore.load();
        if (migratedLegacyConfig) {
            ConfigProfileManager.ensureProfiles(cfg);
            ProfilePersistence.markMigratedProfileMetadata(cfg.profileMetadata, cfg.profiles,
                    ConfigPaths.legacyConfigPath());
        } else {
            ConfigProfileManager.loadProfiles(cfg, saveAfterLoad);
        }
        ConfigProfileManager.bindActiveProfile(cfg);

        RepairLog repairs = new RepairLog("main config " + path);
        ConfigSanitizer.clampValues(cfg, repairs);
        repairs.log();
        if (saveAfterLoad) {
            cfg.save();
        }
        return cfg;
    }

    static void save(Config cfg) {
        ConfigProfileManager.ensureProfiles(cfg);
        ConfigProfileManager.bindActiveProfileIfNeeded(cfg);
        ConfigSanitizer.clampValues(cfg);
        boolean saveProfiles = !cfg.skipNextProfileSave;
        cfg.skipNextProfileSave = false;
        if (saveProfiles) {
            ConfigProfileManager.saveProfiles(cfg);
        }

        Path path = Config.getConfigPath();
        try {
            ConfigFiles.writeJsonAtomic(path, cfg);
        } catch (IOException e) {
            LOGGER.error("Failed to save {}", path, e);
            throw new ConfigSaveException("Failed to save " + path, e);
        }
        ConfigProfileManager.saveProfileMetadata(cfg);
    }

    private static Config readMainConfig(Path path) {
        if (!Files.exists(path)) {
            return null;
        }

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return ConfigFiles.GSON.fromJson(reader, Config.class);
        } catch (IOException | JsonParseException | IllegalStateException e) {
            LOGGER.warn("Failed to load {}, using defaults and available profile json files", path, e);
            return null;
        }
    }
}
