package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.screen.widget.SettingRowPainter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

final class SettingRowLabelTest {
    @Test
    void paintingAnUnnamedRowIsNotAnError() {
        assertDoesNotThrow(() -> SettingRowPainter.paintLabel(null, null, null, 0, 0, 20, 0,
                SettingRowPainter.LABEL_COLOR, 1.0f));
    }
}
