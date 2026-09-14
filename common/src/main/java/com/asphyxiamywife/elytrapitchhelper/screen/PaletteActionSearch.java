package com.asphyxiamywife.elytrapitchhelper.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class PaletteActionSearch {
    private static final int NO_MATCH = Integer.MIN_VALUE;
    private static final int TITLE_WEIGHT = 700;
    private static final int KEYWORD_WEIGHT = 280;
    private static final int CATEGORY_WEIGHT = 180;
    private static final int ID_WEIGHT = 90;

    private PaletteActionSearch() {
    }

    static List<PaletteAction> search(List<PaletteAction> actions, String query) {
        String normalizedQuery = TextMatch.normalize(query);
        return actions.stream()
                .map(action -> new RankedAction(action, score(action, normalizedQuery)))
                .filter(result -> result.score() > NO_MATCH)
                .sorted(Comparator
                        .comparingInt(RankedAction::score).reversed()
                        .thenComparing(result -> result.action().title().getString(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(result -> result.action().id().toString()))
                .map(RankedAction::action)
                .toList();
    }

    private static int score(PaletteAction action, String normalizedQuery) {
        if (normalizedQuery.isEmpty()) {
            return action.priority();
        }

        List<SearchField> fields = searchFields(action);
        int score = action.priority();
        String[] tokens = normalizedQuery.split("\\s+");
        if (tokens.length > 1) {
            int phraseScore = bestFieldScore(fields, normalizedQuery);
            if (phraseScore > NO_MATCH) {
                score += phraseScore + 500;
            }
        }

        for (String token : tokens) {
            int tokenScore = bestFieldScore(fields, token);
            if (tokenScore == NO_MATCH) {
                return NO_MATCH;
            }
            score += tokenScore;
        }
        return score;
    }

    private static List<SearchField> searchFields(PaletteAction action) {
        List<SearchField> fields = new ArrayList<>();
        fields.add(new SearchField(action.title().getString(), TITLE_WEIGHT));
        fields.add(new SearchField(action.category().getString(), CATEGORY_WEIGHT));
        fields.add(new SearchField(action.id().toString(), ID_WEIGHT));
        for (String keyword : action.keywords()) {
            fields.add(new SearchField(keyword, KEYWORD_WEIGHT));
        }
        return fields;
    }

    private static int bestFieldScore(List<SearchField> fields, String query) {
        int best = NO_MATCH;
        for (SearchField field : fields) {
            int score = fieldScore(field.text(), query);
            if (score > NO_MATCH) {
                best = Math.max(best, score + field.weight());
            }
        }
        return best;
    }

    private static int fieldScore(String text, String query) {
        if (text.isEmpty() || query.isEmpty()) {
            return NO_MATCH;
        }
        if (text.equals(query)) {
            return 1600 - lengthPenalty(text, query);
        }
        if (text.startsWith(query)) {
            return 1300 - lengthPenalty(text, query);
        }

        int wordStart = wordStartIndexOf(text, query);
        if (wordStart >= 0) {
            int base = isWordEnd(text, wordStart + query.length()) ? 1120 : 980;
            return base - indexPenalty(wordStart) - lengthPenalty(text, query);
        }

        int containedAt = text.indexOf(query);
        if (containedAt >= 0) {
            return 780 - indexPenalty(containedAt) - lengthPenalty(text, query);
        }

        int acronymScore = acronymScore(text, query);
        return acronymScore > NO_MATCH ? acronymScore : fuzzyScore(text, query);
    }

    private static int wordStartIndexOf(String text, String query) {
        int index = text.indexOf(query);
        while (index >= 0) {
            if (index == 0 || text.charAt(index - 1) == ' ') {
                return index;
            }
            index = text.indexOf(query, index + 1);
        }
        return -1;
    }

    private static boolean isWordEnd(String text, int index) {
        return index >= text.length() || text.charAt(index) == ' ';
    }

    private static int acronymScore(String text, String query) {
        String acronym = acronym(text);
        if (acronym.isEmpty()) {
            return NO_MATCH;
        }
        if (acronym.equals(query)) {
            return 900;
        }
        if (acronym.startsWith(query)) {
            return 760 - lengthPenalty(acronym, query);
        }
        int containedAt = acronym.indexOf(query);
        return containedAt >= 0 ? 620 - indexPenalty(containedAt) : NO_MATCH;
    }

    private static String acronym(String text) {
        StringBuilder acronym = new StringBuilder();
        boolean wordStart = true;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                wordStart = true;
            } else if (wordStart) {
                acronym.append(c);
                wordStart = false;
            }
        }
        return acronym.toString();
    }

    private static int fuzzyScore(String text, String query) {
        if (query.length() > text.length()) {
            return NO_MATCH;
        }

        int queryIndex = 0;
        int firstMatch = -1;
        int lastMatch = -1;
        int gaps = 0;
        int run = 0;
        int bestRun = 0;
        int wordStartHits = 0;
        for (int i = 0; i < text.length() && queryIndex < query.length(); i++) {
            if (text.charAt(i) != query.charAt(queryIndex)) {
                continue;
            }
            if (firstMatch < 0) {
                firstMatch = i;
            }
            if (lastMatch >= 0) {
                if (i == lastMatch + 1) {
                    run++;
                } else {
                    gaps += i - lastMatch - 1;
                    run = 1;
                }
            } else {
                run = 1;
            }
            if (i == 0 || text.charAt(i - 1) == ' ') {
                wordStartHits++;
            }
            bestRun = Math.max(bestRun, run);
            lastMatch = i;
            queryIndex++;
        }
        if (queryIndex < query.length()) {
            return NO_MATCH;
        }

        int span = lastMatch - firstMatch + 1;
        int score = 500 + wordStartHits * 45 + bestRun * 35 - firstMatch * 6 - gaps * 4
                - (span - query.length()) * 5;
        return Math.max(80, score);
    }

    private static int indexPenalty(int index) {
        return Math.min(220, index * 8);
    }

    private static int lengthPenalty(String text, String query) {
        return Math.min(120, Math.max(0, text.length() - query.length()) / 2);
    }

    private record RankedAction(PaletteAction action, int score) {
    }

    private record SearchField(String text, int weight) {
        private SearchField {
            text = TextMatch.normalize(text);
        }
    }
}
