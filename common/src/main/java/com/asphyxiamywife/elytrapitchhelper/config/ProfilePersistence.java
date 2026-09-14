package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.ModConstants;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class ProfilePersistence {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModConstants.MOD_ID);

    private ProfilePersistence() {
    }


    static LoadResult loadProfiles(
            ConfigFileSystem fileSystem, boolean startup, Config config) {
        ProfileLoadResult result = readProfileDirectory(fileSystem, config, startup);
        List<Profile> loaded = result.profiles();
        boolean skipNextProfileSave = false;

        if (loaded.isEmpty()) {
            if (startup && !result.foundProfileFiles()) {
                ProfileDefaults.copyBundledProfilesToConfig(fileSystem, Config.getProfileDirectory(fileSystem));
                loaded = readProfileDirectory(fileSystem, config, startup).profiles();
            } else if (result.foundProfileFiles()) {
                skipNextProfileSave = true;
            }
            if (loaded.isEmpty()) {
                loaded = ProfileDefaults.profiles();
            }
        }

        return new LoadResult(loaded, skipNextProfileSave);
    }


    static boolean hasProfileFiles(ConfigFileSystem fileSystem) {
        return profileDirectoryEntries(fileSystem).stream()
                .anyMatch(path -> ConfigFiles.isJsonFile(fileSystem, path));
    }


    static String incompatibleFileNameWarning(ConfigFileSystem fileSystem) {
        List<String> incompatible = profileDirectoryEntries(fileSystem).stream()
                .filter(path -> !fileSystem.isDirectory(path))
                .map(path -> path.getFileName().toString())
                .filter(name -> name.replaceAll("[ .]+$", "").toLowerCase(Locale.ROOT).endsWith(".json"))
                .filter(name -> !ProfileFileNames.isPortable(name))
                .sorted()
                .toList();
        return incompatible.isEmpty() ? null
                : "Rename incompatible profile files " + String.join(", ", incompatible)
                        + " to portable filenames, then reopen config. Existing files have been left untouched.";
    }

    static String caseInsensitiveCollisionWarning(ConfigFileSystem fileSystem) {
        List<String> fileNames = profileDirectoryEntries(fileSystem).stream()
                .filter(path -> ConfigFiles.isJsonFile(fileSystem, path))
                .map(path -> path.getFileName().toString())
                .sorted(Comparator.comparing((String name) -> name.toLowerCase(Locale.ROOT))
                        .thenComparing(name -> name))
                .toList();
        List<String> collisions = caseInsensitiveCollisions(fileNames);
        return collisions.isEmpty() ? null
                : "Rename case-colliding profile files " + String.join(", ", collisions)
                        + " so their names differ beyond letter case, then reopen config.";
    }

    static List<String> caseInsensitiveCollisions(List<String> fileNames) {
        Map<String, String> firstByKey = new LinkedHashMap<>();
        Set<String> collisions = new LinkedHashSet<>();
        for (String fileName : fileNames) {
            String key = ProfileFileNames.comparisonKey(fileName);
            if (key == null) {
                continue;
            }
            String first = firstByKey.putIfAbsent(key, fileName);
            if (first != null && !first.equals(fileName)) {
                collisions.add(first);
                collisions.add(fileName);
            }
        }
        return List.copyOf(collisions);
    }



    static boolean saveProfiles(ConfigFileSystem fileSystem, Config config,
            Set<Path> approvedConflictPaths) {
        ProfileMetadataStore metadataBeforeSave = config.profileMetadata.copy();
        StoreMetadata storeMetadataBeforeSave = config.storeMetadata;
        try {
            return writeProfiles(fileSystem, config, approvedConflictPaths);
        } catch (RuntimeException failure) {
            config.profileMetadata.restoreFrom(metadataBeforeSave);
            config.storeMetadata = storeMetadataBeforeSave;
            throw failure;
        }
    }

    private static boolean writeProfiles(ConfigFileSystem fileSystem, Config config,
            Set<Path> approvedConflictPaths) {
        Path profileDirectory = Config.getProfileDirectory(fileSystem);
        try {
            fileSystem.createDirectories(profileDirectory);
            List<PendingProfileSave> pendingSaves = new ArrayList<>();
            List<Path> conflictingPaths = new ArrayList<>();
            for (Profile profile : config.profiles) {
                Path path = profilePath(fileSystem, profile);
                ObservedProfileFile observed = observeProfileFile(fileSystem, path, profile);
                DiskSnapshot observedDisk = observed.disk();
                ProfileFileDiff profileFileDiff = observed.diff();
                boolean externallyChanged = profileFileDiff.needsWrite()
                        && profileChangedExternallySinceLoad(
                                observedDisk, config.profileFileState(profile.fileName()));
                if (externallyChanged) {
                    conflictingPaths.add(path);
                }
                if (!profileFileDiff.needsWrite() && observedDisk.exists()) {
                    syncLoadedProfileFile(config, profile.fileName(), observedDisk);
                }
                pendingSaves.add(new PendingProfileSave(profile, path,
                        observedDisk, externallyChanged, profileFileDiff));
            }

            List<Path> unapprovedConflicts = unapprovedPaths(conflictingPaths, approvedConflictPaths);
            if (!unapprovedConflicts.isEmpty()) {
                LOGGER.warn("Refused to overwrite externally changed profiles: {}", unapprovedConflicts);
                throw new ProfileConflictException(unapprovedConflicts);
            }

            boolean savedProfile = false;
            for (PendingProfileSave pending : pendingSaves) {
                if (!pending.diff().needsWrite()) {
                    continue;
                }
                if (pending.diff().settingsChanged()) {
                    config.profileMetadata.markModified(
                            pending.profile().fileName(), System.currentTimeMillis());
                }
                if (pending.externallyChanged() && pending.observedDisk().exists()) {
                    ProfileBackups.saveRawOrThrow(fileSystem, pending.path());
                }
                savedProfile = true;
                DiskSnapshot written = ConfigFiles.writeJsonAtomicWithSnapshot(
                        fileSystem, pending.path(), ProfileJson.write(pending.profile()));
                if (pending.observedDisk().exists()) {
                    ConfigSaveBatch.afterCommit(
                            () -> ProfileBackups.save(fileSystem, pending.path(), pending.profile()));
                }
                syncLoadedProfileFile(config, pending.profile().fileName(), written);
                config.writtenProfiles.put(pending.profile().fileName(),
                        config.profileFileState(pending.profile().fileName()));
            }
            return savedProfile;
        } catch (IOException e) {
            LOGGER.error("Failed to save profiles into {}", profileDirectory, e);
            throw new ConfigSaveException("Failed to save profiles into " + profileDirectory, e);
        }
    }



    static void deleteProfileFilesSafely(
            ConfigFileSystem fileSystem, List<String> fileNames, Config config) {
        if (profilePaths(fileSystem, fileNames).isEmpty()) {
            return;
        }
        try (ConfigSaveLock ignored = ConfigSaveLock.acquire(fileSystem)) {
            deleteProfileFilesChecked(fileSystem, fileNames, config, Set.of());
        }
    }
    static void deleteProfileFilesChecked(ConfigFileSystem fileSystem,
            List<String> fileNames, Config config, Set<Path> approvedConflictPaths) {
        List<Path> paths = profilePaths(fileSystem, fileNames);
        if (paths.isEmpty()) {
            return;
        }
        Map<Path, DiskSnapshot> baseline = captureBaseline(fileSystem, paths);
        preflightProfileDeletes(fileSystem, fileNames, config, approvedConflictPaths, baseline);
        deleteProfileFiles(fileSystem, fileNames, baseline);
        for (Path path : paths) {
            String file = path.getFileName().toString();
            LoadedFileState missing = config.profileFileState(file).syncFrom(DiskSnapshot.MISSING);
            config.restoreProfileFileState(file, missing);
            if (baseline.get(path).exists()) {
                config.writtenProfiles = new java.util.LinkedHashMap<>(config.writtenProfiles);
                config.writtenProfiles.put(file, missing);
            }
        }
    }



    static void deleteProfileFiles(ConfigFileSystem fileSystem,
            List<String> fileNames, Map<Path, DiskSnapshot> baseline) {
        if (fileNames == null || fileNames.isEmpty()) {
            return;
        }

        List<Path> profilePaths = profilePaths(fileSystem, fileNames);
        if (profilePaths.isEmpty()) {
            return;
        }

        ConfigSaveBatch ownedBatch = ConfigSaveBatch.isActive()
                ? null
                : ConfigSaveBatch.begin(fileSystem, profilePaths);
        List<StagedFileMove> stagedMoves = new ArrayList<>();
        try {
            for (Path path : profilePaths) {
                if (fileSystem.exists(path, true)
                        && !fileSystem.isRegularFile(path, true)
                        && !fileSystem.isSymbolicLink(path)) {
                    throw new IOException("Profile path is not a regular file: " + path);
                }
            }

            for (Path source : profilePaths) {
                if (!fileSystem.exists(source, true)) {
                    continue;
                }
                Path staged = ConfigSaveBatch.stageDelete(fileSystem, source);
                stagedMoves.add(new StagedFileMove(source, staged));
            }
            if (baseline != null) {
                List<Path> conflictingPaths = new ArrayList<>();
                for (StagedFileMove stagedMove : stagedMoves) {
                    if (!stagedFileMatchesBaseline(
                            fileSystem, baseline.get(stagedMove.original()), stagedMove.staged())) {
                        conflictingPaths.add(stagedMove.original());
                    }
                }
                if (!conflictingPaths.isEmpty()) {
                    ProfileConflictException conflict = new ProfileConflictException(conflictingPaths);
                    restoreStagedDeletes(fileSystem, stagedMoves, conflict);
                    if (conflict.getSuppressed().length > 0) {
                        throw new ConfigSaveException(
                                "Failed to restore a profile after detecting a delete conflict", conflict);
                    }
                    throw conflict;
                }
            }
            if (ownedBatch != null) {
                ownedBatch.commit();
            }
        } catch (IOException | RuntimeException failure) {
            if (ownedBatch != null) {
                ownedBatch.rollback(failure);
            }
            if (failure instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            LOGGER.error("Failed to delete profiles", failure);
            throw new ConfigSaveException("Failed to delete profiles", failure);
        }
    }

    static void preflightProfileDeletes(ConfigFileSystem fileSystem, List<String> fileNames, Config config,
            Set<Path> approvedConflictPaths, Map<Path, DiskSnapshot> baseline) {
        List<Path> conflicts = new ArrayList<>();
        for (Path path : profilePaths(fileSystem, fileNames)) {
            DiskSnapshot baselineDisk = baseline.get(path);
            if (baselineDisk == null || !baselineDisk.exists()) {
                continue;
            }
            if (profileChangedExternallySinceLoad(
                    baselineDisk, config.profileFileState(path.getFileName().toString()))
                    && !approvedConflictPaths.contains(path)) {
                conflicts.add(path);
            }
        }
        if (!conflicts.isEmpty()) {
            throw new ProfileConflictException(conflicts);
        }
    }


    static Map<Path, DiskSnapshot> captureBaseline(
            ConfigFileSystem fileSystem, List<Path> paths) {
        try {
            Map<Path, DiskSnapshot> baseline = new LinkedHashMap<>();
            for (Path path : paths) {
                baseline.put(path, diskSnapshot(fileSystem, path));
            }
            return baseline;
        } catch (IOException e) {
            throw new ConfigSaveException("Failed to inspect profile files before saving", e);
        }
    }


    private static boolean stagedFileMatchesBaseline(
            ConfigFileSystem fileSystem, DiskSnapshot baseline, Path stagedTarget) throws IOException {
        if (baseline == null || !baseline.exists()) {
            return false;
        }
        return baseline.sameContent(DiskSnapshot.capture(fileSystem, stagedTarget));
    }


    static void saveProfileMetadata(ConfigFileSystem fileSystem, Config config) {
        Set<String> profileFiles = new HashSet<>();
        for (Profile profile : config.profiles) {
            if (profile.fileName() != null) {
                profileFiles.add(profile.fileName());
            }
        }
        config.profileMetadata.prune(profileFiles);
        Path path = Config.getProfileMetadataPath(fileSystem);
        try {
            ConfigFiles.writeJsonAtomic(
                    fileSystem, path, config.profileMetadata.toJson(config.storeMetadata));
        } catch (IOException e) {
            LOGGER.warn("Failed to save internal profile metadata {}", path, e);
            throw new ConfigSaveException("Failed to save internal profile metadata " + path, e);
        }
    }


    static void markMigratedProfileMetadata(ConfigFileSystem fileSystem,
            ProfileMetadataStore metadata, List<Profile> profiles, Path legacyPath) {
        long timestamp = System.currentTimeMillis();
        try {
            timestamp = fileSystem.modifiedAtMillis(legacyPath);
        } catch (IOException ignored) {
        }

        for (Profile profile : profiles) {
            metadata.markCreated(profile.fileName(), timestamp, "Migrated legacy config");
        }
    }

    static long lastModifiedAtMillis(ProfileMetadataStore metadata, String fileName) {
        return metadata.profile(fileName).lastModifiedAtMillis();
    }

    static String basedOn(ProfileMetadataStore metadata, String fileName) {
        return metadata.profile(fileName).basedOn();
    }

    static Path profilePath(ConfigFileSystem fileSystem, Profile profile) {
        return Config.getProfileDirectory(fileSystem).resolve(profile.fileName());
    }


    static Profile readProfileFile(ConfigFileSystem fileSystem, Path path,
            String fileName, String source, Profile defaults) throws IOException {
        if (defaults == null) {
            defaults = ProfileDefaults.template();
        }
        JsonObject root = ConfigJsonFiles.parse(fileSystem.read(path), JsonObject.class);
        RepairLog repairs = new RepairLog(source);
        Profile profile = ProfileJson.parse(root, fileName, defaults, repairs);
        profile = profile.sanitized(repairs, defaults);
        repairs.log();
        return profile;
    }

    private static ProfileLoadResult readProfileDirectory(
            ConfigFileSystem fileSystem, Config config, boolean repairDisk) {
        List<Profile> loaded = new ArrayList<>();
        boolean foundProfileFiles = false;
        List<Path> profilePaths = profileDirectoryEntries(fileSystem).stream()
                .filter(path -> ConfigFiles.isJsonFile(fileSystem, path))
                .sorted(Comparator
                        .comparing((Path path) -> path.getFileName().toString().toLowerCase(Locale.ROOT))
                        .thenComparing(path -> path.getFileName().toString()))
                .toList();
        for (Path path : profilePaths) {
            foundProfileFiles = true;
            Profile profile = readProfile(fileSystem, path, config, repairDisk);
            if (profile != null) {
                loaded.add(profile);
            }
        }
        return new ProfileLoadResult(loaded, foundProfileFiles);
    }

    private static List<Path> profileDirectoryEntries(ConfigFileSystem fileSystem) {
        Path profileDirectory = Config.getProfileDirectory(fileSystem);
        try {
            BasicFileAttributes attributes = fileSystem.attributes(profileDirectory);
            if (!attributes.isDirectory()) {
                throw new IOException("Profile path is not a directory");
            }
            return fileSystem.list(profileDirectory);
        } catch (NoSuchFileException ignored) {
            return List.of();
        } catch (IOException | SecurityException e) {
            throw new ProfileDirectoryAccessException(profileDirectory, e);
        }
    }

    static final class ProfileDirectoryAccessException extends RuntimeException {
        private final Path directory;

        ProfileDirectoryAccessException(Path directory, Throwable cause) {
            super("Could not read profile directory " + directory, cause);
            this.directory = directory;
        }

        Path directory() {
            return directory;
        }
    }

    private static Profile readProfile(
            ConfigFileSystem fileSystem, Path path, Config config, boolean repairDisk) {
        String legacyFileName = config.profileMetadata.existingFileName(path.getFileName().toString());
        try {
            Profile defaults = ProfileBackups.latestValid(fileSystem, path, legacyFileName, repairDisk);
            if (defaults == null) {
                defaults = ProfileDefaults.template();
            }
            Profile profile = readProfileFile(fileSystem, path, path.getFileName().toString(),
                    "profile " + path, defaults);
            syncProfileMetadataFromFile(fileSystem, config, profile.fileName(), path);
            if (repairDisk) {
                ProfileBackups.save(fileSystem, path, profile);
            }
            return profile;
        } catch (IOException | JsonParseException | IllegalStateException e) {
            if (!repairDisk && e instanceof IOException) {
                throw new ConfigStorage.FileAccessException(path, e);
            }
            LOGGER.warn("Failed to load profile {}, trying newest backup", path, e);
            Profile profile = repairDisk
                    ? ProfileBackups.restore(fileSystem, path, legacyFileName)
                    : ProfileBackups.latestValid(fileSystem, path, legacyFileName, false);
            if (profile != null) {
                syncProfileMetadataFromFile(fileSystem, config, profile.fileName(), path);
            } else if (repairDisk) {
                quarantineUnreadableProfile(fileSystem, path, e);
            }
            return profile;
        }
    }

    private static void quarantineUnreadableProfile(
            ConfigFileSystem fileSystem, Path path, Exception parseFailure) {
        try {
            ProfileBackups.saveRawOrThrow(fileSystem, path);
            fileSystem.delete(path);
            LOGGER.error("Profile {} could not be loaded and no valid backup was available; "
                    + "its corrupt contents were preserved in profile backups and the active file was removed",
                    path, parseFailure);
        } catch (IOException preserveFailure) {
            preserveFailure.addSuppressed(parseFailure);
            LOGGER.error("Profile {} could not be loaded, no valid backup was available, and its corrupt "
                    + "contents could not be quarantined; the profile will be skipped", path, preserveFailure);
        }
    }

    private static void syncProfileMetadataFromFile(
            ConfigFileSystem fileSystem, Config config, String fileName, Path path) {
        try {
            DiskSnapshot disk = diskSnapshot(fileSystem, path);
            LoadedFileState previous = config.profileFileState(fileName);
            config.profileMetadata.markLoaded(
                    fileName,
                    disk.modifiedAtMillis(),
                    ProfileDefaults.basedOn(fileName),
                    previous.loaded() && previous.changedSince(disk));
            config.restoreProfileFileState(fileName, previous.syncFrom(disk));
        } catch (IOException ignored) {
            config.profileMetadata.ensure(fileName, ProfileDefaults.basedOn(fileName));
        }
    }

    private static ObservedProfileFile observeProfileFile(
            ConfigFileSystem fileSystem, Path path, Profile profile) throws IOException {
        if (!fileSystem.exists(path, false)) {
            return new ObservedProfileFile(DiskSnapshot.MISSING, new ProfileFileDiff(true, false));
        }
        long modifiedAtMillis = fileSystem.modifiedAtMillis(path);
        byte[] contents = fileSystem.read(path);
        DiskSnapshot disk = new DiskSnapshot(true, modifiedAtMillis, ConfigFiles.sha256Hex(contents));
        return new ObservedProfileFile(disk, profileFileDiff(contents, profile));
    }

    private static ProfileFileDiff profileFileDiff(byte[] contents, Profile profile) {
        try {
            JsonObject existing = ConfigJsonFiles.parse(contents, JsonObject.class);
            JsonObject next = ProfileJson.write(profile);
            boolean needsWrite = !ConfigFiles.jsonValuesEqual(next, existing);
            Profile defaults = ProfileDefaults.template();
            Profile existingProfile = ProfileJson.parse(existing, profile.fileName(), defaults, null);
            boolean settingsChanged = !ConfigFiles.jsonValuesEqual(
                    ProfileJson.write(profile), ProfileJson.write(existingProfile));
            return new ProfileFileDiff(needsWrite, settingsChanged);
        } catch (JsonParseException | IllegalStateException e) {
            return new ProfileFileDiff(true, true);
        }
    }

    private static boolean profileChangedExternallySinceLoad(
            DiskSnapshot disk, LoadedFileState loadedFileState) {
        return loadedFileState.changedSince(disk);
    }

    private static void syncLoadedProfileFile(Config config, String fileName, DiskSnapshot disk) {
        config.restoreProfileFileState(fileName, config.profileFileState(fileName).syncFrom(disk));
    }

    private static List<Path> unapprovedPaths(List<Path> paths, Set<Path> approvedConflictPaths) {
        return paths.stream()
                .filter(path -> !approvedConflictPaths.contains(path))
                .toList();
    }

    private static DiskSnapshot diskSnapshot(ConfigFileSystem fileSystem, Path path) throws IOException {
        return DiskSnapshot.capture(fileSystem, path);
    }

    static List<Path> profilePaths(ConfigFileSystem fileSystem, List<String> fileNames) {
        if (fileNames == null || fileNames.isEmpty()) {
            return List.of();
        }
        List<Path> paths = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String fileName : fileNames) {
            String normalized = ProfileFileNames.normalize(fileName);
            if (normalized != null && seen.add(normalized)) {
                paths.add(Config.getProfileDirectory(fileSystem).resolve(normalized));
            }
        }
        return paths;
    }

    private static void restoreStagedDeletes(
            ConfigFileSystem fileSystem, List<StagedFileMove> stagedMoves, Throwable failure) {
        for (int i = stagedMoves.size() - 1; i >= 0; i--) {
            StagedFileMove stagedMove = stagedMoves.get(i);
            try {
                ConfigSaveBatch.restoreStagedDelete(
                        fileSystem, stagedMove.staged(), stagedMove.original());
            } catch (IOException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
                LOGGER.error("Failed to restore profile {} after delete verification failed",
                        stagedMove.original(), rollbackFailure);
            }
        }
    }

    record LoadResult(List<Profile> profiles, boolean skipNextProfileSave) {
    }

    private record ProfileLoadResult(List<Profile> profiles, boolean foundProfileFiles) {
    }

    private record ProfileFileDiff(boolean needsWrite, boolean settingsChanged) {
    }

    private record ObservedProfileFile(DiskSnapshot disk, ProfileFileDiff diff) {
    }

    private record PendingProfileSave(Profile profile, Path path,
            DiskSnapshot observedDisk, boolean externallyChanged, ProfileFileDiff diff) {
    }

    private record StagedFileMove(Path original, Path staged) {
    }
}
