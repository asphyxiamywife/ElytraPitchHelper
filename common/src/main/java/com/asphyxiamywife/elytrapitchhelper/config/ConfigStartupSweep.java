package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.ModConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ConfigStartupSweep {
    static final long STRAY_TEMP_MIN_AGE_MILLIS = 5L * 60L * 1000L;
    private static final Logger LOGGER = LoggerFactory.getLogger(ModConstants.MOD_ID);
    private static final Pattern STAGED_PROFILE_NAME = Pattern.compile("^\\d+-(.+)$");
    private static final String[] LEGACY_DIRECTORY_PREFIXES = {
            "config-save-", "committed-config-save-", "pending-saves"
    };
    private static final String[] LEGACY_FILE_NAMES = {
            "transaction-ledger-a.json", "transaction-ledger-b.json", "active.lock", "recovery.lock"
    };

    private ConfigStartupSweep() {
    }


    static void run(ConfigFileSystem fileSystem) {
        run(fileSystem, System::currentTimeMillis);
    }

    static void run(ConfigFileSystem fileSystem, LongSupplier currentTimeMillis) {
        Path configDirectory = Config.getConfigDirectory(fileSystem);
        Path internalDirectory = configDirectory.resolve(".internal");
        long now = currentTimeMillis.getAsLong();
        deleteStrayTempFiles(fileSystem, configDirectory, now);
        deleteStrayTempFiles(fileSystem, Config.getProfileDirectory(fileSystem), now);
        deleteStrayTempFiles(fileSystem, internalDirectory, now);
        restoreIncompleteProfileDeletesUnderSaveLock(fileSystem, internalDirectory);
        deleteLegacyPersistenceArtifacts(fileSystem, internalDirectory);
    }

    private static void restoreIncompleteProfileDeletesUnderSaveLock(
            ConfigFileSystem fileSystem, Path internalDirectory) {
        try (ConfigSaveLock ignored = ConfigSaveLock.acquire(fileSystem)) {
            restoreIncompleteProfileDeletes(fileSystem, internalDirectory);
        } catch (ConfigSaveException busy) {
            if (busy.isRetryable()) {
                LOGGER.debug("Skipping interrupted profile-delete recovery while another save is active");
            } else {
                LOGGER.warn("Could not lock the config directory for profile-delete recovery", busy);
            }
        }
    }

    private static void deleteStrayTempFiles(ConfigFileSystem fileSystem, Path directory, long nowMillis) {
        if (!fileSystem.isDirectory(directory)) {
            return;
        }
        try {
            for (Path entry : fileSystem.list(directory)) {
                if (!entry.getFileName().toString().endsWith(".tmp")) {
                    continue;
                }
                if (!isStale(fileSystem, entry, nowMillis)) {
                    continue;
                }
                try {
                    fileSystem.delete(entry);
                } catch (IOException failure) {
                    LOGGER.warn("Could not remove stray temporary file {}", entry, failure);
                }
            }
        } catch (IOException failure) {
            LOGGER.warn("Could not inspect {} for stray temporary files", directory, failure);
        }
    }

    private static boolean isStale(ConfigFileSystem fileSystem, Path entry, long nowMillis) {
        try {
            return nowMillis - fileSystem.modifiedAtMillis(entry) >= STRAY_TEMP_MIN_AGE_MILLIS;
        } catch (IOException failure) {
            LOGGER.warn("Could not read the age of temporary file {}", entry, failure);
            return true;
        }
    }

    private static void restoreIncompleteProfileDeletes(ConfigFileSystem fileSystem, Path internalDirectory) {
        if (!fileSystem.isDirectory(internalDirectory)) {
            return;
        }
        try {
            for (Path stagingDirectory : fileSystem.list(internalDirectory)) {
                if (stagingDirectory.getFileName().toString().startsWith("profile-delete-")
                        && !fileSystem.isSymbolicLink(stagingDirectory)
                        && fileSystem.isDirectory(stagingDirectory)
                        && isContainedIn(stagingDirectory, internalDirectory)) {
                    restoreStagingDirectory(fileSystem, stagingDirectory);
                }
            }
        } catch (IOException failure) {
            LOGGER.warn("Could not inspect {} for interrupted profile deletions", internalDirectory, failure);
        }
    }

    private static void restoreStagingDirectory(ConfigFileSystem fileSystem, Path stagingDirectory) {
        Path profileDirectory = Config.getProfileDirectory(fileSystem);
        try {
            for (Path stagedFile : fileSystem.list(stagingDirectory)) {
                restoreStagedFile(fileSystem, stagedFile, profileDirectory);
            }
        } catch (IOException failure) {
            LOGGER.warn("Could not inspect interrupted profile deletion staging directory {}",
                    stagingDirectory, failure);
            return;
        }
        try {
            fileSystem.delete(stagingDirectory);
        } catch (IOException failure) {
            LOGGER.warn("Could not remove interrupted profile deletion staging directory {}",
                    stagingDirectory, failure);
        }
    }

    private static void restoreStagedFile(
            ConfigFileSystem fileSystem, Path stagedFile, Path profileDirectory) {
        Matcher matcher = STAGED_PROFILE_NAME.matcher(stagedFile.getFileName().toString());
        if (!matcher.matches()) {
            return;
        }
        if (!fileSystem.isRegularFile(stagedFile, true)
                && !fileSystem.isSymbolicLink(stagedFile)) {
            return;
        }
        String originalFileName = ProfileFileNames.normalize(matcher.group(1));
        if (originalFileName == null) {
            return;
        }
        Path normalizedProfileDirectory = profileDirectory.toAbsolutePath().normalize();
        Path original = normalizedProfileDirectory.resolve(originalFileName).normalize();
        if (!original.startsWith(normalizedProfileDirectory) || original.equals(normalizedProfileDirectory)) {
            return;
        }
        try {
            if (fileSystem.exists(original, false)) {
                fileSystem.delete(stagedFile);
            } else {
                fileSystem.createDirectories(profileDirectory);
                fileSystem.moveAtomic(stagedFile, original);
                LOGGER.warn("Restored profile {} that was left mid-deletion by an interrupted save", original);
            }
        } catch (IOException failure) {
            LOGGER.warn("Could not reconcile interrupted profile deletion for {}", original, failure);
        }
    }

    private static void deleteLegacyPersistenceArtifacts(ConfigFileSystem fileSystem, Path internalDirectory) {
        if (!fileSystem.isDirectory(internalDirectory)) {
            return;
        }
        try {
            for (Path entry : fileSystem.list(internalDirectory)) {
                String name = entry.getFileName().toString();
                if (fileSystem.isSymbolicLink(entry)) {
                    continue;
                }
                boolean legacyDirectory = fileSystem.isDirectory(entry)
                        && startsWithAny(name, LEGACY_DIRECTORY_PREFIXES);
                boolean legacyFile = fileSystem.isRegularFile(entry, true)
                        && containsAny(name, LEGACY_FILE_NAMES);
                if (legacyDirectory) {
                    deleteRecursively(fileSystem, entry, internalDirectory);
                } else if (legacyFile) {
                    fileSystem.delete(entry);
                }
            }
        } catch (IOException failure) {
            LOGGER.warn("Could not inspect {} for legacy persistence artifacts", internalDirectory, failure);
        }
    }

    private static boolean startsWithAny(String name, String[] prefixes) {
        for (String prefix : prefixes) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String name, String[] names) {
        for (String candidate : names) {
            if (name.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static void deleteRecursively(ConfigFileSystem fileSystem, Path path, Path root) {
        if (!isContainedIn(path, root)) {
            LOGGER.warn("Refusing to remove legacy persistence artifact {} outside {}", path, root);
            return;
        }
        try {
            if (fileSystem.isDirectory(path) && !fileSystem.isSymbolicLink(path)) {
                for (Path child : fileSystem.list(path)) {
                    deleteRecursively(fileSystem, child, root);
                }
            }
            fileSystem.delete(path);
        } catch (IOException failure) {
            LOGGER.warn("Could not remove legacy persistence artifact {}", path, failure);
        }
    }

    private static boolean isContainedIn(Path path, Path root) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedPath = path.toAbsolutePath().normalize();
        return normalizedPath.startsWith(normalizedRoot) && !normalizedPath.equals(normalizedRoot);
    }
}
