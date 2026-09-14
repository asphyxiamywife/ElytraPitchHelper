package com.asphyxiamywife.elytrapitchhelper.screen;

import static com.asphyxiamywife.elytrapitchhelper.util.MathUtil.clamp;
import static com.asphyxiamywife.elytrapitchhelper.screen.ColorMath.parseHexColor;

import com.asphyxiamywife.elytrapitchhelper.config.PrideFlag;
import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.DropdownButton;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.SliderRow;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

final class ColorEditorScreen extends Screen implements ConfigWorkflowChild, ColorEditorPaletteController.Host,
        ColorEditorWidgetController.Host, ColorEditorPreviewController.Host {
    private static final int CONTROL_HEIGHT = 20;
    private static final int INVALID_HEX_TEXT_COLOR = 0xFFFF5555;

    private final Screen lastScreen;

    @Override
    public Screen configWorkflowParent() {
        return lastScreen;
    }
    private final IntConsumer onChange;
    private final PrideSettingsConsumer onPrideChange;
    private final CommitHandler onCommit;
    private final ValueScroll valueScroll = new ValueScroll();
    private final String historyKey;
    private final Supplier<ColorState> stateSupplier;
    private final ColorEditorHistoryController history;
    private final boolean prideControlsVisible;
    private final int previewLineLength;
    private final int previewLineWidth;
    private final boolean previewCuePeak;
    private final PreviewRenderer previewRenderer;
    private final ColorEditorPaletteController palette;
    private final ColorEditorWidgetController widgets;
    private final ColorEditorPreviewController preview;
    private int color;
    private int flatColor;
    private int lastAppliedColor;
    private boolean prideEnabled;
    private boolean lastAppliedPrideEnabled;
    private String prideFlagId;
    private String lastAppliedPrideFlagId;
    private int[] customPrideColors;
    private int[] lastAppliedCustomPrideColors;
    private int customStripeIndex;
    private SliderRow redSlider;
    private SliderRow greenSlider;
    private SliderRow blueSlider;
    private AbstractWidget customStripeButton;
    private DropdownButton<PrideFlag> prideFlagDropdown;
    private EditBox hexBox;
    private boolean syncingHexBox;

    ColorEditorScreen(Screen lastScreen, Component colorName, int color, boolean prideEnabled, String prideFlagId,
            int[] customPrideColors, int previewLineLength, int previewLineWidth, boolean previewCuePeak,
            IntConsumer onChange, PrideSettingsConsumer onPrideChange, BooleanSupplier onCommit, String historyKey,
            Supplier<ColorState> stateSupplier, HistoryController history) {
        this(lastScreen, colorName, color, prideEnabled, prideFlagId, customPrideColors,
                previewLineLength, previewLineWidth, previewCuePeak, onChange, onPrideChange,
                synchronousCommit(onCommit), historyKey, stateSupplier, history);
    }

    ColorEditorScreen(Screen lastScreen, Component colorName, int color, boolean prideEnabled, String prideFlagId,
            int[] customPrideColors, int previewLineLength, int previewLineWidth, boolean previewCuePeak,
            IntConsumer onChange, PrideSettingsConsumer onPrideChange, CommitHandler onCommit, String historyKey,
            Supplier<ColorState> stateSupplier, HistoryController history) {
        this(lastScreen, colorName, color, prideEnabled, prideFlagId, customPrideColors, previewLineLength,
                previewLineWidth, previewCuePeak, onChange, onPrideChange, onCommit, historyKey, stateSupplier,
                history, true, null);
    }

    ColorEditorScreen(Screen lastScreen, Component colorName, int color, int previewLineLength,
            int previewLineWidth, boolean previewCuePeak, IntConsumer onChange, BooleanSupplier onCommit,
            String historyKey, Supplier<Integer> stateSupplier, HistoryController history) {
        this(lastScreen, colorName, color, previewLineLength, previewLineWidth, previewCuePeak, onChange, onCommit,
                historyKey, stateSupplier, history, null);
    }

    ColorEditorScreen(Screen lastScreen, Component colorName, int color, int previewLineLength,
            int previewLineWidth, boolean previewCuePeak, IntConsumer onChange, BooleanSupplier onCommit,
            String historyKey, Supplier<Integer> stateSupplier, HistoryController history,
            PreviewRenderer previewRenderer) {
        this(lastScreen, colorName, color, previewLineLength, previewLineWidth, previewCuePeak,
                onChange, synchronousCommit(onCommit), historyKey, stateSupplier, history, previewRenderer);
    }

    ColorEditorScreen(Screen lastScreen, Component colorName, int color, int previewLineLength,
            int previewLineWidth, boolean previewCuePeak, IntConsumer onChange, CommitHandler onCommit,
            String historyKey, Supplier<Integer> stateSupplier, HistoryController history,
            PreviewRenderer previewRenderer) {
        this(lastScreen, colorName, color, false, PrideFlag.defaultId(), PrideFlag.defaultCustomColors(),
                previewLineLength, previewLineWidth, previewCuePeak, onChange, (enabled, flagId, customColors) -> {
                }, onCommit, historyKey, () -> new ColorState(stateSupplier.get(), false, PrideFlag.defaultId(),
                        PrideFlag.defaultCustomColors()), history, false, previewRenderer);
    }

    private ColorEditorScreen(Screen lastScreen, Component colorName, int color, boolean prideEnabled,
            String prideFlagId, int[] customPrideColors, int previewLineLength, int previewLineWidth,
            boolean previewCuePeak, IntConsumer onChange, PrideSettingsConsumer onPrideChange, CommitHandler onCommit,
            String historyKey, Supplier<ColorState> stateSupplier, HistoryController history,
            boolean prideControlsVisible, PreviewRenderer previewRenderer) {
        super(Component.translatable("screen.elytrapitchhelper.color.title", colorName));
        this.lastScreen = lastScreen;
        this.color = color & 0x00FFFFFF;
        this.flatColor = this.color;
        this.lastAppliedColor = this.color;
        this.prideEnabled = prideEnabled;
        this.lastAppliedPrideEnabled = prideEnabled;
        this.prideFlagId = PrideFlag.sanitizeId(prideFlagId);
        this.lastAppliedPrideFlagId = this.prideFlagId;
        this.customPrideColors = PrideFlag.sanitizeCustomColors(customPrideColors);
        this.lastAppliedCustomPrideColors = Arrays.copyOf(this.customPrideColors, this.customPrideColors.length);
        if (prideEnabled && PrideFlag.isCustomId(this.prideFlagId)) {
            this.color = this.customPrideColors[customStripeIndex];
            this.lastAppliedColor = this.color;
        }
        this.previewLineLength = clamp(previewLineLength, 2, 200);
        this.previewLineWidth = clamp(previewLineWidth, 1, 20);
        this.previewCuePeak = previewCuePeak;
        this.onChange = onChange;
        this.onPrideChange = onPrideChange;
        this.onCommit = onCommit;
        this.historyKey = historyKey;
        this.stateSupplier = stateSupplier;
        this.history = new ColorEditorHistoryController(history);
        this.prideControlsVisible = prideControlsVisible;
        this.previewRenderer = previewRenderer;
        this.palette = new ColorEditorPaletteController(this);
        this.widgets = new ColorEditorWidgetController();
        this.preview = new ColorEditorPreviewController(this, this.previewLineLength, this.previewLineWidth,
                this.previewCuePeak);
        syncHsvFromColor(true);
    }

    @Override
    protected void init() {
        ColorEditorWidgetController.Widgets created = widgets.init(this);
        hexBox = created.hexBox();
        redSlider = created.redSlider();
        greenSlider = created.greenSlider();
        blueSlider = created.blueSlider();
        customStripeButton = created.customStripeButton();
        prideFlagDropdown = created.prideFlagDropdown();
        syncPrideControls();
    }

    @Override
    public Font editorFont() {
        return font;
    }

    @Override
    public int editorWidth() {
        return width;
    }

    @Override
    public int color() {
        return color;
    }

    @Override
    public boolean prideControlsVisible() {
        return prideControlsVisible;
    }

    @Override
    public PrideFlag prideFlag() {
        return PrideFlag.byId(prideFlagId);
    }

    @Override
    public void addWidget(AbstractWidget widget) {
        addRenderableWidget(widget);
    }

    @Override
    public void closeEditor() {
        onClose();
    }

    @Override
    public void setRed(int value) {
        setColorFromRgb((value << 16) | (green() << 8) | blue(), false);
    }

    @Override
    public void setGreen(int value) {
        setColorFromRgb((red() << 16) | (value << 8) | blue(), false);
    }

    @Override
    public void setBlue(int value) {
        setColorFromRgb((red() << 16) | (green() << 8) | value, false);
    }

    @Override
    public void selectPrideFlag(PrideFlag flag) {
        history.begin(historyKey);
        boolean wasEditingCustom = editingCustomPride();
        prideFlagId = flag.id();
        if (flag == PrideFlag.CUSTOM) {
            syncColorFromCustomStripe();
        } else if (wasEditingCustom) {
            syncColorFromFlatColor();
        }
        syncPrideControls();
        applyPrideChange();
        history.end(true, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        ColorEditorLayout layout = colorEditorLayout();
        boolean dropdownOpen = prideFlagDropdown != null && prideFlagDropdown.isOpen();
        int contentMouseX = dropdownOpen ? -1 : mouseX;
        int contentMouseY = dropdownOpen ? -1 : mouseY;
        int previewWidth = layout.controlWidth();
        int previewX = layout.controlX();
        int previewY = layout.previewY();
        int previewHeight = previewHeight();

        context.centeredText(font, title, width / 2, 15, 0xFFFFFF);
        paintControlBackdrop(context, layout);
        palette.render(context, layout, contentMouseX, contentMouseY);
        if (previewRenderer == null) {
            preview.render(context, previewX, previewY, previewWidth, previewHeight, contentMouseX, contentMouseY);
        } else {
            previewRenderer.render(context, font, previewX, previewY, previewWidth, previewHeight,
                    color & 0x00FFFFFF);
        }

        context.text(font, Component.translatable("option.elytrapitchhelper.color.hex"), previewX, layout.hexY() + 6,
                0xFFFFFF);
        super.extractRenderState(context, contentMouseX, contentMouseY, delta);
        if (dropdownOpen) {
            context.nextStratum();
            prideFlagDropdown.extractDropdownOverlay(context, font, mouseX, mouseY);
        }
    }

    private void paintControlBackdrop(GuiGraphicsExtractor context, ColorEditorLayout layout) {
        int x = layout.controlX();
        int width = layout.controlWidth();
        if (prideControlsVisible) {
            PanelBackdrop.paintBand(context, x, width, layout.prideY(),
                    (editingCustomPride() ? layout.customY() : layout.prideY())
                            + ColorEditorWidgetController.CONTROL_HEIGHT, false);
        }
        int slidersBottom = layout.sliderY()
                + ColorEditorWidgetController.CONTROL_HEIGHT * 3
                + ColorEditorWidgetController.RGB_SLIDER_GAP * 2;
        PanelBackdrop.paintBand(context, x, width, layout.hexY(), slidersBottom, false);
    }

    @Override
    public void onClose() {
        palette.finishOpenDrag();
        applyColorChange();
        applyPrideChange();
        onCommit.commit(() -> {
            if (minecraft.gui.screen() == this) {
                minecraft.gui.setScreen(lastScreen);
            }
        });
    }

    @Override
    public void removed() {
        palette.close();
        super.removed();
    }

    @Override
    public void tick() {
        super.tick();
        history.acceptExternalRevision();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (prideFlagDropdown != null && prideFlagDropdown.handleOpenKeyPressed(event)) {
            return true;
        }
        if ((event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)
                && hexBox != null && hexBox.isFocused()) {
            commitHexBox();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && hexBox != null && hexBox.isFocused()) {
            blurHexBox();
            return true;
        }
        if ((hexBox == null || !hexBox.isFocused())
                && history.handleShortcut(event, this::syncFromConfig)) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (hexBox != null && hexBox.isFocused() && !hexBox.isMouseOver(event.x(), event.y())) {
            history.breakCoalescing();
        }
        if (prideFlagDropdown != null && prideFlagDropdown.handleOpenMouseClicked(event, height)) {
            return true;
        }
        if (previewRenderer == null && preview.selectStripe(event)) {
            return true;
        }
        if (palette.startDrag(event)) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (prideFlagDropdown != null
                && prideFlagDropdown.handleOpenMouseScrolled(mouseX, mouseY, scrollY, height)) {
            return true;
        }
        if (!ValueScroll.isAdjusting(minecraft)) {
            valueScroll.reset();
            return false;
        }
        int steps = valueScroll.steps(scrollX, scrollY);
        if (steps == 0) {
            return true;
        }
        return ValueScroll.replay(steps,
                direction -> super.mouseScrolled(mouseX, mouseY, 0.0, direction));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (palette.drag(event)) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (palette.release(event)) {
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void togglePrideMode() {
        history.begin(historyKey);
        boolean wasEditingCustom = editingCustomPride();
        prideEnabled = !prideEnabled;
        if (editingCustomPride()) {
            syncColorFromCustomStripe();
        } else if (wasEditingCustom) {
            syncColorFromFlatColor();
        }
        syncPrideControls();
        applyPrideChange();
        history.end(true, false);
    }

    @Override
    public void cycleCustomStripe(int direction) {
        if (customPrideColors.length == 0) {
            return;
        }
        customStripeIndex = Math.floorMod(customStripeIndex + direction, customPrideColors.length);
        syncColorFromCustomStripe();
        syncPrideControls();
    }

    @Override
    public SliderRow rgbSlider(Component label, int current, IntConsumer onValueChanged) {
        return new SliderRow(label, font, 0, current, 0, 255, 1.0,
                value -> Integer.toString((int) Math.round(value)),
                value -> onValueChanged.accept((int) Math.round(value)), historyKey, history.sliderListener());
    }

    @Override
    public Component customStripeValue() {
        return Component.literal((customStripeIndex + 1) + "/" + customPrideColors.length);
    }

    @Override
    public boolean canAddCustomStripe() {
        return customPrideColors.length < PrideFlag.MAX_CUSTOM_COLORS;
    }

    @Override
    public boolean canRemoveCustomStripe() {
        return customPrideColors.length > PrideFlag.MIN_CUSTOM_COLORS;
    }

    @Override
    public <T extends AbstractWidget> T tooltip(T widget, String translationKey) {
        widget.setTooltip(Tooltip.create(Component.translatable(translationKey)));
        widget.setTooltipDelay(ConfigScreen.TOOLTIP_DELAY);
        return widget;
    }

    @Override
    public void onHexChanged(String value) {
        if (syncingHexBox) {
            return;
        }
        if (prideEnabled && !editingCustomPride()) {
            return;
        }

        int parsed = parseHexColor(value);
        if (parsed < 0) {
            hexBox.setTextColor(INVALID_HEX_TEXT_COLOR);
            return;
        }

        hexBox.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
        history.begin(historyKey);
        setColorFromRgb(parsed, true);
        applyColorChange();
        history.end(true, true);
    }

    private void setColorFromRgb(int color, boolean syncSliders) {
        this.color = color & 0x00FFFFFF;
        syncHsvFromColor(false);
        if (syncSliders) {
            syncRgbSliders();
        }
        saveAndSyncHexBox();
    }

    @Override
    public void applyPaletteColor(int color) {
        this.color = color & 0x00FFFFFF;
        syncRgbSliders();
        saveAndSyncHexBox();
    }

    private void syncRgbSliders() {
        redSlider.setCurrent(red());
        greenSlider.setCurrent(green());
        blueSlider.setCurrent(blue());
    }

    private void saveAndSyncHexBox() {
        applyColorChange();
        hexBox.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
        setHexBoxValue();
    }

    @Override
    public void blurHexBox() {
        int parsed = parseHexColor(hexBox.getValue());
        if (parsed >= 0) {
            color = parsed;
            syncHsvFromColor(false);
            syncRgbSliders();
            applyColorChange();
            hexBox.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
        } else {
            hexBox.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
            setHexBoxValue();
        }
        setFocused(null);
        hexBox.setFocused(false);
        history.breakCoalescing();
    }

    private void commitHexBox() {
        blurHexBox();
    }

    private void applyColorChange() {
        if (editingCustomPride()) {
            int sanitized = color & 0x00FFFFFF;
            if (customPrideColors[customStripeIndex] != sanitized) {
                customPrideColors[customStripeIndex] = sanitized;
                syncPrideControls();
                applyPrideChange();
            }
            lastAppliedColor = sanitized;
            return;
        }
        if (color != lastAppliedColor) {
            lastAppliedColor = color;
            flatColor = color & 0x00FFFFFF;
            onChange.accept(color);
        }
    }

    private void applyPrideChange() {
        prideFlagId = PrideFlag.sanitizeId(prideFlagId);
        customPrideColors = PrideFlag.sanitizeCustomColors(customPrideColors);
        if (prideEnabled != lastAppliedPrideEnabled || !prideFlagId.equals(lastAppliedPrideFlagId)
                || !Arrays.equals(customPrideColors, lastAppliedCustomPrideColors)) {
            lastAppliedPrideEnabled = prideEnabled;
            lastAppliedPrideFlagId = prideFlagId;
            lastAppliedCustomPrideColors = Arrays.copyOf(customPrideColors, customPrideColors.length);
            onPrideChange.accept(prideEnabled, prideFlagId,
                    Arrays.copyOf(customPrideColors, customPrideColors.length));
        }
    }

    private void syncPrideControls() {
        boolean editingCustom = editingCustomPride();
        if (prideFlagDropdown != null) {
            prideFlagDropdown.setValue(PrideFlag.byId(prideFlagId));
            prideFlagDropdown.active = prideEnabled;
            if (!prideEnabled) {
                prideFlagDropdown.close();
            }
        }
        if (customStripeButton != null) {
            customStripeButton.active = editingCustom;
            customStripeButton.visible = editingCustom;
        }
        boolean colorControlsActive = !prideEnabled || editingCustom;
        if (hexBox != null) {
            if (!colorControlsActive && hexBox.isFocused()) {
                setFocused(null);
                hexBox.setFocused(false);
            }
            hexBox.active = colorControlsActive;
            hexBox.setEditable(colorControlsActive);
        }
        if (redSlider != null) {
            redSlider.active = colorControlsActive;
        }
        if (greenSlider != null) {
            greenSlider.active = colorControlsActive;
        }
        if (blueSlider != null) {
            blueSlider.active = colorControlsActive;
        }
    }

    @Override
    public Component prideModeMessage() {
        Component value = Component.translatable(prideEnabled
                ? "option.elytrapitchhelper.color.pride.on"
                : "option.elytrapitchhelper.color.pride.off");
        return CommonComponents.optionNameValue(Component.translatable("option.elytrapitchhelper.color.pride"), value);
    }

    @Override
    public Component customStripeMessage() {
        return CommonComponents.optionNameValue(
                Component.translatable("option.elytrapitchhelper.color.custom.stripe"),
                Component.literal((customStripeIndex + 1) + "/" + customPrideColors.length));
    }

    @Override
    public int[] stripeColorsForDropdown(PrideFlag flag) {
        return flag == PrideFlag.CUSTOM ? customPrideColors : flag.colors();
    }

    @Override
    public boolean editingCustomPride() {
        return prideEnabled && PrideFlag.isCustomId(prideFlagId);
    }

    @Override
    public boolean prideEnabled() {
        return prideEnabled;
    }

    @Override
    public String prideFlagId() {
        return prideFlagId;
    }

    @Override
    public int[] customPrideColors() {
        return customPrideColors;
    }

    @Override
    public int customStripeIndex() {
        return customStripeIndex;
    }

    @Override
    public void selectCustomStripe(int stripeIndex) {
        customStripeIndex = stripeIndex;
        syncColorFromCustomStripe();
        syncPrideControls();
    }

    @Override
    public boolean paletteDisabled() {
        return prideEnabled && !editingCustomPride();
    }

    @Override
    public void beginPaletteHistory() {
        history.begin(historyKey);
    }

    @Override
    public void endPaletteHistory() {
        history.end(true, false);
    }

    @Override
    public void setPaletteDragging(boolean dragging) {
        setDragging(dragging);
    }

    private void syncColorFromCustomStripe() {
        if (customPrideColors.length == 0) {
            customStripeIndex = 0;
            syncColorFromFlatColor();
            return;
        }
        customStripeIndex = clamp(customStripeIndex, 0, customPrideColors.length - 1);
        color = customPrideColors[customStripeIndex] & 0x00FFFFFF;
        lastAppliedColor = color;
        syncHsvFromColor(false);
        syncColorControls();
    }

    private void syncColorFromFlatColor() {
        color = flatColor & 0x00FFFFFF;
        lastAppliedColor = color;
        syncHsvFromColor(false);
        syncColorControls();
    }

    private void syncColorControls() {
        if (redSlider != null) {
            syncRgbSliders();
        }
        if (hexBox != null) {
            hexBox.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
            setHexBoxValue();
        }
    }

    private void setHexBoxValue() {
        syncingHexBox = true;
        hexBox.setValue(ScreenText.hexColor(color));
        syncingHexBox = false;
    }

    private void syncFromConfig() {
        ColorState state = stateSupplier.get();
        flatColor = state.color() & 0x00FFFFFF;
        prideEnabled = state.prideEnabled();
        prideFlagId = PrideFlag.sanitizeId(state.prideFlagId());
        customPrideColors = PrideFlag.sanitizeCustomColors(state.customPrideColors());
        customStripeIndex = clamp(customStripeIndex, 0, customPrideColors.length - 1);
        color = editingCustomPride() ? customPrideColors[customStripeIndex] : flatColor;
        lastAppliedColor = color;
        lastAppliedPrideEnabled = prideEnabled;
        lastAppliedPrideFlagId = prideFlagId;
        lastAppliedCustomPrideColors = Arrays.copyOf(customPrideColors, customPrideColors.length);
        syncHsvFromColor(true);
        syncColorControls();
        syncPrideControls();
    }

    @Override
    public void addCustomStripe() {
        if (customPrideColors.length >= PrideFlag.MAX_CUSTOM_COLORS) {
            return;
        }
        history.begin(historyKey);
        int insertAt = customStripeIndex + 1;
        int[] next = new int[customPrideColors.length + 1];
        System.arraycopy(customPrideColors, 0, next, 0, insertAt);
        next[insertAt] = color & 0x00FFFFFF;
        System.arraycopy(customPrideColors, insertAt, next, insertAt + 1, customPrideColors.length - insertAt);
        customPrideColors = next;
        customStripeIndex = insertAt;
        syncColorFromCustomStripe();
        syncPrideControls();
        applyPrideChange();
        history.end(true, false);
    }

    @Override
    public void removeCustomStripe() {
        if (customPrideColors.length <= PrideFlag.MIN_CUSTOM_COLORS) {
            return;
        }
        history.begin(historyKey);
        int[] next = new int[customPrideColors.length - 1];
        System.arraycopy(customPrideColors, 0, next, 0, customStripeIndex);
        System.arraycopy(customPrideColors, customStripeIndex + 1, next, customStripeIndex,
                customPrideColors.length - customStripeIndex - 1);
        customPrideColors = next;
        customStripeIndex = Math.max(0, customStripeIndex - 1);
        syncColorFromCustomStripe();
        syncPrideControls();
        applyPrideChange();
        history.end(true, false);
    }

    @Override
    public int red() {
        return MathUtil.red(color);
    }

    @Override
    public int green() {
        return MathUtil.green(color);
    }

    @Override
    public int blue() {
        return MathUtil.blue(color);
    }

    @Override
    public ColorEditorLayout colorEditorLayout() {
        return ColorEditorLayoutController.calculate(width, height, preferredControlWidth(), previewHeight(),
                prideControlsVisible);
    }

    static ColorEditorLayout calculateColorEditorLayout(int width, int height, int preferredControlWidth,
            int previewHeight) {
        return ColorEditorLayoutController.calculate(width, height, preferredControlWidth, previewHeight);
    }

    private int preferredControlWidth() {
        if (previewRenderer != null) {
            return 300;
        }
        return clamp(previewLineLength + 20, 140, 220);
    }

    @Override
    public int previewHeight() {
        if (previewRenderer != null) {
            int rows = CommandPalettePreviewRenderer.rowsFittingHeight(height - 140, 3);
            return CommandPalettePreviewRenderer.previewHeight(rows);
        }
        int cueExtraHeight = previewCuePeak ? 18 : 12;
        return Math.max(24, previewLineWidth + cueExtraHeight);
    }

    private void syncHsvFromColor(boolean resetHueWhenGray) {
        palette.syncFromColor(red(), green(), blue(), resetHueWhenGray);
    }

    record ColorState(int color, boolean prideEnabled, String prideFlagId, int[] customPrideColors) {
        ColorState {
            color &= 0x00FFFFFF;
            prideFlagId = PrideFlag.sanitizeId(prideFlagId);
            customPrideColors = PrideFlag.sanitizeCustomColors(customPrideColors);
        }
    }

    @FunctionalInterface
    interface PrideSettingsConsumer {
        void accept(boolean enabled, String flagId, int[] customColors);
    }

    @FunctionalInterface
    interface CommitHandler {
        void commit(Runnable afterCommit);
    }

    private static CommitHandler synchronousCommit(BooleanSupplier commit) {
        return afterCommit -> {
            if (commit.getAsBoolean()) {
                afterCommit.run();
            }
        };
    }

    @FunctionalInterface
    interface PreviewRenderer {
        void render(GuiGraphicsExtractor context, Font font, int x, int y, int width, int height, int color);
    }

    interface HistoryController {
        void begin(String actionKey);

        void end(boolean changed, boolean coalesce);

        boolean undo();

        boolean redo();

        void breakCoalescing();

        boolean acceptExternalRevision();
    }

}
