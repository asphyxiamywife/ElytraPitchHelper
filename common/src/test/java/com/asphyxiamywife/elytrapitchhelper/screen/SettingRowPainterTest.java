package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.screen.widget.SettingRowPainter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SettingRowPainterTest {
    @Test
    void aRowFadesWithItsSection() {
        int opaque = 0xFF336699;

        assertEquals(0xFF336699, SettingRowPainter.faded(opaque, 1.0f));
        assertEquals(0x00336699, SettingRowPainter.faded(opaque, 0.0f));
        assertEquals(0x336699, SettingRowPainter.faded(opaque, 0.5f) & 0x00FFFFFF,
                "fading must not disturb the colour itself");
        assertTrue(((SettingRowPainter.faded(opaque, 0.5f) >>> 24) & 0xFF) < 0xFF);
    }

    @Test
    void fadingClampsRatherThanWrapping() {
        assertEquals(0xFF336699, SettingRowPainter.faded(0xFF336699, 5.0f));
        assertEquals(0x00336699, SettingRowPainter.faded(0xFF336699, -2.0f));
    }

    @Test
    void fadingNeverBrightensAnAlreadyFaintColour() {
        int faint = 0x40FFFFFF;

        assertTrue(((SettingRowPainter.faded(faint, 1.0f) >>> 24) & 0xFF) == 0x40);
        assertTrue(((SettingRowPainter.faded(faint, 0.5f) >>> 24) & 0xFF) < 0x40);
    }

    @Test
    void childLabelsAreQuieterThanParentsAndDisabledQuieterStill() {
        int parent = SettingRowPainter.labelColor(true, 0);
        int child = SettingRowPainter.labelColor(true, 1);
        int disabled = SettingRowPainter.labelColor(false, 0);

        assertEquals(SettingRowPainter.LABEL_COLOR, parent);
        assertNotEquals(parent, child);
        assertEquals(SettingRowPainter.DISABLED_COLOR, disabled);
        assertEquals(disabled, SettingRowPainter.labelColor(false, 2),
                "a disabled row reads the same whatever its depth");
        assertTrue(brightness(child) < brightness(parent));
        assertTrue(brightness(disabled) < brightness(child));
    }

    @Test
    void inactiveAndDerivedStayWellClearOfTheBackground() {
        int disabled = brightness(SettingRowPainter.DISABLED_COLOR);
        int derived = brightness(SettingRowPainter.DERIVED_COLOR);

        assertTrue(derived < disabled, "derived reads softer than merely disabled");
        assertTrue(derived > 3 * 0x70, () -> "derived is too close to the background: " + derived);
        assertTrue(disabled < brightness(SettingRowPainter.LABEL_COLOR));
    }

    @Test
    void eachNestingLevelIsQuieterThanTheLast() {
        int parent = brightness(SettingRowPainter.labelColor(true, 0));
        int child = brightness(SettingRowPainter.labelColor(true, 1));
        int grandchild = brightness(SettingRowPainter.labelColor(true, 2));

        assertTrue(child < parent);
        assertTrue(grandchild < child);
        assertTrue(grandchild > 3 * 0x90, () -> "grandchild labels are too faint: " + grandchild);
    }

    private static int brightness(int argb) {
        return ((argb >> 16) & 0xFF) + ((argb >> 8) & 0xFF) + (argb & 0xFF);
    }
}
