package com.asphyxiamywife.elytrapitchhelper.hud;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerrainScanCacheTest {
    @Test
    void reusesResultWithinOneSecondWhileHorizontalPositionIsUnchanged() {
        TerrainScanCache cache = new TerrainScanCache();
        AtomicInteger scans = new AtomicInteger();

        assertTrue(cache.collisionBelow("minecraft:overworld", 10, -4, 0, 100L, () -> {
            scans.incrementAndGet();
            return true;
        }));
        assertTrue(cache.collisionBelow("minecraft:overworld", 10, -4, 0,
                100L + TerrainScanCache.RESCAN_INTERVAL_MILLIS - 1L, () -> {
                    scans.incrementAndGet();
                    return false;
                }));

        assertEquals(1, scans.get());
    }

    @Test
    void rescansAfterOneSecondAtTheSamePosition() {
        TerrainScanCache cache = new TerrainScanCache();
        AtomicInteger scans = new AtomicInteger();

        assertTrue(cache.collisionBelow("minecraft:overworld", 10, -4, 0, 100L, () -> {
            scans.incrementAndGet();
            return true;
        }));
        assertFalse(cache.collisionBelow("minecraft:overworld", 10, -4, 0,
                100L + TerrainScanCache.RESCAN_INTERVAL_MILLIS, () -> {
                    scans.incrementAndGet();
                    return false;
                }));

        assertEquals(2, scans.get());
    }

    @Test
    void positionDimensionAndVoidFloorChangesInvalidateImmediately() {
        TerrainScanCache cache = new TerrainScanCache();
        AtomicInteger scans = new AtomicInteger();

        cache.collisionBelow("minecraft:overworld", 10, -4, 0, 100L, () -> scan(scans));
        cache.collisionBelow("minecraft:overworld", 11, -4, 0, 101L, () -> scan(scans));
        cache.collisionBelow("minecraft:overworld", 11, -5, 0, 102L, () -> scan(scans));
        cache.collisionBelow("minecraft:the_nether", 11, -5, 0, 103L, () -> scan(scans));
        cache.collisionBelow("minecraft:the_nether", 11, -5, -64, 104L, () -> scan(scans));

        assertEquals(5, scans.get());
    }

    private static boolean scan(AtomicInteger scans) {
        scans.incrementAndGet();
        return false;
    }
}
