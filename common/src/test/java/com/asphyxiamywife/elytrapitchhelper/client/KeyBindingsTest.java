package com.asphyxiamywife.elytrapitchhelper.client;

import com.asphyxiamywife.elytrapitchhelper.ModConstants;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import org.junit.jupiter.api.Test;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class KeyBindingsTest {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category
            .register(ModConstants.id("test_shortcuts"));

    @Test
    void paletteShortcutWorksRegardlessOfWhichBoundKeyIsPressedLast() {
        KeyMapping modifier = mapping("modifier", InputConstants.KEY_LCONTROL);
        KeyMapping palette = mapping("palette", InputConstants.KEY_K);

        assertTrue(KeyBindings.isCommandPaletteShortcut(keyEvent(InputConstants.KEY_K), palette, modifier,
                Set.of(modifier)::contains));
        assertTrue(KeyBindings.isCommandPaletteShortcut(keyEvent(InputConstants.KEY_LCONTROL), palette, modifier,
                Set.of(palette)::contains));
    }

    @Test
    void paletteShortcutRequiresBothBoundKeys() {
        KeyMapping modifier = mapping("modifier-alone", InputConstants.KEY_LCONTROL);
        KeyMapping palette = mapping("palette-alone", InputConstants.KEY_K);

        assertFalse(KeyBindings.isCommandPaletteShortcut(keyEvent(InputConstants.KEY_K), palette, modifier,
                ignored -> false));
        assertFalse(KeyBindings.isCommandPaletteShortcut(keyEvent(InputConstants.KEY_P), palette, modifier,
                ignored -> true));
    }

    @Test
    void paletteShortcutDoesNotOpenWhenPreflightFails() {
        KeyMapping modifier = mapping("modifier-preflight", InputConstants.KEY_LCONTROL);
        KeyMapping palette = mapping("palette-preflight", InputConstants.KEY_K);
        AtomicBoolean opened = new AtomicBoolean();

        assertTrue(KeyBindings.handleCommandPaletteShortcut(keyEvent(InputConstants.KEY_K), palette, modifier,
                Set.of(modifier)::contains, () -> false, () -> opened.set(true)));
        assertFalse(opened.get());
    }

    @Test
    void palettePressSurvivesBothKeysBeingReleasedBeforeTheTick() {
        KeyMapping modifier = mapping("tap-modifier", InputConstants.KEY_LCONTROL);
        KeyMapping palette = mapping("tap-palette", InputConstants.KEY_K);
        KeyBindings bindings = new KeyBindings(null, null, null, modifier, palette);
        modifier.setDown(true);
        bindings.recordPaletteClick(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_K), true);
        modifier.setDown(false);
        palette.setDown(false);
        assertTrue(bindings.consumePaletteClick());
        assertFalse(bindings.consumePaletteClick());
    }

    @Test
    void modifierPressedLastIsCapturedButSeparateTapsAndScreensAreIgnored() {
        KeyMapping modifier = mapping("last-modifier", InputConstants.KEY_LCONTROL);
        KeyMapping palette = mapping("last-palette", InputConstants.KEY_K);
        KeyBindings bindings = new KeyBindings(null, null, null, modifier, palette);
        var modifierKey = InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_LCONTROL);
        var paletteKey = InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_K);
        bindings.recordPaletteClick(paletteKey, true);
        bindings.recordPaletteClick(modifierKey, true);
        assertFalse(bindings.consumePaletteClick());
        palette.setDown(true);
        bindings.recordPaletteClick(modifierKey, false);
        assertFalse(bindings.consumePaletteClick());
        bindings.recordPaletteClick(modifierKey, true);
        palette.setDown(false);
        assertTrue(bindings.consumePaletteClick());
    }

    private static KeyMapping mapping(String name, int key) {
        return new KeyMapping("key.elytrapitchhelper.test." + name, InputConstants.Type.KEYBOARD, key, CATEGORY);
    }

    private static KeyEvent keyEvent(int key) {
        return new KeyEvent(key, 0, 0);
    }
}
