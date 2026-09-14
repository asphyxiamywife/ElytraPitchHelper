package com.asphyxiamywife.elytrapitchhelper.screen;

import net.minecraft.client.input.KeyEvent;
import org.junit.jupiter.api.Test;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UndoRedoShortcutsTest {
    @Test
    void primaryZUndoesAndShiftZOrYRedoes() {
        AtomicInteger undo = new AtomicInteger();
        AtomicInteger redo = new AtomicInteger();
        AtomicInteger restored = new AtomicInteger();

        assertTrue(handle(InputConstants.KEY_Z, InputConstants.MOD_CONTROL, undo, redo, restored));
        assertTrue(handle(InputConstants.KEY_Z, InputConstants.MOD_SUPER | InputConstants.MOD_SHIFT,
                undo, redo, restored));
        assertTrue(handle(InputConstants.KEY_Y, InputConstants.MOD_CONTROL, undo, redo, restored));

        org.junit.jupiter.api.Assertions.assertEquals(1, undo.get());
        org.junit.jupiter.api.Assertions.assertEquals(2, redo.get());
        org.junit.jupiter.api.Assertions.assertEquals(3, restored.get());
    }

    @Test
    void unrelatedAndUnmodifiedKeysAreNotConsumed() {
        AtomicInteger calls = new AtomicInteger();
        assertFalse(UndoRedoShortcuts.handle(new KeyEvent(InputConstants.KEY_Z, 0, 0),
                () -> { calls.incrementAndGet(); return true; },
                () -> { calls.incrementAndGet(); return true; }, calls::incrementAndGet));
        assertFalse(UndoRedoShortcuts.handle(new KeyEvent(InputConstants.KEY_F, 0, InputConstants.MOD_CONTROL),
                () -> { calls.incrementAndGet(); return true; },
                () -> { calls.incrementAndGet(); return true; }, calls::incrementAndGet));
        org.junit.jupiter.api.Assertions.assertEquals(0, calls.get());
    }

    @Test
    void handledShortcutOnlyRunsRestoreHookWhenHistoryChanged() {
        AtomicInteger restored = new AtomicInteger();
        assertTrue(UndoRedoShortcuts.handle(new KeyEvent(InputConstants.KEY_Z, 0, InputConstants.MOD_CONTROL),
                () -> false, () -> false, restored::incrementAndGet));
        org.junit.jupiter.api.Assertions.assertEquals(0, restored.get());
    }

    private static boolean handle(int key, int modifiers, AtomicInteger undo, AtomicInteger redo,
            AtomicInteger restored) {
        return UndoRedoShortcuts.handle(new KeyEvent(key, 0, modifiers),
                () -> { undo.incrementAndGet(); return true; },
                () -> { redo.incrementAndGet(); return true; },
                restored::incrementAndGet);
    }
}
