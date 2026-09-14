package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.screen.widget.SettingRow;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SettingRowResetTest {
    private static final int ROW_X = 100;
    private static final int ROW_WIDTH = 110;

    @Test
    void resetZoneOccupiesOnlyTheReservedRightEdge() {
        assertFalse(SettingRow.resetZoneContains(ROW_X, ROW_WIDTH, ROW_X));
        assertFalse(SettingRow.resetZoneContains(ROW_X, ROW_WIDTH, ROW_X + ROW_WIDTH - 13));
        assertTrue(SettingRow.resetZoneContains(ROW_X, ROW_WIDTH, ROW_X + ROW_WIDTH - 12));
        assertTrue(SettingRow.resetZoneContains(ROW_X, ROW_WIDTH, ROW_X + ROW_WIDTH - 1));
        assertFalse(SettingRow.resetZoneContains(ROW_X, ROW_WIDTH, ROW_X + ROW_WIDTH));
    }

    @Test
    void gutterClickResetsWithoutActivatingTheRow() {
        AtomicInteger resets = new AtomicInteger();
        TestRow row = row(true, resets);

        row.onClick(click(ROW_X + ROW_WIDTH - 1, GLFW.GLFW_MOUSE_BUTTON_LEFT), false);

        assertEquals(1, resets.get());
        assertEquals(0, row.contentClicks);
    }

    @Test
    void contentClickActivatesWithoutResetting() {
        AtomicInteger resets = new AtomicInteger();
        TestRow row = row(true, resets);

        row.onClick(click(ROW_X + 20, GLFW.GLFW_MOUSE_BUTTON_LEFT), false);

        assertEquals(0, resets.get());
        assertEquals(1, row.contentClicks);
    }

    @Test
    void unmodifiedAndRightClickGutterEventsDoNotFallThrough() {
        AtomicInteger resets = new AtomicInteger();
        TestRow unmodified = row(false, resets);
        TestRow modified = row(true, resets);

        unmodified.onClick(click(ROW_X + ROW_WIDTH - 1, GLFW.GLFW_MOUSE_BUTTON_LEFT), false);
        modified.onClick(click(ROW_X + ROW_WIDTH - 1, GLFW.GLFW_MOUSE_BUTTON_RIGHT), false);

        assertEquals(0, resets.get());
        assertEquals(0, unmodified.contentClicks);
        assertEquals(0, modified.contentClicks);
    }

    @Test
    void deleteAndBackspaceResetOnlyWhenModified() {
        AtomicBoolean modified = new AtomicBoolean(true);
        AtomicInteger resets = new AtomicInteger();
        TestRow row = new TestRow();
        row.setReset(modified::get, resets::incrementAndGet);

        assertTrue(row.keyPressed(new KeyEvent(GLFW.GLFW_KEY_DELETE, 0, 0)));
        modified.set(false);
        assertFalse(row.keyPressed(new KeyEvent(GLFW.GLFW_KEY_BACKSPACE, 0, 0)));
        assertEquals(1, resets.get());
    }

    private static TestRow row(boolean modified, AtomicInteger resets) {
        TestRow row = new TestRow();
        row.setPosition(ROW_X, 0);
        row.setSize(ROW_WIDTH, 20);
        row.setReset(() -> modified, resets::incrementAndGet);
        return row;
    }

    private static MouseButtonEvent click(double x, int button) {
        return new MouseButtonEvent(x, 10, new MouseButtonInfo(button, 0));
    }

    private static final class TestRow extends SettingRow {
        private int contentClicks;

        private TestRow() {
            super(Component.literal("Test"), (Font) null, 0);
        }

        @Override
        protected void renderControl(GuiGraphics context, int mouseX, int mouseY, float delta) {
        }

        @Override
        protected Component narratedValue() {
            return Component.empty();
        }

        @Override
        protected void onRowClick(MouseButtonEvent event, boolean doubleClick) {
            contentClicks++;
        }

        @Override
        protected void playClick() {
        }
    }
}
