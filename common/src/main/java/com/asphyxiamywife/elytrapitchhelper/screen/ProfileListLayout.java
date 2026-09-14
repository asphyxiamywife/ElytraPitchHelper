package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;
import net.minecraft.client.gui.components.AbstractScrollArea;

record ProfileListLayout(int contentWidth, int startX, int startY, int listWidth, int visibleRows,
        int maxScroll, int scrollbarX, boolean needsScrollbar, int scroll) {
    private static final int HEADER_BOTTOM = 64 + ConfigScreen.ROW_HEIGHT;
    static final int ACTION_ROW_OFFSET =
            ConfigScreen.FOOTER_OFFSET + ConfigScreen.CONTROL_HEIGHT + ConfigScreen.CONTROL_GAP;

    static ProfileListLayout of(int screenWidth, int screenHeight, int slotCount, boolean readOnly, int scroll) {
        int contentWidth = Math.min(screenWidth, MathUtil.clamp(screenWidth - 40, 260, 560));
        int startX = Math.max(0, (screenWidth - contentWidth) / 2);
        int contentOffset = readOnly ? ConfigScreen.READ_ONLY_CONTENT_OFFSET : 0;
        int startY = HEADER_BOTTOM + contentOffset;
        int listBottom = screenHeight - ACTION_ROW_OFFSET - ConfigScreen.CONTROL_GAP;
        int availableRows = Math.max(0, (listBottom - startY) / ConfigScreen.ROW_HEIGHT);
        int visibleRows = Math.min(Math.max(0, slotCount), availableRows);
        int maxScroll = Math.max(0, (slotCount - visibleRows) * ConfigScreen.ROW_HEIGHT);
        int clampedScroll = MathUtil.clamp(scroll, 0, maxScroll);
        boolean needsScrollbar = slotCount > visibleRows;
        int listWidth = needsScrollbar
                ? contentWidth - AbstractScrollArea.SCROLLBAR_WIDTH - ConfigScreen.CONTROL_GAP
                : contentWidth;
        int scrollbarX = startX + listWidth + ConfigScreen.CONTROL_GAP;
        return new ProfileListLayout(contentWidth, startX, startY, listWidth, visibleRows, maxScroll,
                scrollbarX, needsScrollbar, clampedScroll);
    }

    int contentOffset() {
        return startY - HEADER_BOTTOM;
    }

    int viewportHeight() {
        return visibleRows * ConfigScreen.ROW_HEIGHT;
    }

    int scrollRow() {
        return scroll / ConfigScreen.ROW_HEIGHT;
    }
}
