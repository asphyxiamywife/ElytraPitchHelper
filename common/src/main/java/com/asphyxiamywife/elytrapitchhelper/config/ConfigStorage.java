package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.ModConstants;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class ConfigStorage {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModConstants.MOD_ID);

    private ConfigStorage() {
    }

    static LoadResult load(ConfigFileSystem fileSystem, boolean startup) {
        Path path = Config.getConfigPath(fileSystem);
        try {
            return loadChecked(fileSystem, startup, path);
        } catch (FileAccessException failure) {
            String warning = "Config file could not be read: " + failure.path;
            LOGGER.error("{}. Running with read-only defaults", warning, failure);
            return loadResult(readOnlyDefaults(fileSystem, path, warning));
        } catch (ProfilePersistence.ProfileDirectoryAccessException failure) {
            String warning = "Profile directory could not be read: " + failure.directory()
                    + ". Fix filesystem access, then reopen config.";
            LOGGER.error("{}. Running with read-only defaults and leaving config files untouched", warning,
                    failure);
            return loadResult(readOnlyDefaults(fileSystem, path, warning));
        } catch (ProfileMetadataStore.MetadataBackupException failure) {
            String warning = "Malformed profile metadata could not be backed up: " + failure.path()
                    + ". Fix filesystem access, then reopen config.";
            LOGGER.error("{}. Running with read-only defaults and leaving config files untouched", warning,
                    failure);
            return loadResult(readOnlyDefaults(fileSystem, path, warning));
        }
    }

    static java.util.Optional<LoadResult> tryReload(ConfigFileSystem fileSystem) {
        try {
            return java.util.Optional.of(loadChecked(fileSystem, false, Config.getConfigPath(fileSystem)));
        } catch (ProfilePersistence.ProfileDirectoryAccessException
                | ProfileMetadataStore.MetadataBackupException | FileAccessException failure) {
            LOGGER.warn("Could not reload configuration; keeping the live configuration and pending edits", failure);
            return java.util.Optional.empty();
        }
    }

    private static LoadResult loadChecked(ConfigFileSystem fileSystem, boolean startup, Path path) {
        String nameWarning = ProfilePersistence.incompatibleFileNameWarning(fileSystem);
        if (nameWarning != null) {
            return loadResult(readOnlyDefaults(fileSystem, path, nameWarning));
        }
        if (startup) {
            ConfigSaveBatch.recoverIncomplete(fileSystem);
            ConfigStartupSweep.run(fileSystem);
        }
        ConfigVersionGuard.Result versionGuard = ConfigVersionGuard.detectNewerFiles(fileSystem, path,
                Config.getProfileDirectory(fileSystem), Config.getProfileMetadataPath(fileSystem));
        if (versionGuard.foundNewerVersion()) {
            return loadResult(newerVersionDefaults(fileSystem, path, versionGuard.warning()));
        }
        DiskSnapshot mainConfigDisk;
        try {
            mainConfigDisk = diskSnapshot(fileSystem, path);
        } catch (IOException failure) {
            if (!startup) {
                throw new FileAccessException(path, failure);
            }
            LOGGER.warn("Failed to inspect {}, treating it as missing for this load", path, failure);
            mainConfigDisk = DiskSnapshot.MISSING;
        }
        String profileCollisionWarning = ProfilePersistence.caseInsensitiveCollisionWarning(fileSystem);
        if (profileCollisionWarning != null) {
            return loadResult(profileCollisionDefaults(fileSystem, path, profileCollisionWarning));
        }
        Config cfg = readMainConfig(fileSystem, path, startup);
        boolean currentConfigExists = fileSystem.exists(path, false);
        boolean migratedLegacyConfig = false;

        if (cfg == null && !currentConfigExists && !ProfilePersistence.hasProfileFiles(fileSystem)) {
            cfg = LegacyConfigMigration.load(fileSystem, ConfigPaths.legacyConfigPath(fileSystem), path);
            migratedLegacyConfig = cfg != null;
        }

        if (cfg == null) {
            cfg = new Config();
        }
        cfg.markMainConfigFileLoaded(mainConfigDisk.exists(), mainConfigDisk.modifiedAtMillis(),
                mainConfigDisk.fingerprint());

        ProfileMetadataStore.Loaded loadedProfileMetadata = ProfileMetadataStore.load(fileSystem, startup);
        cfg.profileMetadata = loadedProfileMetadata.metadata();
        cfg.storeMetadata = cfg.storeMetadata.withProfiles(loadedProfileMetadata.profileFiles());
        if (migratedLegacyConfig) {
            ConfigProfileManager.ensureProfiles(cfg);
            ProfilePersistence.markMigratedProfileMetadata(
                    fileSystem, cfg.profileMetadata, cfg.profiles, ConfigPaths.legacyConfigPath(fileSystem));
        } else {
            ConfigProfileManager.loadProfiles(fileSystem, cfg, startup);
        }
        RepairLog repairs = new RepairLog("main config " + path);
        ConfigSanitizer.clampValues(cfg, repairs);
        repairs.log();
        if (startup && !cfg.isReadOnly()) {
            try {
                cfg.save(fileSystem);
            } catch (ConfigSaveException failure) {
                String warning = "Config could not be saved during startup. Fix filesystem access or free disk "
                        + "space, then reopen config.";
                cfg.enterReadOnlyMode(warning);
                LOGGER.error("{}. Continuing with the loaded configuration in read-only mode", warning, failure);
            }
        }
        return loadResult(cfg);
    }

    private static Config newerVersionDefaults(
            ConfigFileSystem fileSystem, Path path, String warning) {
        LOGGER.error("Found config data from a newer Elytra Pitch Helper version. Running with read-only "
                + "defaults and leaving disk files untouched: {}", warning);
        return readOnlyDefaults(fileSystem, path, warning);
    }

    private static Config profileCollisionDefaults(
            ConfigFileSystem fileSystem, Path path, String warning) {
        LOGGER.error("{}. Running with read-only defaults and leaving all profile files untouched", warning);
        return readOnlyDefaults(fileSystem, path, warning);
    }

    private static Config readOnlyDefaults(
            ConfigFileSystem fileSystem, Path path, String warning) {
        DiskSnapshot disk = diskSnapshotOrMissing(fileSystem, path);
        Config defaults = new Config();
        defaults.enterReadOnlyMode(warning);
        defaults.markMainConfigFileLoaded(disk.exists(), disk.modifiedAtMillis(), disk.fingerprint());
        ConfigProfileManager.ensureProfiles(defaults);
        return defaults;
    }

    private static LoadResult loadResult(Config config) {
        ConfigState state = config.state();
        return config.isReadOnly()
                ? new LoadResult.ReadOnly(state, config.readOnlyWarning())
                : new LoadResult.Loaded(state);
    }

    static void save(ConfigFileSystem fileSystem, Config cfg) {
        save(fileSystem, cfg, Set.of());
    }

    static void save(
            ConfigFileSystem fileSystem, Config cfg, Set<Path> approvedConflictPaths) {
        save(fileSystem, cfg, approvedConflictPaths, List.of());
    }

    static void save(ConfigFileSystem fileSystem, Config cfg,
            Set<Path> approvedConflictPaths, List<String> deletedProfileFiles) {
        if (cfg.isReadOnly()) {
            throw new NewerConfigVersionException("Refusing to save read-only config: " + cfg.readOnlyWarning());
        }
        ConfigProfileManager.ensureProfiles(cfg);
        boolean saveProfiles = !cfg.skipNextProfileSave;

        Path path = Config.getConfigPath(fileSystem);
        ProfileMetadataStore metadataBeforeSave = cfg.profileMetadata.copy();
        StoreMetadata storeMetadataBeforeSave = cfg.storeMetadata;
        cfg.writtenMain = null;
        cfg.writtenProfiles = new java.util.LinkedHashMap<>();
        try (ConfigSaveLock ignored = ConfigSaveLock.acquire(fileSystem)) {
            String nameWarning = ProfilePersistence.incompatibleFileNameWarning(fileSystem);
            if (nameWarning != null) {
                throw new ConfigSaveException(nameWarning);
            }
            for (Profile profile : cfg.profiles) {
                if (!ProfileFileNames.isPortable(profile.fileName())) {
                    throw new ConfigSaveException("Incompatible profile filename: " + profile.fileName());
                }
            }
            List<Path> batchTargets = new ArrayList<>();
            batchTargets.add(path);
            batchTargets.add(Config.getProfileMetadataPath(fileSystem));
            if (saveProfiles) {
                for (Profile profile : cfg.profiles) {
                    batchTargets.add(ProfilePersistence.profilePath(fileSystem, profile));
                }
            }
            batchTargets.addAll(ProfilePersistence.profilePaths(fileSystem, deletedProfileFiles));

            ConfigSaveBatch batch = ConfigSaveBatch.begin(fileSystem, batchTargets);
            try {
                ProfilePersistence.deleteProfileFilesChecked(
                        fileSystem, deletedProfileFiles, cfg, approvedConflictPaths);

                if (saveProfiles) {
                    ConfigProfileManager.saveProfiles(fileSystem, cfg, approvedConflictPaths);
                }
                try {
                    saveMainConfig(fileSystem, cfg, path, approvedConflictPaths);
                } catch (IOException e) {
                    LOGGER.error("Failed to save {}", path, e);
                    throw new ConfigSaveException("Failed to save " + path, e);
                }
                ConfigProfileManager.saveProfileMetadata(fileSystem, cfg);
                batch.commit();
                cfg.skipNextProfileSave = false;
            } catch (ConfigConflictException conflict) {
                batch.rollback(conflict, conflict.conflictPaths());
                throw conflict;
            } catch (RuntimeException failure) {
                batch.rollback(failure);
                throw failure;
            }
        } catch (RuntimeException failure) {
            cfg.profileMetadata = metadataBeforeSave;
            cfg.storeMetadata = storeMetadataBeforeSave;
            cfg.writtenMain = null;
            cfg.writtenProfiles = new java.util.LinkedHashMap<>();
            throw failure;
        }
    }

    private static Config readMainConfig(ConfigFileSystem fileSystem, Path path, boolean repairDisk) {
        if (!fileSystem.exists(path, false)) {
            return null;
        }

        return ConfigJsonFiles.read(fileSystem, path, Config.class, failure -> {
            if (!repairDisk) {
                if (failure instanceof IOException) {
                    throw new FileAccessException(path, failure);
                }
                LOGGER.warn("Failed to reload {}; leaving disk untouched", path, failure);
                return null;
            }
            try {
                ConfigBackups.saveMalformedMainOrMetadata(fileSystem, path, "main");
                LOGGER.warn("Failed to load {}; preserved the original bytes before using defaults and available "
                        + "profile json files", path, failure);
            } catch (IOException backupFailure) {
                failure.addSuppressed(backupFailure);
                LOGGER.error("Failed to load {} and could not preserve its raw bytes; entering read-only mode",
                        path, failure);
                Config readOnlyDefaults = new Config();
                readOnlyDefaults.enterReadOnlyMode("Malformed main config could not be backed up: " + path);
                return readOnlyDefaults;
            }
            return null;
        });
    }

    private static void saveMainConfig(ConfigFileSystem fileSystem, Config cfg, Path path,
            Set<Path> approvedConflictPaths)
            throws IOException {
        ObservedMainConfig observed = observeMainConfig(fileSystem, path, cfg);
        if (!observed.needsWrite()) {
            if (observed.disk().exists()) {
                syncLoadedMainConfigFile(cfg, observed.disk());
            }
            return;
        }
        if (mainConfigChangedExternallySinceLoad(cfg, observed.disk())
                && !approvedConflictPaths.contains(path)) {
            LOGGER.warn("Refused to overwrite externally changed main config: {}", path);
            throw new ConfigConflictException(path);
        }

        syncLoadedMainConfigFile(cfg, ConfigFiles.writeJsonAtomicWithSnapshot(fileSystem, path, cfg));
        cfg.writtenMain = cfg.mainConfigFileState();
    }

    private static ObservedMainConfig observeMainConfig(
            ConfigFileSystem fileSystem, Path path, Config cfg) throws IOException {
        if (!fileSystem.exists(path, false)) {
            return new ObservedMainConfig(DiskSnapshot.MISSING, true);
        }
        long modifiedAtMillis = fileSystem.modifiedAtMillis(path);
        byte[] contents = fileSystem.read(path);
        DiskSnapshot disk = new DiskSnapshot(
                true, modifiedAtMillis, ConfigFiles.sha256Hex(contents));
        try {
            JsonObject existing = ConfigJsonFiles.parse(contents, JsonObject.class);
            if (existing == null) {
                return new ObservedMainConfig(disk, true);
            }
            JsonObject next = ConfigFiles.GSON.toJsonTree(cfg).getAsJsonObject();
            return new ObservedMainConfig(disk, !ConfigFiles.jsonValuesEqual(next, existing));
        } catch (RuntimeException failure) {
            return new ObservedMainConfig(disk, true);
        }
    }

    private static boolean mainConfigChangedExternallySinceLoad(Config cfg, DiskSnapshot disk) {
        return cfg.mainConfigFileState().changedSince(disk);
    }

    private static void syncLoadedMainConfigFile(Config cfg, DiskSnapshot disk) {
        cfg.restoreMainConfigFileState(cfg.mainConfigFileState().syncFrom(disk));
    }

    private static DiskSnapshot diskSnapshotOrMissing(ConfigFileSystem fileSystem, Path path) {
        try {
            return diskSnapshot(fileSystem, path);
        } catch (IOException e) {
            LOGGER.warn("Failed to inspect {}, treating it as missing for this load", path, e);
            return DiskSnapshot.MISSING;
        }
    }

    private static DiskSnapshot diskSnapshot(ConfigFileSystem fileSystem, Path path) throws IOException {
        return DiskSnapshot.capture(fileSystem, path);
    }

    static final class FileAccessException extends RuntimeException {
        private final Path path;

        FileAccessException(Path path, Exception cause) {
            super("Could not read config file " + path, cause);
            this.path = path;
        }
    }

    private record ObservedMainConfig(DiskSnapshot disk, boolean needsWrite) {
    }

}
