package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.ActiveProfileButton;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

final class ProfileListPanel {
    private final Host host;

    ProfileListPanel(Host host) {
        this.host = host;
    }

    void init() {
        int contentWidth = Math.max(260, Math.min(560, host.screenWidth() - 40));
        int startX = (host.screenWidth() - contentWidth) / 2;
        int startY = 64;
        int availableRows = Math.max(3, (host.screenHeight() - 156) / ConfigScreen.ROW_HEIGHT);
        int visibleRows = Math.min(host.config().profileCount(), availableRows);
        int maxScroll = Math.max(0, host.config().profileCount() - visibleRows);
        host.setProfileScroll(Math.max(0, Math.min(host.profileScroll(), maxScroll)));
        boolean needsScrollbar = host.config().profileCount() > visibleRows;
        int listWidth = needsScrollbar ? contentWidth - AbstractScrollArea.SCROLLBAR_WIDTH - ConfigScreen.CONTROL_GAP
                : contentWidth;

        addHeader(startX, listWidth);
        addRows(startX, startY, visibleRows, listWidth);
        if (needsScrollbar) {
            host.addWidget(new ConfigScreen.ProfileScrollBar(startX + listWidth + ConfigScreen.CONTROL_GAP, startY,
                    AbstractScrollArea.SCROLLBAR_WIDTH, visibleRows * ConfigScreen.ROW_HEIGHT,
                    host.config().profileCount(), visibleRows, host.profileScroll()));
        }
        if (host.deleteMode()) {
            addDeleteModeFooter(contentWidth);
        } else {
            addNormalFooter(contentWidth, visibleRows);
        }
    }

    private void addHeader(int startX, int listWidth) {
        int headerY = 38;
        int sortWidth = Math.min(98, Math.max(78, listWidth / 3));
        Button sortButton = Button.builder(host.profileSortMessage(), button -> {
            host.refreshConfigSnapshot();
            host.config().cycleProfileSortMode();
            host.setProfileScroll(0);
            host.save();
            host.rebuildWidgets();
        }).bounds(startX, headerY, sortWidth, ConfigScreen.CONTROL_HEIGHT).build();
        sortButton.active = !host.deleteMode();
        host.addWidget(host.tooltip(sortButton, "tooltip.elytrapitchhelper.profile.sort"));

        CycleButton<Boolean> enabledButton = CycleButton.onOffBuilder(host.config().enabled)
                .create(startX + listWidth - sortWidth, headerY, sortWidth, ConfigScreen.CONTROL_HEIGHT,
                        Component.translatable("option.elytrapitchhelper.enabled"), (button, value) -> {
                            host.refreshConfigSnapshot();
                            host.config().enabled = value;
                            host.save();
                        });
        enabledButton.active = !host.deleteMode();
        host.addWidget(host.tooltip(enabledButton, "tooltip.elytrapitchhelper.enabled"));
    }

    private void addRows(int startX, int startY, int visibleRows, int listWidth) {
        int activeWidth = ConfigScreen.CONTROL_HEIGHT;
        int duplicateWidth = host.screenWidth() >= 420 ? 72 : 44;
        int editWidth = 50;
        int jsonWidth = 58;
        int deleteWidth = 62;
        int trailingWidth = host.deleteMode() ? deleteWidth
                : duplicateWidth + editWidth + jsonWidth + ConfigScreen.CONTROL_GAP * 2;
        int nameWidth = Math.max(72, listWidth - activeWidth - trailingWidth - ConfigScreen.CONTROL_GAP * 2);
        for (int row = 0; row < visibleRows; row++) {
            int profileIndex = host.profileScroll() + row;
            String profileFile = host.config().profile(profileIndex).fileName;
            int y = startY + row * ConfigScreen.ROW_HEIGHT;
            addActiveButton(startX, y, activeWidth, profileIndex, profileFile);
            addProfileName(startX, y, activeWidth, nameWidth, profileIndex, profileFile);

            int actionX = startX + activeWidth + ConfigScreen.CONTROL_GAP + nameWidth + ConfigScreen.CONTROL_GAP;
            if (host.deleteMode()) {
                addRowDeleteButton(actionX, y, deleteWidth, profileFile);
            } else {
                addRowActions(actionX, y, duplicateWidth, editWidth, jsonWidth, profileFile);
            }
        }
    }

