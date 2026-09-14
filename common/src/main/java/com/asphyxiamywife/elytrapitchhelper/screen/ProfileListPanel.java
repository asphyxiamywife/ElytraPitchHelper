package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.FlatButton;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.ActiveProfileButton;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.CycleRow;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.RowActionButton;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.RowBackdrop;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.ToggleRow;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

final class ProfileListPanel {
    private static final int HEADER_Y = 38;
    private static final int STRIP_DUPLICATE = 0;
    private static final int STRIP_JSON = 1;
    private static final int STRIP_RESET = 2;
    private static final int STRIP_DELETE = 3;

    private final Host host;
    private ProfileRowControl.Pending pendingFocus;

    ProfileListPanel(Host host) {
        this.host = host;
    }

    void init() {
        pendingFocus = host.pendingRowFocus();
        ProfileListPlan plan = host.profileListPlan();
        ProfileListLayout layout = layout(plan);
        host.setProfileScroll(layout.scroll());

        if (!host.deleteMode()) {
            addHeader(layout.startX(), layout.listWidth(), layout.contentOffset());
        }
        addRows(plan, layout);
        if (layout.needsScrollbar()) {
            host.addNavigationWidget(ProfileScrollBar.forProfiles(layout.scrollbarX(), layout.startY(),
                    net.minecraft.client.gui.components.AbstractScrollArea.SCROLLBAR_WIDTH,
                    layout.visibleRows() * ConfigScreen.ROW_HEIGHT,
                    plan.slotCount(), host.profileScroll(),
                    host::profileScrollbarDragging));
        }
        if (host.deleteMode()) {
            addDeleteModeFooter(layout.contentWidth());
        } else {
            addNormalFooter(layout.contentWidth());
        }
    }

    private ProfileListLayout layout(ProfileListPlan plan) {
        return ProfileListLayout.of(host.screenWidth(), host.screenHeight(), plan.slotCount(),
                host.config().isReadOnly(), host.profileScroll());
    }

    void extractBackdrop(GuiGraphicsExtractor context) {
        ProfileListPlan plan = host.profileListPlan();
        ProfileListLayout layout = layout(plan);
        int x = layout.startX();
        int width = layout.listWidth();
        int headerY = HEADER_Y + layout.contentOffset();
        int headerBottom = headerY + ConfigScreen.ROW_HEIGHT + ConfigScreen.CONTROL_HEIGHT;
        PanelBackdrop.paintBand(context, x, width, headerY - PanelBackdrop.ROW_BLEED,
                headerBottom + PanelBackdrop.ROW_BLEED, true);
        if (host.deleteMode()) {
            context.centeredText(host.font(),
                    Component.translatable("screen.elytrapitchhelper.profile.delete_prompt"),
                    x + width / 2, headerY + (headerBottom - headerY - host.font().lineHeight) / 2,
                    0xFFFFFFFF);
        }

        int listBottom = layout.startY() + layout.visibleRows() * ConfigScreen.ROW_HEIGHT;
        if (layout.visibleRows() > 0) {
            PanelBackdrop.paintBand(context, x, width, layout.startY() - PanelBackdrop.ROW_BLEED,
                    listBottom - ConfigScreen.ROW_HEIGHT + ConfigScreen.CONTROL_HEIGHT
                            + PanelBackdrop.ROW_BLEED,
                    false);
        }
    }

    void extractChevrons(GuiGraphicsExtractor context) {
        if (host.deleteMode()) {
            return;
        }
        ProfileListPlan plan = host.profileListPlan();
        ProfileListLayout layout = layout(plan);
        ProfileRowLayout row = ProfileRowLayout.of(layout.startX(), layout.listWidth(), false);
        int firstSlot = layout.scrollRow();
        for (int index = 0; index < layout.visibleRows(); index++) {
            int slot = firstSlot + index;
            int profileIndex = plan.profileAtSlot(slot);
            if (profileIndex < 0) {
                continue;
            }
            ProfileChevron.paint(context, row, layout.startY() + index * ConfigScreen.ROW_HEIGHT,
                    plan.stripOpen() && profileIndex == plan.selectedIndex());
        }
    }

