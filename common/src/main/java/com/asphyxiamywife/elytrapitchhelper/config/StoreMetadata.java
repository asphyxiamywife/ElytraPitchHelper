package com.asphyxiamywife.elytrapitchhelper.config;

import java.util.LinkedHashMap;
import java.util.Map;

record StoreMetadata(LoadedFileState mainConfig, Map<String, LoadedFileState> profiles) {
    static final StoreMetadata EMPTY = new StoreMetadata(LoadedFileState.NEVER_LOADED, Map.of());

    StoreMetadata {
        mainConfig = mainConfig == null ? LoadedFileState.NEVER_LOADED : mainConfig;
        LinkedHashMap<String, LoadedFileState> normalized = new LinkedHashMap<>();
        if (profiles != null) {
            profiles.forEach((fileName, state) -> {
                String key = ProfileFileNames.comparisonKey(fileName);
                if (key != null && state != null) {
                    normalized.put(key, state);
                }
            });
        }
        profiles = Map.copyOf(normalized);
    }

    LoadedFileState profile(String fileName) {
        String key = ProfileFileNames.comparisonKey(fileName);
        return key == null ? LoadedFileState.NEVER_LOADED
                : profiles.getOrDefault(key, LoadedFileState.NEVER_LOADED);
    }

    StoreMetadata withMainConfig(LoadedFileState state) {
        return new StoreMetadata(state, profiles);
    }

    StoreMetadata withProfile(String fileName, LoadedFileState state) {
        String key = ProfileFileNames.comparisonKey(fileName);
        if (key == null) {
            return this;
        }
        LinkedHashMap<String, LoadedFileState> updated = new LinkedHashMap<>(profiles);
        updated.put(key, state == null ? LoadedFileState.NEVER_LOADED : state);
        return new StoreMetadata(mainConfig, updated);
    }

    StoreMetadata withProfileFrom(StoreMetadata source, String fileName) {
        return source == null ? this : withProfile(fileName, source.profile(fileName));
    }

    StoreMetadata withProfiles(Map<String, LoadedFileState> states) {
        return new StoreMetadata(mainConfig, states);
    }
}
