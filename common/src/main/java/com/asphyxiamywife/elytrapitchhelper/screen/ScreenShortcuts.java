package com.asphyxiamywife.elytrapitchhelper.screen;

import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

final class ScreenShortcuts {
    private ScreenShortcuts() {
    }

    static boolean isPrimary(KeyEvent event) {
        return (event.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
    }
}
