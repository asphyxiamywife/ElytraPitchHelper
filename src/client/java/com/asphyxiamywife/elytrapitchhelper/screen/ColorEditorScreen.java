package com.asphyxiamywife.elytrapitchhelper.screen;

import static com.asphyxiamywife.elytrapitchhelper.screen.ColorMath.clamp01;
import static com.asphyxiamywife.elytrapitchhelper.screen.ColorMath.clampInt;
import static com.asphyxiamywife.elytrapitchhelper.screen.ColorMath.diskToSquare;
import static com.asphyxiamywife.elytrapitchhelper.screen.ColorMath.hsvToRgb;
import static com.asphyxiamywife.elytrapitchhelper.screen.ColorMath.hueFromVector;
import static com.asphyxiamywife.elytrapitchhelper.screen.ColorMath.parseHexColor;
import static com.asphyxiamywife.elytrapitchhelper.screen.ColorMath.rgbToHsv;

import com.asphyxiamywife.elytrapitchhelper.config.PrideFlag;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.CyclingOptionButton;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.DropdownButton;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.NumberSlider;
import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.function.IntConsumer;

final class ColorEditorScreen extends Screen {
    private static final int CONTROL_HEIGHT = 20;
    private static final int CONTROL_GAP = 16;
    private static final int RGB_SLIDER_GAP = 4;
    private static final int INVALID_HEX_TEXT_COLOR = 0xFFFF5555;
    private static final String CUSTOM_STRIPE_MARKER = "v";

    private final Screen lastScreen;
    private final IntConsumer onChange;
    private final PrideSettingsConsumer onPrideChange;
    private final Runnable onCommit;
    private final int previewLineLength;
    private final int previewLineWidth;
    private final boolean previewCuePeak;
    private final ColorPaletteRenderer paletteRenderer = new ColorPaletteRenderer();
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
    private double hue;
    private double saturation;
    private double value;
    private NumberSlider redSlider;
    private NumberSlider greenSlider;
    private NumberSlider blueSlider;
    private Button prideModeButton;
    private Button customStripeButton;
    private Button customAddButton;
    private Button customRemoveButton;
    private DropdownButton<PrideFlag> prideFlagDropdown;
    private EditBox hexBox;
    private boolean syncingHexBox;
    private PaletteDrag paletteDrag = PaletteDrag.NONE;

    ColorEditorScreen(Screen lastScreen, Component colorName, int color, boolean prideEnabled, String prideFlagId,
            int[] customPrideColors, int previewLineLength, int previewLineWidth, boolean previewCuePeak,
            IntConsumer onChange, PrideSettingsConsumer onPrideChange, Runnable onCommit) {
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
        this.previewLineLength = clampInt(previewLineLength, 2, 200);
        this.previewLineWidth = clampInt(previewLineWidth, 1, 20);
        this.previewCuePeak = previewCuePeak;
        this.onChange = onChange;
        this.onPrideChange = onPrideChange;
        this.onCommit = onCommit;
        syncHsvFromColor(true);
    }

