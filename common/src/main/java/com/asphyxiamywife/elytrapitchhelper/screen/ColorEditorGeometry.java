package com.asphyxiamywife.elytrapitchhelper.screen;

record ColorEditorLayout(int paletteX, int paletteY, int paletteSize, int controlX, int controlWidth,
        int previewY, int prideY, int customY, int hexY, int sliderY, int doneX, int doneY) {
}

record PaletteGeometry(double centerOffset, double outerRadius, double innerRadius, double shadeRadius,
        double markerRadius) {
}

record PreviewLineGeometry(int x, int y, int length, int centerX, int centerY) {
}
