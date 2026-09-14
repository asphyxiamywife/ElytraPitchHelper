package com.asphyxiamywife.elytrapitchhelper.config;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class ProfileFileNames {
    static final String JSON_SUFFIX = ".json";
    static final int MAX_FILE_NAME_LENGTH = 255;

    private ProfileFileNames() {
    }

    static String normalize(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        String normalized = fileName.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }
        if (normalized.isBlank()) {
            return null;
        }
        if (!normalized.toLowerCase(Locale.ROOT).endsWith(JSON_SUFFIX)) {
            normalized += JSON_SUFFIX;
        }
        return normalized;
    }

    static boolean isPortable(String name) {
        if (name == null || name.isBlank() || name.length() > MAX_FILE_NAME_LENGTH
                || name.endsWith(".") || name.endsWith(" ") || isReserved(name)) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char character = name.charAt(i);
            if (character < 32 || "<>:\"/\\|?*".indexOf(character) >= 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isReserved(String name) {
        int dot = name.indexOf('.');
        String stem = (dot < 0 ? name : name.substring(0, dot)).stripTrailing()
                .toUpperCase(Locale.ROOT);
        return Set.of("CON", "PRN", "AUX", "NUL").contains(stem)
                || stem.matches("(?:COM|LPT)[1-9¹²³]");
    }

    static String unique(String name, Set<String> usedFileNames) {
        Set<String> usedKeys = new HashSet<>();
        for (String usedFileName : usedFileNames) {
            String key = comparisonKey(usedFileName);
            if (key != null) {
                usedKeys.add(key);
            }
        }
        String base = slug(name);
        String candidate = candidate(base, "");
        int suffix = 2;
        while (usedKeys.contains(comparisonKey(candidate))) {
            candidate = candidate(base, "-" + suffix);
            suffix++;
        }
        usedFileNames.add(candidate);
        return candidate;
    }

    public static String comparisonKey(String fileName) {
        String normalized = normalize(fileName);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    static String slug(String name) {
        String slug = name == null ? "" : name.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.isBlank()) {
            return "profile";
        }
        return isReserved(slug) ? "profile-" + slug : slug;
    }

    private static String candidate(String base, String suffix) {
        int maxBaseLength = MAX_FILE_NAME_LENGTH - suffix.length() - JSON_SUFFIX.length();
        String boundedBase = base.length() <= maxBaseLength ? base : base.substring(0, maxBaseLength);
        return boundedBase + suffix + JSON_SUFFIX;
    }

    static String displayName(String fileName) {
        String name = normalize(fileName);
        if (name == null) {
            return "Profile";
        }
        name = name.substring(0, name.length() - JSON_SUFFIX.length()).replace('-', ' ').replace('_', ' ');
        String[] words = name.split(" ");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (!word.isBlank()) {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    builder.append(word.substring(1));
                }
            }
        }
        return builder.length() == 0 ? "Profile" : builder.toString();
    }
}
