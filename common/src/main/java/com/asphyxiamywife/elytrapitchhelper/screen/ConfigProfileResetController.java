package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

final class ConfigProfileResetController {
    private final Host host;
    private Config undoSnapshot;
    private Config appliedSnapshot;
    private String undoProfileFile;

    ConfigProfileResetController(Host host) {
        this.host = host;
    }

    void requestReset() {
        requestReset(host.editingProfileFile());
    }

    void requestReset(String profileFile) {
        Config config = host.config();
        int profileIndex = profileFile == null ? -1 : config.profileIndexByFileName(profileFile);
        if (profileIndex < 0) {
            return;
        }
        String profileName = config.profileName(profileIndex);
        host.showConfirmation(new FlatConfirmScreen(confirmed -> completeReset(profileFile, confirmed),
                Component.translatable("screen.elytrapitchhelper.reset_profile.title"),
                Component.translatable("screen.elytrapitchhelper.reset_profile.message", profileName),
                Component.translatable("screen.elytrapitchhelper.reset_profile.confirm"),
                CommonComponents.GUI_CANCEL));
    }

    void completeReset(String profileFile, boolean confirmed) {
        host.returnToConfigScreen();
        if (!confirmed) {
            return;
        }

        Config config = host.config();
        int resolvedIndex = config.profileIndexByFileName(profileFile);
        if (resolvedIndex < 0) {
            host.rebuildWidgets();
            return;
        }
        undoSnapshot = config.copy();
        undoProfileFile = profileFile;
        host.beginHistoryAction("reset-profile");
        config.resetProfileToDefaults(resolvedIndex);
        appliedSnapshot = config.copy();
        host.save();
        host.commitHistoryAction(true, false);
        host.rebuildWidgets();
    }

    void clear() {
        undoSnapshot = null;
        appliedSnapshot = null;
        undoProfileFile = null;
    }

    void reconcile() {
        if (!hasUndo() || !stateMatches(host.config(), appliedSnapshot, undoProfileFile)) {
            clear();
        }
    }

    void undo() {
        if (!hasUndo()) {
            return;
        }
        Config snapshot = undoSnapshot;
        String profileFile = undoProfileFile;
        host.beginHistoryAction("reset-profile");
        clear();
        host.config().restoreProfileStateFrom(snapshot, profileFile);
        host.save();
        host.commitHistoryAction(true, false);
        host.rebuildWidgets();
    }

    boolean hasUndo() {
        return undoSnapshot != null && undoProfileFile != null
                && (host.editingProfileFile() == null
                        || undoProfileFile.equals(host.editingProfileFile()));
    }

    static boolean stateMatches(Config current, Config resetApplied, String profileFile) {
        return current != null && resetApplied != null && profileFile != null
                && current.hasSameProfileState(resetApplied, profileFile);
    }

    interface Host {
        Config config();

        String editingProfileFile();

        void showConfirmation(ConfirmScreen screen);

        void returnToConfigScreen();

        void beginHistoryAction(String actionKey);

        void commitHistoryAction(boolean changed, boolean coalesce);

        void save();

        void rebuildWidgets();
    }
}
