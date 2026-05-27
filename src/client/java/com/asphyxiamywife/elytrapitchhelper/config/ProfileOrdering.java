package com.asphyxiamywife.elytrapitchhelper.config;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class ProfileOrdering {
    private ProfileOrdering() {
    }

    static Selection normalize(List<Profile> profiles, int activeIndex, String activeFile, int sortMode,
            ProfileMetadataStore metadata) {
        if (profiles == null || profiles.isEmpty()) {
            profiles = ProfileDefaults.profiles();
        }

        String selectedFile = activeFile;
        if ((selectedFile == null || selectedFile.isBlank()) && activeIndex >= 0
                && activeIndex < profiles.size() && profiles.get(activeIndex) != null) {
            selectedFile = profiles.get(activeIndex).fileName;
        }

        Set<String> usedFileNames = new HashSet<>();
        Profile defaults = ProfileDefaults.template();
        for (int i = 0; i < profiles.size(); i++) {
            Profile profile = profiles.get(i);
            if (profile == null) {
                profile = defaults.copy();
                profile.name = "Profile " + (i + 1);
                profiles.set(i, profile);
            }
            if (profile.name == null || profile.name.isBlank()) {
                profile.name = "Profile " + (i + 1);
            }

            String fileName = ProfileFileNames.normalize(profile.fileName);
            if (fileName == null || usedFileNames.contains(fileName.toLowerCase(Locale.ROOT))) {
                fileName = ProfileFileNames.unique(profile.name, usedFileNames);
            } else {
                usedFileNames.add(fileName.toLowerCase(Locale.ROOT));
            }
            profile.fileName = fileName;
            profile.sanitize(null, defaults);
            metadata.profile(fileName).ensure(ProfileDefaults.basedOn(fileName));
        }

        return sort(profiles, activeIndex, selectedFile, sortMode, metadata);
    }

    static Selection sort(List<Profile> profiles, int activeIndex, String selectedFile, int sortMode,
            ProfileMetadataStore metadata) {
        if (profiles == null || profiles.isEmpty()) {
            profiles = ProfileDefaults.profiles();
        }

        profiles.sort(comparator(sortMode, metadata));
        int selectedIndex = indexOfFile(profiles, selectedFile);
        if (selectedIndex >= 0) {
            activeIndex = selectedIndex;
        } else {
            activeIndex = Math.max(0, Math.min(activeIndex, profiles.size() - 1));
        }
        return new Selection(profiles, activeIndex, profiles.get(activeIndex).fileName);
    }

    static int indexOfFile(List<Profile> profiles, String fileName) {
        if (fileName == null || fileName.isBlank() || profiles == null) {
            return -1;
        }
        for (int i = 0; i < profiles.size(); i++) {
            if (fileName.equalsIgnoreCase(profiles.get(i).fileName)) {
                return i;
            }
        }
        return -1;
    }

    static int clampIndex(List<Profile> profiles, int index) {
        if (profiles == null || profiles.isEmpty()) {
            return 0;
        }
        return Math.max(0, Math.min(index, profiles.size() - 1));
    }

    private static Comparator<Profile> comparator(int sortMode, ProfileMetadataStore metadata) {
        if (sortMode == Config.PROFILE_SORT_NAME) {
            return Comparator.comparing((Profile profile) -> profile.name.toLowerCase(Locale.ROOT))
                    .thenComparing(profile -> profile.fileName.toLowerCase(Locale.ROOT));
        }
        if (sortMode == Config.PROFILE_SORT_MODIFIED) {
            return Comparator.comparingLong((Profile profile) -> metadata.profile(profile.fileName).lastModifiedAtMillis)
                    .reversed()
                    .thenComparing(profile -> profile.name.toLowerCase(Locale.ROOT))
                    .thenComparing(profile -> profile.fileName.toLowerCase(Locale.ROOT));
        }
        return Comparator.comparingLong((Profile profile) -> metadata.profile(profile.fileName).createdAtMillis)
                .thenComparing(profile -> profile.name.toLowerCase(Locale.ROOT))
                .thenComparing(profile -> profile.fileName.toLowerCase(Locale.ROOT));
    }

    record Selection(List<Profile> profiles, int activeIndex, String activeFile) {
    }
}
