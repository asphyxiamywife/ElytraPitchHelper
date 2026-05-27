package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.client.ClientConfigStore;
import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import com.asphyxiamywife.elytrapitchhelper.config.PrideFlag;
import com.asphyxiamywife.elytrapitchhelper.config.StagedProfileDelete;
import com.asphyxiamywife.elytrapitchhelper.platform.PlatformFileOpener;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.ColorEditButton;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.NumberSlider;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
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

public final class ConfigScreen extends Screen implements ProfileEditorPanel.Host, ProfileListPanel.Host {
    static final int CONTROL_HEIGHT = 20;
    static final int CONTROL_GAP = 6;
    static final int ROW_HEIGHT = 24;
    static final Duration TOOLTIP_DELAY = Duration.ofMillis(350L);
    private static final long SAVE_DEBOUNCE_MILLIS = 750L;
    static final int PROFILE_NAME_MAX_LENGTH = 512;
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
    private ConfigCategory category = ConfigCategory.GENERAL;

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
        new ProfileListPanel(this).init();
    }

    private void initProfileEditor() {
        new ProfileEditorPanel(this).init();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        Component renderedTitle = editingProfile
                ? Component.translatable("screen.elytrapitchhelper.profile_editor.title",
                        config.profileName(editingProfileIndex))
                : Component.translatable("screen.elytrapitchhelper.profiles.title");
        context.centeredText(font, renderedTitle, width / 2, 15, 0xFFFFFF);
        super.extractRenderState(context, mouseX, mouseY, delta);
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

    @Override
    public int profileScroll() {
        return profileScroll;
    }

    @Override
    public void setProfileScroll(int nextScroll) {
        nextScroll = Math.max(0, Math.min(nextScroll, maxProfileScroll()));
        if (nextScroll != profileScroll) {
            profileScroll = nextScroll;
            rebuildWidgets();
        }
    }

    @Override
    public boolean deleteMode() {
        return deleteMode;
    }

    @Override
    public boolean hasStagedProfileDeletes() {
        return !stagedProfileDeletes.isEmpty();
    }

    @Override
    public int visibleProfileRows() {
        return Math.min(config.profileCount(), Math.max(3, (height - 156) / ROW_HEIGHT));
    }

    private int maxProfileScroll() {
        return Math.max(0, config.profileCount() - visibleProfileRows());
    }

    @Override
    public void stageProfileDelete(String profileFile) {
        int resolvedIndex = config.profileIndexByFileName(profileFile);
        StagedProfileDelete deleted = resolvedIndex < 0 ? null : config.stageDeleteProfile(resolvedIndex);
        if (deleted != null) {
            stagedProfileDeletes.add(deleted);
            syncEditingProfileIndex();
            profileScroll = Math.max(0, Math.min(profileScroll, maxProfileScroll()));
        }
        rebuildWidgets();
    }

    @Override
    public void undoProfileDelete() {
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

    @Override
    public void requestCommitProfileDeletes() {
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

    @Override
    public void requestCancelProfileDeletes() {
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

    @Override
    public int screenWidth() {
        return width;
    }

    @Override
    public int screenHeight() {
        return height;
    }

    @Override
    public Font font() {
        return font;
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public int editingProfileIndex() {
        return editingProfileIndex;
    }

    @Override
    public ConfigCategory category() {
        return category;
    }

    @Override
    public void setCategory(ConfigCategory category) {
        this.category = category;
    }

    @Override
    public void rebuildWidgets() {
        super.rebuildWidgets();
    }

    @Override
    public void addWidget(AbstractWidget widget) {
        addRenderableWidget(widget);
    }

    @Override
    public <T extends AbstractWidget> T tooltip(T widget, String translationKey) {
        widget.setTooltip(Tooltip.create(Component.translatable(translationKey)));
        widget.setTooltipDelay(TOOLTIP_DELAY);
        return widget;
    }

    @Override
    public Tooltip profileMetadataTooltip(int profileIndex) {
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

    @Override
    public AbstractSliderButton floatSlider(Component label, double current, double min, double max, double step,
            String suffix, DoubleConsumer onChange) {
        return new NumberSlider(0, 0, 1, CONTROL_HEIGHT, label, current, min, max, step,
                value -> ScreenText.formatDecimal(value) + " " + suffix, onChange);
    }

    @Override
    public AbstractSliderButton intSlider(Component label, int current, int min, int max, String suffix,
            IntConsumer onChange) {
        return new NumberSlider(0, 0, 1, CONTROL_HEIGHT, label, current, min, max, 1.0,
                value -> (int) Math.round(value) + " " + suffix, value -> onChange.accept((int) Math.round(value)));
    }

    @Override
    public Button colorButton(Component label, int current, boolean prideEnabled, String prideFlagId,
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

    @Override
    public void openResetProfileConfirmation() {
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

    @Override
    public Button amplitudeTriggerModeButton(Component label, Profile profile) {
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

    @Override
    public Component profileSortMessage() {
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

    @Override
    public void openProfileJson(int profileIndex) {
        flushSaveIfPending();
        Path path = config.getProfilePath(profileIndex);
        if (!PlatformFileOpener.open(path)) {
            sendOverlay(Component.translatable("message.elytrapitchhelper.profile.open_failed"));
        }
    }

    private void sendOverlay(Component message) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.sendOverlayMessage(message);
        }
    }

    @Override
    public void save() {
        ClientConfigStore.update(config);
        configRevision = ClientConfigStore.revision();
        savePending = true;
        saveAfterMillis = System.currentTimeMillis() + SAVE_DEBOUNCE_MILLIS;
    }

    @Override
    public void saveProfileChange() {
        config.applyProfileIfActive(editingProfileIndex);
        save();
    }

    @Override
    public void closeEditor() {
        onClose();
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

    @Override
    public void refreshConfigSnapshot() {
        if (savePending) {
            return;
        }
        if (configRevision != ClientConfigStore.revision()) {
            loadConfigSnapshot();
            syncEditingProfileIndex();
        }
    }

    @Override
    public int refreshConfigSnapshot(String profileFile) {
        refreshConfigSnapshot();
        return config.profileIndexByFileName(profileFile);
    }

    @Override
    public void enterDeleteMode() {
        flushSaveIfPending();
        stagedProfileDeletes.clear();
        deleteMode = true;
        rebuildWidgets();
    }

    @Override
    public void editProfile(int profileIndex) {
        editingProfileIndex = profileIndex;
        editingProfileFile = config.profile(profileIndex).fileName;
        editingProfile = true;
        category = ConfigCategory.GENERAL;
        rebuildWidgets();
    }

    @Override
    public void closeScreen() {
        onClose();
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

    static final class ProfileScrollBar extends AbstractScrollArea {
        private final int profileCount;

        ProfileScrollBar(int x, int y, int width, int height, int profileCount, int visibleRows,
                int scrollRow) {
            super(x, y, width, height, Component.translatable("screen.elytrapitchhelper.profile.scrollbar"),
                    AbstractScrollArea.defaultSettings(ROW_HEIGHT));
            this.profileCount = profileCount;
            setScrollRow(scrollRow);
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            extractScrollbar(context, mouseX, mouseY);
        }

        @Override
        protected int contentHeight() {
            return profileCount * ROW_HEIGHT;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        private void setScrollRow(int scrollRow) {
            super.setScrollAmount(scrollRow * ROW_HEIGHT);
        }
    }

    static final class MarqueeEditBox extends EditBox {
        private final Font textRenderer;
        private int textColor = EditBox.DEFAULT_TEXT_COLOR;
        private String marqueeValue = "";
        private int marqueeInnerWidth = -1;
        private long marqueeStartedMillis = System.currentTimeMillis();
        private boolean marqueeHovered;

        MarqueeEditBox(Font font, int x, int y, int width, int height, Component message) {
            super(font, x, y, width, height, message);
            this.textRenderer = font;
        }

        @Override
        public void setTextColor(int textColor) {
            super.setTextColor(textColor);
            this.textColor = textColor;
        }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            String value = getValue();
            int innerWidth = getInnerWidth();
            int textWidth = textRenderer.width(value);
            boolean hovered = isHovered();
            if (!hovered || isFocused() || value.isEmpty() || textWidth <= innerWidth) {
                marqueeHovered = false;
                super.extractWidgetRenderState(context, mouseX, mouseY, delta);
                return;
            }

            updateMarqueeStart(value, innerWidth, !marqueeHovered);
            marqueeHovered = true;
            super.setTextColor(TRANSPARENT_TEXT_COLOR);
            try {
                super.extractWidgetRenderState(context, mouseX, mouseY, delta);
            } finally {
                super.setTextColor(textColor);
            }

            int textX = isBordered() ? getX() + 4 : getX();
            int textY = isBordered() ? getY() + (getHeight() - 8) / 2 : getY();
            int overflow = textWidth - innerWidth;
            int offset = marqueeOffset(overflow);
            context.enableScissor(textX, getY(), textX + innerWidth, getY() + getHeight());
            context.text(textRenderer, value, textX - offset, textY, textColor, true);
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

}
