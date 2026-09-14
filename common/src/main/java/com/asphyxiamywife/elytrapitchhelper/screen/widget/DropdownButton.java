package com.asphyxiamywife.elytrapitchhelper.screen.widget;

import com.asphyxiamywife.elytrapitchhelper.screen.ScrollbarMetrics;
import com.asphyxiamywife.elytrapitchhelper.screen.ScrollbarPainter;
import com.asphyxiamywife.elytrapitchhelper.screen.ScreenText;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.GuiGraphicsExtractor.HoveredTextEffects;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import static com.asphyxiamywife.elytrapitchhelper.util.MathUtil.clamp;

public final class DropdownButton<T> extends Button {
    private static final int MAX_VISIBLE_ROWS = 7;
    private static final int MENU_FILL_COLOR = 0xF41A1A1A;
    private static final int MENU_OUTLINE_COLOR = 0x66FFFFFF;
    private static final int ROW_HOVER_COLOR = 0x33FFFFFF;
    private static final int ROW_SELECTED_COLOR = 0x2EC7C1FF;
    private static final int ROW_SELECTED_HOVER_COLOR = 0x4DC7C1FF;
    private static final int MENU_PADDING = 3;
    private static final int MENU_MIN_WIDTH = 90;
    private static final int TEXT_INSET = 8;
    private static final int ROW_TEXT_COLOR = 0xFFFFFFFF;
    private static final int ROW_SELECTED_BAR_COLOR = 0xFFC7C1FF;
    private static final int ROW_DISABLED_TEXT_COLOR = 0xFFAAAAAA;
    private static final int SWATCH_SIZE = 10;
    private static final String CLOSED_MARKER = " ▾";
    private static final String OPEN_MARKER = " ▴";

    private final Component label;
    private final List<T> values;
    private final Function<T, Component> valueMessage;
    private final Function<T, int[]> stripeColors;
    private final Consumer<T> onSelect;
    private T value;
    private boolean open;
    private int scrollIndex;
    private Runnable onOpen = () -> {};
    private boolean rowStyle;
    private boolean centerValue;
    private boolean resetGutter;
    private int depth;

    public DropdownButton(int x, int y, int width, int height, Component label, List<T> values, T selected,
            Function<T, Component> valueMessage, Consumer<T> onSelect) {
        this(x, y, width, height, label, values, selected, valueMessage, value -> null, onSelect);
    }