    @Override
    protected void init() {
        ColorEditorLayout layout = colorEditorLayout();

        hexBox = new EditBox(font, layout.controlX() + layout.controlWidth() - 82, layout.hexY(), 82, CONTROL_HEIGHT,
                Component.translatable("option.elytrapitchhelper.color.hex"));
        hexBox.setMaxLength(6);
        hexBox.setValue(ScreenText.hexColor(color));
        hexBox.setResponder(this::onHexChanged);
        addRenderableWidget(hexBox);

        int prideButtonGap = RGB_SLIDER_GAP;
        int prideButtonWidth = Math.max(58, (layout.controlWidth() - prideButtonGap) / 2);
        prideModeButton = new CyclingOptionButton(layout.controlX(), layout.prideY(), prideButtonWidth,
                CONTROL_HEIGHT, prideModeMessage(), this::togglePrideMode, this::togglePrideMode);
        addRenderableWidget(prideModeButton);

        prideFlagDropdown = new DropdownButton<>(layout.controlX() + prideButtonWidth + prideButtonGap, layout.prideY(),
                layout.controlWidth() - prideButtonWidth - prideButtonGap, CONTROL_HEIGHT, null,
                Arrays.asList(PrideFlag.values()), PrideFlag.byId(prideFlagId),
                flag -> Component.translatable(flag.translationKey()), this::stripeColorsForDropdown, flag -> {
            boolean wasEditingCustom = editingCustomPride();
            prideFlagId = flag.id();
            if (flag == PrideFlag.CUSTOM) {
                syncColorFromCustomStripe();
            } else if (wasEditingCustom) {
                syncColorFromFlatColor();
            }
            syncPrideControls();
            applyPrideChange();
        });
        addRenderableWidget(prideFlagDropdown);

        int customButtonWidth = Math.max(54, (layout.controlWidth() - RGB_SLIDER_GAP * 2) / 3);
        int customSmallWidth = Math.max(36, (layout.controlWidth() - customButtonWidth - RGB_SLIDER_GAP * 2) / 2);
        customStripeButton = new CyclingOptionButton(layout.controlX(), layout.customY(), customButtonWidth,
                CONTROL_HEIGHT, customStripeMessage(), () -> cycleCustomStripe(1), () -> cycleCustomStripe(-1));
        addRenderableWidget(customStripeButton);

        customAddButton = Button.builder(Component.translatable("option.elytrapitchhelper.color.custom.add"),
                button -> addCustomStripe())
                .bounds(layout.controlX() + customButtonWidth + RGB_SLIDER_GAP, layout.customY(),
                        customSmallWidth, CONTROL_HEIGHT)
                .build();
        addRenderableWidget(customAddButton);

        customRemoveButton = Button.builder(Component.translatable("option.elytrapitchhelper.color.custom.remove"),
                button -> removeCustomStripe())
                .bounds(layout.controlX() + customButtonWidth + customSmallWidth + RGB_SLIDER_GAP * 2,
                        layout.customY(), layout.controlWidth() - customButtonWidth - customSmallWidth
                                - RGB_SLIDER_GAP * 2, CONTROL_HEIGHT)
                .build();
        addRenderableWidget(customRemoveButton);

        redSlider = rgbSlider(Component.translatable("option.elytrapitchhelper.color.red"), red(), value -> {
            setColorFromRgb((value << 16) | (green() << 8) | blue(), false);
        });
        greenSlider = rgbSlider(Component.translatable("option.elytrapitchhelper.color.green"), green(), value -> {
            setColorFromRgb((red() << 16) | (value << 8) | blue(), false);
        });
        blueSlider = rgbSlider(Component.translatable("option.elytrapitchhelper.color.blue"), blue(), value -> {
            setColorFromRgb((red() << 16) | (green() << 8) | value, false);
        });

        addSlider(redSlider, layout.controlX(), layout.sliderY(), layout.controlWidth());
        addSlider(greenSlider, layout.controlX(), layout.sliderY() + CONTROL_HEIGHT + RGB_SLIDER_GAP,
                layout.controlWidth());
        addSlider(blueSlider, layout.controlX(), layout.sliderY() + (CONTROL_HEIGHT + RGB_SLIDER_GAP) * 2,
                layout.controlWidth());
        syncPrideControls();

        int buttonWidth = Math.min(120, width - 40);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds((width - buttonWidth) / 2, height - 28, buttonWidth, CONTROL_HEIGHT).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        ColorEditorLayout layout = colorEditorLayout();
        int previewWidth = layout.controlWidth();
        int previewX = layout.controlX();
        int previewY = layout.previewY();
        int previewHeight = previewHeight();

        context.centeredText(font, title, width / 2, 15, 0xFFFFFF);
        renderPalette(context, layout);
        handlePaletteCursor(context, layout, mouseX, mouseY);
        renderLinePreview(context, previewX, previewY, previewWidth, previewHeight);
        handlePreviewCursor(context, previewX, previewY, previewWidth, previewHeight, mouseX, mouseY);

        context.text(font, Component.translatable("option.elytrapitchhelper.color.hex"), previewX, layout.hexY() + 6,
                0xFFFFFF);
        super.extractRenderState(context, mouseX, mouseY, delta);
        if (prideFlagDropdown != null && prideFlagDropdown.isOpen()) {
            context.nextStratum();
            prideFlagDropdown.extractDropdownOverlay(context, font, mouseX, mouseY);
        }
    }

    @Override
    public void onClose() {
        applyColorChange();
        applyPrideChange();
        minecraft.setScreen(lastScreen);
    }

