package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;
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
            selectedFile = profiles.get(activeIndex).fileName();
        }

        Set<String> usedFileNames = new HashSet<>();
        Profile defaults = ProfileDefaults.template();
        for (int i = 0; i < profiles.size(); i++) {
            Profile profile = profiles.get(i);
            if (profile == null) {
                profile = defaults.withName("Profile " + (i + 1));
                profiles.set(i, profile);
            }
            if (profile.name() == null || profile.name().isBlank()) {
                profile = profile.withName("Profile " + (i + 1));
            }

            String fileName = ProfileFileNames.normalize(profile.fileName());
            String fileNameKey = ProfileFileNames.comparisonKey(fileName);
            if (fileName == null || usedFileNames.contains(fileNameKey)) {
                fileName = ProfileFileNames.unique(profile.name(), usedFileNames);
            } else {
                usedFileNames.add(fileNameKey);
            }
            if (!java.util.Objects.equals(profile.fileName(), fileName)) {
                profile = profile.withFileName(fileName);
            }
            profile = profile.sanitized(null, defaults);
            profiles.set(i, profile);
            metadata.ensure(fileName, ProfileDefaults.basedOn(fileName));
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
            activeIndex = MathUtil.clamp(activeIndex, 0, profiles.size() - 1);
        }
        return new Selection(profiles, activeIndex, profiles.get(activeIndex).fileName());
    }

    static int indexOfFile(List<Profile> profiles, String fileName) {
        String fileNameKey = ProfileFileNames.comparisonKey(fileName);
        if (fileNameKey == null || profiles == null) {
            return -1;
        }
        for (int i = 0; i < profiles.size(); i++) {
            Profile profile = profiles.get(i);
            if (profile != null && fileNameKey.equals(ProfileFileNames.comparisonKey(profile.fileName()))) {
                return i;
            }
        }
        return -1;
    }

    static int clampIndex(List<Profile> profiles, int index) {
        if (profiles == null || profiles.isEmpty()) {
            return 0;
        }
        return MathUtil.clamp(index, 0, profiles.size() - 1);
    }

    private static Comparator<Profile> comparator(int sortMode, ProfileMetadataStore metadata) {
        Comparator<String> nullableStrings = Comparator.nullsLast(Comparator.naturalOrder());
        Comparator<Profile> byName = Comparator.comparing(
                        (Profile profile) -> lower(profile.name()), nullableStrings)
                .thenComparing(profile -> lower(profile.fileName()), nullableStrings)
                .thenComparing(Profile::fileName, nullableStrings);
        if (sortMode == Config.PROFILE_SORT_NAME) {
            return byName;
        }
        if (sortMode == Config.PROFILE_SORT_MODIFIED) {
            return Comparator.comparingLong(
                    (Profile profile) -> metadata.profile(profile.fileName()).lastModifiedAtMillis())
                    .reversed()
                    .thenComparing(byName);
        }
        return Comparator.comparingLong(
                (Profile profile) -> metadata.profile(profile.fileName()).createdAtMillis())
                .thenComparing(byName);
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    record Selection(List<Profile> profiles, int activeIndex, String activeFile) {
    }
}
