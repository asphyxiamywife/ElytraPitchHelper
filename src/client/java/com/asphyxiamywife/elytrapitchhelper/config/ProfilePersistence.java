package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.ModConstants;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

final class ProfilePersistence {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModConstants.MOD_ID);

    private ProfilePersistence() {
    }

    static LoadResult loadProfiles(boolean createMissingProfiles, ProfileMetadataStore metadata) {
        ProfileLoadResult result = readProfileDirectory(metadata);
        List<Profile> loaded = result.profiles();
        boolean skipNextProfileSave = false;

        if (loaded.isEmpty()) {
            if (createMissingProfiles && !result.foundProfileFiles()) {
                ProfileDefaults.copyBundledProfilesToConfig(Config.getProfileDirectory());
                loaded = readProfileDirectory(metadata).profiles();
            } else if (result.foundProfileFiles()) {
                skipNextProfileSave = true;
            }
            if (loaded.isEmpty()) {
                loaded = ProfileDefaults.profiles();
            }
        }

        return new LoadResult(loaded, skipNextProfileSave);
    }

    static boolean hasProfileFiles() {
        Path profileDirectory = Config.getProfileDirectory();
        if (!Files.isDirectory(profileDirectory)) {
            return false;
        }
        try (Stream<Path> paths = Files.list(profileDirectory)) {
            return paths.anyMatch(ConfigFiles::isJsonFile);
        } catch (IOException ignored) {
            return false;
        }
    }

    static boolean saveProfiles(List<Profile> profiles, ProfileMetadataStore metadata) {
        Path profileDirectory = Config.getProfileDirectory();
        try {
            Files.createDirectories(profileDirectory);
            boolean savedProfile = false;
            for (Profile profile : profiles) {
                RepairLog repairs = new RepairLog("profile " + profilePath(profile));
                profile.sanitize(repairs, ProfileDefaults.template());
                repairs.log();
                Path path = profilePath(profile);
                ProfileMetadata profileMetadata = metadata.profile(profile.fileName);
                boolean profileFileExists = Files.exists(path);
                ProfileFileDiff profileFileDiff = profileFileDiff(path, profile);
                if (profileFileDiff.needsWrite()) {
                    if (profileChangedExternallySinceLoad(path, profileMetadata)) {
                        LOGGER.warn("Skipped saving stale profile {} because it changed on disk", path);
                        continue;
                    }
                    if (profileFileDiff.settingsChanged()) {
                        profileMetadata.markModified(System.currentTimeMillis());
                    }
                    savedProfile = true;
                    ConfigFiles.writeJsonAtomic(path, profile);
                    if (profileFileExists) {
                        ProfileBackups.save(path, profile);
                    }
                    syncWrittenProfileTimestamp(profileMetadata, path);
                }
            }
            return savedProfile;
        } catch (IOException e) {
            LOGGER.error("Failed to save profiles into {}", profileDirectory, e);
            throw new ConfigSaveException("Failed to save profiles into " + profileDirectory, e);
        }
    }

    static void deleteProfileFile(String fileName) {
        String normalized = ProfileFileNames.normalize(fileName);
        if (normalized == null) {
            return;
        }

        try {
            Files.deleteIfExists(Config.getProfileDirectory().resolve(normalized));
        } catch (IOException ignored) {
        }
    }

    static void saveProfileMetadata(ProfileMetadataStore metadata, List<Profile> profiles) {
        Set<String> profileFiles = new HashSet<>();
        for (Profile profile : profiles) {
            if (profile.fileName != null) {
                profileFiles.add(profile.fileName.toLowerCase(Locale.ROOT));
            }
        }
        metadata.prune(profileFiles);
        Path path = Config.getProfileMetadataPath();
        try {
            ConfigFiles.writeJsonAtomic(path, metadata);
        } catch (IOException e) {
            LOGGER.warn("Failed to save internal profile metadata {}", path, e);
            throw new ConfigSaveException("Failed to save internal profile metadata " + path, e);
        }
    }

    static void markMigratedProfileMetadata(ProfileMetadataStore metadata, List<Profile> profiles, Path legacyPath) {
        long timestamp = System.currentTimeMillis();
        try {
            timestamp = Files.getLastModifiedTime(legacyPath).toMillis();
        } catch (IOException ignored) {
        }

        for (Profile profile : profiles) {
            metadata.profile(profile.fileName).markCreated(timestamp, "Migrated legacy config");
        }
    }

    static long lastModifiedAtMillis(ProfileMetadataStore metadata, String fileName) {
        return metadata.profile(fileName).lastModifiedAtMillis;
    }

    static String basedOn(ProfileMetadataStore metadata, String fileName) {
        return metadata.profile(fileName).basedOn();
    }

    static Path profilePath(Profile profile) {
        return Config.getProfileDirectory().resolve(profile.fileName);
    }

    static Profile readProfileFile(Path path, String fileName, String source) throws IOException {
        return readProfileFile(path, fileName, source, ProfileDefaults.template());
    }

    static Profile readProfileFile(Path path, String fileName, String source, Profile defaults)
            throws IOException {
        if (defaults == null) {
            defaults = ProfileDefaults.template();
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = ConfigFiles.GSON.fromJson(reader, JsonObject.class);
            RepairLog repairs = new RepairLog(source);
            Profile profile = ProfileJson.parse(root, fileName, defaults, repairs);
            profile.sanitize(repairs, defaults);
            repairs.log();
            return profile;
        }
    }

    private static ProfileLoadResult readProfileDirectory(ProfileMetadataStore metadata) {
        List<Profile> loaded = new java.util.ArrayList<>();
        boolean foundProfileFiles = false;
        Path profileDirectory = Config.getProfileDirectory();
        if (Files.isDirectory(profileDirectory)) {
            try (Stream<Path> paths = Files.list(profileDirectory)) {
                List<Path> profilePaths = paths.filter(ConfigFiles::isJsonFile)
                        .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                        .toList();
                for (Path path : profilePaths) {
                    foundProfileFiles = true;
                    Profile profile = readProfile(path, metadata);
                    if (profile != null) {
                        loaded.add(profile);
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return new ProfileLoadResult(loaded, foundProfileFiles);
    }

    private static Profile readProfile(Path path, ProfileMetadataStore metadata) {
        try {
            Profile defaults = ProfileBackups.latestValid(path);
            if (defaults == null) {
                defaults = ProfileDefaults.template();
            }
            Profile profile = readProfileFile(path, path.getFileName().toString(), "profile " + path,
                    defaults);
            syncProfileMetadataFromFile(metadata, profile.fileName, path);
            ProfileBackups.save(path, profile);
            return profile;
        } catch (IOException | JsonParseException | IllegalStateException e) {
            LOGGER.warn("Failed to load profile {}, trying newest backup", path, e);
            Profile profile = ProfileBackups.restore(path);
            if (profile != null) {
                syncProfileMetadataFromFile(metadata, profile.fileName, path);
            }
            return profile;
        }
    }

    private static void syncProfileMetadataFromFile(ProfileMetadataStore metadata, String fileName, Path path) {
        try {
            metadata.profile(fileName).markLoaded(Files.getLastModifiedTime(path).toMillis(),
                    ProfileDefaults.basedOn(fileName));
        } catch (IOException ignored) {
            metadata.profile(fileName).ensure(ProfileDefaults.basedOn(fileName));
        }
    }

    private static ProfileFileDiff profileFileDiff(Path path, Profile profile) {
        if (!Files.exists(path)) {
            return new ProfileFileDiff(true, false);
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject existing = ConfigFiles.GSON.fromJson(reader, JsonObject.class);
            JsonObject next = ConfigFiles.GSON.toJsonTree(profile).getAsJsonObject();
            boolean needsWrite = !next.equals(existing);
            Profile defaults = ProfileDefaults.template();
            Profile existingProfile = ProfileJson.parse(existing, profile.fileName, defaults, null);
            existingProfile.sanitize(null, defaults);
            Profile nextProfile = profile.copy();
            nextProfile.sanitize(null, defaults);
            boolean settingsChanged = !ConfigFiles.GSON.toJsonTree(nextProfile)
                    .equals(ConfigFiles.GSON.toJsonTree(existingProfile));
            return new ProfileFileDiff(needsWrite, settingsChanged);
        } catch (IOException | JsonParseException | IllegalStateException e) {
            return new ProfileFileDiff(true, true);
        }
    }

    private static boolean profileChangedExternallySinceLoad(Path path, ProfileMetadata metadata) {
        if (!Files.exists(path) || metadata.loadedFileModifiedAtMillis <= 0L) {
            return false;
        }
        try {
            return Files.getLastModifiedTime(path).toMillis() > metadata.loadedFileModifiedAtMillis;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static void syncWrittenProfileTimestamp(ProfileMetadata metadata, Path path) {
        try {
            metadata.syncLoadedFileModifiedAtMillis(Files.getLastModifiedTime(path).toMillis());
        } catch (IOException ignored) {
        }
    }

    record LoadResult(List<Profile> profiles, boolean skipNextProfileSave) {
    }

    private record ProfileLoadResult(List<Profile> profiles, boolean foundProfileFiles) {
    }

    private record ProfileFileDiff(boolean needsWrite, boolean settingsChanged) {
    }
}