    public DropdownButton(int x, int y, int width, int height, Component label, List<T> values, T selected,
            Function<T, Component> valueMessage, Function<T, int[]> stripeColors, Consumer<T> onSelect) {
        super(x, y, width, height, Component.empty(), DropdownButton::pressDropdown, DEFAULT_NARRATION);
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Dropdown values cannot be empty");
        }
        this.label = label;
        this.values = List.copyOf(values);
        this.value = this.values.contains(selected) ? selected : this.values.get(0);
        this.valueMessage = valueMessage;
        this.stripeColors = stripeColors;
        this.onSelect = onSelect;
        updateMessage();
    }

    private static void pressDropdown(Button button) {
        if (button instanceof DropdownButton<?> dropdown) {
            dropdown.toggleOpen();
        }
    }

    public void setRowStyle(int depth) {
        this.rowStyle = true;
        this.depth = depth;
    }

    public void centerValue() {
        this.centerValue = true;
    }

    public void reserveResetGutter() {
        resetGutter = true;
    }

    public void setOnOpen(Runnable onOpen) {
        this.onOpen = onOpen == null ? () -> {} : onOpen;
    }

    public boolean isOpen() {
        return open;
    }

    public T value() {
        return value;
    }

    public void setValue(T value) {
        T sanitized = values.contains(value) ? value : values.get(0);
        if (!Objects.equals(this.value, sanitized)) {
            this.value = sanitized;
        }
        updateMessage();
    }

    public void close() {
        if (open) {
            open = false;
            updateMessage();
        }
    }

    public boolean handleOpenMouseClicked(MouseButtonEvent event, int screenHeight) {
        if (!open || !visible) {
            return false;
        }

        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            close();
            return true;
        }

        MenuGeometry menu = menuGeometry(screenHeight);
        clampScroll(menu.visibleRows);
        if (menu.contains(event.x(), event.y())) {
            int row = (int) ((event.y() - menu.y - MENU_PADDING) / getHeight());
            int index = scrollIndex + row;
            if (event.y() < menu.y + MENU_PADDING || row >= menu.visibleRows) {
                close();
                return true;
            }
            if (index >= 0 && index < values.size()) {
                select(values.get(index));
                playDownSound(Minecraft.getInstance().getSoundManager());
            }
            close();
            return true;
        }

        if (containsCollapsed(event.x(), event.y())) {
            close();
            return true;
        }

        close();
        return true;
    }

    public boolean handleOpenMouseScrolled(double mouseX, double mouseY, double scrollY, int screenHeight) {
        if (!open || !visible) {
            return false;
        }

        MenuGeometry menu = menuGeometry(screenHeight);
        if (!menu.contains(mouseX, mouseY)) {
            return false;
        }

        int maxScroll = maxScroll(menu.visibleRows);
        if (maxScroll <= 0) {
            return true;
        }

        int direction = (int) Math.signum(scrollY);
        int nextScroll = clamp(scrollIndex - direction, 0, maxScroll);
        if (nextScroll != scrollIndex) {
            scrollIndex = nextScroll;
        }
        return true;
    }

    public boolean handleOpenKeyPressed(KeyEvent event) {
        if (!open) {
            return false;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!open && event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                && active && visible && containsCollapsed(event.x(), event.y())) {
            cycle(-1);
            playDownSound(Minecraft.getInstance().getSoundManager());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!open && scrollY != 0.0 && active && visible && containsCollapsed(mouseX, mouseY)) {
            cycle(scrollY > 0.0 ? -1 : 1);
            playDownSound(Minecraft.getInstance().getSoundManager());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    public void extractDropdownOverlay(GuiGraphicsExtractor context, Font font, int mouseX, int mouseY) {
        if (!open || !visible) {
            return;
        }

        MenuGeometry menu = menuGeometry(context.guiHeight());
        clampScroll(menu.visibleRows);
        if (menu.contains(mouseX, mouseY)) {
            context.requestCursor(active ? CursorTypes.POINTING_HAND : CursorTypes.NOT_ALLOWED);
        }
        context.fill(menu.x, menu.y, menu.x + menu.width, menu.y + menu.height, MENU_FILL_COLOR);
        context.outline(menu.x, menu.y, menu.width, menu.height, MENU_OUTLINE_COLOR);

        for (int row = 0; row < menu.visibleRows; row++) {
            int index = scrollIndex + row;
            if (index >= values.size()) {
                break;
            }
            T item = values.get(index);
            int rowY = menu.y + MENU_PADDING + row * getHeight();
            boolean hovered = active && mouseX >= menu.x && mouseX < menu.x + menu.width
                    && mouseY >= rowY && mouseY < rowY + getHeight();
            boolean selected = Objects.equals(item, value);
            if (selected || hovered) {
                context.fill(menu.x + 1, rowY, menu.x + menu.width - 1, rowY + getHeight(),
                        selected ? (hovered ? ROW_SELECTED_HOVER_COLOR : ROW_SELECTED_COLOR) : ROW_HOVER_COLOR);
            }
            if (selected) {
                context.fill(menu.x + 2, rowY + 4, menu.x + 4, rowY + getHeight() - 4,
                        ROW_SELECTED_BAR_COLOR);
            }
            extractRow(context, font, item, menu.x, rowY, menu.width, values.size() > menu.visibleRows);
        }

        extractScrollbar(context, menu);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (!rowStyle) {
            extractDefaultSprite(context);
            extractDefaultLabel(context.textRendererForWidget(this, HoveredTextEffects.NONE));
            return;
        }
        Font font = Minecraft.getInstance().font;
        float alpha = getAlpha();
        SettingRowPainter.paintBackground(context, getX(), getY(), getWidth(), getHeight(),
                active && isHovered(), isFocused(), alpha);
        if (label != null) {
            SettingRowPainter.paintLabel(context, font, label, getX(), getY(), getHeight(), depth,
                    SettingRowPainter.labelColor(active, depth), alpha);
        }
        Component reading = Component.empty().append(valueMessage.apply(value))
                .append(open ? OPEN_MARKER : CLOSED_MARKER);
        int valueColor = active ? SettingRowPainter.VALUE_COLOR : SettingRowPainter.DISABLED_COLOR;
        if (centerValue) {
            SettingRowPainter.paintCentered(context, font, reading, getX(), getWidth(), getY(),
                    getHeight(), valueColor, alpha);
        } else {
            SettingRowPainter.paintRightAligned(context, font, reading,
                    getX() + getWidth() - (resetGutter ? SettingRowPainter.RESET_GUTTER : 0), getY(),
                    getHeight(), valueColor, alpha);
        }
    }

    private void toggleOpen() {
        if (open) {
            close();
            return;
        }

        onOpen.run();
        open = true;
        alignScrollToSelection();
        updateMessage();
    }

    private void select(T nextValue) {
        if (!Objects.equals(value, nextValue)) {
            value = nextValue;
            onSelect.accept(nextValue);
        }
        updateMessage();
    }

    private void cycle(int direction) {
        int selected = values.indexOf(value);
        int currentIndex = selected < 0 ? 0 : selected;
        int nextIndex = Math.floorMod(currentIndex + direction, values.size());
        select(values.get(nextIndex));
    }

    private void updateMessage() {
        Component selectedValue = valueMessage.apply(value);
        Component baseMessage = label == null
                ? selectedValue
                : CommonComponents.optionNameValue(label, selectedValue);
        setMessage(Component.empty().append(baseMessage).append(open ? OPEN_MARKER : CLOSED_MARKER));
    }

    private void alignScrollToSelection() {
        int selected = values.indexOf(value);
        if (selected < 0) {
            scrollIndex = 0;
            return;
        }
        scrollIndex = Math.max(0, selected - MAX_VISIBLE_ROWS / 2);
    }

    private void clampScroll(int visibleRows) {
        scrollIndex = clamp(scrollIndex, 0, maxScroll(visibleRows));
    }

    private int maxScroll(int visibleRows) {
        return Math.max(0, values.size() - visibleRows);
    }

    private MenuGeometry menuGeometry(int screenHeight) {
        int preferredRows = Math.min(values.size(), MAX_VISIBLE_ROWS);
        int belowRows = Math.max(1, (screenHeight - getBottom() - 4) / getHeight());
        int aboveRows = Math.max(1, (getY() - 4) / getHeight());
        boolean openUp = belowRows < preferredRows && aboveRows > belowRows;
        int spaceRows = openUp ? aboveRows : belowRows;
        int visibleRows = Math.max(1, Math.min(values.size(), Math.min(MAX_VISIBLE_ROWS, spaceRows)));
        int menuHeight = visibleRows * getHeight() + 2 * MENU_PADDING;
        int menuY = openUp ? getY() - menuHeight + 1 : getBottom() - 1;
        menuY = clamp(menuY, 4, Math.max(4, screenHeight - menuHeight - 4));
        int menuWidth = clamp(menuContentWidth(), Math.min(MENU_MIN_WIDTH, getWidth()), getWidth());
        return new MenuGeometry(getX() + getWidth() - menuWidth, menuY, menuWidth, visibleRows * getHeight()
                + 2 * MENU_PADDING, visibleRows);
    }

    private boolean containsCollapsed(double mouseX, double mouseY) {
        return mouseX >= getX() && mouseX < getRight() && mouseY >= getY() && mouseY < getBottom();
    }

    private int menuContentWidth() {
        Font font = Minecraft.getInstance().font;
        int widest = 0;
        for (T item : values) {
            int width = font.width(valueMessage.apply(item));
            int[] colors = stripeColors.apply(item);
            if (colors != null && colors.length > 0) {
                width += SWATCH_SIZE + 6;
            }
            widest = Math.max(widest, width);
        }
        boolean scrollbar = values.size() > MAX_VISIBLE_ROWS;
        return widest + TEXT_INSET * 2 + (scrollbar ? 8 : 0);
    }

    private void extractRow(GuiGraphicsExtractor context, Font font, T item, int x, int y, int width,
            boolean hasScrollbar) {
        int textX = x + 8;
        int textWidth = width - 16;
        int[] colors = stripeColors.apply(item);
        if (colors != null && colors.length > 0) {
            int swatchX = x + 8;
            int swatchY = y + (getHeight() - SWATCH_SIZE) / 2;
            extractSwatch(context, swatchX, swatchY, colors);
            textX += SWATCH_SIZE + 6;
            textWidth -= SWATCH_SIZE + 6;
        }
        if (hasScrollbar) {
            textWidth -= 8;
        }

        String text = ScreenText.truncate(font, valueMessage.apply(item).getString(), Math.max(0, textWidth));
        int textY = y + (getHeight() - font.lineHeight) / 2;
        context.text(font, text, textX, textY, active ? ROW_TEXT_COLOR : ROW_DISABLED_TEXT_COLOR);
    }

    private static void extractSwatch(GuiGraphicsExtractor context, int x, int y, int[] colors) {
        int segments = Math.min(SWATCH_SIZE, colors.length);
        for (int i = 0; i < segments; i++) {
            int startX = x + i * SWATCH_SIZE / segments;
            int endX = x + (i + 1) * SWATCH_SIZE / segments;
            if (endX > startX) {
                int colorIndex = i * colors.length / segments;
                context.fill(startX, y, endX, y + SWATCH_SIZE,
                        0xFF000000 | (colors[colorIndex] & 0x00FFFFFF));
            }
        }
        context.outline(x - 1, y - 1, SWATCH_SIZE + 2, SWATCH_SIZE + 2, 0xFF000000);
        context.outline(x, y, SWATCH_SIZE, SWATCH_SIZE, 0xFFFFFFFF);
    }

    private void extractScrollbar(GuiGraphicsExtractor context, MenuGeometry menu) {
        if (values.size() <= menu.visibleRows) {
            return;
        }

        int laneX = menu.x + menu.width - MENU_PADDING - ScrollbarPainter.BAR_WIDTH;
        int trackY = menu.y + MENU_PADDING;
        int trackHeight = menu.height - 2 * MENU_PADDING;
        int maxScroll = maxScroll(menu.visibleRows);
        ScrollbarMetrics metrics = ScrollbarMetrics.of(trackY, trackHeight, menu.visibleRows, values.size(),
                scrollIndex, maxScroll, 8, 0);
        ScrollbarPainter.paint(context, laneX, ScrollbarPainter.BAR_WIDTH, trackY, trackHeight,
                metrics.thumbY(), metrics.thumbHeight(), false, false);
    }

    private record MenuGeometry(int x, int y, int width, int height, int visibleRows) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}
