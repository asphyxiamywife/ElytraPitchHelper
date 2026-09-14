package com.asphyxiamywife.elytrapitchhelper.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ValueScrollTest {
    @Test
    void aMouseNotchIsExactlyOneStep() {
        ValueScroll scroll = new ValueScroll();

        assertEquals(1, scroll.steps(0.0, 1.0));
        assertEquals(1, scroll.steps(0.0, 1.0));
        assertEquals(-1, scroll.steps(0.0, -1.0));
    }

    @Test
    void touchpadFractionsBankUpIntoWholeSteps() {
        ValueScroll scroll = new ValueScroll();
        int steps = 0;
        for (int i = 0; i < 10; i++) {
            steps += scroll.steps(0.0, 0.2);
        }

        assertEquals(2, steps);
    }

    @Test
    void horizontalScrollCountsWhenVerticalIsSilent() {
        ValueScroll scroll = new ValueScroll();

        assertEquals(1, scroll.steps(1.0, 0.0));
        assertEquals(-1, scroll.steps(-1.0, 0.0));
    }

    @Test
    void verticalWinsWhenBothAxesReport() {
        ValueScroll scroll = new ValueScroll();

        assertEquals(1, scroll.steps(-1.0, 1.0));
    }

    @Test
    void resetDropsBankedDistanceSoAPlainScrollCannotBleedThrough() {
        ValueScroll scroll = new ValueScroll();
        scroll.steps(0.0, 0.9);
        scroll.reset();

        assertEquals(0, scroll.steps(0.0, 0.9));
    }

    @Test
    void nonFiniteDeltasAreIgnored() {
        ValueScroll scroll = new ValueScroll();

        assertEquals(0, scroll.steps(0.0, Double.NaN));
        assertEquals(0, scroll.steps(0.0, Double.POSITIVE_INFINITY));
        assertEquals(1, scroll.steps(0.0, 1.0));
    }

    @Test
    void anEnormousDeltaIsCappedBeforeItIsReplayed() {
        ValueScroll scroll = new ValueScroll();
        int steps = scroll.steps(0.0, 1.0e9);

        assertEquals(1_000_000_000, steps);
        assertEquals(ValueScroll.MAX_REPLAYS, ValueScroll.replayCount(steps));
        assertEquals(ValueScroll.MAX_REPLAYS, ValueScroll.replayCount(Integer.MIN_VALUE));
    }

    @Test
    void aSaturatedDeltaDoesNotLeaveAnUnboundedBank() {
        ValueScroll scroll = new ValueScroll();

        assertEquals(Integer.MAX_VALUE, scroll.steps(0.0, Double.MAX_VALUE));
        assertEquals(0, scroll.steps(0.0, 0.0));
        assertEquals(Integer.MIN_VALUE, scroll.steps(0.0, -Double.MAX_VALUE));
        assertEquals(0, scroll.steps(0.0, 0.0));
    }

    @Test
    void ordinaryGesturesReplayExactlyTheirSteps() {
        assertEquals(0, ValueScroll.replayCount(0));
        assertEquals(1, ValueScroll.replayCount(1));
        assertEquals(3, ValueScroll.replayCount(-3));
    }
}
