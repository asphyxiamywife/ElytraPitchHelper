package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Config;

import java.util.ArrayDeque;
import java.util.Deque;

final class ConfigNavigation {
    private final Deque<Entry> backStack = new ArrayDeque<>();
    private boolean editingProfile;
    private int editingProfileIndex;
    private String editingProfileFile;

    boolean editingProfile() {
        return editingProfile;
    }

    int editingProfileIndex() {
        return editingProfileIndex;
    }

    String editingProfileFile() {
        return editingProfileFile;
    }

    void openProfiles() {
        editingProfile = false;
        editingProfileFile = null;
    }

    void openEditor(Config config, String profileFile) {
        int index = profileFile == null ? -1 : config.profileIndexByFileName(profileFile);
        openEditor(config, index < 0 ? config.activeProfileIndex : index);
    }

    void openEditor(Config config, int profileIndex) {
        if (config.profileCount() <= 0) {
            return;
        }
        editingProfileIndex = config.clampProfileIndex(profileIndex);
        editingProfileFile = config.profile(editingProfileIndex).fileName();
        editingProfile = true;
    }

    void rememberCurrent() {
        backStack.addLast(new Entry(editingProfile, editingProfileFile));
    }

    void rememberProfiles() {
        backStack.addLast(new Entry(false, null));
    }

    Entry back() {
        return backStack.pollLast();
    }

    void clearBackStack() {
        backStack.clear();
    }

    void restore(boolean editor, String profileFile) {
        editingProfile = editor;
        editingProfileFile = profileFile;
    }

    void sync(Config config) {
        if (config.profileCount() <= 0) {
            editingProfileIndex = -1;
            editingProfileFile = null;
            return;
        }
        if (editingProfileFile != null) {
            int index = config.profileIndexByFileName(editingProfileFile);
            if (index >= 0) {
                editingProfileIndex = index;
                return;
            }
        }
        editingProfileIndex = config.clampProfileIndex(editingProfileIndex);
        if (editingProfile) {
            editingProfileFile = config.profile(editingProfileIndex).fileName();
        }
    }

    record Entry(boolean editor, String profileFile) {}
}
