package com.asphyxiamywife.elytrapitchhelper.config;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class ProfileNameAllocator {
    private ProfileNameAllocator() {
    }

    static Set<String> usedFileNames(List<Profile> profiles) {
        Set<String> used = new HashSet<>();
        for (Profile profile : profiles) {
            if (profile.fileName != null) {
                used.add(profile.fileName.toLowerCase(Locale.ROOT));
            }
        }
        return used;
    }

    static String uniqueName(List<Profile> profiles, String baseName) {
        return uniqueFromBase(usedNames(profiles), baseName);
    }

    static String uniqueCopyName(List<Profile> profiles, String originalName) {
        String base = (originalName == null || originalName.isBlank() ? "Profile" : originalName) + " copy";
        return uniqueFromBase(usedNames(profiles), base);
    }

    private static Set<String> usedNames(List<Profile> profiles) {
        Set<String> usedNames = new HashSet<>();
        for (Profile profile : profiles) {
            usedNames.add(profile.name.toLowerCase(Locale.ROOT));
        }
        return usedNames;
    }

    private static String uniqueFromBase(Set<String> usedNames, String baseName) {
        String name = baseName;
        int suffix = 2;
        while (usedNames.contains(name.toLowerCase(Locale.ROOT))) {
            name = baseName + " " + suffix;
            suffix++;
        }
        return name;
    }
}
