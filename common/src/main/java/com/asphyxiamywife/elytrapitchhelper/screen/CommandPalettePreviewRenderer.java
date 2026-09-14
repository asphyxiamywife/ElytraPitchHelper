package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.CommandPaletteAppearanceSettings;
import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

final class CommandPalettePreviewRenderer {
    static final int PANEL_MAX_WIDTH = 420;
    static final int PANEL_MIN_WIDTH = 260;
    static final int PANEL_MARGIN = 24;
    static final int SEARCH_HEIGHT = 20;
    static final int ROW_HEIGHT = 30;
    static final int ROW_GAP = 2;
    static final int MAX_RESULTS = 8;
    static final int SEARCH_MARGIN = 10;
    static final int ROW_MARGIN = 6;

    private static final int PANEL_TOP_PADDING = 10;
    private static final int SEARCH_ROW_GAP = 10;
    private static final int PANEL_BOTTOM_PADDING = 8;
    private static final List<PreviewRow> PREVIEW_ROWS = List.of(
            new PreviewRow(Component.translatable("palette.elytrapitchhelper.toggle"),
                    Component.translatable("palette.elytrapitchhelper.category.actions")),
            new PreviewRow(Component.translatable("palette.elytrapitchhelper.open_category",
                    Component.translatable("screen.elytrapitchhelper.category.amplitude")),
                    Component.translatable("palette.elytrapitchhelper.category.settings")),
            new PreviewRow(Component.translatable("palette.elytrapitchhelper.open_category",
                    Component.translatable("screen.elytrapitchhelper.category.general")),
                    Component.translatable("palette.elytrapitchhelper.category.settings")),
            new PreviewRow(Component.translatable("palette.elytrapitchhelper.open_profiles"),
                    Component.translatable("palette.elytrapitchhelper.category.profiles")));

    private CommandPalettePreviewRenderer() {
    }

    static int panelHeight(int rowCount) {
        int rows = MathUtil.clamp(rowCount, 1, MAX_RESULTS);
        return PANEL_TOP_PADDING + SEARCH_HEIGHT + SEARCH_ROW_GAP + rows * ROW_HEIGHT
                + Math.max(0, rows - 1) * ROW_GAP + PANEL_BOTTOM_PADDING;
    }

    static int previewHeight(int rowCount) {
        return panelHeight(rowCount);
    }

    static int rowsFittingHeight(int availableHeight, int maximumRows) {
        int rows = MathUtil.clamp(maximumRows, 1, MAX_RESULTS);
        while (rows > 1 && previewHeight(rows) > availableHeight) {
            rows--;
        }
        return rows;
    }

    static int firstRowY(int panelY) {
        return panelY + PANEL_TOP_PADDING + SEARCH_HEIGHT + SEARCH_ROW_GAP;
    }

