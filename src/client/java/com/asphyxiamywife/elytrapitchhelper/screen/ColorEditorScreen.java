package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.PrideFlag;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.NumberSlider;
import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

final class ColorEditorScreen extends Screen {
    private static final int CONTROL_HEIGHT = 20;
    private static final int CONTROL_GAP = 16;
    private static final int RGB_SLIDER_GAP = 4;
    private static final int INVALID_HEX_TEXT_COLOR = 0xFFFF5555;
    private static final double TWO_PI = Math.PI * 2.0;

    private final Screen lastScreen;
    private final IntConsumer onChange;
    private final PrideSettingsConsumer onPrideChange;
    private final Runnable onCommit;
    private final int previewLineLength;
    private final int previewLineWidth;
    private final boolean previewCuePeak;
    private int color;
    private int lastAppliedColor;
    private boolean prideEnabled;
    private boolean lastAppliedPrideEnabled;
    private String prideFlagId;
    private String lastAppliedPrideFlagId;
    private double hue;
    private double saturation;
    private double value;
    private NumberSlider redSlider;
    private NumberSlider greenSlider;
    private NumberSlider blueSlider;
    private Button prideModeButton;
    private Button prideFlagButton;
    private EditBox hexBox;
    private boolean syncingHexBox;
    private PaletteDrag paletteDrag = PaletteDrag.NONE;
    private DynamicTexture paletteTexture;
    private int paletteTextureSize;
    private double paletteTextureHue = -1.0;
    private DynamicTexture hueMarkerTexture;
    private int hueMarkerRadius;
    private DynamicTexture shadeMarkerTexture;
    private int shadeMarkerRadius;

    ColorEditorScreen(Screen lastScreen, Component colorName, int color, boolean prideEnabled, String prideFlagId,
            int previewLineLength, int previewLineWidth, boolean previewCuePeak, IntConsumer onChange,
            PrideSettingsConsumer onPrideChange, Runnable onCommit) {
        super(Component.translatable("screen.elytrapitchhelper.color.title", colorName));
        this.lastScreen = lastScreen;
        this.color = color & 0x00FFFFFF;
        this.lastAppliedColor = this.color;
        this.prideEnabled = prideEnabled;
        this.lastAppliedPrideEnabled = prideEnabled;
        this.prideFlagId = PrideFlag.sanitizeId(prideFlagId);
        this.lastAppliedPrideFlagId = this.prideFlagId;
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

        hexBox = new EditBox(font, layout.controlX + layout.controlWidth - 82, layout.hexY, 82, CONTROL_HEIGHT,
                Component.translatable("option.elytrapitchhelper.color.hex"));
        hexBox.setMaxLength(6);
        hexBox.setValue(ScreenText.hexColor(color));
        hexBox.setResponder(this::onHexChanged);
        addRenderableWidget(hexBox);

        int prideButtonGap = RGB_SLIDER_GAP;
        int prideButtonWidth = Math.max(58, (layout.controlWidth - prideButtonGap) / 2);
        prideModeButton = Button.builder(prideModeMessage(), button -> {
            prideEnabled = !prideEnabled;
            syncPrideControls();
            applyPrideChange();
        }).bounds(layout.controlX, layout.prideY, prideButtonWidth, CONTROL_HEIGHT).build();
        addRenderableWidget(prideModeButton);

        prideFlagButton = Button.builder(prideFlagMessage(), button -> {
            prideFlagId = PrideFlag.next(prideFlagId).id();
            syncPrideControls();
            applyPrideChange();
        }).bounds(layout.controlX + prideButtonWidth + prideButtonGap, layout.prideY,
                layout.controlWidth - prideButtonWidth - prideButtonGap, CONTROL_HEIGHT).build();
        addRenderableWidget(prideFlagButton);

        redSlider = rgbSlider(Component.translatable("option.elytrapitchhelper.color.red"), red(), value -> {
            setColorFromRgb((value << 16) | (green() << 8) | blue(), false);
        });
        greenSlider = rgbSlider(Component.translatable("option.elytrapitchhelper.color.green"), green(), value -> {
            setColorFromRgb((red() << 16) | (value << 8) | blue(), false);
        });
        blueSlider = rgbSlider(Component.translatable("option.elytrapitchhelper.color.blue"), blue(), value -> {
            setColorFromRgb((red() << 16) | (green() << 8) | value, false);
        });

        addSlider(redSlider, layout.controlX, layout.sliderY, layout.controlWidth);
        addSlider(greenSlider, layout.controlX, layout.sliderY + CONTROL_HEIGHT + RGB_SLIDER_GAP,
                layout.controlWidth);
        addSlider(blueSlider, layout.controlX, layout.sliderY + (CONTROL_HEIGHT + RGB_SLIDER_GAP) * 2,
                layout.controlWidth);
        syncPrideControls();

        int buttonWidth = Math.min(120, width - 40);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds((width - buttonWidth) / 2, height - 28, buttonWidth, CONTROL_HEIGHT).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        ColorEditorLayout layout = colorEditorLayout();
        int previewWidth = layout.controlWidth;
        int previewX = layout.controlX;
        int previewY = layout.previewY;
        int previewHeight = previewHeight();

        context.centeredText(font, title, width / 2, 15, 0xFFFFFF);
        renderPalette(context, layout);
        renderLinePreview(context, previewX, previewY, previewWidth, previewHeight);

        context.text(font, Component.translatable("option.elytrapitchhelper.color.hex"), previewX, layout.hexY + 6,
                0xFFFFFF);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        applyColorChange();
        applyPrideChange();
        minecraft.gui.setScreen(lastScreen);
    }

