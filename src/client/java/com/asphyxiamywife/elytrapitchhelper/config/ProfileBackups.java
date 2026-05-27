package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.ModConstants;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

final class ProfileBackups {
    private static final String BACKUP_DIRECTORY_NAME = "backups";
    private static final String PROFILE_DIRECTORY_NAME = "profiles";
    private static final int MAX_PROFILE_BACKUPS = 8;
    private static final DateTimeFormatter BACKUP_TIMESTAMP_FORMAT = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss-SSS")
            .withZone(ZoneOffset.UTC);
    private static final Logger LOGGER = LoggerFactory.getLogger(ModConstants.MOD_ID);

    private ProfileBackups() {
    }

    static Profile latestValid(Path profilePath) {
        Path backupDirectory = profileBackupDirectory(profilePath.getFileName().toString());
        if (!Files.isDirectory(backupDirectory)) {
            return null;
        }

        try (Stream<Path> paths = Files.list(backupDirectory)) {
            List<Path> backups = paths.filter(ConfigFiles::isJsonFile)
                    .sorted(Comparator.<Path, String>comparing(path -> path.getFileName().toString()).reversed())
                    .toList();
            for (Path backup : backups) {
                try {
                    return ProfilePersistence.readProfileFile(backup, profilePath.getFileName().toString(),
                            "profile backup " + backup);
                } catch (IOException | JsonParseException | IllegalStateException e) {
                    LOGGER.warn("Skipped invalid profile backup {}", backup, e);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to read profile backups from {}", backupDirectory, e);
        }
        return null;
    }

    static Profile restore(Path profilePath) {
        Path backupDirectory = profileBackupDirectory(profilePath.getFileName().toString());
        if (!Files.isDirectory(backupDirectory)) {
            return null;
        }

        try (Stream<Path> paths = Files.list(backupDirectory)) {
            List<Path> backups = paths.filter(ConfigFiles::isJsonFile)
                    .sorted(Comparator.<Path, String>comparing(path -> path.getFileName().toString()).reversed())
                    .toList();
            for (Path backup : backups) {
                try {
                    Profile profile = ProfilePersistence.readProfileFile(backup, profilePath.getFileName().toString(),
                            "profile backup " + backup);
                    ConfigFiles.writeJsonAtomic(profilePath, profile);
                    validateAndBackupWrittenProfile(profilePath, profile.fileName);
                    LOGGER.warn("Restored profile {} from backup {}", profilePath, backup);
                    return profile;
                } catch (IOException | JsonParseException | IllegalStateException e) {
                    LOGGER.warn("Skipped invalid profile backup {}", backup, e);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to read profile backups from {}", backupDirectory, e);
        }
        return null;
    }

    static void save(Path profilePath, Profile profile) {
        try {
            profile.sanitize(null, ProfileDefaults.template());
            byte[] contents = ConfigFiles.GSON.toJson(profile).getBytes(StandardCharsets.UTF_8);
            String fileName = profile.fileName == null ? profilePath.getFileName().toString() : profile.fileName;
            if (latestProfileBackupMatches(fileName, contents)) {
                return;
            }

            Path backupDirectory = profileBackupDirectory(fileName);
            Files.createDirectories(backupDirectory);
            String timestamp = BACKUP_TIMESTAMP_FORMAT.format(Instant.now());
            Path backupPath = backupDirectory.resolve(timestamp + ProfileFileNames.JSON_SUFFIX);
            int suffix = 2;
            while (Files.exists(backupPath)) {
                backupPath = backupDirectory.resolve(timestamp + "-" + suffix + ProfileFileNames.JSON_SUFFIX);
                suffix++;
            }
            ConfigFiles.writeBytesAtomic(backupPath, contents);
            rotateProfileBackups(backupDirectory);
        } catch (IOException e) {
            LOGGER.warn("Failed to save profile backup for {}", profilePath, e);
        }
    }

    private static Path profileBackupDirectory(String fileName) {
        String normalized = ProfileFileNames.normalize(fileName);
        String name = normalized == null ? "profile"
                : normalized.substring(0, normalized.length() - ProfileFileNames.JSON_SUFFIX.length());
        return Config.getConfigDirectory().resolve(BACKUP_DIRECTORY_NAME).resolve(PROFILE_DIRECTORY_NAME)
                .resolve(ProfileFileNames.slug(name));
    }

    private static void validateAndBackupWrittenProfile(Path profilePath, String fileName) {
        try {
            Profile validated = ProfilePersistence.readProfileFile(profilePath, fileName,
                    "written profile " + profilePath);
            save(profilePath, validated);
        } catch (IOException | JsonParseException | IllegalStateException e) {
            LOGGER.warn("Wrote profile {}, but could not validate it for backup", profilePath, e);
        }
    }

    private static boolean latestProfileBackupMatches(String fileName, byte[] contents) throws IOException {
        Path backupDirectory = profileBackupDirectory(fileName);
        if (!Files.isDirectory(backupDirectory)) {
            return false;
        }
        try (Stream<Path> paths = Files.list(backupDirectory)) {
            Path latest = paths.filter(ConfigFiles::isJsonFile)
                    .max(Comparator.comparing(path -> path.getFileName().toString()))
                    .orElse(null);
            return latest != null && Arrays.equals(Files.readAllBytes(latest), contents);
        }
    }

    private static void rotateProfileBackups(Path backupDirectory) throws IOException {
        try (Stream<Path> paths = Files.list(backupDirectory)) {
            List<Path> backups = paths.filter(ConfigFiles::isJsonFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            int removeCount = backups.size() - MAX_PROFILE_BACKUPS;
            for (int i = 0; i < removeCount; i++) {
                Files.deleteIfExists(backups.get(i));
            }
        }
    }
}