    private void addActiveButton(int startX, int y, int activeWidth, int profileIndex, String profileFile) {
        ActiveProfileButton activeButton = new ActiveProfileButton(startX, y, activeWidth, ConfigScreen.CONTROL_HEIGHT,
                host.config().isActiveProfile(profileIndex), button -> {
                    int resolvedIndex = host.refreshConfigSnapshot(profileFile);
                    if (resolvedIndex < 0) {
                        host.rebuildWidgets();
                        return;
                    }
                    host.config().selectProfile(resolvedIndex);
                    host.save();
                    host.rebuildWidgets();
                });
        activeButton.active = !host.deleteMode() && !host.config().isActiveProfile(profileIndex);
        host.addWidget(host.tooltip(activeButton, "tooltip.elytrapitchhelper.profile.active"));
    }

    private void addProfileName(int startX, int y, int activeWidth, int nameWidth, int profileIndex,
            String profileFile) {
        String profileName = host.config().profileName(profileIndex);
        EditBox nameBox = new ConfigScreen.MarqueeEditBox(host.font(),
                startX + activeWidth + ConfigScreen.CONTROL_GAP, y, nameWidth,
                ConfigScreen.CONTROL_HEIGHT, Component.translatable("screen.elytrapitchhelper.profile.name"));
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
                host.config().setProfileName(resolvedIndex, value);
                host.save();
            }
        });
        nameBox.active = !host.deleteMode();
        nameBox.setTooltip(host.profileMetadataTooltip(profileIndex));
        nameBox.setTooltipDelay(ConfigScreen.TOOLTIP_DELAY);
        host.addWidget(nameBox);
    }

    private void addRowDeleteButton(int actionX, int y, int deleteWidth, String profileFile) {
        Button rowDeleteButton = Button.builder(Component.translatable("screen.elytrapitchhelper.profile.delete"),
                button -> host.stageProfileDelete(profileFile))
                .bounds(actionX, y, deleteWidth, ConfigScreen.CONTROL_HEIGHT).build();
        rowDeleteButton.active = host.config().profileCount() > 1;
        host.addWidget(host.tooltip(rowDeleteButton, "tooltip.elytrapitchhelper.profile.delete_row"));
    }

    private void addRowActions(int actionX, int y, int duplicateWidth, int editWidth, int jsonWidth,
            String profileFile) {
        Component duplicateLabel = host.screenWidth() >= 420
                ? Component.translatable("screen.elytrapitchhelper.profile.duplicate")
                : Component.translatable("screen.elytrapitchhelper.profile.copy");
        host.addWidget(host.tooltip(Button.builder(duplicateLabel, button -> {
            int resolvedIndex = host.refreshConfigSnapshot(profileFile);
            if (resolvedIndex < 0) {
                host.rebuildWidgets();
                return;
            }
            host.config().duplicateProfile(resolvedIndex);
            host.setProfileScroll(Math.max(0, host.config().profileCount() - host.visibleProfileRows()));
            host.save();
            host.rebuildWidgets();
        }).bounds(actionX, y, duplicateWidth, ConfigScreen.CONTROL_HEIGHT).build(),
                "tooltip.elytrapitchhelper.profile.duplicate"));

        actionX += duplicateWidth + ConfigScreen.CONTROL_GAP;
        host.addWidget(host.tooltip(Button.builder(Component.translatable("screen.elytrapitchhelper.profile.edit"),
                button -> {
                    int resolvedIndex = host.refreshConfigSnapshot(profileFile);
                    if (resolvedIndex < 0) {
                        host.rebuildWidgets();
                        return;
                    }
                    host.editProfile(resolvedIndex);
                }).bounds(actionX, y, editWidth, ConfigScreen.CONTROL_HEIGHT).build(),
                "tooltip.elytrapitchhelper.profile.edit"));

        int jsonX = actionX + editWidth + ConfigScreen.CONTROL_GAP;
        host.addWidget(host.tooltip(Button.builder(Component.translatable("screen.elytrapitchhelper.profile.json"),
                button -> {
                    int resolvedIndex = host.refreshConfigSnapshot(profileFile);
                    if (resolvedIndex >= 0) {
                        host.openProfileJson(resolvedIndex);
                    } else {
                        host.rebuildWidgets();
                    }
                }).bounds(jsonX, y, jsonWidth, ConfigScreen.CONTROL_HEIGHT).build(),
                "tooltip.elytrapitchhelper.profile.open_json"));
    }

    private void addDeleteModeFooter(int contentWidth) {
        int actionWidth = Math.max(76, Math.min(108, (contentWidth - ConfigScreen.CONTROL_GAP * 2) / 3));
        int actionTotalWidth = actionWidth * 3 + ConfigScreen.CONTROL_GAP * 2;
        int actionX = (host.screenWidth() - actionTotalWidth) / 2;
        int actionY = host.screenHeight() - 28;
        Button undoButton = Button.builder(Component.translatable("screen.elytrapitchhelper.profile.undo_delete"),
                button -> host.undoProfileDelete())
                .bounds(actionX, actionY, actionWidth, ConfigScreen.CONTROL_HEIGHT).build();
        undoButton.active = host.hasStagedProfileDeletes();
        host.addWidget(host.tooltip(undoButton, "tooltip.elytrapitchhelper.profile.undo_delete"));
        host.addWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> host.requestCancelProfileDeletes())
                .bounds(actionX + actionWidth + ConfigScreen.CONTROL_GAP, actionY, actionWidth,
                        ConfigScreen.CONTROL_HEIGHT)
                .build());
        Button commitButton = Button.builder(Component.translatable("screen.elytrapitchhelper.profile.commit_deletes"),
                button -> host.requestCommitProfileDeletes())
                .bounds(actionX + (actionWidth + ConfigScreen.CONTROL_GAP) * 2, actionY, actionWidth,
                        ConfigScreen.CONTROL_HEIGHT)
                .build();
        commitButton.active = host.hasStagedProfileDeletes();
        host.addWidget(host.tooltip(commitButton, "tooltip.elytrapitchhelper.profile.commit_deletes"));
    }

    private void addNormalFooter(int contentWidth, int visibleRows) {
        int actionWidth = Math.max(92, Math.min(120, (contentWidth - ConfigScreen.CONTROL_GAP) / 2));
        int actionTotalWidth = actionWidth * 2 + ConfigScreen.CONTROL_GAP;
        int actionX = (host.screenWidth() - actionTotalWidth) / 2;
        int actionY = host.screenHeight() - 54;

        host.addWidget(host.tooltip(Button.builder(Component.translatable("screen.elytrapitchhelper.profile.new"),
                button -> {
                    host.refreshConfigSnapshot();
                    host.config().createProfile();
                    host.setProfileScroll(Math.max(0, host.config().profileCount() - visibleRows));
                    host.save();
                    host.rebuildWidgets();
                }).bounds(actionX, actionY, actionWidth, ConfigScreen.CONTROL_HEIGHT).build(),
                "tooltip.elytrapitchhelper.profile.new"));

        Button deleteButton = Button.builder(Component.translatable("screen.elytrapitchhelper.profile.delete"),
                button -> host.enterDeleteMode())
                .bounds(actionX + actionWidth + ConfigScreen.CONTROL_GAP, actionY, actionWidth,
                        ConfigScreen.CONTROL_HEIGHT)
                .build();
        deleteButton.active = host.config().profileCount() > 1;
        host.addWidget(host.tooltip(deleteButton, "tooltip.elytrapitchhelper.profile.delete"));

        int doneWidth = Math.min(120, host.screenWidth() - 40);
        host.addWidget(Button.builder(CommonComponents.GUI_DONE, button -> host.closeScreen())
                .bounds((host.screenWidth() - doneWidth) / 2, host.screenHeight() - 28, doneWidth,
                        ConfigScreen.CONTROL_HEIGHT)
                .build());
    }

    interface Host {
        int screenWidth();

        int screenHeight();

        Font font();

        Config config();

        int profileScroll();

        void setProfileScroll(int profileScroll);

        boolean deleteMode();

        boolean hasStagedProfileDeletes();

        int visibleProfileRows();

        void addWidget(AbstractWidget widget);

        <T extends AbstractWidget> T tooltip(T widget, String translationKey);

        Tooltip profileMetadataTooltip(int profileIndex);

        Component profileSortMessage();

        void refreshConfigSnapshot();

        int refreshConfigSnapshot(String profileFile);

        void save();

        void rebuildWidgets();

        void stageProfileDelete(String profileFile);

        void undoProfileDelete();

        void requestCancelProfileDeletes();

        void requestCommitProfileDeletes();

        void enterDeleteMode();

        void editProfile(int profileIndex);

        void openProfileJson(int profileIndex);

        void closeScreen();
    }
}
