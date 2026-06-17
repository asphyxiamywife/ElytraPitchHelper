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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class ProfileMetadataStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModConstants.MOD_ID);

    int version = Config.CURRENT_VERSION;
    List<ProfileMetadata> profiles = new ArrayList<>();

    static ProfileMetadataStore load() {
        Path path = Config.getProfileMetadataPath();
        if (!Files.exists(path)) {
            return new ProfileMetadataStore();
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            ProfileMetadataStore store = ConfigFiles.GSON.fromJson(reader, ProfileMetadataStore.class);
            if (store == null) {
                return new ProfileMetadataStore();
            }
            store.sanitize();
            return store;
        } catch (IOException | JsonParseException | IllegalStateException e) {
            LOGGER.warn("Failed to load internal profile metadata {}, using fresh metadata", path, e);
            return new ProfileMetadataStore();
        }
    }

    ProfileMetadataStore copy() {
        ProfileMetadataStore copy = new ProfileMetadataStore();
        copy.version = version;
        if (profiles != null) {
            copy.profiles = new ArrayList<>();
            for (ProfileMetadata profile : profiles) {
                copy.profiles.add(profile.copy());
            }
        }
        return copy;
    }

    ProfileMetadata profile(String fileName) {
        sanitize();
        String normalized = ProfileFileNames.normalize(fileName);
        if (normalized == null) {
            normalized = "profile" + ProfileFileNames.JSON_SUFFIX;
        }
        for (ProfileMetadata profile : profiles) {
            if (normalized.equalsIgnoreCase(profile.fileName)) {
                return profile;
            }
        }
        ProfileMetadata profile = new ProfileMetadata();
        profile.fileName = normalized;
        profiles.add(profile);
        return profile;
    }

    void prune(Set<String> profileFiles) {
        sanitize();
        profiles.removeIf(profile -> profile.fileName == null
                || !profileFiles.contains(profile.fileName.toLowerCase(Locale.ROOT)));
    }

    private void sanitize() {
        version = Config.CURRENT_VERSION;
        if (profiles == null) {
            profiles = new ArrayList<>();
            return;
        }

        Set<String> seen = new HashSet<>();
        profiles.removeIf(profile -> {
            if (profile == null) {
                return true;
            }
            String normalized = ProfileFileNames.normalize(profile.fileName);
            if (normalized == null) {
                return true;
            }
            String key = normalized.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                return true;
            }
            profile.fileName = normalized;
            profile.sanitize(Config.defaultProfileBasedOn(normalized));
            return false;
        });
    }
}
