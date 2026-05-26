package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.client.ClientConfigStore;
import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import com.asphyxiamywife.elytrapitchhelper.config.PrideFlag;
import com.asphyxiamywife.elytrapitchhelper.config.StagedProfileDelete;
import com.asphyxiamywife.elytrapitchhelper.platform.PlatformFileOpener;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.ActiveProfileButton;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.CategoryTabButton;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.ColorEditButton;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.NumberSlider;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class ConfigScreen extends Screen {
    private static final int CONTROL_HEIGHT = 20;
    private static final int CONTROL_GAP = 6;
    private static final int ROW_HEIGHT = 24;
    private static final Duration TOOLTIP_DELAY = Duration.ofMillis(350L);
    private static final long SAVE_DEBOUNCE_MILLIS = 750L;
    private static final int PROFILE_NAME_MAX_LENGTH = 512;
    private static final long PROFILE_NAME_MARQUEE_EDGE_HOLD_MILLIS = 850L;
    private static final int PROFILE_NAME_MARQUEE_PIXELS_PER_SECOND = 18;
    private static final int TRANSPARENT_TEXT_COLOR = 0x00000000;

    private final Screen lastScreen;
    private Config config;
    private boolean editingProfile;
    private boolean deleteMode;
    private int editingProfileIndex;
    private String editingProfileFile;
    private int profileScroll;
    private long configRevision;
    private long saveAfterMillis;
    private boolean savePending;
    private boolean profileScrollbarDragging;
    private double profileScrollbarGrabOffset;
    private boolean deleteModeConfirmationOpen;
    private final List<StagedProfileDelete> stagedProfileDeletes = new ArrayList<>();
    private Category category = Category.GENERAL;

    public ConfigScreen(Screen lastScreen) {
        super(Component.translatable("screen.elytrapitchhelper.config.title"));
        this.lastScreen = lastScreen;
        this.config = ClientConfigStore.get().copy();
        this.configRevision = ClientConfigStore.revision();
    }

    @Override
    protected void init() {
        if (configRevision != ClientConfigStore.revision()) {
            loadConfigSnapshot();
        }
        syncEditingProfileIndex();
        if (editingProfile) {
            initProfileEditor();
        } else {
            initProfileList();
        }
    }

    private void initProfileList() {
        int contentWidth = Math.max(260, Math.min(560, width - 40));
        int startX = (width - contentWidth) / 2;
        int startY = 64;
        int availableRows = Math.max(3, (height - 156) / ROW_HEIGHT);
        int visibleRows = Math.min(config.profileCount(), availableRows);
        int maxScroll = Math.max(0, config.profileCount() - visibleRows);
        profileScroll = Math.max(0, Math.min(profileScroll, maxScroll));
        boolean needsScrollbar = config.profileCount() > visibleRows;
        int listWidth = needsScrollbar ? contentWidth - AbstractScrollArea.SCROLLBAR_WIDTH - CONTROL_GAP : contentWidth;

        int headerY = 38;
        int sortWidth = Math.min(98, Math.max(78, listWidth / 3));
        Button sortButton = Button.builder(profileSortMessage(), button -> {
            refreshConfigSnapshot();
            config.cycleProfileSortMode();
            profileScroll = 0;
            save();
            rebuildWidgets();
        }).bounds(startX, headerY, sortWidth, CONTROL_HEIGHT).build();
        sortButton.active = !deleteMode;
        addRenderableWidget(tooltip(sortButton, "tooltip.elytrapitchhelper.profile.sort"));

        int enabledWidth = sortWidth;
        CycleButton<Boolean> enabledButton = CycleButton.onOffBuilder(config.enabled)
                .create(startX + listWidth - enabledWidth, headerY, enabledWidth, CONTROL_HEIGHT,
                        Component.translatable("option.elytrapitchhelper.enabled"), (button, value) -> {
                            refreshConfigSnapshot();
                            config.enabled = value;
                            save();
                        });
        enabledButton.active = !deleteMode;
        addRenderableWidget(tooltip(enabledButton, "tooltip.elytrapitchhelper.enabled"));

        int activeWidth = CONTROL_HEIGHT;
        int duplicateWidth = width >= 420 ? 72 : 44;
        int editWidth = 50;
        int jsonWidth = 58;
        int deleteWidth = 62;
        int trailingWidth = deleteMode ? deleteWidth
                : duplicateWidth + editWidth + jsonWidth + CONTROL_GAP * 2;
        int nameWidth = Math.max(72, listWidth - activeWidth - trailingWidth - CONTROL_GAP * 2);
        for (int row = 0; row < visibleRows; row++) {
            int profileIndex = profileScroll + row;
            String profileFile = config.profile(profileIndex).fileName;
            int y = startY + row * ROW_HEIGHT;

            ActiveProfileButton activeButton = new ActiveProfileButton(startX, y, activeWidth, CONTROL_HEIGHT,
                    config.isActiveProfile(profileIndex), button -> {
                        int resolvedIndex = refreshConfigSnapshot(profileFile);
                        if (resolvedIndex < 0) {
                            rebuildWidgets();
                            return;
                        }
                        config.selectProfile(resolvedIndex);
                        save();
                        rebuildWidgets();
                    });
            activeButton.active = !deleteMode && !config.isActiveProfile(profileIndex);
            addRenderableWidget(tooltip(activeButton, "tooltip.elytrapitchhelper.profile.active"));

            String profileName = config.profileName(profileIndex);
            EditBox nameBox = new MarqueeEditBox(font, startX + activeWidth + CONTROL_GAP, y, nameWidth,
                    CONTROL_HEIGHT, Component.translatable("screen.elytrapitchhelper.profile.name"));
            nameBox.setMaxLength(Math.max(PROFILE_NAME_MAX_LENGTH, profileName.length()));
            nameBox.setValue(profileName);
            nameBox.setResponder(value -> {
                if (!value.isBlank()) {
                    int resolvedIndex = refreshConfigSnapshot(profileFile);
                    if (resolvedIndex < 0) {
                        rebuildWidgets();
                        return;
                    }
                    if (value.equals(config.profileName(resolvedIndex))) {
                        return;
                    }
                    config.setProfileName(resolvedIndex, value);
                    save();
                }
            });
            nameBox.active = !deleteMode;
            nameBox.setTooltip(profileMetadataTooltip(profileIndex));
            nameBox.setTooltipDelay(TOOLTIP_DELAY);
            addRenderableWidget(nameBox);

            int actionX = startX + activeWidth + CONTROL_GAP + nameWidth + CONTROL_GAP;
            if (deleteMode) {
                Button rowDeleteButton = Button.builder(Component.translatable("screen.elytrapitchhelper.profile.delete"),
                        button -> {
                            stageProfileDelete(profileFile);
                        }).bounds(actionX, y, deleteWidth, CONTROL_HEIGHT).build();
                rowDeleteButton.active = config.profileCount() > 1;
                addRenderableWidget(tooltip(rowDeleteButton, "tooltip.elytrapitchhelper.profile.delete_row"));
            } else {
                int duplicateX = actionX;
                Component duplicateLabel = width >= 420
                        ? Component.translatable("screen.elytrapitchhelper.profile.duplicate")
                        : Component.translatable("screen.elytrapitchhelper.profile.copy");
                addRenderableWidget(tooltip(Button.builder(duplicateLabel, button -> {
                    int resolvedIndex = refreshConfigSnapshot(profileFile);
                    if (resolvedIndex < 0) {
                        rebuildWidgets();
                        return;
                    }
                    config.duplicateProfile(resolvedIndex);
                    profileScroll = Math.max(0, config.profileCount() - visibleProfileRows());
                    save();
                    rebuildWidgets();
                }).bounds(duplicateX, y, duplicateWidth, CONTROL_HEIGHT).build(),
                        "tooltip.elytrapitchhelper.profile.duplicate"));

                actionX += duplicateWidth + CONTROL_GAP;
                addRenderableWidget(tooltip(Button.builder(Component.translatable("screen.elytrapitchhelper.profile.edit"),
                        button -> {
                            int resolvedIndex = refreshConfigSnapshot(profileFile);
                            if (resolvedIndex < 0) {
                                rebuildWidgets();
                                return;
                            }
                            editingProfileIndex = resolvedIndex;
                            editingProfileFile = config.profile(resolvedIndex).fileName;
                            editingProfile = true;
                            category = Category.GENERAL;
                            rebuildWidgets();
                        }).bounds(actionX, y, editWidth, CONTROL_HEIGHT).build(),
                        "tooltip.elytrapitchhelper.profile.edit"));

                int jsonX = actionX + editWidth + CONTROL_GAP;
                addRenderableWidget(tooltip(Button.builder(Component.translatable("screen.elytrapitchhelper.profile.json"),
                        button -> {
                            int resolvedIndex = refreshConfigSnapshot(profileFile);
                            if (resolvedIndex >= 0) {
                                openProfileJson(resolvedIndex);
                            } else {
                                rebuildWidgets();
                            }
                        }).bounds(jsonX, y, jsonWidth, CONTROL_HEIGHT).build(),
                        "tooltip.elytrapitchhelper.profile.open_json"));
            }
        }

        if (needsScrollbar) {
            addRenderableWidget(new ProfileScrollBar(startX + listWidth + CONTROL_GAP, startY,
                    AbstractScrollArea.SCROLLBAR_WIDTH, visibleRows * ROW_HEIGHT, config.profileCount(), visibleRows,
                    profileScroll));
        }

        if (deleteMode) {
            int actionWidth = Math.max(76, Math.min(108, (contentWidth - CONTROL_GAP * 2) / 3));
            int actionTotalWidth = actionWidth * 3 + CONTROL_GAP * 2;
            int actionX = (width - actionTotalWidth) / 2;
            int actionY = height - 28;
            Button undoButton = Button.builder(Component.translatable("screen.elytrapitchhelper.profile.undo_delete"),
                    button -> undoProfileDelete())
                    .bounds(actionX, actionY, actionWidth, CONTROL_HEIGHT).build();
            undoButton.active = !stagedProfileDeletes.isEmpty();
            addRenderableWidget(tooltip(undoButton, "tooltip.elytrapitchhelper.profile.undo_delete"));
            addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> requestCancelProfileDeletes())
                    .bounds(actionX + actionWidth + CONTROL_GAP, actionY, actionWidth, CONTROL_HEIGHT).build());
            Button commitButton = Button.builder(Component.translatable("screen.elytrapitchhelper.profile.commit_deletes"),
                    button -> requestCommitProfileDeletes())
                    .bounds(actionX + (actionWidth + CONTROL_GAP) * 2, actionY, actionWidth, CONTROL_HEIGHT).build();
            commitButton.active = !stagedProfileDeletes.isEmpty();
            addRenderableWidget(tooltip(commitButton, "tooltip.elytrapitchhelper.profile.commit_deletes"));
        } else {
            int actionWidth = Math.max(92, Math.min(120, (contentWidth - CONTROL_GAP) / 2));
            int actionTotalWidth = actionWidth * 2 + CONTROL_GAP;
            int actionX = (width - actionTotalWidth) / 2;
            int actionY = height - 54;

            addRenderableWidget(tooltip(Button.builder(Component.translatable("screen.elytrapitchhelper.profile.new"),
                    button -> {
                        refreshConfigSnapshot();
                        config.createProfile();
                        profileScroll = Math.max(0, config.profileCount() - visibleRows);
                        save();
                        rebuildWidgets();
                    }).bounds(actionX, actionY, actionWidth, CONTROL_HEIGHT).build(),
                    "tooltip.elytrapitchhelper.profile.new"));

            Button deleteButton = Button.builder(Component.translatable("screen.elytrapitchhelper.profile.delete"),
                    button -> {
                        flushSaveIfPending();
                        stagedProfileDeletes.clear();
                        deleteMode = true;
                        rebuildWidgets();
                    }).bounds(actionX + actionWidth + CONTROL_GAP, actionY, actionWidth, CONTROL_HEIGHT).build();
            deleteButton.active = config.profileCount() > 1;
            addRenderableWidget(tooltip(deleteButton, "tooltip.elytrapitchhelper.profile.delete"));

            int doneWidth = Math.min(120, width - 40);
            addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                    .bounds((width - doneWidth) / 2, height - 28, doneWidth, CONTROL_HEIGHT).build());
        }
    }

    private void initProfileEditor() {
        int contentWidth = Math.max(120, Math.min(440, width - 40));
        Profile profile = config.profile(editingProfileIndex);

        addCategoryTabs(contentWidth);

        int columns = width >= 360 ? 2 : 1;
        int controlWidth = columns == 2 ? Math.min(210, (contentWidth - CONTROL_GAP) / 2) : Math.min(210, contentWidth);
        int totalWidth = columns * controlWidth + (columns - 1) * CONTROL_GAP;
        int startX = (width - totalWidth) / 2;
        int startY = 84;
        int index = 0;

        if (category == Category.GENERAL) {
            addControl(index++, startX, startY, columns, controlWidth,
                    tooltip(CycleButton.onOffBuilder(profile.showOnlyWithFirework)
                            .create(0, 0, controlWidth, CONTROL_HEIGHT,
                                    Component.translatable("option.elytrapitchhelper.show_only_with_firework"),
                                    (button, value) -> {
                                        profile.showOnlyWithFirework = value;
                                        saveProfileChange();
                                    }), "tooltip.elytrapitchhelper.show_only_with_firework"));

            addControl(index++, startX, startY, columns, controlWidth,
                    tooltip(CycleButton.onOffBuilder(profile.showInThirdPerson)
                            .create(0, 0, controlWidth, CONTROL_HEIGHT,
                                    Component.translatable("option.elytrapitchhelper.show_in_third_person"),
                                    (button, value) -> {
                                        profile.showInThirdPerson = value;
                                        saveProfileChange();
                                    }), "tooltip.elytrapitchhelper.show_in_third_person"));
        } else if (category == Category.PITCH) {
            addControl(index++, startX, startY, columns, controlWidth,
                    tooltip(floatSlider(Component.translatable("option.elytrapitchhelper.target_up"),
                            profile.targetUpMinecraft, -90.0, 0.0, 1.0, "deg", value -> {
                                profile.targetUpMinecraft = (float) value;
                                saveProfileChange();
                            }), "tooltip.elytrapitchhelper.target_up"));

            addControl(index++, startX, startY, columns, controlWidth,
                    tooltip(floatSlider(Component.translatable("option.elytrapitchhelper.target_down"),
                            profile.targetDownMinecraft, 0.0, 90.0, 1.0, "deg", value -> {
                                profile.targetDownMinecraft = (float) value;
                                saveProfileChange();
                            }), "tooltip.elytrapitchhelper.target_down"));

            addControl(index++, startX, startY, columns, controlWidth,
                    tooltip(floatSlider(Component.translatable("option.elytrapitchhelper.tolerance"),
                            profile.toleranceDegrees, 1.0, 45.0, 0.5, "deg", value -> {
                                profile.toleranceDegrees = (float) value;
                                saveProfileChange();
                            }), "tooltip.elytrapitchhelper.tolerance"));

            addControl(index++, startX, startY, columns, controlWidth,
                    tooltip(intSlider(Component.translatable("option.elytrapitchhelper.max_offset"),
                            profile.maxOffsetPixels, 0, 200, "px", value -> {
                                profile.maxOffsetPixels = value;
                                saveProfileChange();
                            }), "tooltip.elytrapitchhelper.max_offset"));

            addControl(index++, startX, startY, columns, controlWidth,
                    tooltip(floatSlider(Component.translatable("option.elytrapitchhelper.offset_per_degree"),
                            profile.offsetPerDegree, 0.25, 10.0, 0.25, "px/deg", value -> {
                                profile.offsetPerDegree = (float) value;
                                saveProfileChange();
                            }), "tooltip.elytrapitchhelper.offset_per_degree"));
        } else if (category == Category.AMPLITUDE) {
            addControl(index++, startX, startY, columns, controlWidth,
                    tooltip(CycleButton.onOffBuilder(profile.amplitudeHelperEnabled)
                            .create(0, 0, controlWidth, CONTROL_HEIGHT,
                                    Component.translatable("option.elytrapitchhelper.amplitude_helper"),
                                    (button, value) -> {
                                        profile.amplitudeHelperEnabled = value;
                                        saveProfileChange();
                                    }), "tooltip.elytrapitchhelper.amplitude_helper"));

            addControl(index++, startX, startY, columns, controlWidth,
                    tooltip(amplitudeTriggerModeButton(
                            Component.translatable("option.elytrapitchhelper.amplitude_trigger_mode"), profile),
                            "tooltip.elytrapitchhelper.amplitude_trigger_mode"));

            addControl(index++, startX, startY, columns, controlWidth,
                    tooltip(intSlider(Component.translatable("option.elytrapitchhelper.amplitude_down"),
                            profile.amplitudeDownBlocks, 10, 300, "blocks", value -> {
                                profile.amplitudeDownBlocks = value;
                                saveProfileChange();
                            }), "tooltip.elytrapitchhelper.amplitude_down"));

            addControl(index++, startX, startY, columns, controlWidth,
                    tooltip(intSlider(Component.translatable("option.elytrapitchhelper.amplitude_up"),
                            profile.amplitudeUpBlocks, 10, 300, "blocks", value -> {
                                profile.amplitudeUpBlocks = value;
                                saveProfileChange();
                            }), "tooltip.elytrapitchhelper.amplitude_up"));

            addControl(index++, startX, startY, columns, controlWidth,
                    tooltip(intSlider(Component.translatable("option.elytrapitchhelper.amplitude_tolerance"),
                            profile.amplitudeToleranceBlocks, 0, 30, "blocks", value -> {
                                profile.amplitudeToleranceBlocks = value;
                                saveProfileChange();
                            }), "tooltip.elytrapitchhelper.amplitude_tolerance"));

            addControl(index++, startX, startY, columns, controlWidth,
                    tooltip(floatSlider(Component.translatable("option.elytrapitchhelper.amplitude_down_velocity"),
                            profile.amplitudeDownVelocity, 0.5, 5.0, 0.1, "b/t", value -> {
                                profile.amplitudeDownVelocity = (float) value;
                                saveProfileChange();
                            }), "tooltip.elytrapitchhelper.amplitude_down_velocity"));

            addControl(index++, startX, startY, columns, controlWidth,
                    tooltip(floatSlider(Component.translatable("option.elytrapitchhelper.amplitude_up_velocity"),
                            profile.amplitudeUpVelocity, 0.0, 2.0, 0.1, "b/t", value -> {
                                profile.amplitudeUpVelocity = (float) value;
                                saveProfileChange();
                            }), "tooltip.elytrapitchhelper.amplitude_up_velocity"));
        } else if (category == Category.VISUALS) {
            addControl(index++, startX, startY, columns, controlWidth,
                    tooltip(intSlider(Component.translatable("option.elytrapitchhelper.line_length"),
                            profile.lineLengthPixels, 2, 200, "px", value -> {
                                profile.lineLengthPixels = value;
                                saveProfileChange();
                            }), "tooltip.elytrapitchhelper.line_length"));

            addControl(index++, startX, startY, columns, controlWidth,
                    tooltip(intSlider(Component.translatable("option.elytrapitchhelper.line_width"),
                            profile.lineWidthPixels, 1, 20, "px", value -> {
                                profile.lineWidthPixels = value;
                                saveProfileChange();
                            }), "tooltip.elytrapitchhelper.line_width"));

            addControl(index++, startX, startY, columns, controlWidth,
                    tooltip(colorButton(Component.translatable("option.elytrapitchhelper.line_color"),
                            profile.lineColorRgb, profile.linePrideEnabled, profile.linePrideFlag,
                            () -> profile.lineLengthPixels, () -> profile.lineWidthPixels, false, value -> {
                                profile.lineColorRgb = value;
                                saveProfileChange();
                            }, (enabled, flagId) -> {
                                profile.linePrideEnabled = enabled;
                                profile.linePrideFlag = flagId;
                                saveProfileChange();
                            }), "tooltip.elytrapitchhelper.line_color"));

            addControl(index++, startX, startY, columns, controlWidth,
                    tooltip(colorButton(Component.translatable("option.elytrapitchhelper.amplitude_color"),
                            profile.amplitudeCueColorRgb, profile.amplitudeCuePrideEnabled,
                            profile.amplitudeCuePrideFlag, () -> profile.lineLengthPixels,
                            () -> profile.lineWidthPixels, true, value -> {
                                profile.amplitudeCueColorRgb = value;
                                saveProfileChange();
                            }, (enabled, flagId) -> {
                                profile.amplitudeCuePrideEnabled = enabled;
                                profile.amplitudeCuePrideFlag = flagId;
                                saveProfileChange();
                            }), "tooltip.elytrapitchhelper.amplitude_color"));
        }

        int footerY = height - 28;
        int buttonWidth = Math.min(132, (width - 46) / 2);
        int footerX = (width - buttonWidth * 2 - CONTROL_GAP) / 2;
        addRenderableWidget(Button.builder(Component.translatable("screen.elytrapitchhelper.config.reset"),
                button -> openResetProfileConfirmation())
                .bounds(footerX, footerY, buttonWidth, CONTROL_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.elytrapitchhelper.profile.back_to_profiles"),
                button -> onClose())
                .bounds(footerX + buttonWidth + CONTROL_GAP, footerY, buttonWidth, CONTROL_HEIGHT).build());
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        Component renderedTitle = editingProfile
                ? Component.translatable("screen.elytrapitchhelper.profile_editor.title",
                        config.profileName(editingProfileIndex))
                : Component.translatable("screen.elytrapitchhelper.profiles.title");
        context.drawCenteredString(font, renderedTitle, width / 2, 15, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (editingProfile) {
            flushSaveIfPending();
            editingProfile = false;
            editingProfileFile = null;
            refreshConfigSnapshot();
            rebuildWidgets();
        } else if (deleteMode) {
            requestCancelProfileDeletes();
        } else {
            flushSaveIfPending();
            minecraft.setScreen(lastScreen);
        }
    }

    @Override
    public void removed() {
        if (deleteMode && !deleteModeConfirmationOpen) {
            restoreStagedProfileDeletes();
            deleteMode = false;
        }
        flushSaveIfPending();
        super.removed();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!editingProfile && config.profileCount() > 0) {
            int maxScroll = maxProfileScroll();
            int nextScroll = Math.max(0, Math.min(maxScroll, profileScroll - (int) Math.signum(scrollY)));
            if (nextScroll != profileScroll) {
                profileScroll = nextScroll;
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (startProfileScrollbarDrag(event)) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (profileScrollbarDragging && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            scrollProfileListToMouse(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (profileScrollbarDragging && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            stopProfileScrollbarDrag();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if ((event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)
                && commitFocusedTextField()) {
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && blurFocusedTextField()) {
            return true;
        }
        return super.keyPressed(event);
    }

    private void setProfileScroll(int nextScroll) {
        nextScroll = Math.max(0, Math.min(nextScroll, maxProfileScroll()));
        if (nextScroll != profileScroll) {
            profileScroll = nextScroll;
            rebuildWidgets();
        }
    }

    private int visibleProfileRows() {
        return Math.min(config.profileCount(), Math.max(3, (height - 156) / ROW_HEIGHT));
    }

    private int maxProfileScroll() {
        return Math.max(0, config.profileCount() - visibleProfileRows());
    }

    private void stageProfileDelete(String profileFile) {
        int resolvedIndex = config.profileIndexByFileName(profileFile);
        StagedProfileDelete deleted = resolvedIndex < 0 ? null : config.stageDeleteProfile(resolvedIndex);
        if (deleted != null) {
            stagedProfileDeletes.add(deleted);
            syncEditingProfileIndex();
            profileScroll = Math.max(0, Math.min(profileScroll, maxProfileScroll()));
        }
        rebuildWidgets();
    }

    private void undoProfileDelete() {
        if (stagedProfileDeletes.isEmpty()) {
            return;
        }

        StagedProfileDelete deleted = stagedProfileDeletes.remove(stagedProfileDeletes.size() - 1);
        config.restoreStagedProfileDelete(deleted);
        syncEditingProfileIndex();
        int restoredIndex = config.profileIndexByFileName(deleted.fileName());
        if (restoredIndex >= 0) {
            scrollProfileIntoView(restoredIndex);
        }
        rebuildWidgets();
    }

    private void requestCommitProfileDeletes() {
        if (stagedProfileDeletes.isEmpty()) {
            return;
        }

        deleteModeConfirmationOpen = true;
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            deleteModeConfirmationOpen = false;
            minecraft.setScreen(this);
            if (confirmed) {
                commitProfileDeletes();
            } else {
                rebuildWidgets();
            }
        }, Component.translatable("screen.elytrapitchhelper.commit_profile_deletes.title"),
                profileDeleteCommitMessage(), Component.translatable("screen.elytrapitchhelper.profile.commit_deletes"),
                CommonComponents.GUI_CANCEL));
    }

    private void requestCancelProfileDeletes() {
        if (stagedProfileDeletes.isEmpty()) {
            cancelProfileDeletes();
            rebuildWidgets();
            return;
        }

        deleteModeConfirmationOpen = true;
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            deleteModeConfirmationOpen = false;
            minecraft.setScreen(this);
            if (confirmed) {
                cancelProfileDeletes();
            }
            rebuildWidgets();
        }, Component.translatable("screen.elytrapitchhelper.cancel_profile_deletes.title"),
                profileDeleteCancelMessage(),
                Component.translatable("screen.elytrapitchhelper.cancel_profile_deletes.confirm"),
                CommonComponents.GUI_CANCEL));
    }

    private void commitProfileDeletes() {
        for (StagedProfileDelete deleted : stagedProfileDeletes) {
            config.deleteProfileFile(deleted.fileName());
        }
        stagedProfileDeletes.clear();
        deleteMode = false;
        save();
        flushSaveIfPending();
        rebuildWidgets();
    }

    private void cancelProfileDeletes() {
        restoreStagedProfileDeletes();
        deleteMode = false;
    }

    private void restoreStagedProfileDeletes() {
        while (!stagedProfileDeletes.isEmpty()) {
            StagedProfileDelete deleted = stagedProfileDeletes.remove(stagedProfileDeletes.size() - 1);
            config.restoreStagedProfileDelete(deleted);
        }
        syncEditingProfileIndex();
        profileScroll = Math.max(0, Math.min(profileScroll, maxProfileScroll()));
    }

    private Component profileDeleteCommitMessage() {
        int count = stagedProfileDeletes.size();
        if (count == 1) {
            return Component.translatable("screen.elytrapitchhelper.commit_profile_deletes.message.one");
        }
        return Component.translatable("screen.elytrapitchhelper.commit_profile_deletes.message.many", count);
    }

    private Component profileDeleteCancelMessage() {
        int count = stagedProfileDeletes.size();
        if (count == 1) {
            return Component.translatable("screen.elytrapitchhelper.cancel_profile_deletes.message.one");
        }
        return Component.translatable("screen.elytrapitchhelper.cancel_profile_deletes.message.many", count);
    }

    private void scrollProfileIntoView(int profileIndex) {
        int visibleRows = visibleProfileRows();
        if (profileIndex < profileScroll) {
            profileScroll = profileIndex;
        } else if (profileIndex >= profileScroll + visibleRows) {
            profileScroll = profileIndex - visibleRows + 1;
        }
        profileScroll = Math.max(0, Math.min(profileScroll, maxProfileScroll()));
    }

    private boolean startProfileScrollbarDrag(MouseButtonEvent event) {
        if (editingProfile || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }

        ProfileScrollGeometry geometry = profileScrollGeometry();
        if (geometry == null || !geometry.contains(event.x(), event.y())) {
            return false;
        }

        if (getFocused() instanceof EditBox editBox) {
            setFocused(null);
            editBox.setFocused(false);
        }

        profileScrollbarDragging = true;
        setDragging(true);
        profileScrollbarGrabOffset = geometry.containsScroller(event.y())
                ? event.y() - geometry.scrollerY
                : geometry.scrollerHeight / 2.0;
        scrollProfileListToMouse(event.y());
        return true;
    }

    private void scrollProfileListToMouse(double mouseY) {
        ProfileScrollGeometry geometry = profileScrollGeometry();
        if (geometry == null || geometry.movableHeight <= 0 || geometry.maxScrollAmount <= 0) {
            stopProfileScrollbarDrag();
            return;
        }

        double scrollerTop = Math.max(0.0,
                Math.min(geometry.movableHeight, mouseY - geometry.y - profileScrollbarGrabOffset));
        double scrollAmount = scrollerTop * geometry.maxScrollAmount / geometry.movableHeight;
        setProfileScroll((int) Math.round(scrollAmount / ROW_HEIGHT));
    }

    private void stopProfileScrollbarDrag() {
        profileScrollbarDragging = false;
        setDragging(false);
    }

    private ProfileScrollGeometry profileScrollGeometry() {
        int visibleRows = visibleProfileRows();
        if (editingProfile || config.profileCount() <= visibleRows) {
            return null;
        }

        int contentWidth = Math.max(260, Math.min(560, width - 40));
        int startX = (width - contentWidth) / 2;
        int startY = 64;
        int listWidth = contentWidth - AbstractScrollArea.SCROLLBAR_WIDTH - CONTROL_GAP;
        int scrollbarX = startX + listWidth + CONTROL_GAP;
        int scrollbarHeight = visibleRows * ROW_HEIGHT;
        int contentHeight = config.profileCount() * ROW_HEIGHT;
        int maxScrollAmount = Math.max(0, contentHeight - scrollbarHeight);
        int scrollerHeight = Math.max(32,
                Math.min(scrollbarHeight - 8, (int) (scrollbarHeight * scrollbarHeight / (float) contentHeight)));
        int movableHeight = scrollbarHeight - scrollerHeight;
        int scrollAmount = Math.max(0, Math.min(maxScrollAmount, profileScroll * ROW_HEIGHT));
        int scrollerY = maxScrollAmount == 0 ? startY
                : Math.max(startY, startY + scrollAmount * movableHeight / maxScrollAmount);
        return new ProfileScrollGeometry(scrollbarX, startY, AbstractScrollArea.SCROLLBAR_WIDTH, scrollbarHeight,
                scrollerY, scrollerHeight, maxScrollAmount, movableHeight);
    }

    @Override
    public void tick() {
        super.tick();
        flushSaveIfDue();
        if (!savePending && configRevision != ClientConfigStore.revision() && !isTextFieldFocused()) {
            loadConfigSnapshot();
            syncEditingProfileIndex();
            rebuildWidgets();
        }
    }

    private void addControl(int index, int startX, int startY, int columns, int width, AbstractSliderButton widget) {
        addControl(index, startX, startY, columns, width, (AbstractWidget) widget);
    }

    private void addControl(int index, int startX, int startY, int columns, int width, AbstractWidget widget) {
        int x = startX + (index % columns) * (width + CONTROL_GAP);
        int y = startY + (index / columns) * ROW_HEIGHT;
        widget.setSize(width, CONTROL_HEIGHT);
        widget.setX(x);
        widget.setY(y);
        addRenderableWidget(widget);
    }

    private void addCategoryTabs(int contentWidth) {
        int tabCount = Category.values().length;
        int tabWidth = Math.max(62, Math.min(86, (contentWidth - (tabCount - 1) * CONTROL_GAP) / tabCount));
        int totalWidth = tabCount * tabWidth + (tabCount - 1) * CONTROL_GAP;
        int x = (width - totalWidth) / 2;
        int y = 58;
        for (Category tab : Category.values()) {
            Button button = new CategoryTabButton(x, y, tabWidth, CONTROL_HEIGHT,
                    Component.translatable(tab.translationKey), tab == category, pressed -> {
                category = tab;
                rebuildWidgets();
            });
            addRenderableWidget(button);
            x += tabWidth + CONTROL_GAP;
        }
    }

    private static <T extends AbstractWidget> T tooltip(T widget, String translationKey) {
        widget.setTooltip(Tooltip.create(Component.translatable(translationKey)));
        widget.setTooltipDelay(TOOLTIP_DELAY);
        return widget;
    }

    private Tooltip profileMetadataTooltip(int profileIndex) {
        return Tooltip.create(Component.literal("Last modified: "
                + ScreenText.relativeTime(config.profileLastModifiedAtMillis(profileIndex))
                + "\nBased on: " + config.profileBasedOn(profileIndex)));
    }

    private boolean blurFocusedTextField() {
        if (getFocused() instanceof EditBox editBox) {
            setFocused(null);
            editBox.setFocused(false);
            if (editBox.getValue().isBlank()
                    || configRevision != ClientConfigStore.revision()) {
                loadConfigSnapshot();
                syncEditingProfileIndex();
                rebuildWidgets();
            }
            return true;
        }
        return false;
    }

    private boolean commitFocusedTextField() {
        if (!(getFocused() instanceof EditBox editBox)) {
            return false;
        }

        boolean shouldReload = editBox.getValue().isBlank()
                || configRevision != ClientConfigStore.revision();
        setFocused(null);
        editBox.setFocused(false);
        flushSaveIfPending();
        if (shouldReload) {
            loadConfigSnapshot();
            syncEditingProfileIndex();
            rebuildWidgets();
        }
        return true;
    }

    private AbstractSliderButton floatSlider(Component label, double current, double min, double max, double step,
            String suffix, DoubleConsumer onChange) {
        return new NumberSlider(0, 0, 1, CONTROL_HEIGHT, label, current, min, max, step,
                value -> ScreenText.formatDecimal(value) + " " + suffix, onChange);
    }

    private AbstractSliderButton intSlider(Component label, int current, int min, int max, String suffix,
            IntConsumer onChange) {
        return new NumberSlider(0, 0, 1, CONTROL_HEIGHT, label, current, min, max, 1.0,
                value -> (int) Math.round(value) + " " + suffix, value -> onChange.accept((int) Math.round(value)));
    }

    private Button colorButton(Component label, int current, boolean prideEnabled, String prideFlagId,
            IntSupplier previewLineLength, IntSupplier previewLineWidth, boolean previewCuePeak,
            IntConsumer onChange, ColorEditorScreen.PrideSettingsConsumer onPrideChange) {
        int color = current & 0x00FFFFFF;
        PrideFlag prideFlag = PrideFlag.byId(prideFlagId);
        Component value = prideEnabled
                ? Component.translatable("option.elytrapitchhelper.color.pride_value",
                        Component.translatable(prideFlag.translationKey()))
                : ScreenText.colorComponent(color);
        int[] stripeColors = prideEnabled ? prideFlag.colors() : null;
        return new ColorEditButton(CommonComponents.optionNameValue(label, value), color, stripeColors,
                button -> minecraft.setScreen(new ColorEditorScreen(this, label, color, prideEnabled,
                        prideFlag.id(), previewLineLength.getAsInt(), previewLineWidth.getAsInt(), previewCuePeak,
                        onChange, onPrideChange, this::flushSaveIfPending)));
    }

    private void openResetProfileConfirmation() {
        String profileName = config.profileName(editingProfileIndex);
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            minecraft.setScreen(this);
            if (confirmed) {
                config.resetProfileToDefaults(editingProfileIndex);
                save();
                rebuildWidgets();
            }
        }, Component.translatable("screen.elytrapitchhelper.reset_profile.title"),
                Component.translatable("screen.elytrapitchhelper.reset_profile.message", profileName),
                Component.translatable("screen.elytrapitchhelper.reset_profile.confirm"),
                CommonComponents.GUI_CANCEL));
    }

    private Button amplitudeTriggerModeButton(Component label, Profile profile) {
        return Button.builder(amplitudeTriggerModeMessage(label), button -> {
            profile.amplitudeTriggerMode = Config.nextAmplitudeTriggerMode(profile.amplitudeTriggerMode);
            button.setMessage(amplitudeTriggerModeMessage(label, profile.amplitudeTriggerMode));
            saveProfileChange();
        }).bounds(0, 0, 1, CONTROL_HEIGHT).build();
    }

    private Component amplitudeTriggerModeMessage(Component label) {
        return amplitudeTriggerModeMessage(label, config.profile(editingProfileIndex).amplitudeTriggerMode);
    }

    private Component amplitudeTriggerModeMessage(Component label, int mode) {
        return CommonComponents.optionNameValue(label, amplitudeTriggerModeComponent(mode));
    }

    private static Component amplitudeTriggerModeComponent(int mode) {
        if (mode == Config.AMPLITUDE_TRIGGER_VELOCITY) {
            return Component.translatable("option.elytrapitchhelper.amplitude_trigger.velocity");
        }
        if (mode == Config.AMPLITUDE_TRIGGER_EITHER) {
            return Component.translatable("option.elytrapitchhelper.amplitude_trigger.either");
        }
        return Component.translatable("option.elytrapitchhelper.amplitude_trigger.height");
    }

    private Component profileSortMessage() {
        return CommonComponents.optionNameValue(Component.translatable("option.elytrapitchhelper.profile_sort"),
                profileSortComponent(config.profileSortMode()));
    }

    private static Component profileSortComponent(int mode) {
        if (mode == Config.PROFILE_SORT_NAME) {
            return Component.translatable("option.elytrapitchhelper.profile_sort.name");
        }
        if (mode == Config.PROFILE_SORT_MODIFIED) {
            return Component.translatable("option.elytrapitchhelper.profile_sort.modified");
        }
        return Component.translatable("option.elytrapitchhelper.profile_sort.created");
    }

    private void openProfileJson(int profileIndex) {
        flushSaveIfPending();
        Path path = config.getProfilePath(profileIndex);
        if (!PlatformFileOpener.open(path)) {
            sendOverlay(Component.translatable("message.elytrapitchhelper.profile.open_failed"));
        }
    }

    private void sendOverlay(Component message) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(message, true);
        }
    }

    private void save() {
        ClientConfigStore.update(config);
        configRevision = ClientConfigStore.revision();
        savePending = true;
        saveAfterMillis = System.currentTimeMillis() + SAVE_DEBOUNCE_MILLIS;
    }

    private void saveProfileChange() {
        config.applyProfileIfActive(editingProfileIndex);
        save();
    }

    private void flushSaveIfDue() {
        if (savePending && System.currentTimeMillis() >= saveAfterMillis) {
            flushSaveIfPending();
        }
    }

    private void flushSaveIfPending() {
        if (!savePending) {
            return;
        }
        ClientConfigStore.set(config);
        configRevision = ClientConfigStore.revision();
        savePending = false;
    }

    private void loadConfigSnapshot() {
        config = ClientConfigStore.get().copy();
        configRevision = ClientConfigStore.revision();
        savePending = false;
    }

    private void refreshConfigSnapshot() {
        if (savePending) {
            return;
        }
        if (configRevision != ClientConfigStore.revision()) {
            loadConfigSnapshot();
            syncEditingProfileIndex();
        }
    }

    private int refreshConfigSnapshot(String profileFile) {
        refreshConfigSnapshot();
        return config.profileIndexByFileName(profileFile);
    }

    private void syncEditingProfileIndex() {
        if (config.profileCount() <= 0) {
            editingProfileIndex = 0;
            editingProfileFile = null;
            return;
        }

        if (editingProfileFile != null) {
            int resolvedIndex = config.profileIndexByFileName(editingProfileFile);
            if (resolvedIndex >= 0) {
                editingProfileIndex = resolvedIndex;
                return;
            }
        }

        editingProfileIndex = Math.max(0, Math.min(editingProfileIndex, config.profileCount() - 1));
        if (editingProfile) {
            editingProfileFile = config.profile(editingProfileIndex).fileName;
        }
    }

    private boolean isTextFieldFocused() {
        return getFocused() instanceof EditBox;
    }

    private final class ProfileScrollBar extends AbstractScrollArea {
        private final int profileCount;

        private ProfileScrollBar(int x, int y, int width, int height, int profileCount, int visibleRows,
                int scrollRow) {
            super(x, y, width, height, Component.translatable("screen.elytrapitchhelper.profile.scrollbar"));
            this.profileCount = profileCount;
            setScrollRow(scrollRow);
        }

        @Override
        protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
            renderScrollbar(context, mouseX, mouseY);
        }

        @Override
        protected int contentHeight() {
            return profileCount * ROW_HEIGHT;
        }

        @Override
        protected double scrollRate() {
            return ROW_HEIGHT;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        private void setScrollRow(int scrollRow) {
            super.setScrollAmount(scrollRow * ROW_HEIGHT);
        }
    }

    private static final class MarqueeEditBox extends EditBox {
        private final Font textRenderer;
        private int textColor = EditBox.DEFAULT_TEXT_COLOR;
        private String marqueeValue = "";
        private int marqueeInnerWidth = -1;
        private long marqueeStartedMillis = System.currentTimeMillis();
        private boolean marqueeHovered;

        private MarqueeEditBox(Font font, int x, int y, int width, int height, Component message) {
            super(font, x, y, width, height, message);
            this.textRenderer = font;
        }

        @Override
        public void setTextColor(int textColor) {
            super.setTextColor(textColor);
            this.textColor = textColor;
        }

        @Override
        public void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
            String value = getValue();
            int innerWidth = getInnerWidth();
            int textWidth = textRenderer.width(value);
            boolean hovered = isHovered();
            if (!hovered || isFocused() || value.isEmpty() || textWidth <= innerWidth) {
                marqueeHovered = false;
                super.renderWidget(context, mouseX, mouseY, delta);
                return;
            }

            updateMarqueeStart(value, innerWidth, !marqueeHovered);
            marqueeHovered = true;
            super.setTextColor(TRANSPARENT_TEXT_COLOR);
            try {
                super.renderWidget(context, mouseX, mouseY, delta);
            } finally {
                super.setTextColor(textColor);
            }

            int textX = isBordered() ? getX() + 4 : getX();
            int textY = isBordered() ? getY() + (getHeight() - 8) / 2 : getY();
            int overflow = textWidth - innerWidth;
            int offset = marqueeOffset(overflow);
            context.enableScissor(textX, getY(), textX + innerWidth, getY() + getHeight());
            context.drawString(textRenderer, value, textX - offset, textY, textColor, true);
            context.disableScissor();
        }

        private void updateMarqueeStart(String value, int innerWidth, boolean forceRestart) {
            if (forceRestart || !value.equals(marqueeValue) || innerWidth != marqueeInnerWidth) {
                marqueeValue = value;
                marqueeInnerWidth = innerWidth;
                marqueeStartedMillis = System.currentTimeMillis();
            }
        }

        private int marqueeOffset(int overflow) {
            long travelMillis = Math.max(1L, overflow * 1000L / PROFILE_NAME_MARQUEE_PIXELS_PER_SECOND);
            long cycleMillis = travelMillis * 2L + PROFILE_NAME_MARQUEE_EDGE_HOLD_MILLIS * 2L;
            long elapsed = (System.currentTimeMillis() - marqueeStartedMillis) % cycleMillis;
            if (elapsed < PROFILE_NAME_MARQUEE_EDGE_HOLD_MILLIS) {
                return 0;
            }

            elapsed -= PROFILE_NAME_MARQUEE_EDGE_HOLD_MILLIS;
            if (elapsed < travelMillis) {
                return Math.round(overflow * (elapsed / (float) travelMillis));
            }

            elapsed -= travelMillis;
            if (elapsed < PROFILE_NAME_MARQUEE_EDGE_HOLD_MILLIS) {
                return overflow;
            }

            elapsed -= PROFILE_NAME_MARQUEE_EDGE_HOLD_MILLIS;
            return Math.round(overflow * (1.0F - elapsed / (float) travelMillis));
        }
    }

    private record ProfileScrollGeometry(int x, int y, int width, int height, int scrollerY, int scrollerHeight,
            int maxScrollAmount, int movableHeight) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY < y + height;
        }

        private boolean containsScroller(double mouseY) {
            return mouseY >= scrollerY && mouseY < scrollerY + scrollerHeight;
        }
    }

    private enum Category {
        GENERAL("screen.elytrapitchhelper.category.general"),
        PITCH("screen.elytrapitchhelper.category.pitch"),
        AMPLITUDE("screen.elytrapitchhelper.category.amplitude"),
        VISUALS("screen.elytrapitchhelper.category.visuals");

        private final String translationKey;

        Category(String translationKey) {
            this.translationKey = translationKey;
        }
    }
}
