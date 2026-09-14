package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

import java.util.List;

import static com.asphyxiamywife.elytrapitchhelper.testing.RangeAssertions.assertFiniteUnit;
import static com.asphyxiamywife.elytrapitchhelper.testing.RangeAssertions.assertUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ScreenLogicFuzzTest {
    private static final double EPSILON = 1.0E-9;
    private static final double SQUARE_DISK_EPSILON = 2.0E-8;

    @FuzzTest(maxDuration = "30s")
    void colorGeometryAndHistoryPreserveTheirContracts(FuzzedDataProvider data) {
        fuzzColorMath(data);
        fuzzGeometry(data);
        fuzzEditorLayout(data);
        fuzzHistory(data);
    }

    private static void fuzzEditorLayout(FuzzedDataProvider data) {
        int screenWidth = data.consumeInt(1, 4000);
        int screenHeight = data.consumeInt(1, 4000);
        int rowCount = data.consumeInt(-5, 200);
        boolean readOnly = data.consumeBoolean();
        int scroll = data.consumeInt(-100, 500);

        ProfileEditorLayout layout = ProfileEditorLayout.of(screenWidth, screenHeight, rowCount,
                readOnly, scroll);

        assertTrue(layout.totalRows() >= 0);
        assertTrue(layout.visibleRows() >= 0 && layout.visibleRows() <= layout.totalRows());
        assertTrue(layout.maxScroll() >= 0);
        assertEquals(layout.totalRows() * ConfigScreen.ROW_HEIGHT,
                layout.visibleRows() * ConfigScreen.ROW_HEIGHT + layout.maxScroll());
        assertTrue(layout.scroll() >= 0 && layout.scroll() <= layout.maxScroll());
        assertEquals(layout.maxScroll() > 0, layout.needsScrollbar());
        assertTrue(layout.scrollbarX() >= layout.startX());
        assertTrue(layout.rowWidth() <= layout.contentWidth());
        assertTrue(layout.rowWidth() <= ProfileEditorLayout.MAX_ROW_WIDTH);

        for (int index = 0; index < Math.max(0, rowCount); index++) {
            if (!layout.drawsRow(layout.rowY(index))) {
                continue;
            }
            int y = layout.rowY(index);
            assertTrue(y + ConfigScreen.CONTROL_HEIGHT > layout.startY());
            assertTrue(y < layout.viewportBottom());
            assertTrue(layout.visibleFraction(y) > 0.0);
            for (int depth = 0; depth <= 2; depth++) {
                assertTrue(layout.rowWidth(depth) >= 24);
                assertTrue(layout.rowX(depth) + layout.rowWidth(depth)
                        <= layout.rowX(0) + layout.rowWidth(0));
            }
        }
    }

    private static void fuzzColorMath(FuzzedDataProvider data) {
        int red = data.consumeInt(0, 255);
        int green = data.consumeInt(0, 255);
        int blue = data.consumeInt(0, 255);
        int rgb = (red << 16) | (green << 8) | blue;

        double[] hsv = ColorMath.rgbToHsv(red, green, blue);
        assertUnit(hsv[0]);
        assertUnit(hsv[1]);
        assertUnit(hsv[2]);
        int roundTripped = ColorMath.hsvToRgb(hsv[0], hsv[1], hsv[2]);
        assertChannelClose(red, (roundTripped >> 16) & 0xFF);
        assertChannelClose(green, (roundTripped >> 8) & 0xFF);
        assertChannelClose(blue, roundTripped & 0xFF);

        String validHex = Integer.toHexString(rgb | 0x01000000).substring(1);
        assertEquals(rgb, ColorMath.parseHexColor(validHex));
        int parsed = ColorMath.parseHexColor(data.consumeString(16));
        assertTrue(parsed == -1 || parsed >= 0 && parsed <= 0x00FFFFFF);

        double x = data.consumeRegularDouble(-1.0, 1.0);
        double y = data.consumeRegularDouble(-1.0, 1.0);
        double[] disk = ColorMath.squareToDisk(x, y);
        assertFiniteUnit(disk[0]);
        assertFiniteUnit(disk[1]);
        assertTrue(Math.hypot(disk[0], disk[1]) <= 1.0 + EPSILON);
        double[] square = ColorMath.diskToSquare(disk[0], disk[1]);
        assertEquals(x, square[0], SQUARE_DISK_EPSILON);
        assertEquals(y, square[1], SQUARE_DISK_EPSILON);

        double hue = ColorMath.hueFromVector(x, y);
        assertTrue(Double.isFinite(hue) && hue >= 0.0 && hue < 1.0);
    }

    private static void fuzzGeometry(FuzzedDataProvider data) {
        int x = data.consumeInt(-10_000, 10_000);
        int y = data.consumeInt(-10_000, 10_000);
        int width = data.consumeInt(0, 10_000);
        int height = data.consumeInt(0, 10_000);
        int scrollerY = data.consumeInt(-10_000, 20_000);
        int scrollerHeight = data.consumeInt(0, 10_000);
        ProfileScrollGeometry geometry = new ProfileScrollGeometry(x, y, width, height,
                scrollerY, scrollerHeight, data.consumeInt(0, 100_000), data.consumeInt(0, 10_000));
        double mouseX = data.consumeRegularDouble(-20_000.0, 20_000.0);
        double mouseY = data.consumeRegularDouble(-20_000.0, 20_000.0);

        assertEquals(mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY < y + height,
                geometry.contains(mouseX, mouseY));
        assertEquals(mouseY >= scrollerY && mouseY < scrollerY + scrollerHeight,
                geometry.containsScroller(mouseY));
    }

    private static void fuzzHistory(FuzzedDataProvider data) {
        ConfigHistory history = new ConfigHistory();
        Config current = new Config();
        current.selectProfile(0);
        long nowMillis = 0L;
        int operations = data.consumeInt(1, 24);
        for (int i = 0; i < operations; i++) {
            Config before = current.copy();
            Config after = before.copy();
            after.enabled = !before.enabled;
            after.profileSortMode = data.consumeInt(Config.PROFILE_SORT_CREATED, Config.PROFILE_SORT_MODIFIED);
            ConfigHistory.Context context = context(data, current);
            nowMillis += data.consumeLong(0L, 1_500L);
            String key = data.consumeString(24);
            if (data.consumeBoolean()) {
                history.begin(key, before, context);
                history.commit(after, context, true, data.consumeBoolean(), nowMillis);
            } else {
                history.record(key, before, context, after, context, data.consumeBoolean(), nowMillis);
            }
            current = after;
            if (data.consumeBoolean()) {
                history.breakCoalescing();
            }
        }

        for (int i = 0; i < operations; i++) {
            ConfigHistory.Restore restore = data.consumeBoolean() ? history.undo() : history.redo();
            if (restore != null) {
                assertNotNull(restore.config());
                assertNotNull(restore.context());
                assertTrue(restore.config().profileCount() > 0);
            }
        }
        history.clear();
        assertEquals(null, history.undo());
        assertEquals(null, history.redo());
    }

    private static ConfigHistory.Context context(FuzzedDataProvider data, Config config) {
        ConfigCategory[] categories = ConfigCategory.values();
        return new ConfigHistory.Context(data.consumeBoolean(), config.profile(0).fileName(),
                categories[data.consumeInt(0, categories.length - 1)], data.consumeString(32),
                data.consumeInt(0, 10_000), data.consumeInt(0, 10_000), data.consumeBoolean(), null,
                List.of(), data.consumeString(32));
    }

    private static void assertChannelClose(int expected, int actual) {
        assertTrue(Math.abs(expected - actual) <= 1,
                () -> expected + " differs from " + actual + " by more than one channel value");
    }
}
