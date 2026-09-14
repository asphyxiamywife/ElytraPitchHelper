package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.screen.widget.SettingRow;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SettingRowKeysTest {
    @Test
    void enterAndSpaceActivateARow() {
        assertTrue(SettingRow.isActivationKey(GLFW.GLFW_KEY_ENTER));
        assertTrue(SettingRow.isActivationKey(GLFW.GLFW_KEY_KP_ENTER));
        assertTrue(SettingRow.isActivationKey(GLFW.GLFW_KEY_SPACE));
    }

    @Test
    void arrowsAndEscapeAreLeftAlone() {
        for (int key : new int[] {GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP,
                GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_TAB}) {
            assertFalse(SettingRow.isActivationKey(key), () -> "key " + key + " should pass through");
        }
    }

    @Test
    void deleteAndBackspaceAreResetKeysOnly() {
        assertTrue(SettingRow.isResetKey(GLFW.GLFW_KEY_DELETE));
        assertTrue(SettingRow.isResetKey(GLFW.GLFW_KEY_BACKSPACE));
        assertFalse(SettingRow.isResetKey(GLFW.GLFW_KEY_ENTER));
        assertFalse(SettingRow.isResetKey(GLFW.GLFW_KEY_LEFT));
    }
}
