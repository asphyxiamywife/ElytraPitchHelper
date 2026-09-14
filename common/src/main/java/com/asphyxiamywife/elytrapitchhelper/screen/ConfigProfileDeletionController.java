package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.StagedProfileDelete;
import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ConfigProfileDeletionController {
    interface Host {
        Config config();

        Minecraft minecraftClient();

        boolean deleteMode();

        void setDeleteMode(boolean deleteMode);

        int profileScroll();

        void setProfileScrollDirect(int profileScroll);

        int maxProfileScroll();

        void syncEditingProfileIndex();

        void clearHistory();

        void updateHistoryContextBaseline();

        void saveProfileDeletes(List<String> profileFiles);

        void flushSaveAsync();

        boolean openChildScreen(Screen screen);

        void rebuildWidgets();
    }

    private final Host host;
    private final Set<String> markedFiles = new LinkedHashSet<>();
    private boolean confirmationOpen;

    ConfigProfileDeletionController(Host host) {
        this.host = host;
    }

    boolean hasMarks() {
        return !markedFiles.isEmpty();
    }

    int markCount() {
        return markedFiles.size();
    }

    boolean isMarked(String profileFile) {
        return profileFile != null && markedFiles.contains(profileFile);
    }

    List<String> markedFiles() {
        return List.copyOf(markedFiles);
    }

    void restoreContext(List<String> marked) {
        markedFiles.clear();
        if (marked != null) {
            markedFiles.addAll(marked);
        }
    }

    boolean canMark(String profileFile) {
        return isMarked(profileFile) || resolvableMarkCount() < host.config().profileCount() - 1;
    }

    private int resolvableMarkCount() {
        int resolvable = 0;
        for (String markedFile : markedFiles) {
            if (host.config().profileIndexByFileName(markedFile) >= 0) {
                resolvable++;
            }
        }
        return resolvable;
    }

    void beginDeletion(String profileFile) {
        if (host.config().isReadOnly() || host.config().profileCount() <= 1) {
            return;
        }
        markedFiles.clear();
        if (profileFile != null && host.config().profileIndexByFileName(profileFile) >= 0) {
            markedFiles.add(profileFile);
        }
        host.setDeleteMode(true);
        host.updateHistoryContextBaseline();
        host.rebuildWidgets();
    }

    void toggleMark(String profileFile) {
        if (profileFile == null || host.config().isReadOnly()) {
            return;
        }
        if (markedFiles.remove(profileFile)) {
            host.rebuildWidgets();
            return;
        }
        if (!canMark(profileFile) || host.config().profileIndexByFileName(profileFile) < 0) {
            return;
        }
        markedFiles.add(profileFile);
        host.rebuildWidgets();
    }

    void requestCommit() {
        if (markedFiles.isEmpty()) {
            return;
        }
        confirmationOpen = true;
        Minecraft minecraft = host.minecraftClient();
        FlatConfirmScreen confirmation = new FlatConfirmScreen(confirmed -> {
            confirmationOpen = false;
            minecraft.gui.setScreen((ConfigScreen) host);
            if (confirmed) {
                commit();
            } else {
                host.rebuildWidgets();
            }
        }, Component.translatable("screen.elytrapitchhelper.commit_profile_deletes.title"),
                commitMessage(), commitLabel(), CommonComponents.GUI_CANCEL);
        if (!host.openChildScreen(confirmation)) {
            confirmationOpen = false;
        }
    }

    void requestCancel() {
        if (markedFiles.isEmpty()) {
            cancel();
            host.rebuildWidgets();
            return;
        }
        confirmationOpen = true;
        Minecraft minecraft = host.minecraftClient();
        FlatConfirmScreen confirmation = new FlatConfirmScreen(confirmed -> {
            confirmationOpen = false;
            minecraft.gui.setScreen((ConfigScreen) host);
            if (confirmed) {
                cancel();
            }
            host.rebuildWidgets();
        }, Component.translatable("screen.elytrapitchhelper.cancel_profile_deletes.title"),
                cancelMessage(), Component.translatable("screen.elytrapitchhelper.cancel_profile_deletes.confirm"),
                CommonComponents.GUI_CANCEL);
        if (!host.openChildScreen(confirmation)) {
            confirmationOpen = false;
        }
    }

    void restoreIfAbandoned() {
        if (host.deleteMode() && !confirmationOpen) {
            markedFiles.clear();
            host.setDeleteMode(false);
        }
    }

    void commit() {
        List<Integer> indices = new ArrayList<>();
        for (String profileFile : markedFiles) {
            int index = host.config().profileIndexByFileName(profileFile);
            if (index >= 0) {
                indices.add(index);
            }
        }
        indices.sort(Comparator.reverseOrder());

        List<String> deletedFiles = new ArrayList<>();
        for (int index : indices) {
            StagedProfileDelete deleted = host.config().stageDeleteProfile(index);
            if (deleted != null) {
                deletedFiles.add(deleted.fileName());
            }
        }
        host.syncEditingProfileIndex();
        host.setProfileScrollDirect(MathUtil.clamp(host.profileScroll(), 0, host.maxProfileScroll()));

        host.saveProfileDeletes(deletedFiles);
        deletedFiles.forEach(ScrollMemory::forget);
        markedFiles.clear();
        host.setDeleteMode(false);
        host.flushSaveAsync();
        host.clearHistory();
        host.rebuildWidgets();
    }

    private void cancel() {
        markedFiles.clear();
        host.setDeleteMode(false);
        host.updateHistoryContextBaseline();
    }

    Component commitLabel() {
        int count = markCount();
        return count == 1
                ? Component.translatable("screen.elytrapitchhelper.profile.commit_deletes.one")
                : Component.translatable("screen.elytrapitchhelper.profile.commit_deletes.many", count);
    }

    private Component commitMessage() {
        int count = markCount();
        return count == 1
                ? Component.translatable("screen.elytrapitchhelper.commit_profile_deletes.message.one")
                : Component.translatable("screen.elytrapitchhelper.commit_profile_deletes.message.many", count);
    }

    private Component cancelMessage() {
        int count = markCount();
        return count == 1
                ? Component.translatable("screen.elytrapitchhelper.cancel_profile_deletes.message.one")
                : Component.translatable("screen.elytrapitchhelper.cancel_profile_deletes.message.many", count);
    }
}
