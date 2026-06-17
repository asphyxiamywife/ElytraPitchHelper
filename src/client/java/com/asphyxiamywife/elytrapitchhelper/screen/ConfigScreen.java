package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.client.ClientConfigStore;
import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.ConfigSaveException;
import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import com.asphyxiamywife.elytrapitchhelper.config.PrideFlag;
import com.asphyxiamywife.elytrapitchhelper.config.StagedProfileDelete;
import com.asphyxiamywife.elytrapitchhelper.config.VoidWarningSettings;
import com.asphyxiamywife.elytrapitchhelper.platform.PlatformFileOpener;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.ColorEditButton;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.CyclingOptionButton;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.DropdownButton;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.NumberSlider;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.Tooltip;
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
    private final DropdownOverlayController dropdowns = new DropdownOverlayController();
    private ConfigCategory category = ConfigCategory.GENERAL;
    private String selectedVoidWarningDimensionKey;

    public ConfigScreen(Screen lastScreen) {
        super(Component.translatable("screen.elytrapitchhelper.config.title"));
        this.lastScreen = lastScreen;
        this.config = ClientConfigStore.get().copy();
        this.configRevision = ClientConfigStore.revision();
    }

    @Override
    protected void init() {
        dropdowns.clear();
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
        dropdowns.extractOverlays(context, font, mouseX, mouseY);
    }

    @Override
    public void onClose() {
        if (editingProfile) {
            if (!flushSaveIfPending()) {
                return;
            }
            editingProfile = false;
            editingProfileFile = null;
            selectedVoidWarningDimensionKey = null;
            refreshConfigSnapshot();
            rebuildWidgets();
        } else if (deleteMode) {
            requestCancelProfileDeletes();
        } else {
            if (!flushSaveIfPending()) {
                return;
            }
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
        if (dropdowns.handleMouseScrolled(mouseX, mouseY, scrollY, height)) {
            return true;
        }
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        if (!editingProfile && config.profileCount() > 0) {
            int maxScroll = maxProfileScroll();
            int nextScroll = Math.max(0, Math.min(maxScroll, profileScroll - (int) Math.signum(scrollY)));
            if (nextScroll != profileScroll) {
                profileScroll = nextScroll;
                rebuildWidgets();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (dropdowns.handleMouseClicked(event, height)) {
            return true;
        }
        if (handleCycleButtonRightClick(event)) {
            return true;
        }
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
        if (dropdowns.handleKeyPressed(event)) {
            return true;
        }
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
                ? event.y() - geometry.scrollerY()
                : geometry.scrollerHeight() / 2.0;
        scrollProfileListToMouse(event.y());
        return true;
    }

    private void scrollProfileListToMouse(double mouseY) {
        ProfileScrollGeometry geometry = profileScrollGeometry();
        if (geometry == null || geometry.movableHeight() <= 0 || geometry.maxScrollAmount() <= 0) {
            stopProfileScrollbarDrag();
            return;
        }

        double scrollerTop = Math.max(0.0,
                Math.min(geometry.movableHeight(), mouseY - geometry.y() - profileScrollbarGrabOffset));
        double scrollAmount = scrollerTop * geometry.maxScrollAmount() / geometry.movableHeight();
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
        if (widget instanceof DropdownButton<?> dropdown) {
            dropdowns.register(dropdown);
        }
        addRenderableWidget(widget);
    }

    private boolean handleCycleButtonRightClick(MouseButtonEvent event) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return false;
        }
        List<? extends GuiEventListener> children = children();
        for (int i = children.size() - 1; i >= 0; i--) {
            GuiEventListener child = children.get(i);
            if (child instanceof CycleButton<?> cycleButton && child.isMouseOver(event.x(), event.y())) {
                cycleButton.mouseScrolled(event.x(), event.y(), 0.0, 1.0);
                cycleButton.playDownSound(minecraft.getSoundManager());
                return true;
            }
        }
        return false;
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
        if (!flushSaveIfPending()) {
            return true;
        }
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
                value -> formatIntValue(value, suffix), value -> onChange.accept((int) Math.round(value)));
    }

    private static String formatIntValue(double value, String suffix) {
        int rounded = (int) Math.round(value);
        return suffix == null || suffix.isBlank() ? Integer.toString(rounded) : rounded + " " + suffix;
    }

    @Override
    public String currentDimensionKey() {
        return minecraft != null && minecraft.level != null
                ? minecraft.level.dimension().identifier().toString()
                : null;
    }

    @Override
    public String selectedVoidWarningDimensionKey() {
        return selectedVoidWarningDimensionKey;
    }

    @Override
    public void setSelectedVoidWarningDimensionKey(String dimensionKey) {
        selectedVoidWarningDimensionKey = dimensionKey;
    }

    @Override
    public Button colorButton(Component label, int current, boolean prideEnabled, String prideFlagId,
            int[] customPrideColors,
            IntSupplier previewLineLength, IntSupplier previewLineWidth, boolean previewCuePeak,
            IntConsumer onChange, ColorEditorScreen.PrideSettingsConsumer onPrideChange) {
        int color = current & 0x00FFFFFF;
        PrideFlag prideFlag = PrideFlag.byId(prideFlagId);
        Component value = prideEnabled
                ? Component.translatable("option.elytrapitchhelper.color.pride_value",
                        Component.translatable(prideFlag.translationKey()))
                : ScreenText.colorComponent(color);
        int[] stripeColors = prideEnabled ? PrideFlag.colorsFor(prideFlag.id(), customPrideColors) : null;
        return new ColorEditButton(CommonComponents.optionNameValue(label, value), color, stripeColors,
                button -> minecraft.setScreen(new ColorEditorScreen(this, label, color, prideEnabled,
                        prideFlag.id(), customPrideColors, previewLineLength.getAsInt(),
                        previewLineWidth.getAsInt(), previewCuePeak, onChange, onPrideChange,
                        this::flushSaveIfPending)));
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
        CyclingOptionButton[] buttonRef = new CyclingOptionButton[1];
        CyclingOptionButton button = new CyclingOptionButton(0, 0, 1, CONTROL_HEIGHT,
                amplitudeTriggerModeMessage(label), () -> {
                    profile.amplitude.triggerMode = Config.nextAmplitudeTriggerMode(profile.amplitude.triggerMode);
                    buttonRef[0].setMessage(amplitudeTriggerModeMessage(label, profile.amplitude.triggerMode));
                    saveProfileChange();
                }, () -> {
                    profile.amplitude.triggerMode = Config.previousAmplitudeTriggerMode(profile.amplitude.triggerMode);
                    buttonRef[0].setMessage(amplitudeTriggerModeMessage(label, profile.amplitude.triggerMode));
                    saveProfileChange();
                });
        buttonRef[0] = button;
        button.setMessage(amplitudeTriggerModeMessage(label, profile.amplitude.triggerMode));
        return button;
    }

    @Override
    public Button voidWarningModeButton(Component label, Profile profile) {
        CyclingOptionButton[] buttonRef = new CyclingOptionButton[1];
        CyclingOptionButton button = new CyclingOptionButton(0, 0, 1, CONTROL_HEIGHT,
                voidWarningModeMessage(label), () -> {
                    profile.voidWarning.mode = VoidWarningSettings.nextMode(profile.voidWarning.mode);
                    buttonRef[0].setMessage(voidWarningModeMessage(label, profile.voidWarning.mode));
                    saveProfileChange();
                    rebuildWidgets();
                }, () -> {
                    profile.voidWarning.mode = VoidWarningSettings.previousMode(profile.voidWarning.mode);
                    buttonRef[0].setMessage(voidWarningModeMessage(label, profile.voidWarning.mode));
                    saveProfileChange();
                    rebuildWidgets();
                });
        buttonRef[0] = button;
        button.setMessage(voidWarningModeMessage(label, profile.voidWarning.mode));
        return button;
    }

    private Component amplitudeTriggerModeMessage(Component label) {
        return amplitudeTriggerModeMessage(label, config.profile(editingProfileIndex).amplitude.triggerMode);
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

    private Component voidWarningModeMessage(Component label) {
        return voidWarningModeMessage(label, config.profile(editingProfileIndex).voidWarning.mode);
    }

    private Component voidWarningModeMessage(Component label, int mode) {
        return CommonComponents.optionNameValue(label, voidWarningModeComponent(mode));
    }

    private static Component voidWarningModeComponent(int mode) {
        if (mode == VoidWarningSettings.MODE_SIMPLE_HEIGHT) {
            return Component.translatable("option.elytrapitchhelper.void_mode.simple");
        }
        return Component.translatable("option.elytrapitchhelper.void_mode.predicted");
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
        if (!flushSaveIfPending()) {
            return;
        }
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

    private boolean flushSaveIfPending() {
        if (!savePending) {
            return true;
        }
        try {
            ClientConfigStore.set(config);
            configRevision = ClientConfigStore.revision();
            savePending = false;
            return true;
        } catch (ConfigSaveException e) {
            saveAfterMillis = System.currentTimeMillis() + SAVE_DEBOUNCE_MILLIS;
            sendOverlay(Component.translatable("message.elytrapitchhelper.config.save_failed"));
            return false;
        }
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
        if (!flushSaveIfPending()) {
            return;
        }
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
        selectedVoidWarningDimensionKey = null;
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

}
