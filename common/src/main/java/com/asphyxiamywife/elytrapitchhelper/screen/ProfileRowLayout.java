package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.screen.widget.SettingRowPainter;

record ProfileRowLayout(int contentX, int contentWidth, int chevronWidth, int activeX, int activeWidth,
        int nameX, int nameWidth, int actionX, int actionWidth, boolean deleteMode) {
    private static final int EDIT_WIDTH = 50;
    private static final int MARK_WIDTH = 78;
    private static final int MIN_NAME_WIDTH = 72;
    private static final int CHEVRON_WIDTH = 10;

    static ProfileRowLayout of(int startX, int listWidth, boolean deleteMode) {
        int contentX = startX + SettingRowPainter.ROW_PADDING;
        int contentWidth = listWidth - SettingRowPainter.ROW_PADDING * 2;
        int activeWidth = ConfigScreen.CONTROL_HEIGHT;
        int actionWidth = deleteMode ? MARK_WIDTH : EDIT_WIDTH;
        int activeX = contentX + CHEVRON_WIDTH;
        int nameX = activeX + activeWidth + ConfigScreen.CONTROL_GAP;
        int nameWidth = Math.max(MIN_NAME_WIDTH,
                contentWidth - CHEVRON_WIDTH - activeWidth - actionWidth - ConfigScreen.CONTROL_GAP * 2);
        return new ProfileRowLayout(contentX, contentWidth, CHEVRON_WIDTH, activeX, activeWidth,
                nameX, nameWidth, nameX + nameWidth + ConfigScreen.CONTROL_GAP, actionWidth, deleteMode);
    }

    int chevronX() {
        return contentX;
    }

    int nameRight() {
        return nameX + nameWidth;
    }

    int actionsRight() {
        return actionX + actionWidth;
    }

    int stripX() {
        return nameX;
    }

    int stripWidth() {
        return actionsRight() - nameX;
    }
}
