package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.CommandPaletteAppearanceSettings;
import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingSpec;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingsRegistry;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.ColorRow;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.FlatButton;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.SettingRow;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.SliderRow;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class CommandPaletteAppearanceScreen extends Screen implements ConfigWorkflowChild {
    private final ValueScroll valueScroll = new ValueScroll();
    private static final int PREVIEW_ROWS = 4;
    private static final int COLOR_EDITOR_PREVIEW_ROWS = 3;
    private static final int PREVIEW_HEIGHT = CommandPalettePreviewRenderer.previewHeight(PREVIEW_ROWS);
    private static final int CONTROL_ROWS = 4;

    private final Screen lastScreen;

    @Override
    public Screen configWorkflowParent() {
        return lastScreen;
    }
    private final Supplier<Profile> profileSupplier;
    private final Consumer<Profile> onChange;
    private final ColorEditorScreen.CommitHandler onCommit;
    private final ColorEditorHistoryController history;

    CommandPaletteAppearanceScreen(Screen lastScreen, Supplier<Profile> profileSupplier, Consumer<Profile> onChange,
            BooleanSupplier onCommit,
            ColorEditorScreen.HistoryController history) {
        this(lastScreen, profileSupplier, onChange, afterCommit -> {
            if (onCommit.getAsBoolean()) {
                afterCommit.run();
            }
        }, history);
    }

    CommandPaletteAppearanceScreen(Screen lastScreen, Supplier<Profile> profileSupplier, Consumer<Profile> onChange,
            ColorEditorScreen.CommitHandler onCommit, ColorEditorScreen.HistoryController history) {
        super(Component.translatable("screen.elytrapitchhelper.command_palette_appearance.title"));
        this.lastScreen = lastScreen;
        this.profileSupplier = profileSupplier;
        this.onChange = onChange;
        this.onCommit = onCommit;
        this.history = new ColorEditorHistoryController(history);
    }

    @Override
    protected void init() {
        Layout layout = layout();
        int rowWidth = Math.min(ProfileEditorLayout.MAX_ROW_WIDTH, layout.contentWidth());
        int rowX = (width - rowWidth) / 2;

        addRow(0, rowX, layout.controlsY(), rowWidth,
                percentSlider(integerSpec("command_palette_shadow")));
        addRow(1, rowX, layout.controlsY(), rowWidth,
                percentSlider(integerSpec("command_palette_blur")));
        addRow(2, rowX, layout.controlsY(), rowWidth,
                colorRow(integerSpec("command_palette_accent_color"), true));
        addRow(3, rowX, layout.controlsY(), rowWidth,
                colorRow(integerSpec("command_palette_base_color"), false));

        int doneWidth = Math.min(120, width - 40);
        addRenderableWidget(FlatButton.of(CommonComponents.GUI_DONE, (width - doneWidth) / 2,
                height - 28, doneWidth, ConfigScreen.CONTROL_HEIGHT, button -> onClose()));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.centeredText(font, title, width / 2, 15, 0xFFFFFF);
        drawPreview(context);
        Layout layout = layout();
        int rowWidth = Math.min(ProfileEditorLayout.MAX_ROW_WIDTH, layout.contentWidth());
        PanelBackdrop.paintBand(context, (width - rowWidth) / 2, rowWidth, layout.controlsY(),
                layout.controlsY() + (CONTROL_ROWS - 1) * ConfigScreen.ROW_HEIGHT
                        + ConfigScreen.CONTROL_HEIGHT,
                false);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
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
    public boolean keyPressed(KeyEvent event) {
        if (history.handleShortcut(event, this::rebuildWidgets)) {
            return true;
        }
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void tick() {
        super.tick();
        history.acceptExternalRevision();
    }

    @Override
    public void onClose() {
        onCommit.commit(() -> {
            if (minecraft.screen == this) {
                minecraft.setScreen(lastScreen);
            }
        });
    }

    private void drawPreview(GuiGraphicsExtractor context) {
        CommandPaletteAppearanceSettings appearance = appearance();
        Layout layout = layout();
        int shadowAlpha = appearance.shadowOpacity() * 255 / 100;
        context.fill(layout.x(), layout.previewY(), layout.x() + layout.contentWidth(),
                layout.previewY() + PREVIEW_HEIGHT, CommandPalettePreviewRenderer.argb(shadowAlpha, 0));

        int panelWidth = Math.min(CommandPalettePreviewRenderer.PANEL_MAX_WIDTH, layout.contentWidth() - 20);
        int panelX = layout.x() + (layout.contentWidth() - panelWidth) / 2;
        CommandPalettePreviewRenderer.drawPreview(context, font, panelX, layout.previewY(), panelWidth,
                PREVIEW_ROWS, appearance);
    }

    private void addRow(int index, int rowX, int startY, int rowWidth, AbstractWidget widget) {
        widget.setPosition(rowX, startY + index * ConfigScreen.ROW_HEIGHT);
        widget.setSize(rowWidth, ConfigScreen.CONTROL_HEIGHT);
        addRenderableWidget(widget);
    }

    private SliderRow percentSlider(SettingSpec<Integer> spec) {
        SettingSpec.Control<?> control = spec.control();
        if (control instanceof SettingSpec.Hidden<?> hidden) {
            control = hidden.metadata();
        }
        if (!(control instanceof SettingSpec.IntSlider slider)) {
            throw new IllegalStateException("Expected integer slider metadata for " + spec.id());
        }
        return resettable(new SliderRow(Component.translatable(spec.labelKey()), font, 0,
                spec.get().apply(profileSupplier.get()), slider.min(), slider.max(), slider.step(),
                ScreenText::formatPercent, value -> apply(spec, (int) Math.round(value)),
                spec.actionKey(), history.sliderListener()), spec);
    }

    private ColorRow colorRow(SettingSpec<Integer> spec, boolean editsAccent) {
        Component label = Component.translatable(spec.labelKey());
        return resettable(new ColorRow(label, font, 0, () -> spec.get().apply(profileSupplier.get()), () -> null,
                () -> ScreenText.colorComponent(spec.get().apply(profileSupplier.get())),
                () -> minecraft.setScreen(new ColorEditorScreen(this, label,
                        spec.get().apply(profileSupplier.get()), 96, 4, false, value -> {
                            apply(spec, value & 0x00FFFFFF);
                            rebuildWidgets();
                        }, onCommit, spec.actionKey(), () -> spec.get().apply(profileSupplier.get()),
                        history.delegate(), palettePreviewRenderer(editsAccent)))), spec);
    }

    private <T extends SettingRow, V> T resettable(T row, SettingSpec<V> spec) {
        V defaultValue = spec.get().apply(Config.defaultProfileTemplate());
        row.setReset(() -> !Objects.deepEquals(spec.get().apply(profileSupplier.get()), defaultValue),
                () -> resetSetting(spec, defaultValue));
        return row;
    }

    private <T> void resetSetting(SettingSpec<T> spec, T defaultValue) {
        if (Objects.deepEquals(spec.get().apply(profileSupplier.get()), defaultValue)) {
            return;
        }
        history.begin("reset-" + spec.id());
        apply(spec, defaultValue);
        history.end(true, false);
        rebuildWidgets();
    }

    private ColorEditorScreen.PreviewRenderer palettePreviewRenderer(boolean editsAccent) {
        return (context, previewFont, x, y, previewWidth, previewHeight, color) -> {
            CommandPaletteAppearanceSettings preview = editsAccent
                    ? appearance().withAccentColorRgb(color & 0x00FFFFFF)
                    : appearance().withBaseColorRgb(color & 0x00FFFFFF);

            int rows = CommandPalettePreviewRenderer.rowsFittingHeight(previewHeight, COLOR_EDITOR_PREVIEW_ROWS);
            int drawHeight = CommandPalettePreviewRenderer.previewHeight(rows);
            int drawY = y + Math.max(0, (previewHeight - drawHeight) / 2);
            int drawWidth = Math.min(CommandPalettePreviewRenderer.PANEL_MAX_WIDTH, previewWidth);
            int drawX = x + (previewWidth - drawWidth) / 2;
            CommandPalettePreviewRenderer.drawPreview(context, previewFont, drawX, drawY, drawWidth,
                    rows, preview);
        };
    }

    private CommandPaletteAppearanceSettings appearance() {
        return profileSupplier.get().commandPaletteAppearance();
    }

    private <T> void apply(SettingSpec<T> spec, T value) {
        onChange.accept(spec.apply(profileSupplier.get(), value));
    }

    @SuppressWarnings("unchecked")
    private static SettingSpec<Integer> integerSpec(String id) {
        SettingSpec<?> spec = SettingsRegistry.byId(id);
        if (spec == null) {
            throw new IllegalStateException("Missing setting " + id);
        }
        return (SettingSpec<Integer>) spec;
    }

    private Layout layout() {
        int contentWidth = Math.max(220, Math.min(440, width - 40));
        int previewY = 42;
        int controlsHeight = CONTROL_ROWS * ConfigScreen.ROW_HEIGHT;
        int controlsY = Math.max(previewY, Math.min(previewY + PREVIEW_HEIGHT + 16,
                height - ConfigScreen.FOOTER_OFFSET - ConfigScreen.CONTROL_GAP - controlsHeight));
        return new Layout(contentWidth, (width - contentWidth) / 2, previewY, controlsY);
    }

    private record Layout(int contentWidth, int x, int previewY, int controlsY) {
    }
}
