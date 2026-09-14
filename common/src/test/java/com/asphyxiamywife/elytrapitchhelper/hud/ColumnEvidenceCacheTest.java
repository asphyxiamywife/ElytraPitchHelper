package com.asphyxiamywife.elytrapitchhelper.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ColumnEvidenceCacheTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final int FLOOR = -64;

    @Test
    void supportAnswersEveryCeilingAtOrAboveTheBlockItWasFoundAt() {
        ColumnEvidenceCache cache = configured();
        cache.record(10, 20, new ColumnScan(-30, -31, 34), 100L);

        assertEquals(GroundSupport.SUPPORTED, cache.answer(10, 20, -30, 100L));
        assertEquals(GroundSupport.SUPPORTED, cache.answer(10, 20, 0, 100L),
                "a ceiling above the one in force when it was found is still supported");
        assertEquals(GroundSupport.SUPPORTED, cache.answer(10, 20, 200, 100L));
    }

    @Test
    void supportSaysNothingAboutCeilingsBelowIt() {
        ColumnEvidenceCache cache = configured();
        cache.record(10, 20, new ColumnScan(-30, -40, 24), 100L);

        assertEquals(GroundSupport.UNSUPPORTED, cache.answer(10, 20, -40, 100L),
                "the walk proved this part empty on its way up to the support");
        assertEquals(GroundSupport.UNKNOWN, cache.answer(10, 20, -35, 100L),
                "nothing was ever looked at between the proven-empty run and the support");
    }

    @Test
    void anEmptyRunAnswersOnlyCeilingsInsideIt() {
        ColumnEvidenceCache cache = configured();
        cache.record(10, 20, new ColumnScan(ColumnScan.NO_SUPPORT, -20, 44), 100L);

        assertEquals(GroundSupport.UNSUPPORTED, cache.answer(10, 20, -20, 100L));
        assertEquals(GroundSupport.UNSUPPORTED, cache.answer(10, 20, -50, 100L),
                "a descending player keeps asking about ranges already inside the run");
        assertEquals(GroundSupport.UNKNOWN, cache.answer(10, 20, -19, 100L),
                "one block above the run was never looked at");
    }

    @Test
    void aPartialRunTellsAResumedScanWhereToStart() {
        ColumnEvidenceCache cache = configured();

        assertEquals(FLOOR, cache.resumeY(10, 20, 100L));

        cache.record(10, 20, new ColumnScan(ColumnScan.NO_SUPPORT, -30, 34), 100L);
        assertEquals(-29, cache.resumeY(10, 20, 100L));

        cache.record(10, 20, new ColumnScan(ColumnScan.NO_SUPPORT, -10, 20), 150L);
        assertEquals(-9, cache.resumeY(10, 20, 150L));
    }

    @Test
    void accumulatedEvidenceExpiresFromWhenItStartedNotFromTheLastAddition() {
        ColumnEvidenceCache cache = configured();
        cache.record(10, 20, new ColumnScan(ColumnScan.NO_SUPPORT, -30, 34), 100L);
        cache.record(10, 20, new ColumnScan(ColumnScan.NO_SUPPORT, -10, 20), 900L);

        assertEquals(GroundSupport.UNSUPPORTED, cache.answer(10, 20, -10, 900L));
        assertEquals(GroundSupport.UNKNOWN,
                cache.answer(10, 20, -10, 100L + ColumnEvidenceCache.RESCAN_INTERVAL_MILLIS),
                "the run is only as fresh as the oldest block in it");
        assertEquals(FLOOR, cache.resumeY(10, 20, 100L + ColumnEvidenceCache.RESCAN_INTERVAL_MILLIS));
    }

    @Test
    void clockGoingBackwardsDiscardsEvidenceRatherThanTrustingIt() {
        ColumnEvidenceCache cache = configured();
        cache.record(10, 20, new ColumnScan(-30, -31, 34), 100L);

        assertEquals(GroundSupport.UNKNOWN, cache.answer(10, 20, 0, 99L));
    }

    @Test
    void forgettingAColumnDropsWhatWasKnownAboutIt() {
        ColumnEvidenceCache cache = configured();
        cache.record(10, 20, new ColumnScan(-30, -31, 34), 100L);
        cache.record(11, 20, new ColumnScan(-30, -31, 34), 100L);

        cache.forget(10, 20);

        assertEquals(GroundSupport.UNKNOWN, cache.answer(10, 20, 0, 100L));
        assertEquals(GroundSupport.SUPPORTED, cache.answer(11, 20, 0, 100L));
    }

    @Test
    void forgettingAChunkDropsOnlyColumnsInsideIt() {
        ColumnEvidenceCache cache = configured();
        cache.record(-1, -1, new ColumnScan(-30, -31, 34), 100L);
        cache.record(15, 15, new ColumnScan(-30, -31, 34), 100L);
        cache.record(16, 15, new ColumnScan(-30, -31, 34), 100L);

        cache.forgetChunk(0, 0);

        assertEquals(GroundSupport.SUPPORTED, cache.answer(-1, -1, 0, 100L));
        assertEquals(GroundSupport.UNKNOWN, cache.answer(15, 15, 0, 100L));
        assertEquals(GroundSupport.SUPPORTED, cache.answer(16, 15, 0, 100L));
    }

    @Test
    void columnsAreKeptApartByBothCoordinates() {
        ColumnEvidenceCache cache = configured();
        cache.record(10, 20, new ColumnScan(-30, -31, 34), 100L);

        assertEquals(GroundSupport.UNKNOWN, cache.answer(20, 10, 0, 100L));
        assertEquals(GroundSupport.UNKNOWN, cache.answer(-10, -20, 0, 100L));
    }

    @Test
    void aChangedFloorOrDimensionDiscardsEverything() {
        ColumnEvidenceCache cache = configured();
        cache.record(10, 20, new ColumnScan(-30, -31, 34), 100L);

        cache.configure(OVERWORLD, FLOOR + 1);
        assertEquals(GroundSupport.UNKNOWN, cache.answer(10, 20, 0, 100L));

        cache.configure(OVERWORLD, FLOOR + 1);
        cache.record(10, 20, new ColumnScan(-30, -31, 34), 100L);
        cache.configure("minecraft:the_end", FLOOR + 1);
        assertEquals(GroundSupport.UNKNOWN, cache.answer(10, 20, 0, 100L));
    }

    @Test
    void evictionForgetsColumnsRatherThanAnsweringForThem() {
        ColumnEvidenceCache cache = configured();
        for (int x = 0; x < 64; x++) {
            cache.record(x, 0, new ColumnScan(-30, -31, 34), 100L);
            assertEquals(GroundSupport.SUPPORTED, cache.answer(x, 0, 0, 100L));
        }

        assertEquals(GroundSupport.UNKNOWN, cache.answer(0, 0, 0, 100L),
                "an evicted column reads as unknown, never as a stale answer");
    }

    private static ColumnEvidenceCache configured() {
        ColumnEvidenceCache cache = new ColumnEvidenceCache();
        cache.configure(OVERWORLD, FLOOR);
        return cache;
    }
}