    static void drawPanel(GuiGraphicsExtractor context, int panelX, int panelY,
            int panelWidth, int panelHeight, CommandPaletteAppearanceSettings appearance) {
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight,
                argb(238, appearance.baseColorRgb()));
        context.outline(panelX, panelY, panelWidth, panelHeight, argb(255, appearance.accentColorRgb()));
    }

    static void drawActionRows(GuiGraphicsExtractor context, Font font, int panelX, int panelY, int panelWidth,
            List<PaletteAction> actions, int selectedIndex, CommandPaletteAppearanceSettings appearance) {
        if (actions.isEmpty()) {
            int rowX = panelX + ROW_MARGIN;
            int rowY = firstRowY(panelY);
            context.text(font, Component.translatable("palette.elytrapitchhelper.no_results"),
                    rowX + 4, rowY + 8, 0xFFAAAAAA, false);
            return;
        }

        int rowCount = Math.min(actions.size(), MAX_RESULTS);
        for (int i = 0; i < rowCount; i++) {
            PaletteAction action = actions.get(i);
            drawRow(context, font, panelX, panelY, panelWidth, i, i == selectedIndex,
                    action.title(), action.category(), appearance);
        }
    }

    static void drawPreview(GuiGraphicsExtractor context, Font font, int x, int y, int width, int rowCount,
            CommandPaletteAppearanceSettings appearance) {
        int rows = MathUtil.clamp(rowCount, 1, PREVIEW_ROWS.size());
        int panelY = y;
        int panelHeight = panelHeight(rows);
        drawPanel(context, x, panelY, width, panelHeight, appearance);
        drawSearchPreview(context, font, x, panelY, width, appearance);
        for (int i = 0; i < rows; i++) {
            PreviewRow row = PREVIEW_ROWS.get(i);
            drawRow(context, font, x, panelY, width, i, i == 0, row.title(), row.category(), appearance);
        }
    }

    static void drawSearchPreview(GuiGraphicsExtractor context, Font font, int panelX, int panelY, int panelWidth,
            CommandPaletteAppearanceSettings appearance) {
        int x = panelX + SEARCH_MARGIN;
        int y = panelY + PANEL_TOP_PADDING;
        int width = panelWidth - SEARCH_MARGIN * 2;
        int fill = mix(appearance.baseColorRgb() & 0x00FFFFFF, 0, 0.62f);
        context.fill(x, y, x + width, y + SEARCH_HEIGHT, searchFieldColor(appearance));
        context.outline(x, y, width, SEARCH_HEIGHT, 0xFFEFEFEF);
        context.text(font, Component.translatable("palette.elytrapitchhelper.search_hint"),
                x + 5, y + 6, argb(255, readableTextColor(fill)), false);
    }

    static int searchFieldColor(CommandPaletteAppearanceSettings appearance) {
        return argb(232, mix(appearance.baseColorRgb() & 0x00FFFFFF, 0, 0.62f));
    }

    static int argb(int alpha, int rgb) {
        return (MathUtil.clamp(alpha, 0, 255) << 24) | (rgb & 0x00FFFFFF);
    }

    private static void drawRow(GuiGraphicsExtractor context, Font font, int panelX, int panelY, int panelWidth,
            int index, boolean selected, Component title, Component category,
            CommandPaletteAppearanceSettings appearance) {
        int rowX = panelX + ROW_MARGIN;
        int rowY = firstRowY(panelY) + index * (ROW_HEIGHT + ROW_GAP);
        int rowWidth = panelWidth - ROW_MARGIN * 2;
        int base = appearance.baseColorRgb() & 0x00FFFFFF;
        int accent = appearance.accentColorRgb() & 0x00FFFFFF;
        int rowBase = selected ? mix(base, accent, 0.35f) : mix(base, 0xFFFFFF, 0.07f);
        context.fill(rowX, rowY, rowX + rowWidth, rowY + ROW_HEIGHT, argb(selected ? 204 : 170, rowBase));
        context.outline(rowX, rowY, rowWidth, ROW_HEIGHT,
                selected ? argb(255, accent) : argb(255, mix(base, 0xFFFFFF, 0.14f)));
        context.text(font, ScreenText.truncate(font, title.getString(), rowWidth - 12),
                rowX + 6, rowY + 5, argb(255, readableTextColor(rowBase)), false);
        context.text(font, ScreenText.truncate(font, category.getString(), rowWidth - 12),
                rowX + 6, rowY + 17, argb(255, mix(readableTextColor(rowBase), rowBase, 0.28f)), false);
    }

    private static int mix(int a, int b, float amount) {
        amount = MathUtil.clamp01(amount);
        int red = Math.round(red(a) + (red(b) - red(a)) * amount);
        int green = Math.round(green(a) + (green(b) - green(a)) * amount);
        int blue = Math.round(blue(a) + (blue(b) - blue(a)) * amount);
        return MathUtil.packRgb(red, green, blue);
    }

    private static int readableTextColor(int background) {
        int luminance = red(background) * 299 + green(background) * 587 + blue(background) * 114;
        return luminance >= 128000 ? 0x111111 : 0xFFFFFF;
    }

    private static int red(int color) {
        return MathUtil.red(color);
    }

    private static int green(int color) {
        return MathUtil.green(color);
    }

    private static int blue(int color) {
        return MathUtil.blue(color);
    }

    private record PreviewRow(Component title, Component category) {
    }
}
