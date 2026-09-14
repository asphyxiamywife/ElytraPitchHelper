package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.ModConstants;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ProfileBackups {
    private static final String BACKUP_DIRECTORY_NAME = "backups";
    private static final String PROFILE_DIRECTORY_NAME = "profiles";
    private static final TimestampedBackupStore STORE = new TimestampedBackupStore(8);
    private static final Comparator<Path> BACKUP_PATH_ORDER = TimestampedBackupStore.ORDER;
    private static final Logger LOGGER = LoggerFactory.getLogger(ModConstants.MOD_ID);

    private ProfileBackups() {
    }

    static synchronized Profile latestValid(
            ConfigFileSystem fileSystem, Path profilePath, String legacyFileName) {
        return latestValid(fileSystem, profilePath, legacyFileName, true);
    }

    static synchronized Profile latestValid(
            ConfigFileSystem fileSystem, Path profilePath, String legacyFileName, boolean repairDisk) {
        for (Path backup : backupFiles(
                fileSystem, profilePath.getFileName().toString(), legacyFileName, repairDisk)) {
            try {
                return ProfilePersistence.readProfileFile(fileSystem, backup,
                        profilePath.getFileName().toString(), "profile backup " + backup,
                        ProfileDefaults.template());
            } catch (IOException | JsonParseException | IllegalStateException e) {
                LOGGER.warn("Skipped invalid profile backup {}", backup, e);
            }
        }
        return null;
    }

    static synchronized Profile restore(ConfigFileSystem fileSystem, Path profilePath, String legacyFileName) {
        for (Path backup : backupFiles(
                fileSystem, profilePath.getFileName().toString(), legacyFileName, true)) {
            try {
                Profile profile = ProfilePersistence.readProfileFile(fileSystem, backup,
                        profilePath.getFileName().toString(), "profile backup " + backup,
                        ProfileDefaults.template());
                if (!preserveExistingProfileBeforeRestore(fileSystem, profilePath)) {
                    return null;
                }
                ConfigFiles.writeJsonAtomic(fileSystem, profilePath, ProfileJson.write(profile));
                validateAndBackupWrittenProfile(fileSystem, profilePath, profile.fileName());
                LOGGER.warn("Restored profile {} from backup {}", profilePath, backup);
                return profile;
            } catch (IOException | JsonParseException | IllegalStateException e) {
                LOGGER.warn("Skipped invalid profile backup {}", backup, e);
            }
        }
        return null;
    }

    private static boolean preserveExistingProfileBeforeRestore(
            ConfigFileSystem fileSystem, Path profilePath) {
        if (!fileSystem.exists(profilePath, false)) {
            return true;
        }
        try {
            saveRawOrThrow(fileSystem, profilePath);
            return true;
        } catch (IOException e) {
            LOGGER.warn("Refusing to restore profile {} because its current contents could not be backed up",
                    profilePath, e);
            return false;
        }
    }


    static synchronized void save(ConfigFileSystem fileSystem, Path profilePath, Profile profile) {
        profile = profile.sanitized(null, ProfileDefaults.template());
        byte[] contents = ConfigFiles.jsonBytes(ProfileJson.write(profile));
        String fileName = profile.fileName() == null ? profilePath.getFileName().toString() : profile.fileName();
        try {
            saveContents(fileSystem, fileName, contents);
        } catch (IOException e) {
            LOGGER.warn("Failed to save profile backup for {}", profilePath, e);
        }
    }


    static synchronized void saveRawOrThrow(ConfigFileSystem fileSystem, Path profilePath) throws IOException {
        saveContents(fileSystem, profilePath.getFileName().toString(), fileSystem.read(profilePath));
    }

    private static void saveContents(
            ConfigFileSystem fileSystem, String fileName, byte[] contents) throws IOException {
        if (latestProfileBackupMatches(fileSystem, fileName, contents)) {
            return;
        }
        STORE.saveBytes(fileSystem, profileBackupDirectory(fileSystem, fileName), contents);
    }

    static String backupFileName(String timestamp, int sequence) {
        return TimestampedBackupStore.fileName(timestamp, sequence);
    }

    private static Path profileBackupDirectory(ConfigFileSystem fileSystem, String fileName) {
        return encodedProfileBackupDirectory(fileSystem, fileName);
    }

    static Path profileBackupDirectoryForTests(ConfigFileSystem fileSystem, String fileName) {
        return encodedProfileBackupDirectory(fileSystem, fileName);
    }

    static Path legacyProfileBackupDirectoryForTests(ConfigFileSystem fileSystem, String fileName) {
        return legacyEncodedProfileBackupDirectory(fileSystem, fileName);
    }

    private static Path encodedProfileBackupDirectory(ConfigFileSystem fileSystem, String fileName) {
        String normalized = ProfileFileNames.normalize(fileName);
        String identity = ProfileFileNames.comparisonKey(normalized);
        if (identity == null) {
            identity = "profile.json";
        }
        return encodedProfileBackupDirectory(fileSystem, normalized, identity);
    }

    private static Path legacyEncodedProfileBackupDirectory(
            ConfigFileSystem fileSystem, String fileName) {
        String normalized = ProfileFileNames.normalize(fileName);
        String identity = normalized == null ? "profile.json" : normalized;
        return encodedProfileBackupDirectory(fileSystem, normalized, identity);
    }

    private static Path encodedProfileBackupDirectory(
            ConfigFileSystem fileSystem, String normalized, String identity) {
        String name = normalized == null ? "profile"
                : normalized.substring(0, normalized.length() - ProfileFileNames.JSON_SUFFIX.length());
        String slug = ProfileFileNames.slug(name);
        if (slug.length() > 80) {
            slug = slug.substring(0, 80);
        }
        String hash = ConfigFiles.sha256Hex(identity.getBytes(StandardCharsets.UTF_8));
        return Config.getConfigDirectory(fileSystem).resolve(BACKUP_DIRECTORY_NAME).resolve(PROFILE_DIRECTORY_NAME)
                .resolve(slug + "--" + hash);
    }

    private static List<Path> backupFiles(
            ConfigFileSystem fileSystem, String fileName, String legacyFileName, boolean repairDisk) {
        Path canonicalDirectory = profileBackupDirectory(fileSystem, fileName);
        Set<Path> directories = new LinkedHashSet<>();
        directories.add(canonicalDirectory);
        directories.add(legacyEncodedProfileBackupDirectory(fileSystem, fileName));
        if (legacyFileName != null) {
            directories.add(legacyEncodedProfileBackupDirectory(fileSystem, legacyFileName));
        }
        if (repairDisk) {
            migrateLegacyBackups(fileSystem, canonicalDirectory, directories);
        }

        List<Path> backups = new ArrayList<>();
        for (Path directory : directories) {
            if (!fileSystem.isDirectory(directory)) {
                continue;
            }
            try {
                backups.addAll(fileSystem.list(directory).stream()
                        .filter(path -> ConfigFiles.isJsonFile(fileSystem, path))
                        .toList());
            } catch (IOException e) {
                LOGGER.warn("Failed to read profile backups from {}", directory, e);
            }
        }
        return backups.stream().distinct().sorted(BACKUP_PATH_ORDER.reversed()).toList();
    }

    private static void migrateLegacyBackups(
            ConfigFileSystem fileSystem, Path canonicalDirectory, Set<Path> directories) {
        for (Path directory : directories) {
            if (directory.equals(canonicalDirectory)
                    || fileSystem.isSymbolicLink(directory)
                    || !fileSystem.isDirectory(directory)) {
                continue;
            }
            try {
                for (Path backup : fileSystem.list(directory).stream()
                        .filter(path -> ConfigFiles.isJsonFile(fileSystem, path))
                        .toList()) {
                    if (copyLegacyBackup(fileSystem, canonicalDirectory, backup)) {
                        fileSystem.delete(backup);
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("Failed to migrate legacy profile backups from {} to {}", directory,
                        canonicalDirectory, e);
            }
            try {
                fileSystem.delete(directory);
            } catch (IOException ignored) {
            }
        }
        if (fileSystem.isDirectory(canonicalDirectory)) {
            try {
                rotateProfileBackups(fileSystem, canonicalDirectory);
            } catch (IOException e) {
                LOGGER.warn("Failed to rotate migrated profile backups in {}", canonicalDirectory, e);
            }
        }
    }

    private static boolean copyLegacyBackup(
            ConfigFileSystem fileSystem, Path canonicalDirectory, Path backup) throws IOException {
        byte[] contents = fileSystem.read(backup);
        fileSystem.createDirectories(canonicalDirectory);
        Path target = canonicalDirectory.resolve(backup.getFileName().toString());
        if (!fileSystem.exists(target, false)) {
            ConfigFiles.writeBytesAtomic(fileSystem, target, contents);
            return true;
        }
        if (Arrays.equals(fileSystem.read(target), contents)) {
            return true;
        }
        TimestampedBackupStore.FileOrder order =
                TimestampedBackupStore.fileOrder(backup.getFileName().toString());
        if (order == null || order.sequence() >= Integer.MAX_VALUE) {
            return false;
        }
        long sequence = order.sequence() + 1L;
        while (sequence <= Integer.MAX_VALUE) {
            target = canonicalDirectory.resolve(backupFileName(order.timestamp(), Math.toIntExact(sequence)));
            if (!fileSystem.exists(target, false)) {
                ConfigFiles.writeBytesAtomic(fileSystem, target, contents);
                return true;
            }
            sequence++;
        }
        return false;
    }

    private static void validateAndBackupWrittenProfile(
            ConfigFileSystem fileSystem, Path profilePath, String fileName) {
        try {
            Profile validated = ProfilePersistence.readProfileFile(fileSystem, profilePath, fileName,
                    "written profile " + profilePath, ProfileDefaults.template());
            save(fileSystem, profilePath, validated);
        } catch (IOException | JsonParseException | IllegalStateException e) {
            LOGGER.warn("Wrote profile {}, but could not validate it for backup", profilePath, e);
        }
    }

    private static boolean latestProfileBackupMatches(
            ConfigFileSystem fileSystem, String fileName, byte[] contents) throws IOException {
        Path latest = TimestampedBackupStore.latest(fileSystem, profileBackupDirectory(fileSystem, fileName));
        return latest != null && Arrays.equals(fileSystem.read(latest), contents);
    }

    private static void rotateProfileBackups(
            ConfigFileSystem fileSystem, Path backupDirectory) throws IOException {
        STORE.rotate(fileSystem, backupDirectory);
    }

    static int compareBackupFileNames(String leftName, String rightName) {
        return TimestampedBackupStore.compareFileNames(leftName, rightName);
    }
}
