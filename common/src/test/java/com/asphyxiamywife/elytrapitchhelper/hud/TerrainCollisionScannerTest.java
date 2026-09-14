package com.asphyxiamywife.elytrapitchhelper.hud;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerrainCollisionScannerTest {
    @Test
    void emptyColumnChecksEveryBlockAndProvesTheWholeRange() {
        AtomicInteger lookups = new AtomicInteger();

        ColumnScan scan = TerrainCollisionScanner.scanColumn((x, y, z) -> {
            lookups.incrementAndGet();
            return false;
        }, 0, 0, 7, 10, 64);

        assertFalse(scan.foundSupport());
        assertEquals(10, scan.provedEmptyThroughY());
        assertEquals(4, scan.lookups());
        assertEquals(4, lookups.get());
    }

    @Test
    void walkStopsAtTheFirstCollisionAboveTheFloor() {
        ColumnScan scan = TerrainCollisionScanner.scanColumn((x, y, z) -> y == 8, 0, 0, 7, 10, 64);

        assertTrue(scan.foundSupport());
        assertEquals(8, scan.supportY());
        assertEquals(2, scan.lookups());
        assertEquals(7, scan.provedEmptyThroughY(),
                "everything the walk passed through on the way up is proven empty too");
    }

    @Test
    void budgetStopsTheWalkAndReportsHowFarItGot() {
        AtomicInteger lookups = new AtomicInteger();

        ColumnScan scan = TerrainCollisionScanner.scanColumn((x, y, z) -> {
            lookups.incrementAndGet();
            return y == 20;
        }, 0, 0, 7, 100, 3);

        assertFalse(scan.foundSupport());
        assertEquals(3, scan.lookups());
        assertEquals(3, lookups.get());
        assertEquals(9, scan.provedEmptyThroughY(), "y 7, 8 and 9 were checked and were empty");
    }

    @Test
    void exhaustedBudgetDoesNoLookupsAndProvesNothingNew() {
        AtomicInteger lookups = new AtomicInteger();

        ColumnScan scan = TerrainCollisionScanner.scanColumn((x, y, z) -> {
            lookups.incrementAndGet();
            return true;
        }, 0, 0, 7, 100, 0);

        assertFalse(scan.foundSupport());
        assertEquals(0, lookups.get());
        assertEquals(6, scan.provedEmptyThroughY());
    }

    @Test
    void invertedRangeDoesNoLookups() {
        AtomicInteger lookups = new AtomicInteger();

        ColumnScan scan = TerrainCollisionScanner.scanColumn((x, y, z) -> {
            lookups.incrementAndGet();
            return true;
        }, 0, 0, 7, 6, 64);

        assertFalse(scan.foundSupport());
        assertEquals(0, lookups.get());
        assertEquals(6, scan.provedEmptyThroughY());
    }
}
