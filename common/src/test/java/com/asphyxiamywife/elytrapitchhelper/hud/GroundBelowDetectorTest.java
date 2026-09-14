package com.asphyxiamywife.elytrapitchhelper.hud;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GroundBelowDetectorTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final int OVERHANG_Y = 500;


    @Test
    void losingCachedSupportRechecksTheOldEmptyRunForReplacementGround() {
        AtomicBoolean changed = new AtomicBoolean(false);
        FakeColumnProbe probe = underAnOverhang((x, y, z) -> y == (changed.get() ? 2 : 4));
        GroundBelowDetector detector = new GroundBelowDetector();
        assertEquals(GroundSupport.SUPPORTED, detect(detector, probe, 0, 9, 0, 0, 0, 0, 100L));
        changed.set(true);
        assertEquals(GroundSupport.SUPPORTED, detect(detector, probe, 0, 9, 0, 0, 0, 0, 101L));
        assertEquals(GroundSupport.SUPPORTED, detect(detector, probe, 0, 2, 0, 0, 0, 0, 102L));
    }

    @Test
    void removedSupportUnderAnOverhangIsDetectedWithoutAnInvalidationEvent() {
        AtomicBoolean present = new AtomicBoolean(true);
        FakeColumnProbe probe = underAnOverhang((x, y, z) -> present.get() && y == 4);
        GroundBelowDetector detector = new GroundBelowDetector();
        assertEquals(GroundSupport.SUPPORTED, detect(detector, probe, 0, 9, 0, 0, 0, 0, 100L));
        present.set(false);
        assertEquals(GroundSupport.UNSUPPORTED, detect(detector, probe, 0, 9, 0, 0, 0, 0, 101L));
    }

    @Test
    void removedSupportDoesNotSuppressWarningWhileRescanIsIncomplete() {
        AtomicBoolean present = new AtomicBoolean(true);
        FakeColumnProbe probe = underAnOverhang((x, y, z) -> present.get() && y == 1);
        GroundBelowDetector detector = new GroundBelowDetector(2);
        assertEquals(GroundSupport.SUPPORTED, detect(detector, probe, 0, 9, 0, 0, 0, 0, 100L));
        present.set(false);
        assertEquals(GroundSupport.UNKNOWN, detect(detector, probe, 0, 9, 0, 0, 0, 0, 101L));
        GroundSupport result = GroundSupport.UNKNOWN;
        for (long tick = 102; tick < 110 && result == GroundSupport.UNKNOWN; tick++) {
            result = detect(detector, probe, 0, 9, 0, 0, 0, 0, tick);
        }
        assertEquals(GroundSupport.UNSUPPORTED, result);
    }

    @Test
    void aColumnOfAirIsSettledWithoutLookingAtASingleBlock() {
        FakeColumnProbe probe = new FakeColumnProbe(FakeColumnProbe.EMPTY);
        GroundBelowDetector detector = new GroundBelowDetector();

        assertEquals(GroundSupport.UNSUPPORTED, detect(detector, probe, 0, 999, 0, 1, 0, 1, 100L));
        assertEquals(0, probe.lookups(), "empty columns should not require block lookups");
        assertEquals(4, probe.takeSurfaceReads());
    }

    @Test
    void groundAtTheSurfaceIsSettledWithOneBlockLookup() {
        FakeColumnProbe probe = new FakeColumnProbe((x, y, z) -> y <= 60);
        GroundBelowDetector detector = new GroundBelowDetector();

        assertEquals(GroundSupport.SUPPORTED, detect(detector, probe, -64, 300, 0, 0, 0, 0, 100L));
        assertEquals(1, probe.takeLookups());
    }

    @Test
    void theHeightmapIsReReadEveryTickRatherThanRemembered() {
        FakeColumnProbe probe = new FakeColumnProbe((x, y, z) -> y <= 60);
        GroundBelowDetector detector = new GroundBelowDetector();
        assertEquals(GroundSupport.SUPPORTED, detect(detector, probe, -64, 300, 0, 0, 0, 0, 100L));
        probe.takeLookups();

        assertEquals(GroundSupport.SUPPORTED, detect(detector, probe, -64, 300, 0, 0, 0, 0, 101L));
        assertEquals(1, probe.takeLookups(),
                "a suppressed warning must not outlive the floor it was suppressed by");
    }

    @Test
    void aBlockUpdateInvalidatesSupportCachedBelowANonCollidingSurface() {
        AtomicBoolean supportPresent = new AtomicBoolean(true);
        FakeColumnProbe probe = new FakeColumnProbe(
                (x, y, z) -> supportPresent.get() && y == 40)
                .withNonCollidingAt((x, y, z) -> y == 62);
        GroundBelowDetector detector = new GroundBelowDetector();
        assertEquals(GroundSupport.SUPPORTED, detect(detector, probe, 0, 300, 0, 0, 0, 0, 100L));

        supportPresent.set(false);
        detector.invalidateColumn(0, 0);

        assertEquals(GroundSupport.UNSUPPORTED, detect(detector, probe, 0, 300, 0, 0, 0, 0, 101L),
                "the removed floor must stop suppressing the warning immediately");
    }

    @Test
    void aSurfaceThatCannotBeLandedOnFallsBackToTheWalk() {
        FakeColumnProbe probe = new FakeColumnProbe((x, y, z) -> y <= 40)
                .withNonCollidingAt((x, y, z) -> y <= 62);
        GroundBelowDetector detector = new GroundBelowDetector();

        assertEquals(GroundSupport.SUPPORTED, detect(detector, probe, 0, 300, 0, 0, 0, 0, 100L));
        assertTrue(probe.lookups() > 1, "the water at the top does not answer the question");
    }

    @Test
    void theWalkStopsBelowTheSurfaceRatherThanClimbingToThePlayer() {
        FakeColumnProbe probe = new FakeColumnProbe(FakeColumnProbe.EMPTY)
                .withNonCollidingAt((x, y, z) -> y <= 62);
        GroundBelowDetector detector = new GroundBelowDetector();

        assertEquals(GroundSupport.UNSUPPORTED, detect(detector, probe, 0, 300, 0, 0, 0, 0, 100L));
        assertEquals(63, probe.takeLookups(),
                "y 0 to 61 walked, plus the surface block itself; the air up to 300 is free");
    }

    @Test
    void theRangeAboveTheSurfaceCountsAsProvenSoRaisingTheCeilingDoesNotRewalk() {
        FakeColumnProbe probe = new FakeColumnProbe(FakeColumnProbe.EMPTY)
                .withNonCollidingAt((x, y, z) -> y <= 62);
        GroundBelowDetector detector = new GroundBelowDetector();
        assertEquals(GroundSupport.UNSUPPORTED, detect(detector, probe, 0, 100, 0, 0, 0, 0, 100L));
        probe.takeLookups();

        assertEquals(GroundSupport.UNSUPPORTED, detect(detector, probe, 0, 300, 0, 0, 0, 0, 101L));
        assertEquals(1, probe.takeLookups(),
                "the surface block is re-checked because it is never cached, but nothing is re-walked");
    }


    @Test
    void groundUnderOneColumnSupportsTheWholeFootprint() {
        FakeColumnProbe probe = new FakeColumnProbe((x, y, z) -> x == 1 && z == 1 && y == 4);
        GroundBelowDetector detector = new GroundBelowDetector();

        assertEquals(GroundSupport.SUPPORTED, detect(detector, probe, 0, 9, 0, 1, 0, 1, 100L));
    }

    @Test
    void anUnloadedColumnLeavesTheAnswerUnknownRatherThanUnsupported() {
        FakeColumnProbe probe = new FakeColumnProbe(FakeColumnProbe.EMPTY, (x, z) -> x != 1);
        GroundBelowDetector detector = new GroundBelowDetector();

        assertEquals(GroundSupport.UNKNOWN, detect(detector, probe, 0, 9, 0, 1, 0, 1, 100L));
    }

    @Test
    void loadedGroundStillWinsOverAnUnloadedNeighbour() {
        FakeColumnProbe probe = new FakeColumnProbe(
                (x, y, z) -> x == 0 && z == 0 && y == 4, (x, z) -> x != 1);
        GroundBelowDetector detector = new GroundBelowDetector();

        assertEquals(GroundSupport.SUPPORTED, detect(detector, probe, 0, 9, 0, 1, 0, 1, 100L));
    }

    @Test
    void aColumnSettledOffTheHeightmapSparesItsNeighbourTheWalk() {
        FakeColumnProbe probe = new FakeColumnProbe((x, y, z) -> x == 1 && y == 4)
                .withNonCollidingAt((x, y, z) -> x == 0 && y == OVERHANG_Y);
        GroundBelowDetector detector = new GroundBelowDetector();

        assertEquals(GroundSupport.SUPPORTED, detect(detector, probe, 0, 9, 0, 1, 0, 0, 100L));
        assertEquals(1, probe.takeLookups(),
                "the column with ground answers before the column that needs walking is walked");
    }


    @Test
    void descendingReusesTheEmptyRunWithoutLookingAtAnythingAgain() {
        FakeColumnProbe probe = underAnOverhang(FakeColumnProbe.EMPTY);
        GroundBelowDetector detector = new GroundBelowDetector();

        assertEquals(GroundSupport.UNSUPPORTED, detect(detector, probe, 0, 9, 0, 0, 0, 0, 100L));
        assertEquals(10, probe.takeLookups());

        assertEquals(GroundSupport.UNSUPPORTED, detect(detector, probe, 0, 5, 0, 0, 0, 0, 101L));
        assertEquals(0, probe.takeLookups(), "the lower range is inside the run already proven empty");
    }

    @Test
    void climbingScansOnlyTheBlocksAboveWhatWasAlreadyProven() {
        FakeColumnProbe probe = underAnOverhang(FakeColumnProbe.EMPTY);
        GroundBelowDetector detector = new GroundBelowDetector();

        assertEquals(GroundSupport.UNSUPPORTED, detect(detector, probe, 0, 5, 0, 0, 0, 0, 100L));
        assertEquals(6, probe.takeLookups());

        assertEquals(GroundSupport.UNSUPPORTED, detect(detector, probe, 0, 9, 0, 0, 0, 0, 101L));
        assertEquals(4, probe.takeLookups(), "only y 6 through 9 were still unproven");
    }

    @Test
    void supportFoundByAWalkKeepsAnsweringAsThePlayerDescendsTowardIt() {
        FakeColumnProbe probe = underAnOverhang((x, y, z) -> y == 4);
        GroundBelowDetector detector = new GroundBelowDetector();

        assertEquals(GroundSupport.SUPPORTED, detect(detector, probe, 0, 9, 0, 0, 0, 0, 100L));
        probe.takeLookups();

        assertEquals(GroundSupport.SUPPORTED, detect(detector, probe, 0, 4, 0, 0, 0, 0, 101L));
        assertEquals(1, probe.takeLookups(), "cached support is verified with one block lookup");

        assertEquals(GroundSupport.UNSUPPORTED, detect(detector, probe, 0, 3, 0, 0, 0, 0, 102L),
                "below the support the walk falls back on the run it proved on the way up");
        assertEquals(0, probe.takeLookups());
    }

    @Test
    void theFootprintSlidingKeepsWhatTheOverlappingColumnsProved() {
        FakeColumnProbe probe = underAnOverhang(FakeColumnProbe.EMPTY);
        GroundBelowDetector detector = new GroundBelowDetector();

        assertEquals(GroundSupport.UNSUPPORTED, detect(detector, probe, 0, 9, 0, 1, 0, 0, 100L));
        assertEquals(20, probe.takeLookups());

        assertEquals(GroundSupport.UNSUPPORTED, detect(detector, probe, 0, 9, 1, 2, 0, 0, 101L));
        assertEquals(10, probe.takeLookups(), "only the column that just entered the footprint is new");
    }

    @Test
    void aChunkGoingAwayDiscardsWhatWasKnownAboutItsColumn() {
        FakeColumnProbe probe = underAnOverhang((x, y, z) -> y == 4);
        GroundBelowDetector detector = new GroundBelowDetector();
        assertEquals(GroundSupport.SUPPORTED, detect(detector, probe, 0, 9, 0, 0, 0, 0, 100L));

        probe.setLoaded((x, z) -> false);
        assertEquals(GroundSupport.UNKNOWN, detect(detector, probe, 0, 9, 0, 0, 0, 0, 101L),
                "support proven while the chunk was loaded must not answer for it once it is gone");

        probe.setLoaded(FakeColumnProbe.ALL_LOADED);
        probe.takeLookups();
        assertEquals(GroundSupport.SUPPORTED, detect(detector, probe, 0, 9, 0, 0, 0, 0, 102L));
        assertNotEquals(0, probe.lookups(), "the reloaded column is looked at again, not remembered");
    }


    @Test
    void aTinyBudgetMakesProgressEveryTickInsteadOfRestarting() {
        FakeColumnProbe probe = underAnOverhang(FakeColumnProbe.EMPTY);
        GroundBelowDetector detector = new GroundBelowDetector(3);

        GroundSupport result = GroundSupport.UNKNOWN;
        int ticks = 0;
        while (result == GroundSupport.UNKNOWN && ticks < 10) {
            result = detect(detector, probe, 0, 9, 0, 0, 0, 0, 100L + ticks);
            assertTrue(probe.takeLookups() > 0, "every tick spends its budget moving the run upward");
            ticks++;
        }

        assertEquals(GroundSupport.UNSUPPORTED, result);
        assertEquals(4, ticks, "ten blocks at three lookups a tick");
    }

    @Test
    void anExhaustedBudgetNeverClaimsTheFootprintIsEmpty() {
        FakeColumnProbe probe = underAnOverhang((x, y, z) -> y == 9);
        GroundBelowDetector detector = new GroundBelowDetector(3);

        assertEquals(GroundSupport.UNKNOWN, detect(detector, probe, 0, 9, 0, 1, 0, 1, 100L));
        assertEquals(1, probe.scanCalls(), "columns after the exhausted budget are not dispatched");
    }


    @Test
    void aCeilingBelowTheFloorAsksTheWorldNothing() {
        FakeColumnProbe probe = new FakeColumnProbe((x, y, z) -> true);
        GroundBelowDetector detector = new GroundBelowDetector();

        assertEquals(GroundSupport.UNSUPPORTED, detect(detector, probe, 0, -1, 0, 0, 0, 0, 100L));
        assertEquals(0, probe.lookups());
        assertEquals(0, probe.takeSurfaceReads());
    }

    @Test
    void aNonCollidingBlockSittingOnTheFloorIsNotGround() {
        FakeColumnProbe probe = new FakeColumnProbe(FakeColumnProbe.EMPTY)
                .withNonCollidingAt((x, y, z) -> y == 0);
        GroundBelowDetector detector = new GroundBelowDetector();

        assertEquals(GroundSupport.UNSUPPORTED, detect(detector, probe, 0, 9, 0, 0, 0, 0, 100L));
        assertEquals(1, probe.takeLookups());
    }

    @Test
    void resetForgetsTheDimensionItWasFlyingOver() {
        FakeColumnProbe probe = underAnOverhang(FakeColumnProbe.EMPTY);
        GroundBelowDetector detector = new GroundBelowDetector();
        detect(detector, probe, 0, 9, 0, 0, 0, 0, 100L);
        probe.takeLookups();

        detector.reset();

        assertEquals(GroundSupport.UNSUPPORTED, detect(detector, probe, 0, 9, 0, 0, 0, 0, 101L));
        assertEquals(10, probe.takeLookups());
    }

    private static FakeColumnProbe underAnOverhang(FakeColumnProbe.Blocks colliding) {
        return new FakeColumnProbe(colliding).withNonCollidingAt((x, y, z) -> y == OVERHANG_Y);
    }

    private static GroundSupport detect(GroundBelowDetector detector, ColumnProbe probe,
            int floorY, int ceilingY, int minX, int maxX, int minZ, int maxZ, long nowMillis) {
        return detector.detect(probe, OVERWORLD, floorY, ceilingY, minX, maxX, minZ, maxZ, nowMillis);
    }
}
