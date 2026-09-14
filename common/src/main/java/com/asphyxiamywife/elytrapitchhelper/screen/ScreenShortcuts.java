package com.asphyxiamywife.elytrapitchhelper.screen;

import net.minecraft.client.input.KeyEvent;
import com.mojang.blaze3d.platform.InputConstants;

final class ScreenShortcuts {
    private ScreenShortcuts() {
    }

    static boolean isPrimary(KeyEvent event) {
        return (event.modifiers() & (InputConstants.MOD_CONTROL | InputConstants.MOD_SUPER)) != 0;
    }
}
