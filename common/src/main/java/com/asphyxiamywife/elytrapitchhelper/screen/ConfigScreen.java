package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.client.ClientConfigStore;
import com.asphyxiamywife.elytrapitchhelper.client.KeyBindings;
import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import com.asphyxiamywife.elytrapitchhelper.config.SectionCollapseSettings;
import com.asphyxiamywife.elytrapitchhelper.config.PrideFlag;
import com.asphyxiamywife.elytrapitchhelper.config.VoidWarningSettings;
import com.asphyxiamywife.elytrapitchhelper.hud.PitchGuideHud;
import com.asphyxiamywife.elytrapitchhelper.platform.PlatformFileOpener;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.CycleRow;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.ScreenRow;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.SectionHeader;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.SettingRow;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.DropdownButton;
import com.asphyxiamywife.elytrapitchhelper.util.MonotonicClock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.FittingMultiLineTextWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public final class ConfigScreen extends Screen implements ProfileEditorPanel.Host, ProfileListPanel.Host,
        ConfigScreenSaveController.Host, ConfigProfileDeletionController.Host, ConfigScreenHistoryController.Host,
        ConfigScreenPaletteController.Host, ConfigProfileScrollController.Host,
        ConfigEditorScrollController.Host, ConfigProfileResetController.Host {
    static final int CONTROL_HEIGHT = 20;
    static final int CONTROL_GAP = 6;
    static final int ROW_HEIGHT = 24;
    static final int READ_ONLY_CONTENT_OFFSET = 40;
    static final int FOOTER_OFFSET = 28;
    static final Duration TOOLTIP_DELAY = Duration.ofMillis(350L);
    static final int PROFILE_NAME_MAX_LENGTH = 512;
    private final Screen lastScreen;
    private final boolean closePaletteSettingsToLastScreen;
    private final ConfigScreenSaveController saves;
    private final ConfigProfileDeletionController profileDeletes;
    private final ConfigScreenHistoryController history;
    private final ConfigEditSession edits;
    private final ConfigScreenPaletteController palette;
    private final ValueScroll valueScroll = new ValueScroll();
    private final SectionAnimation sectionAnimation = new SectionAnimation();
    private ProfileEditorPanel editorPanel;
    private String pendingHistorySettingFocus;
    private ProfileListPanel listPanel;
    private final ConfigProfileScrollController profileScrolling;
    private final ConfigEditorScrollController editorScrolling;
    private final ConfigProfileResetController profileReset;
    private final SettingControlFactory controls;
    private Config config;
    private final ConfigNavigation navigation = new ConfigNavigation();
    private boolean deleteMode;
    private final ProfileListSelectionController listSelection;
    private boolean buildingWidgets;
    private boolean handingOffToChildScreen;
    private final DropdownOverlayController dropdowns = new DropdownOverlayController();
    private final EditorSearchController editorSearch;
    private String selectedVoidWarningDimensionKey;
    private AbstractWidget pendingFocus;
    private final EditorHints.Hint editorHint;
    private boolean restoredRememberedScroll;
    private static EditorHints.Hint lastEditorHint;

    public ConfigScreen(Screen lastScreen) {
        this(lastScreen, false);
    }

    public static ConfigScreen profiles(Screen lastScreen) {
        return profiles(lastScreen, false);
    }

    static ConfigScreen profiles(Screen lastScreen, boolean closePaletteSettingsToLastScreen) {
        return new ConfigScreen(lastScreen, closePaletteSettingsToLastScreen, Surface.PROFILES);
    }

    ConfigScreen(Screen lastScreen, boolean closePaletteSettingsToLastScreen) {
        this(lastScreen, closePaletteSettingsToLastScreen, Surface.EDITOR);
    }

    private ConfigScreen(Screen lastScreen, boolean closePaletteSettingsToLastScreen, Surface surface) {
        super(Component.translatable("screen.elytrapitchhelper.config.title"));
        this.lastScreen = lastScreen;
        this.closePaletteSettingsToLastScreen = closePaletteSettingsToLastScreen;
        this.config = ClientConfigStore.get().copy();
        this.saves = new ConfigScreenSaveController(this);
        this.profileDeletes = new ConfigProfileDeletionController(this);
        this.history = new ConfigScreenHistoryController(this);
        this.palette = new ConfigScreenPaletteController(this);
        this.profileScrolling = new ConfigProfileScrollController(this);
        this.editorScrolling = new ConfigEditorScrollController(this);
        this.listSelection = new ProfileListSelectionController(() -> config, () -> deleteMode,
                profileScrolling::scrollIntoView, this::rebuildWidgets);
        this.profileReset = new ConfigProfileResetController(this);
        this.edits = new ConfigEditSession(history, profileReset::clear, this::saveInternal,
                this::recordLocalSettingEdit);
        this.editorSearch = new EditorSearchController(() -> config, sectionAnimation,
                this::sectionRowCount, this::saveViewPreference);
        this.controls = new SettingControlFactory(() -> font, history.sliderListener(),
                this::openChildScreen, TOOLTIP_DELAY);
        this.editorHint = EditorHints.pick(config.usedSettingsSearch, lastEditorHint,
                java.util.concurrent.ThreadLocalRandom.current());
        lastEditorHint = this.editorHint;
        if (surface == Surface.EDITOR) {
            openActiveProfileEditor();
        }
        history.resetBaseline();
    }

    private void openActiveProfileEditor() {
        navigation.openEditor(config, config.activeProfileIndex);
        editorSearch.setCategory(ConfigCategory.GENERAL);
    }

    @Override
    protected void init() {
        buildingWidgets = true;
        try {
            buildWidgets();
        } finally {
            buildingWidgets = false;
        }
    }

    private void buildWidgets() {
        dropdowns.clear();
        SettingRow.setOverlayOpen(dropdowns::anyOpen);
        editorSearch.widgetsRebuilt();
        if (!saves.pending() && saves.hasExternalRevision()) {
            loadConfigSnapshot();
            clearHistory();
        }
        syncEditingProfileIndex();
        restoreRememberedScroll();
        if (config.isReadOnly()) {
            initReadOnlyWarning();
        }
        if (editingProfile()) {
            initProfileEditor();
        } else {
            initProfileList();
        }
    }

    private void restoreRememberedScroll() {
        if (restoredRememberedScroll) {
            return;
        }
        restoredRememberedScroll = true;
        profileScrolling.restore(ScrollMemory.profileList());
        if (editingProfile()) {
            restoreEditorScroll();
        }
    }

    private void restoreEditorScroll() {
        syncSectionFolds();
        editorScrolling.restore(ScrollMemory.editor(editingProfileFile()));
    }

    @Override
    public SectionAnimation sectionAnimation() {
        return sectionAnimation;
    }

    private void saveViewPreference() {
        if (config.isReadOnly()) {
            return;
        }
        save();
    }

    private int sectionRowCount(ConfigCategory section) {
        if (editingProfileIndex() < 0 || editingProfileIndex() >= config.profileCount()) {
            return 0;
        }
        return com.asphyxiamywife.elytrapitchhelper.config.schema.SettingsRegistry
                .visible(section, config.profile(editingProfileIndex())).size();
    }

    private void initProfileList() {
        editorPanel = null;
        editorSearch.setPendingSetting(null);
        listPanel = new ProfileListPanel(this);
        listPanel.init();
    }

    private void initProfileEditor() {
        syncSectionFolds();
        listPanel = null;
        editorPanel = new ProfileEditorPanel(this);
        editorPanel.init();
        editorPanel.restoreSettingFocus(pendingHistorySettingFocus);
        pendingHistorySettingFocus = null;
    }

    private void syncSectionFolds() {
        for (ConfigCategory section : ConfigCategory.values()) {
            sectionAnimation.syncTo(section, !config.sectionCollapse.isCollapsed(section));
        }
    }

    private void initReadOnlyWarning() {
        int warningWidth = Math.max(40, width - 40);
        FittingMultiLineTextWidget warning = new FittingMultiLineTextWidget(20, 40, warningWidth, 30,
                Component.translatable("screen.elytrapitchhelper.config.read_only_reason",
                        config.readOnlyWarning()),
                font);
        addRenderableWidget(warning);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        Component renderedTitle = editingProfile()
                ? null
                : Component.translatable("screen.elytrapitchhelper.profiles.title");
        if (editorPanel != null) {
            editorScrolling.advanceAnimation(MonotonicClock.millis());
            editorPanel.advanceAnimation(MonotonicClock.millis());
            editorPanel.extractBackdrop(context);
        } else if (listPanel != null) {
            listPanel.extractBackdrop(context);
        }
        if (renderedTitle != null) {
            context.drawCenteredString(font, renderedTitle, width / 2, 15, 0xFFFFFF);
        }
        if (config.isReadOnly()) {
            context.drawCenteredString(font,
                    Component.translatable("screen.elytrapitchhelper.config.newer_version_read_only"),
                    width / 2, 28, 0xFFFF5555);
        }
        boolean dropdownOpen = dropdowns.anyOpen();
        int contentMouseX = dropdownOpen ? -1 : mouseX;
        int contentMouseY = dropdownOpen ? -1 : mouseY;
        ProfileEditorPanel panel = editorPanel;
        if (panel != null) {
            panel.extractRowsClipped(context, contentMouseX, contentMouseY, delta, editorLayout());
        }
        try {
            super.render(context, contentMouseX, contentMouseY, delta);
        } finally {
            if (panel != null) {
                panel.restoreRows();
            }
        }
        if (listPanel != null) {
            listPanel.extractChevrons(context);
        }
        dropdowns.extractOverlays(context, font, mouseX, mouseY);
    }

    @Override
    public void onClose() {
        if (deleteMode) {
            requestCancelProfileDeletes();
            return;
        }
        if (!editingProfile() && listSelection.selectedFile() != null) {
            listSelection.select(null);
            return;
        }
        ConfigNavigation.Entry back = navigation.back();
        if (back == null) {
            closeToLastScreen();
            return;
        }
        switchSurface(() -> {
            if (back.editor()) {
                showEditor(back.profileFile());
            } else {
                showProfiles();
            }
        });
    }

    private void switchSurface(Runnable move) {
        profileReset.clear();
        editorSearch.setPendingSetting(null);
        listSelection.restore(null);
        selectedVoidWarningDimensionKey = null;
        editorSearch.setQuery("");
        editorSearch.setSearchBox(null);
        refreshConfigSnapshot();
        move.run();
        history.updateContextBaseline();
        rebuildWidgets();
    }

    private void showProfiles() {
        rememberEditorScroll();
        navigation.openProfiles();
        editorScrolling.setDirect(0);
    }

    private void showEditor(String profileFile) {
        if (config.profileCount() <= 0) {
            return;
        }
        rememberEditorScroll();
        navigation.openEditor(config, profileFile);
        editorSearch.setCategory(ConfigCategory.GENERAL);
        editorScrolling.setDirect(0);
        restoreEditorScroll();
    }

    private void rememberEditorScroll() {
        if (editingProfile() && editingProfileFile() != null) {
            ScrollMemory.rememberEditor(editingProfileFile(), editorScrolling.scroll());
        }
    }

    @Override
    public void openProfileList() {
        if (!editingProfile()) {
            return;
        }
        navigation.rememberCurrent();
        switchSurface(this::showProfiles);
    }

    @Override
    public void switchEditingProfile(String profileFile) {
        if (!editingProfile() || profileFile == null || profileFile.equals(editingProfileFile())) {
            return;
        }
        switchSurface(() -> showEditor(profileFile));
    }

    @Override
    public boolean editingProfileIsActive() {
        return editingProfile() && config.profileCount() > 0 && config.isActiveProfile(editingProfileIndex());
    }

    @Override
    public void makeEditingProfileActive() {
        if (!editingProfile() || config.isReadOnly()) {
            return;
        }
        int resolvedIndex = refreshConfigSnapshot(editingProfileFile());
        if (resolvedIndex < 0) {
            rebuildWidgets();
            return;
        }
        edits.changeConfig("active-profile", () -> config.selectProfile(resolvedIndex));
        rebuildWidgets();
    }

    private void finishConfig() {
        if (deleteMode) {
            requestCancelProfileDeletes();
            return;
        }
        navigation.clearBackStack();
        closeToLastScreen();
    }

    private void closeToLastScreen() {
        saves.leaveAfterSaving();
    }

    @Override
    public void leaveConfigWorkflow() {
        profileReset.clear();
        clearHistory();
        minecraft.setScreen(lastScreen);
    }

    @Override
    public void removed() {
        rememberScroll();
        if (handingOffToChildScreen) {
            handingOffToChildScreen = false;
        } else {
            saves.workflowRemoved();
            profileReset.clear();
            profileDeletes.restoreIfAbandoned();
        }
        super.removed();
    }

    private void rememberScroll() {
        if (editingProfile()) {
            ScrollMemory.rememberEditor(editingProfileFile(), editorScrolling.scroll());
        }
        ScrollMemory.rememberProfileList(profileScrolling.scroll());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (dropdowns.handleMouseScrolled(mouseX, mouseY, scrollY, height)) {
            return true;
        }
        if (ValueScroll.isAdjusting(minecraft)) {
            return applyValueScroll(mouseX, mouseY, scrollX, scrollY);
        }
        valueScroll.reset();
        if (profileScrolling.handleWheel(scrollY)) {
            return true;
        }
        return editorScrolling.handleWheel(scrollY);
    }

    private boolean applyValueScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        int steps = valueScroll.steps(scrollX, scrollY);
        if (steps == 0) {
            return true;
        }
        return ValueScroll.replay(steps,
                direction -> super.mouseScrolled(mouseX, mouseY, 0.0, direction));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (dropdowns.handleMouseClicked(event, height)) {
            return true;
        }
        if (handleCycleButtonRightClick(event)) {
            return true;
        }
        if (profileScrolling.startDrag(event) || editorScrolling.startDrag(event)) {
            return true;
        }
        ProfileEditorPanel panel = editorPanel;
        boolean held = panel != null && panel.hideRowsOutside(event.y(), editorLayout());
        try {
            return super.mouseClicked(event, doubleClick) || selectProfileAt(event.x(), event.y());
        } finally {
            if (held) {
                panel.restoreRows();
            }
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (profileScrolling.drag(event) || editorScrolling.drag(event)) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (profileScrolling.release(event) || editorScrolling.release(event)) {
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (handleExplicitSaveShortcut(event)) {
            return true;
        }
        if (KeyBindings.handleCommandPaletteShortcut(minecraft, event, () -> {
            history.finishActiveAction();
            handingOffToChildScreen = true;
            return true;
        })) {
            return true;
        }
        if (dropdowns.handleKeyPressed(event)) {
            return true;
        }
        if (handleSettingsSearchShortcut(event)) {
            return true;
        }
        if (!isTextFieldFocused() && history.handleShortcut(event)) {
            return true;
        }
        if ((event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)
                && finishFocusedTextField()) {
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && finishFocusedTextField()) {
            return true;
        }
        if (handleRowFocusKey(event)) {
            return true;
        }
        if (handleEditorScrollKey(event)) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (editorSearch.consumeShortcutCharacter(event.codepoint())) {
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public int profileScroll() {
        return profileScrolling.scroll();
    }

    @Override
    public boolean profileScrollbarDragging() {
        return profileScrolling.dragging();
    }

    @Override
    public boolean editorScrollbarDragging() {
        return editorScrolling.dragging();
    }

    @Override
    public void setProfileScroll(int nextScroll) {
        profileScrolling.set(nextScroll);
    }

    @Override
    public void setProfileScrollDirect(int profileScroll) {
        profileScrolling.setDirect(profileScroll);
    }

    @Override
    public boolean deleteMode() {
        return deleteMode;
    }

    @Override
    public void setDeleteMode(boolean deleteMode) {
        this.deleteMode = deleteMode;
    }

    @Override
    public boolean hasMarkedProfiles() {
        return profileDeletes.hasMarks();
    }

    @Override
    public boolean isProfileMarkedForDelete(String profileFile) {
        return profileDeletes.isMarked(profileFile);
    }

    @Override
    public boolean canMarkProfileForDelete(String profileFile) {
        return profileDeletes.canMark(profileFile);
    }

    @Override
    public Component commitProfileDeletesLabel() {
        return profileDeletes.commitLabel();
    }

    private ProfileListLayout profileListLayout() {
        return ProfileListLayout.of(width, height, profileListPlan().slotCount(), config.isReadOnly(),
                profileScroll());
    }

    @Override
    public ProfileListPlan profileListPlan() {
        return listSelection.plan();
    }

    @Override
    public ProfileRowControl.Pending pendingRowFocus() {
        return listSelection.takePendingFocus();
    }

    private boolean selectProfileAt(double mouseX, double mouseY) {
        if (editingProfile() || deleteMode || config.profileCount() <= 0) {
            return false;
        }
        return listSelection.selectAt(profileListLayout(), mouseX, mouseY);
    }

    @Override
    public void setFocused(GuiEventListener focused) {
        super.setFocused(focused);
        if (buildingWidgets || editingProfile() || deleteMode || history.isRestoring()
                || !(focused instanceof AbstractWidget widget)) {
            return;
        }
        listSelection.focus(widget, profileListLayout());
    }

    @Override
    public int editorContentHeight() {
        if (!editingProfile() || editingProfileIndex() < 0 || editingProfileIndex() >= config.profileCount()) {
            return 0;
        }
        return ProfileEditorPlan.contentHeight(config.profile(editingProfileIndex()), editorSearch.query(),
                config.sectionCollapse, key -> Component.translatable(key).getString(), sectionAnimation, ROW_HEIGHT);
    }

    @Override
    public int profileViewportHeight() {
        return profileListLayout().viewportHeight();
    }

    @Override
    public int maxProfileScroll() {
        return profileListLayout().maxScroll();
    }

    @Override
    public boolean readOnly() {
        return config.isReadOnly();
    }

    @Override
    public void toggleProfileDeleteMark(String profileFile) {
        profileDeletes.toggleMark(profileFile);
    }

    @Override
    public void requestCommitProfileDeletes() {
        profileDeletes.requestCommit();
    }

    @Override
    public void requestCancelProfileDeletes() {
        profileDeletes.requestCancel();
    }

    @Override
    public boolean editingProfile() {
        return navigation.editingProfile();
    }

    @Override
    public int profileSlotCount() {
        return profileListPlan().slotCount();
    }

    @Override
    public void blurFocusedEditBox() {
        if (getFocused() instanceof EditBox editBox) {
            setFocused(null);
            editBox.setFocused(false);
        }
    }

    @Override
    public void setScreenDragging(boolean dragging) {
        setDragging(dragging);
    }

    @Override
    public void tick() {
        super.tick();
        saves.flushIfDue();
        if (!saves.pending() && saves.hasExternalRevision() && !isTextFieldFocused()) {
            loadConfigSnapshot();
            clearHistory();
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
    public void replaceConfig(Config config) {
        this.config = config;
    }

    @Override
    public Minecraft minecraftClient() {
        return minecraft;
    }

    @Override
    public int editingProfileIndex() {
        return navigation.editingProfileIndex();
    }

    @Override
    public String editingProfileFile() {
        return navigation.editingProfileFile();
    }

    @Override
    public void showConfirmation(net.minecraft.client.gui.screens.ConfirmScreen screen) {
        openChildScreen(screen);
    }

    @Override
    public boolean openChildScreen(Screen screen) {
        handingOffToChildScreen = true;
        minecraft.setScreen(screen);
        return true;
    }

    @Override
    public void returnToConfigScreen() {
        minecraft.setScreen(this);
    }

    @Override
    public ConfigCategory category() {
        return editorSearch.category();
    }

    @Override
    public void setCategory(ConfigCategory category) {
        editorSearch.setCategory(category);
        history.updateContextBaseline();
    }

    @Override
    public void rebuildWidgets() {
        super.rebuildWidgets();
    }

    @Override
    public void addWidget(AbstractWidget widget) {
        register(widget, config.isReadOnly());
    }

    @Override
    public void addNavigationWidget(AbstractWidget widget) {
        register(widget, false);
    }

    private void register(AbstractWidget widget, boolean lockForReadOnly) {
        if (widget instanceof DropdownButton<?> dropdown) {
            dropdowns.register(dropdown);
        }
        if (lockForReadOnly) {
            widget.active = false;
        }
        addRenderableWidget(widget);
    }

    @Override
    public String settingsSearchQuery() {
        return editorSearch.query();
    }

    @Override
    public void setSettingsSearchQuery(String query) {
        editorSearch.setQuery(query);
    }

    @Override
    public void setSettingsSearchBox(EditBox searchBox) {
        editorSearch.setSearchBox(searchBox);
    }

    @Override
    public EditorHints.Hint editorHint() {
        return editorHint;
    }

    @Override
    public void recordSettingsSearchUse() {
        if (config.usedSettingsSearch) {
            return;
        }
        config.usedSettingsSearch = true;
        saveViewPreference();
    }

    @Override
    public void focusSettingControl(SettingsSearch.Entry entry, AbstractWidget widget) {
        if (!isPendingSettingFocus(entry)) {
            return;
        }
        setFocused(widget);
        widget.setFocused(true);
        editorSearch.setPendingSetting(null);
    }

    @Override
    public void setPendingSettingFocus(SettingsSearch.Entry entry) {
        editorSearch.setPendingSetting(entry);
    }

    @Override
    public boolean isPendingSettingFocus(SettingsSearch.Entry entry) {
        return editorSearch.isPendingSetting(entry);
    }

    @Override
    public ProfileEditorLayout editorLayout() {
        return editorScrolling.layout();
    }

    @Override
    public int editorRowCount() {
        if (!editingProfile() || editingProfileIndex() < 0 || editingProfileIndex() >= config.profileCount()) {
            return 0;
        }
        return ProfileEditorPlan.rowCount(config.profile(editingProfileIndex()), editorSearch.query(),
                config.sectionCollapse, key -> Component.translatable(key).getString(), sectionAnimation);
    }

    @Override
    public SectionCollapseSettings sectionCollapse() {
        return config.sectionCollapse;
    }

    @Override
    public void toggleSection(ConfigCategory section) {
        refreshConfigSnapshot();
        config.sectionCollapse = config.sectionCollapse.toggled(section);
        sectionAnimation.setExpanded(section, !config.sectionCollapse.isCollapsed(section),
                sectionRowCount(section));
        saveViewPreference();
        rebuildWidgets();
    }

    @Override
    public void layoutEditorRows() {
        if (editorPanel != null) editorPanel.applyRowLayout();
    }

    @Override
    public void setEditorScrollDirect(int scroll) {
        editorScrolling.setDirect(scroll);
    }

    @Override
    public void scrollEditorRowIntoView(int rowIndex) {
        editorScrolling.scrollRowIntoView(rowIndex);
    }

    @Override
    public AbstractWidget focusedWidget() {
        return getFocused() instanceof AbstractWidget widget ? widget : null;
    }

    @Override
    public void focusWidget(AbstractWidget widget) {
        setFocused(widget);
        widget.setFocused(true);
    }

    @Override
    public void focusAfterRebuild(AbstractWidget widget) {
        pendingFocus = widget;
    }

    @Override
    protected void setInitialFocus() {
        AbstractWidget widget = pendingFocus;
        pendingFocus = null;
        if (widget != null && widget.visible) {
            focusWidget(widget);
            return;
        }
        super.setInitialFocus();
    }

    private boolean handleRowFocusKey(KeyEvent event) {
        if (!editingProfile() || editorPanel == null) {
            return false;
        }
        if (!(getFocused() instanceof SettingRow) && !(getFocused() instanceof SectionHeader)) {
            return false;
        }
        boolean backwards = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        return switch (event.key()) {
            case GLFW.GLFW_KEY_DOWN -> editorPanel.moveRowFocus(1);
            case GLFW.GLFW_KEY_UP -> editorPanel.moveRowFocus(-1);
            case GLFW.GLFW_KEY_TAB -> editorPanel.moveRowFocus(backwards ? -1 : 1);
            default -> false;
        };
    }

    @Override
    public ConfigCategory takePendingSectionScroll() {
        return editorSearch.takePendingSection();
    }

    private boolean handleEditorScrollKey(KeyEvent event) {
        if (!editingProfile() || getFocused() instanceof EditBox) {
            return false;
        }
        return switch (event.key()) {
            case GLFW.GLFW_KEY_PAGE_DOWN -> editorScrolling.scrollPage(1);
            case GLFW.GLFW_KEY_PAGE_UP -> editorScrolling.scrollPage(-1);
            case GLFW.GLFW_KEY_HOME -> editorScrolling.scrollToEdge(true);
            case GLFW.GLFW_KEY_END -> editorScrolling.scrollToEdge(false);
            default -> false;
        };
    }

    private boolean handleSettingsSearchShortcut(KeyEvent event) {
        return editingProfile() && editorSearch.handleShortcut(event, getFocused(), this::setFocused);
    }

    private boolean handleExplicitSaveShortcut(KeyEvent event) {
        if (!ScreenShortcuts.isPrimary(event) || event.key() != GLFW.GLFW_KEY_S) {
            return false;
        }
        blurFocusedEditBox();
        saves.explicitSave();
        history.breakCoalescing();
        return true;
    }

    private boolean handleCycleButtonRightClick(MouseButtonEvent event) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return false;
        }
        List<? extends GuiEventListener> children = children();
        for (int i = children.size() - 1; i >= 0; i--) {
            GuiEventListener child = children.get(i);
            if (child instanceof CycleButton<?> cycleButton && cycleButton.active && cycleButton.visible
                    && child.isMouseOver(event.x(), event.y())) {
                cycleButton.mouseScrolled(event.x(), event.y(), 0.0, 1.0);
                cycleButton.playDownSound(minecraft.getSoundManager());
                return true;
            }
        }
        return false;
    }

    @Override
    public SettingControlFactory controls() {
        return controls;
    }

    @Override
    public <T extends AbstractWidget> T tooltip(T widget, String translationKey) {
        return controls.tooltip(widget, translationKey);
    }

    @Override
    public Tooltip profileMetadataTooltip(int profileIndex) {
        return Tooltip.create(Component.translatable("tooltip.elytrapitchhelper.profile.metadata",
                ScreenText.relativeTime(config.profileLastModifiedAtMillis(profileIndex)),
                ScreenText.profileBasedOn(config.profileBasedOn(profileIndex))));
    }

    private boolean finishFocusedTextField() {
        if (!(getFocused() instanceof EditBox editBox)) {
            return false;
        }

        if (editBox == editorSearch.searchBox()) {
            setFocused(null);
            editBox.setFocused(false);
            return true;
        }

        boolean shouldReload = editBox.getValue().isBlank()
                || saves.hasExternalRevision();
        setFocused(null);
        editBox.setFocused(false);
        if (shouldReload) {
            saves.flushAsyncIfPending(() -> {
                loadConfigSnapshot();
                clearHistory();
                syncEditingProfileIndex();
                rebuildWidgets();
            });
        }
        history.breakCoalescing();
        return true;
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
    public Integer detectedDefaultVoidY(String dimensionKey) {
        if (minecraft == null || minecraft.level == null || dimensionKey == null
                || !dimensionKey.equals(currentDimensionKey())) {
            return null;
        }
        return PitchGuideHud.defaultVoidY(minecraft.level.getMinY());
    }

    @Override
    public ColorEditorScreen createColorEditor(Component label, ColorSettingBinding binding) {
        var state = binding.value();
        return new ColorEditorScreen(settingReturnScreen(), label, state.color() & 0x00FFFFFF,
                state.prideEnabled(), PrideFlag.byId(state.prideFlagId()).id(), state.customPrideColors(),
                binding.previewLineLength(), binding.previewLineWidth(), binding.previewCuePeak(),
                binding::setColor, binding::setPride, this::commitWhenLeavingNestedSettings, historyKey(label),
                () -> {
                    var current = binding.value();
                    return new ColorEditorScreen.ColorState(current.color(), current.prideEnabled(),
                            current.prideFlagId(), current.customPrideColors());
                }, nestedHistoryController());
    }

    private Screen settingReturnScreen() {
        return closePaletteSettingsToLastScreen ? lastScreen : this;
    }

    @Override
    public void toggleEnabled() {
        refreshConfigSnapshot();
        edits.changeConfig("enabled", () -> config.enabled = !config.enabled);
        rebuildWidgets();
    }

    @Override
    public void reconcileResetUndo() {
        profileReset.reconcile();
    }

    static boolean resetStateMatches(Config current, Config resetApplied, String profileFile) {
        return ConfigProfileResetController.stateMatches(current, resetApplied, profileFile);
    }

    @Override
    public CycleRow amplitudeTriggerModeButton(Component label, String optionName,
            String actionKey) {
        return controls.amplitudeTriggerModeButton(label,
                () -> config.profile(editingProfileIndex()).amplitude().triggerMode(),
                () -> cycleAmplitudeTriggerMode(optionName, actionKey, true),
                () -> cycleAmplitudeTriggerMode(optionName, actionKey, false));
    }

    private void cycleAmplitudeTriggerMode(String optionName, String actionKey, boolean forward) {
        edits.changeProfile(optionName, actionKey, () -> {
            Profile current = config.profile(editingProfileIndex());
            int mode = forward
                    ? Config.nextAmplitudeTriggerMode(current.amplitude().triggerMode())
                    : Config.previousAmplitudeTriggerMode(current.amplitude().triggerMode());
            replaceEditingProfile(current.withAmplitude(current.amplitude().withTriggerMode(mode)));
        });
    }

    public CycleRow voidWarningModeButton(Component label, String optionName,
            String actionKey) {
        return controls.voidWarningModeButton(label,
                () -> config.profile(editingProfileIndex()).voidWarning().mode(),
                () -> cycleVoidWarningMode(optionName, actionKey, true),
                () -> cycleVoidWarningMode(optionName, actionKey, false));
    }

    private void cycleVoidWarningMode(String optionName, String actionKey, boolean forward) {
        edits.changeProfile(optionName, actionKey, () -> {
            Profile current = config.profile(editingProfileIndex());
            int mode = forward
                    ? VoidWarningSettings.nextMode(current.voidWarning().mode())
                    : VoidWarningSettings.previousMode(current.voidWarning().mode());
            replaceEditingProfile(current.withVoidWarning(current.voidWarning().withMode(mode)));
        });
        rebuildWidgets();
    }

    @Override
    public ScreenRow commandPaletteAppearanceButton() {
        return new ScreenRow(Component.translatable("option.elytrapitchhelper.command_palette_appearance"),
                font, 0, () -> openCommandPaletteAppearanceScreen());
    }

    @Override
    public boolean openCommandPaletteAppearanceScreen() {
        return openChildScreen(new CommandPaletteAppearanceScreen(settingReturnScreen(),
                () -> config.profile(editingProfileIndex()),
                updated -> {
                    replaceEditingProfile(updated);
                    saveProfileChange("command_palette_appearance");
                }, this::commitWhenLeavingNestedSettings,
                nestedHistoryController()));
    }

    private ColorEditorScreen.HistoryController nestedHistoryController() {
        return new ColorEditorScreen.HistoryController() {
            @Override
            public void begin(String actionKey) {
                beginHistoryAction(actionKey);
            }

            @Override
            public void end(boolean changed, boolean coalesce) {
                commitHistoryAction(changed, coalesce);
            }

            @Override
            public boolean undo() {
                return history.restore(false);
            }

            @Override
            public boolean redo() {
                return history.restore(true);
            }

            @Override
            public void breakCoalescing() {
                breakHistoryCoalescing();
            }

            @Override
            public boolean acceptExternalRevision() {
                return history.acceptExternalRevision();
            }
        };
    }

    @Override
    public Component profileSortValue() {
        return profileSortComponent(config.profileSortMode());
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
        Path path = config.getProfilePath(ClientConfigStore.store().fileSystem(), profileIndex);
        saves.flushAsyncIfPending(() -> PlatformFileOpener.openAsync(path)
                .thenAcceptAsync(opened -> {
                    if (!opened) {
                        sendOverlay(Component.translatable("message.elytrapitchhelper.profile.open_failed"));
                    }
                }, minecraft::execute));
    }

    @Override
    public void sendOverlay(Component message) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(message, true);
        }
    }

    @Override
    public void save() {
        saveInternal("config-edit");
    }

    @Override
    public void saveProfileDeletes(List<String> profileFiles) {
        saves.scheduleProfileDeletes(profileFiles);
    }

    @Override
    public void saveInternal(String actionKey) {
        if (config.isReadOnly()) {
            sendOverlay(Component.translatable("message.elytrapitchhelper.config.newer_version_read_only"));
            return;
        }
        history.recordImplicit(actionKey);
        saves.scheduleSave();
    }

    public void saveProfileChange() {
        edits.profileChanged(null);
    }

    public void saveProfileChange(String optionName) {
        edits.profileChanged(optionName);
    }

    @Override
    public void replaceEditingProfile(Profile profile) {
        config.replaceProfile(editingProfileIndex(), profile);
    }

    @Override
    public void performProfileChange(String optionName, String actionKey, Runnable change) {
        edits.changeProfile(optionName, actionKey, change);
    }

    @Override
    public void finishEditing() {
        finishConfig();
    }

    @Override
    public void flushSaveAsync() {
        saves.flushAsyncIfPending(() -> {
        });
    }

    private void commitWhenLeavingNestedSettings(Runnable afterCommit) {
        if (!closePaletteSettingsToLastScreen) {
            afterCommit.run();
            return;
        }
        saves.flushAsyncIfPending(afterCommit);
    }

    @Override
    public void loadConfigSnapshot() {
        saves.loadSnapshot();
    }

    @Override
    public boolean savePending() {
        return saves.pending();
    }

    @Override
    public boolean hasExternalRevision() {
        return saves.hasExternalRevision();
    }

    @Override
    public void refreshConfigSnapshot() {
        saves.refreshSnapshot();
    }

    @Override
    public int refreshConfigSnapshot(String profileFile) {
        refreshConfigSnapshot();
        return config.profileIndexByFileName(profileFile);
    }

    @Override
    public void beginProfileDeletion(String profileFile) {
        profileDeletes.beginDeletion(profileFile);
    }

    @Override
    public void requestProfileReset(String profileFile) {
        if (config.isReadOnly()) {
            return;
        }
        if (refreshConfigSnapshot(profileFile) < 0) {
            rebuildWidgets();
            return;
        }
        profileReset.requestReset(profileFile);
    }

    @Override
    public void editProfile(int profileIndex) {
        String profileFile = config.profile(profileIndex).fileName();
        navigation.rememberProfiles();
        switchSurface(() -> showEditor(profileFile));
    }

    @Override
    public void closeScreen() {
        finishConfig();
    }

    Config paletteConfig() {
        return config;
    }

    @Override
    public boolean historyRestoring() {
        return history.isRestoring();
    }

    @Override
    public void copyPaletteUsageToHistoryBaseline() {
        history.copyPaletteUsageToBaseline();
    }

    @Override
    public void scheduleSave() {
        if (config.isReadOnly()) {
            return;
        }
        saves.scheduleSave();
    }

    void recordPaletteActionUse(String actionId, long nowMillis) {
        palette.recordActionUse(actionId, nowMillis);
    }

    private void recordLocalSettingEdit(String optionName) {
        palette.recordLocalSettingEdit(optionName);
    }

    boolean paletteDeleteMode() {
        return deleteMode;
    }

    @Override
    public int paletteCurrentProfileIndex() {
        if (config.profileCount() <= 0) {
            return 0;
        }
        syncEditingProfileIndex();
        if (editingProfile()) {
            return config.clampProfileIndex(editingProfileIndex());
        }
        return config.clampProfileIndex(config.activeProfileIndex);
    }

    boolean paletteCanUndo() {
        return history.canUndo();
    }

    boolean paletteCanRedo() {
        return history.canRedo();
    }

    void openProfilesFromPalette() {
        if (editingProfile()) {
            navigation.rememberCurrent();
        }
        switchSurface(this::showProfiles);
    }

    void openCategoryFromPalette(ConfigCategory category) {
        if (!openProfileEditorFromPalette(paletteCurrentProfileIndex())) {
            return;
        }
        editorSearch.jumpToCategory(category);
        history.updateContextBaseline();
        rebuildWidgets();
    }

    void expandAllSectionsFromPalette() {
        if (!openProfileEditorFromPalette(paletteCurrentProfileIndex())) {
            return;
        }
        refreshConfigSnapshot();
        config.sectionCollapse = config.sectionCollapse.expandedAll();
        for (ConfigCategory section : ConfigCategory.values()) {
            sectionAnimation.setExpanded(section, true, sectionRowCount(section));
        }
        editorScrolling.setDirect(0);
        saveViewPreference();
        rebuildWidgets();
    }

    void openSettingFromPalette(SettingsSearch.Entry entry) {
        if (!openProfileEditorFromPalette(paletteCurrentProfileIndex())) {
            return;
        }
        boolean opensScreen = editorSearch.jumpToSetting(entry);
        history.updateContextBaseline();
        rebuildWidgets();
        if (opensScreen && !palette.openSettingScreen(entry)) {
            editorSearch.setPendingSetting(entry);
            rebuildWidgets();
        }
    }

    void toggleSettingFromPalette(SettingsSearch.Entry entry) {
        if (config.isReadOnly() || deleteMode || config.profileCount() <= 0) {
            return;
        }
        int profileIndex = paletteCurrentProfileIndex();
        edits.changeProfileIfChanged(null, entry.spec().actionKey(),
                () -> PaletteActions.toggleSetting(config, profileIndex, entry));
        rebuildWidgets();
    }

    boolean paletteCanResetVoidY() {
        if (!editingProfile() || selectedVoidWarningDimensionKey == null || config.profileCount() <= 0) {
            return false;
        }
        int profileIndex = paletteCurrentProfileIndex();
        return config.profile(profileIndex).voidWarning().dimensionYOverrides()
                .containsKey(selectedVoidWarningDimensionKey);
    }

    void resetSettingFromPalette(SettingsSearch.Entry entry) {
        if (config.isReadOnly() || deleteMode || config.profileCount() <= 0) {
            return;
        }
        int profileIndex = paletteCurrentProfileIndex();
        Profile profile = config.profile(profileIndex);
        boolean dynamicVoid = "void_y_mode".equals(entry.spec().id()) && paletteCanResetVoidY();
        var replacement = dynamicVoid
                ? SettingResetPlan.voidDimension(profile, selectedVoidWarningDimensionKey)
                : SettingResetPlan.setting(profile, entry.spec().id());
        if (replacement.isEmpty()) {
            return;
        }
        edits.changeProfile(entry.spec().id(), "reset-" + entry.spec().id(), () -> {
            config.replaceProfile(profileIndex, replacement.get());
            editorSearch.setPendingSetting(dynamicVoid
                    || entry.paletteOpenMode() != SettingsSearch.PaletteOpenMode.OPEN_SCREEN ? entry : null);
        });
        rebuildWidgets();
    }

    void toggleEnabledFromPalette() {
        if (config.isReadOnly()) {
            return;
        }
        refreshConfigSnapshot();
        edits.changeConfig("enabled", () -> config.enabled = !config.enabled);
        sendOverlay(Component.translatable("message.elytrapitchhelper.toggle." + (config.enabled ? "on" : "off")));
        rebuildWidgets();
    }

    void switchProfileFromPalette(String profileFile) {
        if (config.isReadOnly()) {
            return;
        }
        int resolvedIndex = refreshConfigSnapshot(profileFile);
        if (resolvedIndex < 0) {
            rebuildWidgets();
            return;
        }
        edits.changeConfig("active-profile", () -> config.selectProfile(resolvedIndex));
        rebuildWidgets();
    }

    void duplicateCurrentProfileFromPalette() {
        if (config.isReadOnly()) {
            return;
        }
        refreshConfigSnapshot();
        if (config.profileCount() <= 0) {
            return;
        }
        int sourceIndex = paletteCurrentProfileIndex();
        config.duplicateProfile(sourceIndex);
        setProfileScroll(maxProfileScroll());
        save();
        checkpointHistory();
        rebuildWidgets();
    }

    void requestResetCurrentProfileFromPalette() {
        if (config.isReadOnly()) {
            return;
        }
        if (!openProfileEditorFromPalette(paletteCurrentProfileIndex())) {
            return;
        }
        profileReset.requestReset();
    }

    boolean paletteCanUndoReset() {
        return profileReset.hasUndo();
    }

    void undoResetFromPalette() {
        profileReset.undo();
    }

    void openCurrentProfileJsonFromPalette() {
        if (config.profileCount() <= 0) {
            return;
        }
        openProfileJson(paletteCurrentProfileIndex());
    }

    void undoFromPalette() {
        history.restore(false);
    }

    void redoFromPalette() {
        history.restore(true);
    }

    private boolean openProfileEditorFromPalette(int profileIndex) {
        if (deleteMode || config.profileCount() <= 0) {
            return false;
        }
        profileReset.clear();
        syncEditingProfileIndex();
        if (!editingProfile()) {
            navigation.rememberProfiles();
        }
        rememberEditorScroll();
        navigation.openEditor(config, profileIndex);
        selectedVoidWarningDimensionKey = null;
        return true;
    }

    @Override
    public void syncEditingProfileIndex() {
        navigation.sync(config);
    }

    private enum Surface {
        EDITOR,
        PROFILES
    }

    private boolean isTextFieldFocused() {
        return getFocused() instanceof EditBox;
    }

    private static String historyKey(Component label) {
        return TextMatch.slug(label.getString());
    }

    @Override
    public void beginHistoryAction(String actionKey) {
        history.begin(actionKey);
    }

    @Override
    public void commitHistoryAction(boolean changed, boolean coalesce) {
        history.commit(changed, coalesce);
    }

    @Override
    public void breakHistoryCoalescing() {
        history.breakCoalescing();
    }

    @Override
    public void checkpointHistory() {
        clearHistory();
    }

    @Override
    public ConfigHistory.Context historyContext() {
        return new ConfigHistory.Context(editingProfile(), editingProfileFile(), editorSearch.category(),
                selectedVoidWarningDimensionKey, profileScrolling.scroll(), editorScrolling.scroll(),
                deleteMode, listSelection.selectedFile(), profileDeletes.markedFiles(),
                editingProfile() && editorPanel != null ? editorPanel.focusedSetting() : null);
    }

    @Override
    public void updateHistoryContextBaseline() {
        history.updateContextBaseline();
    }

    @Override
    public void restoreHistoryContext(ConfigHistory.Context context) {
        pendingHistorySettingFocus = context.editingProfile() ? context.focusedSetting() : null;
        navigation.restore(context.editingProfile(), context.profileFile());
        editorSearch.setCategory(context.category() == null ? ConfigCategory.GENERAL : context.category());
        selectedVoidWarningDimensionKey = context.dimensionKey();
        profileScrolling.restore(context.profileScroll());
        editorScrolling.restore(context.editorScroll());
        deleteMode = context.deleteMode();
        listSelection.restore(context.selectedProfile());
        profileDeletes.restoreContext(context.markedProfiles());
    }

    @Override
    public void resetHistoryBaseline() {
        history.resetBaseline();
    }

    @Override
    public void clearHistory() {
        history.clear();
    }

}
