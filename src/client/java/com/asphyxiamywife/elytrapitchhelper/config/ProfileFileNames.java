package com.asphyxiamywife.elytrapitchhelper.config;

import java.util.Locale;
import java.util.Set;

final class ProfileFileNames {
    static final String JSON_SUFFIX = ".json";

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

    static String unique(String name, Set<String> usedFileNames) {
        String base = slug(name);
        String candidate = base + JSON_SUFFIX;
        int suffix = 2;
        while (usedFileNames.contains(candidate.toLowerCase(Locale.ROOT))) {
            candidate = base + "-" + suffix + JSON_SUFFIX;
            suffix++;
        }
        usedFileNames.add(candidate.toLowerCase(Locale.ROOT));
        return candidate;
    }

    static String slug(String name) {
        String slug = name == null ? "" : name.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.isBlank() ? "profile" : slug;
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
