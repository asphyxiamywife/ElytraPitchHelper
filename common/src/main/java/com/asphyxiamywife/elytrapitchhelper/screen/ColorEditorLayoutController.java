package com.asphyxiamywife.elytrapitchhelper.screen;

import static com.asphyxiamywife.elytrapitchhelper.util.MathUtil.clamp;

final class ColorEditorLayoutController {
    private static final int CONTROL_HEIGHT = 20;
    private static final int CONTROL_GAP = 16;
    private static final int RGB_SLIDER_GAP = 4;
    private static final int STACK_GAP = 6;
    private static final int MIN_STACK_GAP = 2;
    private static final int MIN_STACK_TOP = 4;
    private static final int MAX_PALETTE_SIZE = 172;
    private static final int MIN_STACKED_PALETTE_SIZE = 64;

    private ColorEditorLayoutController() {
    }

    static ColorEditorLayout calculate(int width, int height, int preferredControlWidth, int previewHeight) {
        return calculate(width, height, preferredControlWidth, previewHeight, true);
    }

    static ColorEditorLayout calculate(int width, int height, int preferredControlWidth, int previewHeight,
            boolean prideControlsVisible) {
        int availableWidth = Math.max(1, width - 40);
        int controlsHeight = previewHeight + (prideControlsVisible ? 156 : 102);
        int top = clamp(height - controlsHeight - 2, 34, 58);
        int doneWidth = Math.min(120, width - 40);
        int doneY = height - 28;
        if (availableWidth >= 280) {
            int minControlWidth = availableWidth >= 96 + CONTROL_GAP + preferredControlWidth ? preferredControlWidth
                    : 140;
            int maxPaletteByWidth = availableWidth - CONTROL_GAP - minControlWidth;
            int maxPaletteByHeight = Math.max(96, height - top - 36);
            int paletteSize = clamp(Math.min(Math.min(MAX_PALETTE_SIZE, maxPaletteByWidth), maxPaletteByHeight),
                    96, MAX_PALETTE_SIZE);
            int controlWidth = clamp(availableWidth - paletteSize - CONTROL_GAP, 140,
                    Math.max(220, preferredControlWidth));
            int contentWidth = paletteSize + CONTROL_GAP + controlWidth;
            int paletteX = (width - contentWidth) / 2;
            int controlX = paletteX + paletteSize + CONTROL_GAP;
            int prideY = top + previewHeight + 8;
            int customY = prideY + CONTROL_HEIGHT + 6;
            int hexY = prideControlsVisible ? customY + CONTROL_HEIGHT + 8 : top + previewHeight + 8;
            int sliderY = hexY + 26;
            int controlsBottom = sliderY + (CONTROL_HEIGHT + RGB_SLIDER_GAP) * 2 + CONTROL_HEIGHT;
            int centeredDoneX = (width - doneWidth) / 2;
            int paletteDoneX = paletteX + (paletteSize - doneWidth) / 2;
            int doneX = controlsBottom + 4 > doneY ? paletteDoneX : centeredDoneX;
            return new ColorEditorLayout(paletteX, top, paletteSize, controlX, controlWidth, top, prideY,
                    customY, hexY, sliderY, doneX, doneY);
        }

        return stacked(width, preferredControlWidth, previewHeight, availableWidth, top, doneWidth, doneY,
                prideControlsVisible);
    }

    private static ColorEditorLayout stacked(int width, int preferredControlWidth, int previewHeight,
            int availableWidth, int top, int doneWidth, int doneY, boolean prideControlsVisible) {
        int controlWidth = Math.max(140, Math.min(preferredControlWidth, availableWidth));
        int controlX = Math.max(0, (width - controlWidth) / 2);
        int slidersHeight = CONTROL_HEIGHT * 3 + RGB_SLIDER_GAP * 2;

        int stackedRowsHeight = previewHeight + CONTROL_HEIGHT * (prideControlsVisible ? 3 : 1) + slidersHeight;
        int gapCount = prideControlsVisible ? 5 : 3;
        int stackTop = clamp(Math.min(top, doneY - stackedRowsHeight - MIN_STACK_GAP * gapCount), MIN_STACK_TOP, top);
        int gap = clamp((doneY - stackTop - stackedRowsHeight) / gapCount, MIN_STACK_GAP, STACK_GAP);

        int previewY = stackTop;
        int prideY = previewY + previewHeight + gap;
        int customY = prideY + CONTROL_HEIGHT + gap;
        int paletteY = prideControlsVisible ? customY + CONTROL_HEIGHT + gap : prideY;

        int sliderY = doneY - gap - slidersHeight;
        int hexY = Math.max(paletteY, sliderY - gap - CONTROL_HEIGHT);
        sliderY = Math.max(sliderY, hexY + CONTROL_HEIGHT + gap);

        int paletteRoom = hexY - gap - paletteY;
        int paletteSize = paletteRoom >= MIN_STACKED_PALETTE_SIZE
                ? Math.min(Math.min(paletteRoom, availableWidth), MAX_PALETTE_SIZE)
                : 0;
        int paletteX = (width - paletteSize) / 2;
        return new ColorEditorLayout(paletteX, paletteY, paletteSize, controlX, controlWidth, previewY, prideY,
                customY, hexY, sliderY, (width - doneWidth) / 2, doneY);
    }
}
