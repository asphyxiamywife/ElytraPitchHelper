package com.asphyxiamywife.elytrapitchhelper.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ColorEditorLayoutTest {
    private static final int CONTROL_HEIGHT = 20;
    private static final int SLIDER_GAP = 4;
    private static final int DONE_WIDTH = 120;

    @Test
    void paletteColorEditorKeepsItsPreviewAndControlsReachableAtHighGuiScale() throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        var unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        var instance = net.minecraft.client.Minecraft.class.getDeclaredField("instance");
        instance.setAccessible(true);
        Object previous = instance.get(null);
        try {
            instance.set(null, unsafeClass.getMethod("allocateInstance", Class.class)
                    .invoke(unsafe, net.minecraft.client.Minecraft.class));
            ColorEditorScreen editor = new ColorEditorScreen(null,
                    net.minecraft.network.chat.Component.literal("Accent"), 0, 96, 4, false,
                    value -> {}, () -> true, "accent", () -> 0, null,
                    (context, font, x, y, width, height, color) -> {});
            for (int width : new int[] {300, 320, 411, 822}) {
                for (int height : new int[] {240, 270, 300, 450}) {
                    editor.width = width;
                    editor.height = height;
                    ColorEditorLayout layout = editor.colorEditorLayout();
                    String at = " at " + width + "x" + height;
                    int previewHeight = editor.previewHeight();
                    assertTrue(layout.previewY() >= 0, at);
                    assertTrue(layout.previewY() + previewHeight <= layout.hexY(),
                            "preview overlaps hex field" + at);
                    assertTrue(layout.hexY() + CONTROL_HEIGHT <= layout.sliderY(), at);
                    assertTrue(layout.sliderY() + 68 <= height, "sliders leave the screen" + at);
                    assertFalse(overlaps(layout.controlX(), layout.hexY(), layout.controlWidth(),
                            layout.sliderY() + 68 - layout.hexY(),
                            layout.doneX(), layout.doneY(), DONE_WIDTH, CONTROL_HEIGHT),
                            "controls overlap Done" + at);
                }
            }
            editor.height = 450;
            org.junit.jupiter.api.Assertions.assertEquals(CommandPalettePreviewRenderer.previewHeight(3),
                    editor.previewHeight(), "Tall screens retain the full preview");
        } finally {
            instance.set(null, previous);
        }
    }

    @Test
    void highGuiScaleKeepsDoneButtonOutOfRgbControls() {
        ColorEditorLayout layout = ColorEditorScreen.calculateColorEditorLayout(411, 240, 220, 24);

        assertTrue(layout.sliderY() + CONTROL_HEIGHT * 3 + SLIDER_GAP * 2 <= 240);
        assertFalse(overlaps(layout.controlX(), layout.sliderY(), layout.controlWidth(),
                CONTROL_HEIGHT * 3 + SLIDER_GAP * 2,
                layout.doneX(), layout.doneY(), DONE_WIDTH, CONTROL_HEIGHT));
    }

    @Test
    void tallScreensKeepDoneButtonCenteredBelowContent() {
        ColorEditorLayout layout = ColorEditorScreen.calculateColorEditorLayout(822, 450, 220, 24);

        assertTrue(layout.doneX() == (822 - DONE_WIDTH) / 2);
        assertFalse(overlaps(layout.controlX(), layout.sliderY(), layout.controlWidth(),
                CONTROL_HEIGHT * 3 + SLIDER_GAP * 2,
                layout.doneX(), layout.doneY(), DONE_WIDTH, CONTROL_HEIGHT));
    }

    @Test
    void theNarrowColumnKeepsEveryControlOnScreenAndClearOfDone() {
        for (int previewHeight : new int[] {24, 42, 96}) {
            int smallestUsableHeight = previewHeight + 170;
            for (int width = 200; width < 320; width += 4) {
                for (int height = smallestUsableHeight; height <= 480; height += 4) {
                    assertNarrowColumnIsUsable(width, height, previewHeight);
                }
            }
        }
    }

    @Test
    void theNarrowColumnFitsAtThreeHundredByTwoForty() {
        assertNarrowColumnIsUsable(300, 240, 24);
    }

    private static void assertNarrowColumnIsUsable(int width, int height, int previewHeight) {
        ColorEditorLayout layout = ColorEditorScreen.calculateColorEditorLayout(width, height, 220, previewHeight);
        String at = " at " + width + "x" + height + " preview=" + previewHeight;
        int slidersHeight = CONTROL_HEIGHT * 3 + SLIDER_GAP * 2;

        assertTrue(layout.sliderY() + slidersHeight <= height, () -> "sliders run off the bottom" + at);
        assertTrue(layout.hexY() + CONTROL_HEIGHT <= layout.sliderY(), () -> "hex field overlaps the sliders" + at);
        assertTrue(layout.customY() + CONTROL_HEIGHT <= layout.hexY(),
                () -> "the custom stripe row overlaps the hex field" + at);
        assertTrue(layout.prideY() + CONTROL_HEIGHT <= layout.customY(),
                () -> "the pride row overlaps the custom stripe row" + at);
        assertTrue(layout.controlX() >= 0, () -> "the control column starts off the left edge" + at);
        assertFalse(overlaps(layout.controlX(), layout.sliderY(), layout.controlWidth(), slidersHeight,
                layout.doneX(), layout.doneY(), DONE_WIDTH, CONTROL_HEIGHT),
                () -> "the sliders overlap Done" + at);
        assertFalse(overlaps(layout.controlX(), layout.hexY(), layout.controlWidth(), CONTROL_HEIGHT,
                layout.doneX(), layout.doneY(), DONE_WIDTH, CONTROL_HEIGHT),
                () -> "the hex field overlaps Done" + at);
        if (layout.paletteSize() > 0) {
            assertTrue(layout.paletteY() + layout.paletteSize() <= layout.hexY(),
                    () -> "the palette overlaps the hex field" + at);
            assertTrue(layout.paletteX() >= 0, () -> "the palette starts off the left edge" + at);
        }
    }

    @Test
    void theNarrowColumnStillShowsAPaletteWhenThereIsRoomForOne() {
        ColorEditorLayout layout = ColorEditorScreen.calculateColorEditorLayout(300, 480, 220, 24);

        assertTrue(layout.paletteSize() >= 64);
    }

    private static boolean overlaps(int leftA, int topA, int widthA, int heightA,
            int leftB, int topB, int widthB, int heightB) {
        return leftA < leftB + widthB && leftA + widthA > leftB
                && topA < topB + heightB && topA + heightA > topB;
    }
}
