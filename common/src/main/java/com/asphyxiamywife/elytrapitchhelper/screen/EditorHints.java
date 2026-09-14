package com.asphyxiamywife.elytrapitchhelper.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

final class EditorHints {
    private static final int SEARCH_WEIGHT_UNUSED = 50;
    private static final int SEARCH_WEIGHT_USED = 15;

    enum Hint {
        SEARCH_SHORTCUT("screen.elytrapitchhelper.config.search_hint"),
        SHIFT_SCROLL("tip.elytrapitchhelper.shift_scroll"),
        FOLD_SECTION("tip.elytrapitchhelper.fold_section"),
        EXPAND_ALL("tip.elytrapitchhelper.expand_all"),
        CYCLE_BACKWARDS("tip.elytrapitchhelper.cycle_backwards"),
        RESET_SETTING("tip.elytrapitchhelper.reset_setting"),
        UNDO("tip.elytrapitchhelper.undo");

        final String translationKey;

        Hint(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    private EditorHints() {
    }

    static Hint pick(boolean usedSearch, Hint previous, RandomGenerator random) {
        int searchWeight = usedSearch ? SEARCH_WEIGHT_USED : SEARCH_WEIGHT_UNUSED;
        if (previous != Hint.SEARCH_SHORTCUT && random.nextInt(100) < searchWeight) {
            return Hint.SEARCH_SHORTCUT;
        }

        List<Hint> candidates = new ArrayList<>();
        for (Hint hint : Hint.values()) {
            if (hint != Hint.SEARCH_SHORTCUT && hint != previous) {
                candidates.add(hint);
            }
        }
        if (candidates.isEmpty()) {
            return Hint.SEARCH_SHORTCUT;
        }
        return candidates.get(random.nextInt(candidates.size()));
    }
}
