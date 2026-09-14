package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

final class ProfileListSelectionController {
    private final Supplier<Config> config;
    private final BooleanSupplier deleteMode;
    private final IntConsumer scrollIntoView;
    private final Runnable rebuild;

    private String selectedProfileFile;
    private ProfileRowControl.Pending pendingRowFocus;

    ProfileListSelectionController(Supplier<Config> config, BooleanSupplier deleteMode,
            IntConsumer scrollIntoView, Runnable rebuild) {
        this.config = config;
        this.deleteMode = deleteMode;
        this.scrollIntoView = scrollIntoView;
        this.rebuild = rebuild;
    }

    String selectedFile() {
        return selectedProfileFile;
    }

    void restore(String profileFile) {
        selectedProfileFile = profileFile;
    }

    ProfileListPlan plan() {
        int selectedIndex = deleteMode.getAsBoolean() || selectedProfileFile == null
                ? -1
                : config.get().profileIndexByFileName(selectedProfileFile);
        return ProfileListPlan.of(config.get().profileCount(), selectedIndex);
    }

    ProfileRowControl.Pending takePendingFocus() {
        ProfileRowControl.Pending pending = pendingRowFocus;
        pendingRowFocus = null;
        return pending;
    }

    private void toggleSelectedProfile(String profileFile) {
        select(profileFile != null && profileFile.equals(selectedProfileFile)
                ? null : profileFile);
    }

    void select(String profileFile) {
        if (java.util.Objects.equals(profileFile, selectedProfileFile)) {
            return;
        }
        selectedProfileFile = profileFile;
        keepSelectionOnScreen();
        rebuild.run();
    }

    private void keepSelectionOnScreen() {
        ProfileListPlan plan = plan();
        if (!plan.stripOpen()) {
            return;
        }
        scrollIntoView.accept(plan.selectedIndex());
        scrollIntoView.accept(plan.stripSlot());
    }

    boolean selectAt(ProfileListLayout layout, double mouseX, double mouseY) {
        int slot = profileSlotAt(layout, mouseX, mouseY);
        if (slot < 0) {
            return false;
        }
        ProfileListPlan plan = plan();
        int profileIndex = plan.profileAtSlot(slot);
        if (profileIndex < 0) {
            return plan.isStripSlot(slot);
        }
        toggleSelectedProfile(config.get().profile(profileIndex).fileName());
        return true;
    }

    private int profileSlotAt(ProfileListLayout layout, double x, double y) {
        if (x < layout.startX() || x >= layout.startX() + layout.listWidth()) {
            return -1;
        }
        if (y < layout.startY() || y >= layout.startY() + layout.viewportHeight()) {
            return -1;
        }
        return layout.scrollRow() + (int) ((y - layout.startY()) / ConfigScreen.ROW_HEIGHT);
    }

    void focus(AbstractWidget widget, ProfileListLayout layout) {
        int slot = profileSlotAt(layout, widget.getX(), widget.getY());
        int profileIndex = slot < 0 ? -1 : plan().profileAtSlot(slot);
        if (profileIndex < 0) {
            return;
        }
        String profileFile = config.get().profile(profileIndex).fileName();
        if (profileFile.equals(selectedProfileFile)) {
            return;
        }
        pendingRowFocus = pendingFocusFor(widget, profileFile, rowControlAt(widget.getX(), layout));
        select(profileFile);
    }

    private ProfileRowControl.Pending pendingFocusFor(AbstractWidget widget, String profileFile,
            ProfileRowControl control) {
        if (control == null) {
            return null;
        }
        return control == ProfileRowControl.NAME && widget instanceof EditBox box
                ? new ProfileRowControl.Pending(profileFile, control, box.getCursorPosition())
                : ProfileRowControl.Pending.of(profileFile, control);
    }

    private ProfileRowControl rowControlAt(int x, ProfileListLayout layout) {
        ProfileRowLayout row = ProfileRowLayout.of(layout.startX(), layout.listWidth(), false);
        if (x == row.activeX()) {
            return ProfileRowControl.ACTIVE;
        }
        if (x == row.nameX()) {
            return ProfileRowControl.NAME;
        }
        return x == row.actionX() ? ProfileRowControl.ACTION : null;
    }

}
