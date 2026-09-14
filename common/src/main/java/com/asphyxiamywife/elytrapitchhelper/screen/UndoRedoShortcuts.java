package com.asphyxiamywife.elytrapitchhelper.screen;

import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import java.util.function.BooleanSupplier;

final class UndoRedoShortcuts {
    private UndoRedoShortcuts() {
    }

    static boolean handle(KeyEvent event, BooleanSupplier undo, BooleanSupplier redo, Runnable afterRestore) {
        if (!ScreenShortcuts.isPrimary(event)) {
            return false;
        }
        BooleanSupplier action;
        if (event.key() == GLFW.GLFW_KEY_Z) {
            action = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 ? redo : undo;
        } else if (event.key() == GLFW.GLFW_KEY_Y) {
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
