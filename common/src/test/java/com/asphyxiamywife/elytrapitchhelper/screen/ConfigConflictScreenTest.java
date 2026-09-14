package com.asphyxiamywife.elytrapitchhelper.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ConfigConflictScreenTest {
    @Test
    void wheelUpMovesTowardTheTopAndWheelDownMovesTowardTheBottom() {
        assertEquals(70, ConfigConflictScreen.scrollAfterWheel(100, 200, 1.0));
        assertEquals(130, ConfigConflictScreen.scrollAfterWheel(100, 200, -1.0));
    }

    @Test
    void wheelScrollingClampsToTheAvailableRange() {
        assertEquals(0, ConfigConflictScreen.scrollAfterWheel(10, 200, 1.0));
        assertEquals(200, ConfigConflictScreen.scrollAfterWheel(190, 200, -1.0));
    }
}
