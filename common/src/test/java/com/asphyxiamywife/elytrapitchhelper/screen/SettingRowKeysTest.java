package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.screen.widget.SettingRow;
import org.junit.jupiter.api.Test;
import com.mojang.blaze3d.platform.InputConstants;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SettingRowKeysTest {
    @Test
    void enterAndSpaceActivateARow() {
        assertTrue(SettingRow.isActivationKey(InputConstants.KEY_RETURN));
        assertTrue(SettingRow.isActivationKey(InputConstants.KEY_NUMPADENTER));
        assertTrue(SettingRow.isActivationKey(InputConstants.KEY_SPACE));
    }

    @Test
    void arrowsAndEscapeAreLeftAlone() {
        for (int key : new int[] {InputConstants.KEY_LEFT, InputConstants.KEY_RIGHT, InputConstants.KEY_UP,
                InputConstants.KEY_DOWN, InputConstants.KEY_ESCAPE, InputConstants.KEY_TAB}) {
            assertFalse(SettingRow.isActivationKey(key), () -> "key " + key + " should pass through");
        }
    }

    @Test
    void deleteAndBackspaceAreResetKeysOnly() {
        assertTrue(SettingRow.isResetKey(InputConstants.KEY_DELETE));
        assertTrue(SettingRow.isResetKey(InputConstants.KEY_BACKSPACE));
        assertFalse(SettingRow.isResetKey(InputConstants.KEY_RETURN));
        assertFalse(SettingRow.isResetKey(InputConstants.KEY_LEFT));
    }
}
