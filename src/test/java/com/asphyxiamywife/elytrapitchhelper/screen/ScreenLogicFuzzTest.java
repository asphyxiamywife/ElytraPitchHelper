package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ScreenLogicFuzzTest {
    private static final double EPSILON = 1.0E-9;

    @FuzzTest(maxDuration = "30s")
    void colorGeometryAndHistoryPreserveTheirContracts(FuzzedDataProvider data) {
        fuzzColorMath(data);
        fuzzGeometry(data);
        fuzzHistory(data);
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
        assertEquals(x, square[0], EPSILON);
        assertEquals(y, square[1], EPSILON);

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
        return new ConfigHistory.Context(data.consumeBoolean(), config.profile(0).fileName,
                categories[data.consumeInt(0, categories.length - 1)], data.consumeString(32),
                data.consumeInt(0, 10_000), data.consumeBoolean(), List.of());
    }

    private static void assertChannelClose(int expected, int actual) {
        assertTrue(Math.abs(expected - actual) <= 1,
                () -> expected + " differs from " + actual + " by more than one channel value");
    }

    private static void assertUnit(double value) {
        assertTrue(Double.isFinite(value) && value >= 0.0 && value <= 1.0,
                () -> value + " is outside [0, 1]");
    }

    private static void assertFiniteUnit(double value) {
        assertTrue(Double.isFinite(value) && value >= -1.0 && value <= 1.0,
                () -> value + " is outside [-1, 1]");
    }
}
