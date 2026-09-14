package com.asphyxiamywife.elytrapitchhelper.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfigSaveFailureScreenTest {
    @Test
    void allThreeButtonsStayInsideTheWindow() {
        for (int width = 120; width <= 800; width++) {
            ConfigSaveFailureScreen.ButtonRow row = ConfigSaveFailureScreen.buttonRow(width);
            int screenWidth = width;
            assertTrue(row.x() >= 0, () -> "the row starts off the left edge at width " + screenWidth);
            assertTrue(row.right() <= width, () -> "the row runs off the right edge at width " + screenWidth);
            assertTrue(row.buttonWidth() > 0, () -> "a button collapsed at width " + screenWidth);
        }
    }

    @Test
    void anOrdinaryWindowStillGetsTheFullSizedRow() {
        assertEquals(89, ConfigSaveFailureScreen.buttonRow(320).buttonWidth());
        assertEquals(120, ConfigSaveFailureScreen.buttonRow(800).buttonWidth());
    }
}
