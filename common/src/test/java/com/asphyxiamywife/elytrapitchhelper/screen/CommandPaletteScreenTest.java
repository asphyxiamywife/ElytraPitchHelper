package com.asphyxiamywife.elytrapitchhelper.screen;

import org.junit.jupiter.api.Test;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import com.mojang.blaze3d.platform.InputConstants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CommandPaletteScreenTest {
    @com.asphyxiamywife.elytrapitchhelper.testing.IsolatedConfigRoot
    java.nio.file.Path configRoot;

    @Test
    void paletteInvalidatesCachedWritableStateAfterReadOnlyReload() throws Exception {
        var store = com.asphyxiamywife.elytrapitchhelper.client.ClientConfigStore.store();
        store.initialize();
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        java.lang.reflect.Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        CommandPaletteScreen screen = (CommandPaletteScreen) unsafeClass
                .getMethod("allocateInstance", Class.class)
                .invoke(unsafeField.get(null), CommandPaletteScreen.class);
        var capture = CommandPaletteScreen.class.getDeclaredMethod("paletteState");
        capture.setAccessible(true);
        PaletteState before = (PaletteState) capture.invoke(screen);
        assertFalse(before.config().isReadOnly());
        var json = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(store.configPath()))
                .getAsJsonObject();
        json.addProperty("version", 999);
        java.nio.file.Files.writeString(store.configPath(), json.toString());
        assertTrue(store.reloadFromDiskIfIdle());
        PaletteState after = (PaletteState) capture.invoke(screen);
        assertTrue(after.config().isReadOnly());
    }

    @Test
    void nonLeftClicksDoNotDispatchPaletteActions() throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        java.lang.reflect.Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        CommandPaletteScreen screen = (CommandPaletteScreen) unsafeClass
                .getMethod("allocateInstance", Class.class)
                .invoke(unsafeField.get(null), CommandPaletteScreen.class);
        for (int button : new int[] {InputConstants.MOUSE_BUTTON_RIGHT,
                InputConstants.MOUSE_BUTTON_MIDDLE, InputConstants.MOUSE_BUTTON_4}) {
            assertFalse(screen.mouseClicked(new MouseButtonEvent(100, 100,
                    new MouseButtonInfo(button, 0)), false));
        }
    }

    @Test
    void blurPercentageMapsToMinecraftBlurRadius() {
        assertEquals(0, CommandPaletteScreen.blurRadius(0));
        assertEquals(4, CommandPaletteScreen.blurRadius(40));
        assertEquals(10, CommandPaletteScreen.blurRadius(100));
    }

    @Test
    void blurRadiusClampsInvalidPercentages() {
        assertEquals(0, CommandPaletteScreen.blurRadius(-1));
        assertEquals(10, CommandPaletteScreen.blurRadius(101));
    }

    @Test
    void resultCapacityKeepsThePaletteInsideShortScreens() {
        int screenHeight = 240;
        int rows = CommandPaletteScreen.resultCapacity(screenHeight);
        int panelHeight = CommandPalettePreviewRenderer.panelHeight(rows);
        int panelY = CommandPaletteScreen.fittedPanelY(screenHeight, panelHeight);

        assertEquals(5, rows);
        assertTrue(panelY >= 0);
        assertTrue(panelY + panelHeight <= screenHeight);
    }

    @Test
    void panelWidthNeverExceedsTheScreen() {
        for (int screenWidth = 40; screenWidth <= 800; screenWidth++) {
            int panelWidth = CommandPaletteScreen.fittedPanelWidth(screenWidth);
            int panelX = (screenWidth - panelWidth) / 2;
            assertTrue(panelWidth > 0);
            assertTrue(panelX >= 0);
            assertTrue(panelX + panelWidth <= screenWidth);
        }
    }
}
