package com.asphyxiamywife.elytrapitchhelper.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SliderRowClickTest {
    private static final int ROW_LEFT = 100;
    private static final int ROW_WIDTH = 310;

    private static boolean onTrack(double mouseX) {
        return com.asphyxiamywife.elytrapitchhelper.screen.widget.SliderRow.onTrackForTesting(
                ROW_LEFT, ROW_WIDTH, mouseX);
    }

    @Test
    void theLabelEndOfTheRowIsNotTheSlider() {
        assertFalse(onTrack(ROW_LEFT + 1), "the label must not move the value");
        assertFalse(onTrack(ROW_LEFT + 60), "nor the gap before the track");
    }

    @Test
    void theReadingIsNotTheSliderEither() {
        assertFalse(onTrack(ROW_LEFT + ROW_WIDTH - 2), "the value text must not move the value");
    }

    @Test
    void theTrackItselfStillTakesTheClick() {
        assertTrue(onTrack(trackLeft()));
        assertTrue(onTrack(trackLeft() + 37));
        assertTrue(onTrack(trackLeft() + 74));
    }

    @Test
    void theEndsHaveALittleSlack() {
        assertTrue(onTrack(trackLeft() - 3));
        assertTrue(onTrack(trackLeft() + 77));
        assertFalse(onTrack(trackLeft() - 12));
    }

    @Test
    void reservingTheResetGutterShiftsTheTrackByExactlyItsWidth() {
        int withoutReset = com.asphyxiamywife.elytrapitchhelper.screen.widget.SliderRow
                .trackLeftForTesting(ROW_LEFT, ROW_WIDTH, false);

        assertEquals(com.asphyxiamywife.elytrapitchhelper.screen.widget.SettingRowPainter.RESET_GUTTER,
                withoutReset - trackLeft());
    }

    private static int trackLeft() {
        return com.asphyxiamywife.elytrapitchhelper.screen.widget.SliderRow.trackLeftForTesting(
                ROW_LEFT, ROW_WIDTH);
    }
}