    @Override
    public void removed() {
        paletteRenderer.close();
        super.removed();
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
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (prideFlagDropdown != null && prideFlagDropdown.handleOpenMouseClicked(event, height)) {
            return true;
        }
        if (selectPreviewStripe(event)) {
            return true;
        }
        if (startPaletteDrag(event)) {
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
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (paletteDrag != PaletteDrag.NONE && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            updatePaletteColor(event.x(), event.y(), paletteDrag);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (paletteDrag != PaletteDrag.NONE && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            paletteDrag = PaletteDrag.NONE;
            setDragging(false);
            return true;
        }
        return super.mouseReleased(event);
    }

    private void togglePrideMode() {
        boolean wasEditingCustom = editingCustomPride();
        prideEnabled = !prideEnabled;
        if (editingCustomPride()) {
            syncColorFromCustomStripe();
        } else if (wasEditingCustom) {
            syncColorFromFlatColor();
        }
        syncPrideControls();
        applyPrideChange();
    }

    private void cycleCustomStripe(int direction) {
        customStripeIndex = Math.floorMod(customStripeIndex + direction, customPrideColors.length);
        syncColorFromCustomStripe();
        syncPrideControls();
    }

    private NumberSlider rgbSlider(Component label, int current, IntConsumer onValueChanged) {
        return new NumberSlider(0, 0, 1, CONTROL_HEIGHT, label, current, 0, 255, 1.0,
                value -> Integer.toString((int) Math.round(value)),
                value -> onValueChanged.accept((int) Math.round(value)));
    }

    private void addSlider(AbstractWidget widget, int x, int y, int width) {
        widget.setSize(width, CONTROL_HEIGHT);
        widget.setX(x);
        widget.setY(y);
        addRenderableWidget(widget);
    }

    private void onHexChanged(String value) {
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
        setColorFromRgb(parsed, true);
        applyColorChange();
    }

    private void setColorFromRgb(int color, boolean syncSliders) {
        this.color = color & 0x00FFFFFF;
        syncHsvFromColor(false);
        if (syncSliders) {
            syncRgbSliders();
        }
        saveAndSyncHexBox();
    }

    private void setColorFromPalette() {
        color = hsvToRgb(hue, saturation, value);
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
        syncingHexBox = true;
        hexBox.setValue(ScreenText.hexColor(color));
        syncingHexBox = false;
    }

    private void blurHexBox() {
        int parsed = parseHexColor(hexBox.getValue());
        if (parsed >= 0) {
            color = parsed;
            applyColorChange();
            hexBox.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
        } else {
            hexBox.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
            syncingHexBox = true;
            hexBox.setValue(ScreenText.hexColor(color));
            syncingHexBox = false;
        }
        setFocused(null);
        hexBox.setFocused(false);
    }

    private void commitHexBox() {
        blurHexBox();
        onCommit.run();
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
        if (prideModeButton != null) {
            prideModeButton.setMessage(prideModeMessage());
        }
        if (prideFlagDropdown != null) {
            prideFlagDropdown.setValue(PrideFlag.byId(prideFlagId));
            prideFlagDropdown.active = prideEnabled;
            if (!prideEnabled) {
                prideFlagDropdown.close();
            }
        }
        if (customStripeButton != null) {
            customStripeIndex = clampInt(customStripeIndex, 0, customPrideColors.length - 1);
            customStripeButton.setMessage(customStripeMessage());
            customStripeButton.active = editingCustom;
            customStripeButton.visible = editingCustom;
        }
        if (customAddButton != null) {
            customAddButton.active = editingCustom && customPrideColors.length < PrideFlag.MAX_CUSTOM_COLORS;
            customAddButton.visible = editingCustom;
        }
        if (customRemoveButton != null) {
            customRemoveButton.active = editingCustom && customPrideColors.length > PrideFlag.MIN_CUSTOM_COLORS;
            customRemoveButton.visible = editingCustom;
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

    private Component prideModeMessage() {
        Component value = Component.translatable(prideEnabled
                ? "option.elytrapitchhelper.color.pride.on"
                : "option.elytrapitchhelper.color.pride.off");
        return CommonComponents.optionNameValue(Component.translatable("option.elytrapitchhelper.color.pride"), value);
    }

    private Component customStripeMessage() {
        return CommonComponents.optionNameValue(
                Component.translatable("option.elytrapitchhelper.color.custom.stripe"),
                Component.literal((customStripeIndex + 1) + "/" + customPrideColors.length));
    }

    private int[] stripeColorsForDropdown(PrideFlag flag) {
        return flag == PrideFlag.CUSTOM ? customPrideColors : flag.colors();
    }

    private boolean editingCustomPride() {
        return prideEnabled && PrideFlag.isCustomId(prideFlagId);
    }

    private void syncColorFromCustomStripe() {
        customStripeIndex = clampInt(customStripeIndex, 0, customPrideColors.length - 1);
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
            syncingHexBox = true;
            hexBox.setValue(ScreenText.hexColor(color));
            syncingHexBox = false;
        }
    }

    private void addCustomStripe() {
        if (customPrideColors.length >= PrideFlag.MAX_CUSTOM_COLORS) {
            return;
        }
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
    }

    private void removeCustomStripe() {
        if (customPrideColors.length <= PrideFlag.MIN_CUSTOM_COLORS) {
            return;
        }
        int[] next = new int[customPrideColors.length - 1];
        System.arraycopy(customPrideColors, 0, next, 0, customStripeIndex);
        System.arraycopy(customPrideColors, customStripeIndex + 1, next, customStripeIndex,
                customPrideColors.length - customStripeIndex - 1);
        customPrideColors = next;
        customStripeIndex = clampInt(customStripeIndex, 0, customPrideColors.length - 1);
        syncColorFromCustomStripe();
        syncPrideControls();
        applyPrideChange();
    }

    private int red() {
        return (color >> 16) & 0xFF;
    }

    private int green() {
        return (color >> 8) & 0xFF;
    }

    private int blue() {
        return color & 0xFF;
    }

    private ColorEditorLayout colorEditorLayout() {
        int availableWidth = Math.max(1, width - 40);
        int top = height < 240 ? 34 : 58;
        int previewHeight = previewHeight();
        if (availableWidth >= 280) {
            int preferredControlWidth = preferredControlWidth();
            int minControlWidth = availableWidth >= 96 + CONTROL_GAP + preferredControlWidth ? preferredControlWidth
                    : 140;
            int maxPaletteByWidth = availableWidth - CONTROL_GAP - minControlWidth;
            int maxPaletteByHeight = Math.max(96, height - top - 36);
            int paletteSize = clampInt(Math.min(Math.min(172, maxPaletteByWidth), maxPaletteByHeight), 96, 172);
            int controlWidth = clampInt(availableWidth - paletteSize - CONTROL_GAP, 140, 220);
            int contentWidth = paletteSize + CONTROL_GAP + controlWidth;
            int paletteX = (width - contentWidth) / 2;
            int controlX = paletteX + paletteSize + CONTROL_GAP;
            int prideY = top + previewHeight + 8;
            int customY = prideY + CONTROL_HEIGHT + 6;
            int hexY = customY + CONTROL_HEIGHT + 8;
            return new ColorEditorLayout(paletteX, top, paletteSize, controlX, controlWidth, top, prideY,
                    customY, hexY, hexY + 26);
        }

        int controlWidth = Math.max(140, Math.min(preferredControlWidth(), availableWidth));
        int stackedControlsHeight = previewHeight + CONTROL_HEIGHT * 2 + CONTROL_HEIGHT * 4
                + RGB_SLIDER_GAP * 4 + 64;
        int paletteSize = clampInt(Math.min(availableWidth, height - stackedControlsHeight), 64, 140);
        int paletteX = (width - paletteSize) / 2;
        int controlX = (width - controlWidth) / 2;
        int previewY = top;
        int prideY = previewY + previewHeight + 6;
        int customY = prideY + CONTROL_HEIGHT + 6;
        int paletteY = customY + CONTROL_HEIGHT + 6;
        int hexY = paletteY + paletteSize + 6;
        return new ColorEditorLayout(paletteX, paletteY, paletteSize, controlX, controlWidth, previewY, prideY,
                customY, hexY, hexY + 24);
    }

    private int preferredControlWidth() {
        return clampInt(previewLineLength + 20, 140, 220);
    }

    private int previewHeight() {
        int cueExtraHeight = previewCuePeak ? 18 : 12;
        return Math.max(24, previewLineWidth + cueExtraHeight);
    }

    private void renderLinePreview(GuiGraphicsExtractor context, int x, int y, int width, int height) {
        context.outline(x, y, width, height, 0x99FFFFFF);

        PreviewLineGeometry line = previewLineGeometry(x, y, width, height);
        int rgb = color & 0x00FFFFFF;
        int[] prideColors = prideEnabled ? PrideFlag.colorsFor(prideFlagId, customPrideColors) : null;
        if (previewCuePeak) {
            float glowAlpha = 0.18f;
            drawCenteredPreviewRect(context, line.centerX(), line.centerY(), Math.min(line.length() + 18, width - 2),
                    Math.min(previewLineWidth + 6, height - 2), glowAlpha * 0.45f, rgb, prideColors);
            drawCenteredPreviewRect(context, line.centerX(), line.centerY(), Math.min(line.length() + 10, width - 2),
                    Math.min(previewLineWidth + 4, height - 2), glowAlpha, rgb, prideColors);
        }

        drawPreviewRect(context, line.x(), line.y(), line.length(), previewLineWidth, 1.0f, rgb, prideColors);
        drawCustomStripeMarker(context, x, y, width, line);
    }

    private void drawCustomStripeMarker(GuiGraphicsExtractor context, int previewX, int previewY, int previewWidth,
            PreviewLineGeometry line) {
        if (!editingCustomPride()) {
            return;
        }

        int stripes = Math.max(1, customPrideColors.length);
        int markerX = line.x() + (int) Math.round((customStripeIndex + 0.5) * line.length() / stripes);
        markerX = clampInt(markerX, previewX + 2, previewX + previewWidth - 2);
        int markerY = Math.max(previewY + 2, line.y() - font.lineHeight - 1);
        int markerWidth = font.width(CUSTOM_STRIPE_MARKER);
        context.text(font, CUSTOM_STRIPE_MARKER, markerX - markerWidth / 2, markerY, 0xFFFFFFFF, true);
    }

    private void handlePreviewCursor(GuiGraphicsExtractor context, int previewX, int previewY, int previewWidth,
            int previewHeight, int mouseX, int mouseY) {
        if (previewStripeIndexAt(previewX, previewY, previewWidth, previewHeight, mouseX, mouseY) >= 0) {
            context.requestCursor(CursorTypes.POINTING_HAND);
        }
    }

    private boolean selectPreviewStripe(MouseButtonEvent event) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }

        ColorEditorLayout layout = colorEditorLayout();
        int stripe = previewStripeIndexAt(layout.controlX(), layout.previewY(), layout.controlWidth(), previewHeight(),
                event.x(), event.y());
        if (stripe < 0) {
            return false;
        }

        customStripeIndex = stripe;
        syncColorFromCustomStripe();
        syncPrideControls();
        return true;
    }

    private int previewStripeIndexAt(int previewX, int previewY, int previewWidth, int previewHeight, double mouseX,
            double mouseY) {
        if (!editingCustomPride()) {
            return -1;
        }

        PreviewLineGeometry line = previewLineGeometry(previewX, previewY, previewWidth, previewHeight);
        int hitTop = line.y() - 2;
        int hitBottom = line.y() + previewLineWidth + 2;
        if (mouseX < line.x() || mouseX >= line.x() + line.length() || mouseY < hitTop || mouseY >= hitBottom) {
            return -1;
        }

        int stripes = Math.max(1, customPrideColors.length);
        double position = (mouseX - line.x()) / Math.max(1.0, line.length());
        return clampInt((int) Math.floor(position * stripes), 0, stripes - 1);
    }

    private PreviewLineGeometry previewLineGeometry(int previewX, int previewY, int previewWidth,
            int previewHeight) {
        int lineLength = Math.min(previewLineLength, Math.max(1, previewWidth - 8));
        int centerX = previewX + previewWidth / 2;
        int centerY = previewY + previewHeight / 2;
        return new PreviewLineGeometry(centerX - lineLength / 2, centerY - previewLineWidth / 2,
                lineLength, centerX, centerY);
    }

    private static void drawCenteredPreviewRect(GuiGraphicsExtractor context, int centerX, int centerY, int width,
            int height, float alpha, int rgb, int[] prideColors) {
        int x = centerX - width / 2;
        int y = centerY - height / 2;
        drawPreviewRect(context, x, y, width, height, alpha, rgb, prideColors);
    }

    private static void drawPreviewRect(GuiGraphicsExtractor context, int x, int y, int width, int height, float alpha,
            int rgb, int[] prideColors) {
        if (prideColors == null || prideColors.length == 0) {
            context.fill(x, y, x + width, y + height, MathUtil.argb(alpha, rgb));
            return;
        }

        int segments = Math.min(width, prideColors.length);
        for (int i = 0; i < segments; i++) {
            int startX = x + i * width / segments;
            int endX = x + (i + 1) * width / segments;
            if (endX > startX) {
                double position = (i + 0.5) / segments;
                context.fill(startX, y, endX, y + height, MathUtil.argb(alpha,
                        PrideFlag.colorAt(prideColors, position)));
            }
        }
    }

    private void renderPalette(GuiGraphicsExtractor context, ColorEditorLayout layout) {
        paletteRenderer.render(context, layout, hue, saturation, value, prideEnabled && !editingCustomPride());
    }

    private void handlePaletteCursor(GuiGraphicsExtractor context, ColorEditorLayout layout, int mouseX, int mouseY) {
        if (mouseX < layout.paletteX() || mouseX >= layout.paletteX() + layout.paletteSize()
                || mouseY < layout.paletteY() || mouseY >= layout.paletteY() + layout.paletteSize()) {
            return;
        }

        context.requestCursor(prideEnabled && !editingCustomPride() ? CursorTypes.NOT_ALLOWED
                : CursorTypes.POINTING_HAND);
    }

    private boolean startPaletteDrag(MouseButtonEvent event) {
        if ((prideEnabled && !editingCustomPride()) || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }

        PaletteDrag hit = paletteHit(event.x(), event.y());
        if (hit == PaletteDrag.NONE) {
            return false;
        }

        blurHexBox();
        paletteDrag = hit;
        setDragging(true);
        updatePaletteColor(event.x(), event.y(), hit);
        return true;
    }

    private PaletteDrag paletteHit(double mouseX, double mouseY) {
        ColorEditorLayout layout = colorEditorLayout();
        PaletteGeometry geometry = ColorPaletteRenderer.geometry(layout);
        double dx = mouseX - layout.paletteX() - geometry.centerOffset();
        double dy = mouseY - layout.paletteY() - geometry.centerOffset();
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance <= geometry.shadeRadius()) {
            return PaletteDrag.SHADE;
        }
        if (distance >= geometry.innerRadius() && distance <= geometry.outerRadius()) {
            return PaletteDrag.HUE;
        }
        return PaletteDrag.NONE;
    }