    @Override
    public void removed() {
        closePaletteTexture();
        super.removed();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation() && hexBox != null && hexBox.isFocused()) {
            commitHexBox();
            return true;
        }
        if (event.isEscape() && hexBox != null && hexBox.isFocused()) {
            blurHexBox();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (startPaletteDrag(event)) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (paletteDrag != PaletteDrag.NONE && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            updatePaletteColor(event.x(), event.y(), paletteDrag);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (paletteDrag != PaletteDrag.NONE && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            paletteDrag = PaletteDrag.NONE;
            setDragging(false);
            return true;
        }
        return super.mouseReleased(event);
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
        if (prideEnabled) {
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
        if (color != lastAppliedColor) {
            lastAppliedColor = color;
            onChange.accept(color);
        }
    }

    private void applyPrideChange() {
        prideFlagId = PrideFlag.sanitizeId(prideFlagId);
        if (prideEnabled != lastAppliedPrideEnabled || !prideFlagId.equals(lastAppliedPrideFlagId)) {
            lastAppliedPrideEnabled = prideEnabled;
            lastAppliedPrideFlagId = prideFlagId;
            onPrideChange.accept(prideEnabled, prideFlagId);
        }
    }

    private void syncPrideControls() {
        if (prideModeButton != null) {
            prideModeButton.setMessage(prideModeMessage());
        }
        if (prideFlagButton != null) {
            prideFlagButton.setMessage(prideFlagMessage());
            prideFlagButton.active = prideEnabled;
        }

        boolean colorControlsActive = !prideEnabled;
        if (hexBox != null) {
            if (!colorControlsActive && hexBox.isFocused()) {
                setFocused(null);
                hexBox.setFocused(false);
            }
            hexBox.active = colorControlsActive;
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

    private Component prideFlagMessage() {
        return Component.translatable(PrideFlag.byId(prideFlagId).translationKey());
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
            int hexY = prideY + CONTROL_HEIGHT + 8;
            return new ColorEditorLayout(paletteX, top, paletteSize, controlX, controlWidth, top, prideY, hexY,
                    hexY + 26);
        }

        int controlWidth = Math.max(140, Math.min(preferredControlWidth(), availableWidth));
        int stackedControlsHeight = previewHeight + CONTROL_HEIGHT + CONTROL_HEIGHT * 4 + RGB_SLIDER_GAP * 4 + 58;
        int paletteSize = clampInt(Math.min(availableWidth, height - stackedControlsHeight), 64, 140);
        int paletteX = (width - paletteSize) / 2;
        int controlX = (width - controlWidth) / 2;
        int previewY = top;
        int prideY = previewY + previewHeight + 6;
        int paletteY = prideY + CONTROL_HEIGHT + 6;
        int hexY = paletteY + paletteSize + 6;
        return new ColorEditorLayout(paletteX, paletteY, paletteSize, controlX, controlWidth, previewY, prideY,
                hexY, hexY + 24);
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

        int lineLength = Math.min(previewLineLength, Math.max(1, width - 8));
        int centerX = x + width / 2;
        int centerY = y + height / 2;
        int rgb = color & 0x00FFFFFF;
        int[] prideColors = prideEnabled ? PrideFlag.byId(prideFlagId).colors() : null;
        if (previewCuePeak) {
            float glowAlpha = 0.18f;
            drawCenteredPreviewRect(context, centerX, centerY, Math.min(lineLength + 18, width - 2),
                    Math.min(previewLineWidth + 6, height - 2), glowAlpha * 0.45f, rgb, prideColors);
            drawCenteredPreviewRect(context, centerX, centerY, Math.min(lineLength + 10, width - 2),
                    Math.min(previewLineWidth + 4, height - 2), glowAlpha, rgb, prideColors);
        }

        int lineX = centerX - lineLength / 2;
        int lineY = centerY - previewLineWidth / 2;
        drawPreviewRect(context, lineX, lineY, lineLength, previewLineWidth, 1.0f, rgb, prideColors);
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
        ensurePaletteTexture(layout);
        if (paletteTexture != null) {
            context.blit(paletteTexture.getTextureView(), paletteTexture.getSampler(), layout.paletteX,
                    layout.paletteY, layout.paletteX + layout.paletteSize, layout.paletteY + layout.paletteSize,
                    0.0f, 1.0f, 0.0f, 1.0f);
        }

        if (prideEnabled) {
            context.fill(layout.paletteX, layout.paletteY, layout.paletteX + layout.paletteSize,
                    layout.paletteY + layout.paletteSize, 0x99000000);
            return;
        }

        PaletteGeometry geometry = paletteGeometry(layout);
        int centerX = layout.paletteX + (int) Math.round(geometry.centerOffset);
        int centerY = layout.paletteY + (int) Math.round(geometry.centerOffset);
        double hueAngle = (1.0 - hue) * TWO_PI;
        int hueMarkerX = (int) Math.round(centerX + Math.cos(hueAngle) * geometry.markerRadius);
        int hueMarkerY = (int) Math.round(centerY + Math.sin(hueAngle) * geometry.markerRadius);
        drawMarker(context, hueMarkerX, hueMarkerY, Math.max(5, layout.paletteSize / 22), true);

        double[] shadePoint = squareToDisk(saturation * 2.0 - 1.0, 1.0 - value * 2.0);
        int shadeMarkerX = (int) Math.round(centerX + shadePoint[0] * geometry.shadeRadius);
        int shadeMarkerY = (int) Math.round(centerY + shadePoint[1] * geometry.shadeRadius);
        drawMarker(context, shadeMarkerX, shadeMarkerY, Math.max(4, layout.paletteSize / 28), false);
    }

    private void ensurePaletteTexture(ColorEditorLayout layout) {
        if (paletteTexture != null && paletteTextureSize == layout.paletteSize
                && Math.abs(paletteTextureHue - hue) < 0.000001) {
            return;
        }

        if (paletteTexture == null || paletteTextureSize != layout.paletteSize) {
            closePaletteTexture();
            NativeImage image = new NativeImage(layout.paletteSize, layout.paletteSize, true);
            writePalettePixels(image, layout);
            paletteTexture = new DynamicTexture(() -> "elytrapitchhelper color palette", image);
            paletteTextureSize = layout.paletteSize;
        } else {
            writePalettePixels(paletteTexture.getPixels(), layout);
            paletteTexture.upload();
        }
        paletteTextureHue = hue;
    }

    private void writePalettePixels(NativeImage image, ColorEditorLayout layout) {
        PaletteGeometry geometry = paletteGeometry(layout);

        for (int y = 0; y < layout.paletteSize; y++) {
            for (int x = 0; x < layout.paletteSize; x++) {
                double dx = x + 0.5 - geometry.centerOffset;
                double dy = y + 0.5 - geometry.centerOffset;
                double distance = Math.sqrt(dx * dx + dy * dy);
                int argb = 0;

                if (distance <= geometry.outerRadius && distance >= geometry.innerRadius) {
                    argb = 0xFF000000 | hsvToRgb(hueFromVector(dx, dy), 1.0, 1.0);
                } else if (distance <= geometry.shadeRadius) {
                    double[] square = diskToSquare(dx / geometry.shadeRadius, dy / geometry.shadeRadius);
                    double sampleSaturation = clamp01((square[0] + 1.0) * 0.5);
                    double sampleValue = clamp01((1.0 - square[1]) * 0.5);
                    argb = 0xFF000000 | hsvToRgb(hue, sampleSaturation, sampleValue);
                }

                image.setPixel(x, y, argb);
            }
        }
    }

    private void closePaletteTexture() {
        if (paletteTexture != null) {
            paletteTexture.close();
            paletteTexture = null;
            paletteTextureSize = 0;
            paletteTextureHue = -1.0;
        }
        closeMarkerTextures();
    }

    private void drawMarker(GuiGraphicsExtractor context, int centerX, int centerY, int radius, boolean hueMarker) {
        DynamicTexture markerTexture = markerTexture(radius, hueMarker);
        int halfSize = markerTexture.getPixels().getWidth() / 2;
        context.blit(markerTexture.getTextureView(), markerTexture.getSampler(), centerX - halfSize,
                centerY - halfSize, centerX + halfSize + 1, centerY + halfSize + 1, 0.0f, 1.0f, 0.0f, 1.0f);
    }

    private DynamicTexture markerTexture(int radius, boolean hueMarker) {
        DynamicTexture texture = hueMarker ? hueMarkerTexture : shadeMarkerTexture;
        int cachedRadius = hueMarker ? hueMarkerRadius : shadeMarkerRadius;
        if (texture != null && cachedRadius == radius) {
            return texture;
        }

        if (texture != null) {
            texture.close();
        }

        NativeImage image = markerImage(radius);
        texture = new DynamicTexture(() -> "elytrapitchhelper color marker", image);
        if (hueMarker) {
            hueMarkerTexture = texture;
            hueMarkerRadius = radius;
        } else {
            shadeMarkerTexture = texture;
            shadeMarkerRadius = radius;
        }
        return texture;
    }

    private NativeImage markerImage(int radius) {
        int halfSize = radius + 3;
        int size = halfSize * 2 + 1;
        NativeImage image = new NativeImage(size, size, true);
        double whiteInner = Math.max(0, radius - 1.6);
        double whiteOuter = radius + 0.6;
        double blackInner = Math.max(0, radius - 0.6);
        double blackOuter = radius + 1.9;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double dx = x - halfSize;
                double dy = y - halfSize;
                double distance = Math.sqrt(dx * dx + dy * dy);
                int argb = 0;
                if (distance >= whiteInner && distance <= whiteOuter) {
                    argb = 0xFFFFFFFF;
                } else if (distance >= blackInner && distance <= blackOuter) {
                    argb = 0xFF000000;
                }
                image.setPixel(x, y, argb);
            }
        }
        return image;
    }

    private void closeMarkerTextures() {
        if (hueMarkerTexture != null) {
            hueMarkerTexture.close();
            hueMarkerTexture = null;
            hueMarkerRadius = 0;
        }
        if (shadeMarkerTexture != null) {
            shadeMarkerTexture.close();
            shadeMarkerTexture = null;
            shadeMarkerRadius = 0;
        }
    }

    private boolean startPaletteDrag(MouseButtonEvent event) {
        if (prideEnabled || event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
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
        PaletteGeometry geometry = paletteGeometry(layout);
        double dx = mouseX - layout.paletteX - geometry.centerOffset;
        double dy = mouseY - layout.paletteY - geometry.centerOffset;
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance <= geometry.shadeRadius) {
            return PaletteDrag.SHADE;
        }
        if (distance >= geometry.innerRadius && distance <= geometry.outerRadius) {
            return PaletteDrag.HUE;
        }
        return PaletteDrag.NONE;
    }

    private void updatePaletteColor(double mouseX, double mouseY, PaletteDrag drag) {
        ColorEditorLayout layout = colorEditorLayout();
        PaletteGeometry geometry = paletteGeometry(layout);
        double dx = mouseX - layout.paletteX - geometry.centerOffset;
        double dy = mouseY - layout.paletteY - geometry.centerOffset;

        if (drag == PaletteDrag.HUE) {
            hue = hueFromVector(dx, dy);
        } else if (drag == PaletteDrag.SHADE) {
            double shadeX = dx / geometry.shadeRadius;
            double shadeY = dy / geometry.shadeRadius;
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

    private PaletteGeometry paletteGeometry(ColorEditorLayout layout) {
        double centerOffset = (layout.paletteSize - 1.0) * 0.5;
        double outerRadius = layout.paletteSize * 0.5;
        double ringThickness = Math.max(11.0, layout.paletteSize * 0.145);
        double innerRadius = outerRadius - ringThickness;
        double shadeRadius = Math.max(12.0, innerRadius - Math.max(5.0, layout.paletteSize * 0.035));
        double markerRadius = (outerRadius + innerRadius) * 0.5;
        return new PaletteGeometry(centerOffset, outerRadius, innerRadius, shadeRadius, markerRadius);
    }

    private void syncHsvFromColor(boolean resetHueWhenGray) {
        double[] hsv = rgbToHsv(red(), green(), blue());
        if (resetHueWhenGray || hsv[1] > 0.0001) {
            hue = hsv[0];
        }
        saturation = hsv[1];
        value = hsv[2];
    }

    private static double hueFromVector(double dx, double dy) {
        if (dx == 0.0 && dy == 0.0) {
            return 0.0;
        }
        double hue = 1.0 - Math.atan2(dy, dx) / TWO_PI;
        return hue - Math.floor(hue);
    }

    private static double[] squareToDisk(double x, double y) {
        double length = Math.sqrt(x * x + y * y);
        if (length <= 0.0) {
            return new double[] { 0.0, 0.0 };
        }

        double radius = Math.max(Math.abs(x), Math.abs(y));
        return new double[] { x / length * radius, y / length * radius };
    }

    private static double[] diskToSquare(double x, double y) {
        double distance = Math.sqrt(x * x + y * y);
        if (distance <= 0.0) {
            return new double[] { 0.0, 0.0 };
        }

        double unitX = x / distance;
        double unitY = y / distance;
        double maxComponent = Math.max(Math.abs(unitX), Math.abs(unitY));
        return new double[] { clamp(unitX * distance / maxComponent, -1.0, 1.0),
                clamp(unitY * distance / maxComponent, -1.0, 1.0) };
    }

    private static double[] rgbToHsv(int red, int green, int blue) {
        double r = red / 255.0;
        double g = green / 255.0;
        double b = blue / 255.0;
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double delta = max - min;
        double hue = 0.0;

        if (delta > 0.0) {
            if (max == r) {
                hue = ((g - b) / delta) % 6.0;
            } else if (max == g) {
                hue = (b - r) / delta + 2.0;
            } else {
                hue = (r - g) / delta + 4.0;
            }
            hue /= 6.0;
            if (hue < 0.0) {
                hue += 1.0;
            }
        }

        double saturation = max <= 0.0 ? 0.0 : delta / max;
        return new double[] { hue, saturation, max };
    }

    private static int hsvToRgb(double hue, double saturation, double value) {
        hue = hue - Math.floor(hue);
        saturation = clamp01(saturation);
        value = clamp01(value);

        double sector = hue * 6.0;
        int index = (int) Math.floor(sector);
        double fraction = sector - index;
        double p = value * (1.0 - saturation);
        double q = value * (1.0 - fraction * saturation);
        double t = value * (1.0 - (1.0 - fraction) * saturation);
        double r;
        double g;
        double b;

        switch (index % 6) {
            case 0 -> {
                r = value;
                g = t;
                b = p;
            }
            case 1 -> {
                r = q;
                g = value;
                b = p;
            }
            case 2 -> {
                r = p;
                g = value;
                b = t;
            }
            case 3 -> {
                r = p;
                g = q;
                b = value;
            }
            case 4 -> {
                r = t;
                g = p;
                b = value;
            }
            default -> {
                r = value;
                g = p;
                b = q;
            }
        }

        return (toRgbChannel(r) << 16) | (toRgbChannel(g) << 8) | toRgbChannel(b);
    }

    private static int toRgbChannel(double value) {
        return clampInt((int) Math.round(value * 255.0), 0, 255);
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int parseHexColor(String value) {
        if (value.length() != 6) {
            return -1;
        }
        try {
            return Integer.parseInt(value, 16) & 0x00FFFFFF;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private enum PaletteDrag {
        NONE,
        HUE,
        SHADE
    }

    @FunctionalInterface
    interface PrideSettingsConsumer {
        void accept(boolean enabled, String flagId);
    }

    private record ColorEditorLayout(int paletteX, int paletteY, int paletteSize, int controlX, int controlWidth,
            int previewY, int prideY, int hexY, int sliderY) {
    }

    private record PaletteGeometry(double centerOffset, double outerRadius, double innerRadius, double shadeRadius,
            double markerRadius) {
    }
}
