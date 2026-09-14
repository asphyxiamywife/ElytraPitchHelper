package com.asphyxiamywife.elytrapitchhelper.screen;

import java.util.HashMap;
import java.util.Map;

final class ScrollMemory {
    private static final Map<String, Integer> EDITOR_SCROLLS = new HashMap<>();

    private static int profileListScroll;

    private ScrollMemory() {
    }

    static int profileList() {
        return profileListScroll;
    }

    static void rememberProfileList(int scroll) {
        profileListScroll = Math.max(0, scroll);
    }

    static int editor(String profileFile) {
        if (profileFile == null) {
            return 0;
        }
        return EDITOR_SCROLLS.getOrDefault(profileFile, 0);
    }

    static void rememberEditor(String profileFile, int scroll) {
        if (profileFile == null) {
            return;
        }
        EDITOR_SCROLLS.put(profileFile, Math.max(0, scroll));
    }

    static void forget(String profileFile) {
        if (profileFile != null) {
            EDITOR_SCROLLS.remove(profileFile);
        }
    }

}
