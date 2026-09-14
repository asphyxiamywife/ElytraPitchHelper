package com.asphyxiamywife.elytrapitchhelper.screen;

import java.text.Normalizer;
import java.util.Locale;

final class TextMatch {
    private TextMatch() {
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }

        String folded = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        StringBuilder normalized = new StringBuilder(folded.length());
        boolean separatorPending = false;
        for (int index = 0; index < folded.length(); ) {
            int codePoint = folded.codePointAt(index);
            index += Character.charCount(codePoint);
            if (!Character.isLetterOrDigit(codePoint)) {
                separatorPending = true;
                continue;
            }
            if (separatorPending && !normalized.isEmpty()) {
                normalized.append(' ');
            }
            separatorPending = false;
            normalized.appendCodePoint(codePoint);
        }
        return normalized.toString();
    }

    static boolean matchesAllTokens(String haystack, String query) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isEmpty()) {
            return true;
        }
        String normalizedHaystack = normalize(haystack);
        for (String token : normalizedQuery.split("\\s+")) {
            if (!normalizedHaystack.contains(token)) {
                return false;
            }
        }
        return true;
    }

    static String slug(String value) {
        return normalize(value).replace(' ', '-');
    }
}
