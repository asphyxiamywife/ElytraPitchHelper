package com.asphyxiamywife.elytrapitchhelper.screen;

final class ScreenGeometry {
    private ScreenGeometry() {
    }

    static boolean contains(int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
