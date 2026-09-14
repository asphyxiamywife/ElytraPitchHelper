package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.screen.widget.SliderRow;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SliderRowKeysTest {
    @Test
    void disabledFocusedSliderIgnoresBothArrowsAndResumesWhenEnabled() {
        AtomicInteger value = new AtomicInteger(50);
        AtomicInteger interactions = new AtomicInteger();
        SliderRow row = new SliderRow(Component.literal("test"), null, 0, 50, 0, 100, 1,
                Double::toString, next -> value.set((int) next), "test",
                new SliderRow.InteractionListener() {
                    public void begin(String key) { interactions.incrementAndGet(); }
                    public void end(String key, boolean changed, boolean coalesce) {
                        interactions.incrementAndGet();
                    }
                });
        row.setFocused(true);
        row.active = false;
        assertFalse(row.keyPressed(new KeyEvent(InputConstants.KEY_RIGHT, 0, 0)));
        assertFalse(row.keyPressed(new KeyEvent(InputConstants.KEY_LEFT, 0, 0)));
        assertEquals(50, value.get());
        assertEquals(0, interactions.get());

        row.active = true;
        assertTrue(row.keyPressed(new KeyEvent(InputConstants.KEY_RIGHT, 0, 0)));
        assertEquals(51, value.get());
        assertEquals(2, interactions.get());
    }
}