    private void addHeader(int startX, int listWidth, int contentOffset) {
        int headerY = HEADER_Y + contentOffset;
        CycleRow sortRow = new CycleRow(Component.translatable("option.elytrapitchhelper.profile_sort"),
                host.font(), 0, host::profileSortValue,
                () -> cycleProfileSort(true), () -> cycleProfileSort(false));
        sortRow.setPosition(startX, headerY);
        sortRow.setSize(listWidth, ConfigScreen.CONTROL_HEIGHT);
        host.addWidget(host.tooltip(sortRow, "tooltip.elytrapitchhelper.profile.sort"));

        ToggleRow enabledRow = new ToggleRow(Component.translatable("option.elytrapitchhelper.enabled"),
                host.font(), 0, () -> host.config().enabled, value -> {
                    host.refreshConfigSnapshot();
                    host.beginHistoryAction("enabled");
                    host.config().enabled = value;
                    host.save();
                    host.commitHistoryAction(true, false);
                }, CommonComponents.OPTION_ON, CommonComponents.OPTION_OFF);
        enabledRow.setPosition(startX, headerY + ConfigScreen.ROW_HEIGHT);
        enabledRow.setSize(listWidth, ConfigScreen.CONTROL_HEIGHT);
        host.addWidget(host.tooltip(enabledRow, "tooltip.elytrapitchhelper.enabled"));
    }

    private void cycleProfileSort(boolean forward) {
        host.refreshConfigSnapshot();
        host.beginHistoryAction("profile-sort");
        if (forward) {
            host.config().cycleProfileSortMode();
        } else {
            host.config().cycleProfileSortModeBackward();
        }
        host.setProfileScroll(0);
        host.save();
        host.commitHistoryAction(true, false);
        host.rebuildWidgets();
    }

    private void addRows(ProfileListPlan plan, ProfileListLayout layout) {
        ProfileRowLayout row = ProfileRowLayout.of(layout.startX(), layout.listWidth(), host.deleteMode());
        int firstSlot = layout.scrollRow();
        for (int index = 0; index < layout.visibleRows(); index++) {
            int slot = firstSlot + index;
            int y = layout.startY() + index * ConfigScreen.ROW_HEIGHT;
            int profileIndex = plan.profileAtSlot(slot);
            int partnerY = partnerY(plan, slot, y);
            host.addWidget(new RowBackdrop(layout.startX(), y, layout.listWidth(),
                    ConfigScreen.CONTROL_HEIGHT, () -> focusInRow(y) || focusInRow(partnerY)));

            if (profileIndex < 0) {
                addStripActions(row, y, plan);
                continue;
            }

            String profileFile = host.config().profile(profileIndex).fileName();
            addActiveButton(row, y, profileIndex, profileFile);
            addProfileName(row, y, profileIndex, profileFile);
            if (host.deleteMode()) {
                addMarkButton(row, y, profileFile);
            } else {
                addEditButton(row, y, profileFile);
            }
        }
    }

    private int partnerY(ProfileListPlan plan, int slot, int y) {
        if (!plan.stripOpen()) {
            return y;
        }
        if (plan.isStripSlot(slot)) {
            return y - ConfigScreen.ROW_HEIGHT;
        }
        return slot == plan.selectedIndex() ? y + ConfigScreen.ROW_HEIGHT : y;
    }

    private boolean focusInRow(int rowY) {
        AbstractWidget focused = host.focusedWidget();
        return focused != null && focused.getY() == rowY;
    }

    private void addActiveButton(ProfileRowLayout row, int y, int profileIndex, String profileFile) {
        boolean isActive = host.config().isActiveProfile(profileIndex);
        ActiveProfileButton activeButton = new ActiveProfileButton(row.activeX(), y, row.activeWidth(),
                ConfigScreen.CONTROL_HEIGHT, isActive,
                Component.translatable("screen.elytrapitchhelper.profile.active",
                        host.config().profileName(profileIndex)),
                button -> {
                    int resolvedIndex = host.refreshConfigSnapshot(profileFile);
                    if (resolvedIndex < 0) {
                        host.rebuildWidgets();
                        return;
                    }
                    if (host.config().isActiveProfile(resolvedIndex)) {
                        return;
                    }
                    host.beginHistoryAction("active-profile");
                    host.config().selectProfile(resolvedIndex);
                    host.save();
                    host.commitHistoryAction(true, false);
                    host.rebuildWidgets();
                });
        activeButton.active = !host.deleteMode();
        host.addWidget(host.tooltip(activeButton, isActive
                ? "tooltip.elytrapitchhelper.profile.active_current"
                : "tooltip.elytrapitchhelper.profile.active"));
        restoreFocus(activeButton, profileFile, ProfileRowControl.ACTIVE);
    }

