package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.ModConstants;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class ProfileMetadataStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModConstants.MOD_ID);

    int version = Config.CURRENT_VERSION;
    List<ProfileMetadata> profiles = new ArrayList<>();


    static Loaded load(ConfigFileSystem fileSystem, boolean repairDisk) {
        Path path = Config.getProfileMetadataPath(fileSystem);
        if (!fileSystem.exists(path, false)) {
            return new Loaded(new ProfileMetadataStore(), Map.of());
        }
        JsonObject root = ConfigJsonFiles.read(fileSystem, path, JsonObject.class,
                failure -> recoverMalformed(fileSystem, path, failure, repairDisk));
        if (root == null) {
            return new Loaded(new ProfileMetadataStore(), Map.of());
        }
        ProfileMetadataStore store;
        try {
            store = ConfigFiles.GSON.fromJson(root, ProfileMetadataStore.class);
        } catch (RuntimeException failure) {
            recoverMalformed(fileSystem, path, failure, repairDisk);
            return new Loaded(new ProfileMetadataStore(), Map.of());
        }
        store.sanitize();
        return new Loaded(store, loadedFiles(root));
    }

    private static JsonObject recoverMalformed(
            ConfigFileSystem fileSystem, Path path, Exception failure, boolean repairDisk) {
        if (!repairDisk) {
            if (failure instanceof IOException) {
                throw new ConfigStorage.FileAccessException(path, failure);
            }
            LOGGER.warn("Failed to reload internal profile metadata {}; leaving disk untouched", path, failure);
            return null;
        }
        try {
            ConfigBackups.saveMalformedMainOrMetadata(fileSystem, path, "profile-metadata");
            LOGGER.warn("Failed to load internal profile metadata {}; preserved its original bytes before "
                    + "using fresh metadata", path, failure);
            return null;
        } catch (IOException backupFailure) {
            failure.addSuppressed(backupFailure);
            throw new MetadataBackupException(path, failure);
        }
    }

    JsonObject toJson(StoreMetadata storeMetadata) {
        JsonObject root = ConfigFiles.GSON.toJsonTree(this).getAsJsonObject();
        JsonArray serializedProfiles = root.getAsJsonArray("profiles");
        if (serializedProfiles == null || profiles == null) {
            return root;
        }
        int serializedCount = Math.min(profiles.size(), serializedProfiles.size());
        for (int index = 0; index < serializedCount; index++) {
            ProfileMetadata profile = profiles.get(index);
            JsonElement element = serializedProfiles.get(index);
            if (profile == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject serialized = element.getAsJsonObject();
            LoadedFileState loaded = storeMetadata.profile(profile.fileName());
            serialized.addProperty("loadedFileModifiedAtMillis", loaded.modifiedAtMillis());
            if (loaded.fingerprint() != null) {
                serialized.addProperty("loadedFileFingerprint", loaded.fingerprint());
            }
        }
        return root;
    }

    private static Map<String, LoadedFileState> loadedFiles(JsonObject root) {
        JsonArray serializedProfiles = root.getAsJsonArray("profiles");
        if (serializedProfiles == null) {
            return Map.of();
        }
        LinkedHashMap<String, LoadedFileState> loaded = new LinkedHashMap<>();
        for (JsonElement element : serializedProfiles) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject profile = element.getAsJsonObject();
            String fileName = stringValue(profile.get("fileName"));
            long modifiedAtMillis = longValue(profile.get("loadedFileModifiedAtMillis"));
            String fingerprint = stringValue(profile.get("loadedFileFingerprint"));
            if (ProfileFileNames.comparisonKey(fileName) != null
                    && (modifiedAtMillis > 0L || fingerprint != null)) {
                loaded.put(fileName, LoadedFileState.existing(modifiedAtMillis, fingerprint));
            }
        }
        return Map.copyOf(loaded);
    }

    private static String stringValue(JsonElement element) {
        try {
            return element == null || !element.isJsonPrimitive() ? null : element.getAsString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static long longValue(JsonElement element) {
        try {
            return element == null || !element.isJsonPrimitive()
                    ? 0L : Math.max(0L, element.getAsLong());
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    static final class MetadataBackupException extends RuntimeException {
        private final Path path;

        MetadataBackupException(Path path, Throwable cause) {
            super("Malformed profile metadata could not be backed up: " + path, cause);
            this.path = path;
        }

        Path path() {
            return path;
        }
    }

    record Loaded(ProfileMetadataStore metadata, Map<String, LoadedFileState> profileFiles) {
        Loaded {
            metadata = metadata == null ? new ProfileMetadataStore() : metadata;
            profileFiles = profileFiles == null ? Map.of() : Map.copyOf(profileFiles);
        }
    }

    ProfileMetadataStore copy() {
        ProfileMetadataStore copy = new ProfileMetadataStore();
        copy.version = version;
        copy.profiles = profiles == null ? new ArrayList<>() : new ArrayList<>(profiles);
        return copy;
    }

    void restoreFrom(ProfileMetadataStore snapshot) {
        ProfileMetadataStore restored = snapshot == null ? new ProfileMetadataStore() : snapshot.copy();
        version = restored.version;
        profiles = restored.profiles;
    }

    ProfileMetadata profile(String fileName) {
        String normalized = normalizedFileName(fileName);
        String key = ProfileFileNames.comparisonKey(normalized);
        if (key != null && profiles != null) {
            for (ProfileMetadata profile : profiles) {
                if (profile != null && key.equals(ProfileFileNames.comparisonKey(profile.fileName()))) {
                    return new ProfileMetadata(
                            normalized,
                            profile.basedOn(),
                            profile.createdAtMillis(),
                            profile.lastModifiedAtMillis());
                }
            }
        }
        return ProfileMetadata.empty(normalized);
    }

    void ensure(String fileName, String fallbackBasedOn) {
        put(profile(fileName).ensured(fallbackBasedOn));
    }

    void markCreated(String fileName, long timestamp, String basedOn) {
        put(profile(fileName).markedCreated(timestamp, basedOn));
    }

    void markModified(String fileName, long timestamp) {
        put(profile(fileName).markedModified(timestamp, null));
    }

    void markModified(String fileName, long timestamp, String basedOn) {
        put(profile(fileName).markedModified(timestamp, basedOn));
    }

    void markLoaded(String fileName, long fileModifiedAtMillis, String fallbackBasedOn,
            boolean changedSinceMetadata) {
        put(profile(fileName).markedLoaded(fileModifiedAtMillis, fallbackBasedOn, changedSinceMetadata));
    }

    void copyProfileFrom(String fileName, ProfileMetadataStore source) {
        if (source != null) {
            put(source.profile(fileName));
        }
    }

    void restoreProfileFrom(String fileName, ProfileMetadataStore snapshot) {
        copyProfileFrom(fileName, snapshot);
    }

    String existingFileName(String fileName) {
        String key = ProfileFileNames.comparisonKey(fileName);
        if (key == null || profiles == null) {
            return null;
        }
        for (ProfileMetadata profile : profiles) {
            if (profile != null && key.equals(ProfileFileNames.comparisonKey(profile.fileName()))) {
                return profile.fileName();
            }
        }
        return null;
    }

    void prune(Set<String> profileFiles) {
        Map<String, String> currentFileNames = new LinkedHashMap<>();
        for (String profileFile : profileFiles) {
            String normalized = ProfileFileNames.normalize(profileFile);
            String key = ProfileFileNames.comparisonKey(normalized);
            if (key != null) {
                currentFileNames.putIfAbsent(key, normalized);
            }
        }
        sanitize();
        List<ProfileMetadata> retained = new ArrayList<>();
        for (ProfileMetadata profile : profiles) {
            String currentFileName = currentFileNames.get(
                    ProfileFileNames.comparisonKey(profile.fileName()));
            if (currentFileName != null) {
                retained.add(new ProfileMetadata(currentFileName, profile.basedOn(),
                        profile.createdAtMillis(), profile.lastModifiedAtMillis()));
            }
        }
        profiles = retained;
    }

    private void put(ProfileMetadata replacement) {
        if (replacement == null || replacement.fileName() == null) {
            return;
        }
        if (profiles == null) {
            profiles = new ArrayList<>();
        }
        String key = ProfileFileNames.comparisonKey(replacement.fileName());
        for (int i = 0; i < profiles.size(); i++) {
            ProfileMetadata existing = profiles.get(i);
            if (existing != null && key.equals(ProfileFileNames.comparisonKey(existing.fileName()))) {
                profiles.set(i, replacement);
                return;
            }
        }
        profiles.add(replacement);
    }

    private void sanitize() {
        version = Config.CURRENT_VERSION;
        if (profiles == null) {
            profiles = new ArrayList<>();
            return;
        }

        Set<String> seen = new HashSet<>();
        List<ProfileMetadata> sanitized = new ArrayList<>();
        for (ProfileMetadata profile : profiles) {
            if (profile == null) {
                continue;
            }
            String normalized = ProfileFileNames.normalize(profile.fileName());
            String key = ProfileFileNames.comparisonKey(normalized);
            if (key == null || !seen.add(key)) {
                continue;
            }
            sanitized.add(new ProfileMetadata(normalized, profile.basedOn(),
                    profile.createdAtMillis(), profile.lastModifiedAtMillis())
                    .ensured(Config.defaultProfileBasedOn(normalized)));
        }
        profiles = sanitized;
    }

    private static String normalizedFileName(String fileName) {
        String normalized = ProfileFileNames.normalize(fileName);
        return normalized == null ? "profile" + ProfileFileNames.JSON_SUFFIX : normalized;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof ProfileMetadataStore store
                && version == store.version
                && Objects.equals(profiles, store.profiles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, profiles);
    }
}
