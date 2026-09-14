package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;
import net.minecraft.client.gui.components.AbstractScrollArea;

record ProfileEditorLayout(int contentWidth, int rowWidth, int startX, int startY, int visibleRows,
        int totalRows, int contentHeight, int maxScroll, int scrollbarX, boolean needsScrollbar,
        int scroll) {
    static final int LIST_TOP = 56;
    static final int MAX_ROW_WIDTH = 310;
    static final int INDENT = 12;
    private static final double EDGE_EPSILON = 0.5;
    static final int WHEEL_STEP = 16;

    static ProfileEditorLayout of(int screenWidth, int screenHeight, int rowCount, boolean readOnly,
            int scroll) {
        return of(screenWidth, screenHeight, rowCount, Math.max(0, rowCount) * ConfigScreen.ROW_HEIGHT,
                readOnly, scroll);
    }

    static ProfileEditorLayout of(int screenWidth, int screenHeight, int rowCount, int contentHeight,
            boolean readOnly, int scroll) {
        int contentWidth = MathUtil.clamp(screenWidth - 40, 120, 440);
        int contentOffset = readOnly ? ConfigScreen.READ_ONLY_CONTENT_OFFSET : 0;
        int startY = LIST_TOP + contentOffset;
        int viewportBottom = screenHeight - ConfigScreen.FOOTER_OFFSET - ConfigScreen.CONTROL_GAP;
        int rowCapacity = Math.max(1, (viewportBottom - startY) / ConfigScreen.ROW_HEIGHT);

        int totalRows = Math.max(0, rowCount);
        int visibleRows = Math.min(totalRows, rowCapacity);
        int safeContentHeight = Math.max(0, contentHeight);
        int maxScroll = Math.max(0, safeContentHeight - visibleRows * ConfigScreen.ROW_HEIGHT);
        int clampedScroll = MathUtil.clamp(scroll, 0, maxScroll);
        boolean needsScrollbar = totalRows > visibleRows;

        int usableWidth = needsScrollbar
                ? contentWidth - 2 * (AbstractScrollArea.SCROLLBAR_WIDTH + ConfigScreen.CONTROL_GAP)
                : contentWidth;
        int rowWidth = Math.min(MAX_ROW_WIDTH, usableWidth);
        int startX = (screenWidth - rowWidth) / 2;
        int scrollbarX = Math.min(
                startX + rowWidth + ConfigScreen.CONTROL_GAP,
                (screenWidth - contentWidth) / 2 + contentWidth - AbstractScrollArea.SCROLLBAR_WIDTH);

        return new ProfileEditorLayout(contentWidth, rowWidth, startX, startY, visibleRows, totalRows,
                safeContentHeight, maxScroll, scrollbarX, needsScrollbar, clampedScroll);
    }

    int contentOffset() {
        return startY - LIST_TOP;
    }

    int scrollbarHeight() {
        return visibleRows * ConfigScreen.ROW_HEIGHT;
    }

    int rowY(int index) {
        return startY + index * ConfigScreen.ROW_HEIGHT - scroll;
    }

    double scrolledTop() {
        return startY - scroll;
    }

    int viewportBottom() {
        return startY + viewportHeight();
    }

    int viewportHeight() {
        return visibleRows * ConfigScreen.ROW_HEIGHT;
    }

    boolean drawsRow(double y) {
        return y + ConfigScreen.CONTROL_HEIGHT > startY + EDGE_EPSILON
                && y < viewportBottom() - EDGE_EPSILON;
    }

    boolean viewportContains(double y) {
        return y >= startY && y < viewportBottom();
    }

    int scrimTop(double bandTop, boolean topmost) {
        double top = topmost && needsScrollbar ? startY : bandTop - PanelBackdrop.ROW_BLEED;
        return (int) Math.max(top, startY);
    }

    int scrimBottom(double bandBottom, boolean bottommost) {
        double bottom = bottommost && needsScrollbar
                ? viewportBottom()
                : bandBottom + PanelBackdrop.ROW_BLEED;
        return (int) Math.min(bottom, viewportBottom());
    }

    double visibleFraction(double y) {
        double top = Math.max(y, startY);
        double bottom = Math.min(y + ConfigScreen.CONTROL_HEIGHT, viewportBottom());
        return MathUtil.clamp((bottom - top) / ConfigScreen.CONTROL_HEIGHT, 0.0, 1.0);
    }

    int rowX(int depth) {
        return startX;
    }

    int rowWidth(int depth) {
        return rowWidth;
    }
}
