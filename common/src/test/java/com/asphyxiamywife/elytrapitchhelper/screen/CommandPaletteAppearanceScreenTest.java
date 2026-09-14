package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.CommandPaletteAppearanceSettings;
import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.SettingRow;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.SliderRow;
import net.minecraft.client.InputType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.junit.jupiter.api.Test;
import com.mojang.blaze3d.platform.InputConstants;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CommandPaletteAppearanceScreenTest {
    @Test
    void appearanceControlsFollowUndoAndRedoBeforeTheNextEdit() throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        Field instance = Minecraft.class.getDeclaredField("instance");
        instance.setAccessible(true);
        Object previous = instance.get(null);
        try {
            instance.set(null, unsafeClass.getMethod("allocateInstance", Class.class).invoke(unsafe, Minecraft.class));
            Minecraft.getInstance().setLastInputType(InputType.NONE);
            Profile baseline = Config.defaultProfileTemplate();
            AtomicReference<Profile> sharedProfile = new AtomicReference<>(baseline);
            AtomicReference<Profile> redoProfile = new AtomicReference<>();
            ColorEditorScreen.HistoryController history = new ColorEditorScreen.HistoryController() {
                public void begin(String key) {}
                public void end(boolean changed, boolean coalesce) {}
                public boolean undo() {
                    redoProfile.set(sharedProfile.get());
                    sharedProfile.set(baseline);
                    return true;
                }
                public boolean redo() {
                    sharedProfile.set(redoProfile.get());
                    return true;
                }
                public void breakCoalescing() {}
                public boolean acceptExternalRevision() { return false; }
            };
            CommandPaletteAppearanceScreen screen = new CommandPaletteAppearanceScreen(null,
                    sharedProfile::get, sharedProfile::set, () -> true, history);
            screen.width = 600;
            screen.height = 400;
            screen.init();
            for (int i = 0; i < 4; i++) {
                assertFalse(resetModified(screen, i).getAsBoolean(),
                        "Every appearance control has its own reset gutter");
            }
            int oldShadow = baseline.commandPaletteAppearance().shadowOpacity();
            int oldBlur = baseline.commandPaletteAppearance().blurAmount();
            KeyEvent right = new KeyEvent(InputConstants.KEY_RIGHT, 0, 0);
            assertTrue(((SliderRow) screen.children().get(0)).keyPressed(right));
            assertEquals(oldShadow + 1, sharedProfile.get().commandPaletteAppearance().shadowOpacity());

            screen.keyPressed(new KeyEvent(InputConstants.KEY_Z, 0, InputConstants.MOD_CONTROL));
            assertEquals(oldShadow, sharedProfile.get().commandPaletteAppearance().shadowOpacity());
            screen.keyPressed(new KeyEvent(InputConstants.KEY_Y, 0, InputConstants.MOD_CONTROL));
            assertEquals(oldShadow + 1, sharedProfile.get().commandPaletteAppearance().shadowOpacity());
            screen.keyPressed(new KeyEvent(InputConstants.KEY_Z, 0, InputConstants.MOD_CONTROL));

            assertTrue(((SliderRow) screen.children().get(1)).keyPressed(right));
            assertEquals(oldBlur + 1, sharedProfile.get().commandPaletteAppearance().blurAmount());
            assertEquals(oldShadow, sharedProfile.get().commandPaletteAppearance().shadowOpacity(),
                    "Changing blur after undo must not resurrect the undone shadow edit");
            assertTrue(((SliderRow) screen.children().get(0)).keyPressed(right));
            assertEquals(oldShadow + 1, sharedProfile.get().commandPaletteAppearance().shadowOpacity(),
                    "The rebuilt shadow slider must start from the restored value");

            runReset(screen, 0);
            assertEquals(oldShadow, sharedProfile.get().commandPaletteAppearance().shadowOpacity());
            runReset(screen, 1);
            assertEquals(oldBlur, sharedProfile.get().commandPaletteAppearance().blurAmount());

            CommandPaletteAppearanceSettings defaults = baseline.commandPaletteAppearance();
            sharedProfile.set(sharedProfile.get().withCommandPaletteAppearance(
                    sharedProfile.get().commandPaletteAppearance()
                            .withAccentColorRgb(defaults.accentColorRgb() ^ 1)
                            .withBaseColorRgb(defaults.baseColorRgb() ^ 1)));
            runReset(screen, 2);
            assertEquals(defaults.accentColorRgb(),
                    sharedProfile.get().commandPaletteAppearance().accentColorRgb());
            runReset(screen, 3);
            assertEquals(defaults.baseColorRgb(),
                    sharedProfile.get().commandPaletteAppearance().baseColorRgb());
        } finally {
            instance.set(null, previous);
        }
    }

    private static BooleanSupplier resetModified(CommandPaletteAppearanceScreen screen, int row)
            throws Exception {
        Field field = SettingRow.class.getDeclaredField("resetModified");
        field.setAccessible(true);
        return (BooleanSupplier) field.get(screen.children().get(row));
    }

    private static void runReset(CommandPaletteAppearanceScreen screen, int row) throws Exception {
        assertTrue(resetModified(screen, row).getAsBoolean());
        Field field = SettingRow.class.getDeclaredField("onReset");
        field.setAccessible(true);
        ((Runnable) field.get(screen.children().get(row))).run();
    }
}
