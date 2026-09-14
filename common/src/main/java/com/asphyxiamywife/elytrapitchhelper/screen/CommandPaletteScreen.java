package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.client.ClientConfigStore;
import com.asphyxiamywife.elytrapitchhelper.client.ConfigStore;
import com.asphyxiamywife.elytrapitchhelper.client.KeyBindings;
import com.asphyxiamywife.elytrapitchhelper.config.CommandPaletteAppearanceSettings;
import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.FlatEditBox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.List;

public final class CommandPaletteScreen extends Screen implements ConfigWorkflowChild {
    private static final int MAX_BLUR_RADIUS = 10;

    private static final float SETTINGS_SCRIM_BOOST = 1.4f;

    private final Screen lastScreen;

    @Override
    public Screen configWorkflowParent() {
        return lastScreen;
    }
    private EditBox searchBox;
    private List<PaletteAction> actions = List.of();
    private List<PaletteAction> results = List.of();
    private int selectedIndex;
    private Integer previousMenuBlurRadius;
    private PaletteState cachedPaletteState;
    private long cachedPaletteRevision = Long.MIN_VALUE;
    private ConfigStore cachedStore;
    private boolean paletteActionsDirty;

    public CommandPaletteScreen(Screen lastScreen) {
        super(Component.translatable("palette.elytrapitchhelper.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        applyBlurAmount();
        int panelWidth = panelWidth();
        int panelX = panelX();
        int panelY = panelY();
        searchBox = new FlatEditBox(font, panelX + CommandPalettePreviewRenderer.SEARCH_MARGIN,
                panelY + CommandPalettePreviewRenderer.SEARCH_MARGIN,
                panelWidth - CommandPalettePreviewRenderer.SEARCH_MARGIN * 2,
                CommandPalettePreviewRenderer.SEARCH_HEIGHT,
                Component.translatable("palette.elytrapitchhelper.search"))
                .withColors(CommandPalettePreviewRenderer.searchFieldColor(appearance()),
                        CommandPalettePreviewRenderer.argb(255, appearance().accentColorRgb()));
        searchBox.setMaxLength(128);
        searchBox.setHint(Component.translatable("palette.elytrapitchhelper.search_hint"));
        searchBox.setResponder(query -> refreshResults());
        addRenderableWidget(searchBox);
        setFocused(searchBox);
        searchBox.setFocused(true);
        refreshResults();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (lastScreen != null) {
            lastScreen.extractBackground(context, mouseX, mouseY, delta);
        } else {
            super.extractBackground(context, mouseX, mouseY, delta);
        }
        minecraft.gui.hud.extractDeferredSubtitles();
    }

    @Override
    public void removed() {
        restoreMenuBlurAmount();
        super.removed();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        refreshResultsIfNeeded();
        CommandPaletteAppearanceSettings appearance = appearance();
        if (lastScreen != null) {
            lastScreen.extractRenderState(context, mouseX, mouseY, delta);
            context.nextStratum();
        }
        context.fill(0, 0, width, height,
                CommandPalettePreviewRenderer.argb(scrimAlpha(appearance.shadowOpacity(),
                        lastScreen instanceof ConfigScreen), 0));

        int panelX = panelX();
        int panelY = panelY();
        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        CommandPalettePreviewRenderer.drawPanel(context, panelX, panelY, panelWidth, panelHeight,
                appearance);

        super.extractRenderState(context, mouseX, mouseY, delta);
        CommandPalettePreviewRenderer.drawActionRows(context, font, panelX, panelY, panelWidth, results,
                selectedIndex, appearance);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        refreshResultsIfNeeded();
        if (KeyBindings.handleCommandPaletteShortcut(minecraft, event)) {
            return true;
        }
        if (event.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (event.key() == InputConstants.KEY_DOWN) {
            moveSelection(1);
            return true;
        }
        if (event.key() == InputConstants.KEY_UP) {
            moveSelection(-1);
            return true;
        }
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            executeSelected();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        refreshResultsIfNeeded();
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        int row = rowAt(event.x(), event.y());
        if (row >= 0) {
            selectedIndex = row;
            executeSelected();
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && !insidePanel(event.x(), event.y())) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(lastScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isInGameUi() {
        return lastScreen == null || lastScreen.isInGameUi();
    }

    private void refreshResults() {
        actions = PaletteActions.available(paletteState());
        String query = searchBox == null ? "" : searchBox.getValue();
        results = PaletteActions.search(actions, query);
        int resultCapacity = resultCapacity(height);
        if (results.size() > resultCapacity) {
            results = results.subList(0, resultCapacity);
        }
        if (searchBox != null) {
            searchBox.setY(panelY() + CommandPalettePreviewRenderer.SEARCH_MARGIN);
        }
        selectedIndex = MathUtil.clamp(selectedIndex, 0, Math.max(0, results.size() - 1));
        paletteActionsDirty = false;
    }

    private void refreshResultsIfNeeded() {
        paletteState();
        if (paletteActionsDirty) {
            refreshResults();
        }
    }

    private void moveSelection(int delta) {
        if (results.isEmpty()) {
            selectedIndex = 0;
            return;
        }
        selectedIndex = Math.floorMod(selectedIndex + delta, results.size());
    }

    private void executeSelected() {
        if (results.isEmpty()) {
            return;
        }
        PaletteAction action = results.get(selectedIndex);
        PaletteState state = paletteState();
        PaletteActions.recordUse(action, state, minecraft);
        action.execute(minecraft, state);
        if (minecraft.gui.screen() == this) {
            minecraft.gui.setScreen(lastScreen);
        }
    }

    private int rowAt(double mouseX, double mouseY) {
        int panelY = panelY();
        int rowX = panelX() + CommandPalettePreviewRenderer.ROW_MARGIN;
        int rowY = CommandPalettePreviewRenderer.firstRowY(panelY);
        int rowWidth = panelWidth() - CommandPalettePreviewRenderer.ROW_MARGIN * 2;
        if (mouseX < rowX || mouseX > rowX + rowWidth || mouseY < rowY) {
            return -1;
        }
        int offset = (int) (mouseY - rowY);
        int stride = CommandPalettePreviewRenderer.ROW_HEIGHT + CommandPalettePreviewRenderer.ROW_GAP;
        int row = offset / stride;
        if (row < 0 || row >= results.size() || offset % stride >= CommandPalettePreviewRenderer.ROW_HEIGHT) {
            return -1;
        }
        return row;
    }

    private boolean insidePanel(double mouseX, double mouseY) {
        return ScreenGeometry.contains(panelX(), panelY(), panelWidth(), panelHeight(), mouseX, mouseY);
    }

    private int panelWidth() {
        return fittedPanelWidth(width);
    }

    private int panelX() {
        return (width - panelWidth()) / 2;
    }

    private int panelY() {
        return fittedPanelY(height, panelHeight());
    }

    private int panelHeight() {
        int rows = MathUtil.clamp(results.size(), 1, CommandPalettePreviewRenderer.MAX_RESULTS);
        return CommandPalettePreviewRenderer.panelHeight(rows);
    }

    static int resultCapacity(int screenHeight) {
        int availableHeight = Math.max(1, screenHeight - 16);
        for (int rows = CommandPalettePreviewRenderer.MAX_RESULTS; rows > 1; rows--) {
            if (CommandPalettePreviewRenderer.panelHeight(rows) <= availableHeight) {
                return rows;
            }
        }
        return 1;
    }

    static int fittedPanelWidth(int screenWidth) {
        int safeWidth = Math.max(1, screenWidth);
        int preferred = Math.max(CommandPalettePreviewRenderer.PANEL_MIN_WIDTH,
                safeWidth - CommandPalettePreviewRenderer.PANEL_MARGIN * 2);
        return Math.min(safeWidth,
                Math.min(CommandPalettePreviewRenderer.PANEL_MAX_WIDTH, preferred));
    }

    static int fittedPanelY(int screenHeight, int panelHeight) {
        int preferred = Math.max(18, screenHeight / 5);
        return Math.max(0, Math.min(preferred, screenHeight - panelHeight - 8));
    }

    private CommandPaletteAppearanceSettings appearance() {
        return paletteState().appearance();
    }

    private PaletteState paletteState() {
        if (lastScreen instanceof ConfigScreen configScreen) {
            boolean replacingCachedState = cachedPaletteState != null;
            if (cachedPaletteState == null
                    || !cachedPaletteState.config().hasSamePersistedSnapshot(configScreen.paletteConfig())) {
                cachedPaletteState = PaletteState.capture(lastScreen);
                paletteActionsDirty |= replacingCachedState;
            }
            return cachedPaletteState;
        }
        var currentStore = ClientConfigStore.store();
        long configViewRevision = currentStore.stateRevision();
        if (cachedPaletteState == null || cachedStore != currentStore || cachedPaletteRevision != configViewRevision) {
            boolean replacingCachedState = cachedPaletteState != null;
            cachedPaletteState = PaletteState.capture(lastScreen, currentStore.get());
            cachedPaletteRevision = configViewRevision;
            cachedStore = currentStore;
            paletteActionsDirty |= replacingCachedState;
        }
        return cachedPaletteState;
    }

    static int scrimAlpha(int shadowOpacity, boolean overSettings) {
        int alpha = MathUtil.clamp(shadowOpacity, 0, 100) * 255 / 100;
        if (!overSettings) {
            return alpha;
        }
        return Math.min(255, Math.round(alpha * SETTINGS_SCRIM_BOOST));
    }

    static int blurRadius(int blurAmount) {
        int clampedAmount = MathUtil.clamp(blurAmount, 0, 100);
        return Math.round(clampedAmount * MAX_BLUR_RADIUS / 100.0F);
    }

    private void applyBlurAmount() {
        if (previousMenuBlurRadius == null) {
            previousMenuBlurRadius = minecraft.options.getMenuBackgroundBlurriness();
        }
        minecraft.options.menuBackgroundBlurriness().set(blurRadius(appearance().blurAmount()));
    }

    private void restoreMenuBlurAmount() {
        if (previousMenuBlurRadius == null) {
            return;
        }
        minecraft.options.menuBackgroundBlurriness().set(previousMenuBlurRadius);
        previousMenuBlurRadius = null;
    }
}