    private void updatePaletteColor(double mouseX, double mouseY, PaletteDrag drag) {
        ColorEditorLayout layout = colorEditorLayout();
        PaletteGeometry geometry = ColorPaletteRenderer.geometry(layout);
        double dx = mouseX - layout.paletteX() - geometry.centerOffset();
        double dy = mouseY - layout.paletteY() - geometry.centerOffset();

        if (drag == PaletteDrag.HUE) {
            hue = hueFromVector(dx, dy);
        } else if (drag == PaletteDrag.SHADE) {
            double shadeX = dx / geometry.shadeRadius();
            double shadeY = dy / geometry.shadeRadius();
            double distance = Math.sqrt(shadeX * shadeX + shadeY * shadeY);
            if (distance > 1.0) {
                shadeX /= distance;
                shadeY /= distance;
            }

            double[] square = diskToSquare(shadeX, shadeY);
            saturation = clamp01((square[0] + 1.0) * 0.5);
            value = clamp01((1.0 - square[1]) * 0.5);
        }

        setColorFromPalette();
    }

    private void syncHsvFromColor(boolean resetHueWhenGray) {
        double[] hsv = rgbToHsv(red(), green(), blue());
        if (resetHueWhenGray || hsv[1] > 0.0001) {
            hue = hsv[0];
        }
        saturation = hsv[1];
        value = hsv[2];
    }

    private enum PaletteDrag {
        NONE,
        HUE,
        SHADE
    }

    @FunctionalInterface
    interface PrideSettingsConsumer {
        void accept(boolean enabled, String flagId, int[] customColors);
    }

}
