package com.asphyxiamywife.elytrapitchhelper.screen;

import net.minecraft.client.input.KeyEvent;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.function.BooleanSupplier;

final class UndoRedoShortcuts {
    private UndoRedoShortcuts() {
    }

    static boolean handle(KeyEvent event, BooleanSupplier undo, BooleanSupplier redo, Runnable afterRestore) {
        if (!ScreenShortcuts.isPrimary(event)) {
            return false;
        }
        BooleanSupplier action;
        if (event.key() == InputConstants.KEY_Z) {
            action = (event.modifiers() & InputConstants.MOD_SHIFT) != 0 ? redo : undo;
        } else if (event.key() == InputConstants.KEY_Y) {
            action = redo;
        } else {
            return false;
        }
        if (action.getAsBoolean()) {
            afterRestore.run();
        }
        return true;
    }
}
