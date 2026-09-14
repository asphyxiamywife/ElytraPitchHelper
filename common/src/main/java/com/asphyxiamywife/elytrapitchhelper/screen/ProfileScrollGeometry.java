package com.asphyxiamywife.elytrapitchhelper.screen;

record ProfileScrollGeometry(int x, int y, int width, int height, int scrollerY, int scrollerHeight,
        int maxScrollAmount, int movableHeight) {
    boolean contains(double mouseX, double mouseY) {
        return ScreenGeometry.contains(x, y, width, height, mouseX, mouseY);
    }

    boolean containsScroller(double mouseY) {
        return mouseY >= scrollerY && mouseY < scrollerY + scrollerHeight;
    }
}
