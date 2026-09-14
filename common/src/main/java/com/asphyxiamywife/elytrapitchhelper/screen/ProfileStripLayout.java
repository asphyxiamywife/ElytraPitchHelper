package com.asphyxiamywife.elytrapitchhelper.screen;

record ProfileStripLayout(int startX, int width, boolean wideLabels) {
    static final int ACTIONS = 4;
    private static final int WIDE_LABEL_WIDTH = 80;

    static ProfileStripLayout of(int startX, int width) {
        int safeWidth = Math.max(0, width);
        return new ProfileStripLayout(startX, safeWidth,
                actionWidthAt(safeWidth, 0) >= WIDE_LABEL_WIDTH);
    }

    int actionX(int action) {
        return startX + boundary(width, action);
    }

    int actionWidth(int action) {
        return actionWidthAt(width, action);
    }

    int actionsRight() {
        return actionX(ACTIONS - 1) + actionWidth(ACTIONS - 1);
    }

    private static int actionWidthAt(int width, int action) {
        return Math.max(0, boundary(width, action + 1) - boundary(width, action) - ConfigScreen.CONTROL_GAP);
    }

    private static int boundary(int width, int action) {
        return (width + ConfigScreen.CONTROL_GAP) * action / ACTIONS;
    }
}