    private void addProfileName(ProfileRowLayout row, int y, int profileIndex, String profileFile) {
        String profileName = host.config().profileName(profileIndex);
        EditBox nameBox = new MarqueeEditBox(host.font(), row.nameX(), y, row.nameWidth(),
                ConfigScreen.CONTROL_HEIGHT, Component.translatable("screen.elytrapitchhelper.profile.name"),
                host::breakHistoryCoalescing);
        nameBox.setMaxLength(Math.max(ConfigScreen.PROFILE_NAME_MAX_LENGTH, profileName.length()));
        nameBox.setValue(profileName);
        nameBox.setResponder(value -> {
            if (!value.isBlank()) {
                int resolvedIndex = host.refreshConfigSnapshot(profileFile);
                if (resolvedIndex < 0) {
                    host.rebuildWidgets();
                    return;
                }
                if (value.equals(host.config().profileName(resolvedIndex))) {
                    return;
                }
                host.beginHistoryAction("profile-name:" + profileFile);
                host.config().setProfileName(resolvedIndex, value);
                host.save();
                host.commitHistoryAction(true, true);
            }
        });
        nameBox.active = !host.deleteMode();
        nameBox.setTooltip(host.profileMetadataTooltip(profileIndex));
        nameBox.setTooltipDelay(ConfigScreen.TOOLTIP_DELAY);
        host.addWidget(nameBox);
        restoreFocus(nameBox, profileFile, ProfileRowControl.NAME);
    }

    private void addEditButton(ProfileRowLayout row, int y, String profileFile) {
        RowActionButton editButton = new RowActionButton(row.actionX(), y, row.actionWidth(),
                ConfigScreen.CONTROL_HEIGHT,
                Component.translatable("screen.elytrapitchhelper.profile.edit"), host.font(), () -> {
                    int resolvedIndex = host.refreshConfigSnapshot(profileFile);
                    if (resolvedIndex < 0) {
                        host.rebuildWidgets();
                        return;
                    }
                    host.editProfile(resolvedIndex);
                });
        host.addNavigationWidget(host.tooltip(editButton, "tooltip.elytrapitchhelper.profile.edit"));
        restoreFocus(editButton, profileFile, ProfileRowControl.ACTION);
    }

    private void addMarkButton(ProfileRowLayout row, int y, String profileFile) {
        boolean marked = host.isProfileMarkedForDelete(profileFile);
        RowActionButton markButton = new RowActionButton(row.actionX(), y, row.actionWidth(),
                ConfigScreen.CONTROL_HEIGHT,
                Component.translatable(marked
                        ? "screen.elytrapitchhelper.profile.marked_delete"
                        : "screen.elytrapitchhelper.profile.mark_delete"),
                host.font(), () -> host.toggleProfileDeleteMark(profileFile));
        markButton.active = marked || host.canMarkProfileForDelete(profileFile);
        host.addWidget(host.tooltip(markButton, marked
                ? "tooltip.elytrapitchhelper.profile.unmark_delete"
                : "tooltip.elytrapitchhelper.profile.mark_delete"));
        restoreFocus(markButton, profileFile, ProfileRowControl.ACTION);
    }

    private void addStripActions(ProfileRowLayout row, int y, ProfileListPlan plan) {
        Config config = host.config();
        int profileIndex = plan.selectedIndex();
        if (profileIndex < 0 || profileIndex >= config.profileCount()) {
            return;
        }
        String profileFile = config.profile(profileIndex).fileName();
        ProfileStripLayout strip = ProfileStripLayout.of(row.stripX(), row.stripWidth());
        boolean wide = strip.wideLabels();

        host.addWidget(host.tooltip(stripButton(strip, STRIP_DUPLICATE, y,
                wide ? "screen.elytrapitchhelper.profile.duplicate"
                        : "screen.elytrapitchhelper.profile.copy",
                () -> duplicateProfile(profileFile)),
                "tooltip.elytrapitchhelper.profile.duplicate"));

        host.addNavigationWidget(host.tooltip(stripButton(strip, STRIP_JSON, y,
                "screen.elytrapitchhelper.profile.json",
                () -> {
                    int resolvedIndex = host.refreshConfigSnapshot(profileFile);
                    if (resolvedIndex >= 0) {
                        host.openProfileJson(resolvedIndex);
                    } else {
                        host.rebuildWidgets();
                    }
                }), "tooltip.elytrapitchhelper.profile.open_json"));

        host.addWidget(host.tooltip(stripButton(strip, STRIP_RESET, y,
                wide ? "screen.elytrapitchhelper.profile.reset_settings"
                        : "screen.elytrapitchhelper.profile.reset_short",
                () -> host.requestProfileReset(profileFile)),
                "tooltip.elytrapitchhelper.profile.reset_settings"));

        RowActionButton deleteButton = stripButton(strip, STRIP_DELETE, y,
                "screen.elytrapitchhelper.profile.delete",
                () -> host.beginProfileDeletion(profileFile));
        deleteButton.active = config.profileCount() > 1;
        host.addWidget(host.tooltip(deleteButton, "tooltip.elytrapitchhelper.profile.delete"));
    }

    private RowActionButton stripButton(ProfileStripLayout strip, int action, int y, String labelKey,
            Runnable onPress) {
        return new RowActionButton(strip.actionX(action), y, strip.actionWidth(action),
                ConfigScreen.CONTROL_HEIGHT, Component.translatable(labelKey), host.font(), onPress);
    }

    private void duplicateProfile(String profileFile) {
        int resolvedIndex = host.refreshConfigSnapshot(profileFile);
        if (resolvedIndex < 0) {
            host.rebuildWidgets();
            return;
        }
        host.config().duplicateProfile(resolvedIndex);
        host.setProfileScroll(host.maxProfileScroll());
        host.save();
        host.checkpointHistory();
        host.rebuildWidgets();
    }

    private void restoreFocus(AbstractWidget widget, String profileFile, ProfileRowControl control) {
        if (pendingFocus == null || !pendingFocus.matches(profileFile, control)) {
            return;
        }
        host.focusAfterRebuild(widget);
        if (pendingFocus.caret() != ProfileRowControl.Pending.NO_CARET && widget instanceof EditBox box) {
            box.setCursorPosition(pendingFocus.caret());
            box.setHighlightPos(pendingFocus.caret());
        }
    }

    private void addDeleteModeFooter(int contentWidth) {
        int actionWidth = Math.max(96, Math.min(140, (contentWidth - ConfigScreen.CONTROL_GAP) / 2));
        int actionTotalWidth = actionWidth * 2 + ConfigScreen.CONTROL_GAP;
        int actionX = (host.screenWidth() - actionTotalWidth) / 2;
        int actionY = host.screenHeight() - ConfigScreen.FOOTER_OFFSET;

        host.addNavigationWidget(FlatButton.of(CommonComponents.GUI_CANCEL, actionX, actionY,
                actionWidth, ConfigScreen.CONTROL_HEIGHT,
                button -> host.requestCancelProfileDeletes()));

        Button commitButton = FlatButton.of(host.commitProfileDeletesLabel(),
                actionX + actionWidth + ConfigScreen.CONTROL_GAP, actionY, actionWidth,
                ConfigScreen.CONTROL_HEIGHT, button -> host.requestCommitProfileDeletes());
        commitButton.active = host.hasMarkedProfiles();
        host.addWidget(host.tooltip(commitButton, "tooltip.elytrapitchhelper.profile.commit_deletes"));
    }

    private void addNormalFooter(int contentWidth) {
        int actionWidth = Math.max(92, Math.min(120, contentWidth / 2));
        int actionY = host.screenHeight() - ProfileListLayout.ACTION_ROW_OFFSET;

        host.addWidget(host.tooltip(FlatButton.of(Component.translatable("screen.elytrapitchhelper.profile.new"),
                (host.screenWidth() - actionWidth) / 2, actionY, actionWidth,
                ConfigScreen.CONTROL_HEIGHT, button -> {
                    host.refreshConfigSnapshot();
                    host.config().createProfile();
                    host.setProfileScroll(host.maxProfileScroll());
                    host.save();
                    host.checkpointHistory();
                    host.rebuildWidgets();
                }), "tooltip.elytrapitchhelper.profile.new"));

        int doneWidth = Math.min(120, host.screenWidth() - 40);
        host.addNavigationWidget(FlatButton.of(CommonComponents.GUI_DONE,
                (host.screenWidth() - doneWidth) / 2,
                host.screenHeight() - ConfigScreen.FOOTER_OFFSET, doneWidth,
                ConfigScreen.CONTROL_HEIGHT, button -> host.closeScreen()));
    }

    interface Host {
        AbstractWidget focusedWidget();

        int screenWidth();

        int screenHeight();

        Font font();

        Config config();

        int profileScroll();

        boolean profileScrollbarDragging();

        void setProfileScroll(int profileScroll);

        ProfileListPlan profileListPlan();

        ProfileRowControl.Pending pendingRowFocus();

        void focusAfterRebuild(AbstractWidget widget);

        boolean deleteMode();

        boolean hasMarkedProfiles();

        boolean isProfileMarkedForDelete(String profileFile);

        boolean canMarkProfileForDelete(String profileFile);

        Component commitProfileDeletesLabel();

        int maxProfileScroll();

        void addWidget(AbstractWidget widget);

        void addNavigationWidget(AbstractWidget widget);

        <T extends AbstractWidget> T tooltip(T widget, String translationKey);

        Tooltip profileMetadataTooltip(int profileIndex);

        Component profileSortValue();

        void refreshConfigSnapshot();

        int refreshConfigSnapshot(String profileFile);

        void save();

        void beginHistoryAction(String actionKey);

        void commitHistoryAction(boolean changed, boolean coalesce);

        void breakHistoryCoalescing();

        void checkpointHistory();

        void rebuildWidgets();

        void toggleProfileDeleteMark(String profileFile);

        void requestCancelProfileDeletes();

        void requestCommitProfileDeletes();

        void beginProfileDeletion(String profileFile);

        void requestProfileReset(String profileFile);

        void editProfile(int profileIndex);

        void openProfileJson(int profileIndex);

        void closeScreen();
    }
}
