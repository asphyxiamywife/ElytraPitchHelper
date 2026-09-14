package com.asphyxiamywife.elytrapitchhelper.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MotionGlyphTimerTest {
    private static final float EPSILON = 0.0001f;

    @Test
    void pullUpIsBriefAndReleaseDownLastsTwiceAsLong() {
        long[] now = {1_000L};
        MotionGlyphTimer timer = new MotionGlyphTimer(() -> now[0]);

        timer.trigger(AmplitudeLeg.DESCENDING, 700L);
        now[0] += 350L;
        assertEquals(AmplitudeLeg.DESCENDING, timer.cue().afterLeg());
        assertEquals(0.5f, timer.cue().progressNow(), EPSILON);
        now[0] += 350L;
        assertEquals(1.0f, timer.cue().progressNow(), EPSILON);

        timer.trigger(AmplitudeLeg.ASCENDING, 1_400L);
        now[0] += 700L;
        assertEquals(AmplitudeLeg.ASCENDING, timer.cue().afterLeg());
        assertEquals(0.5f, timer.cue().progressNow(), EPSILON,
                "the downward release should demonstrate a tempo twice as long as the pull-up");
    }

    @Test
    void resetAndBackwardClocksLeaveNoDrawableGesture() {
        long[] now = {1_000L};
        MotionGlyphTimer timer = new MotionGlyphTimer(() -> now[0]);
        timer.trigger(AmplitudeLeg.DESCENDING, 700L);
        now[0] = 999L;
        assertEquals(1.0f, timer.cue().progressNow(), EPSILON);

        timer.reset();
        assertEquals(AmplitudeLeg.NONE, timer.cue().afterLeg());
    }

    @Test
    void configuredDurationIsAuthoritative() {
        long[] now = {1_000L};
        MotionGlyphTimer timer = new MotionGlyphTimer(() -> now[0]);

        timer.trigger(AmplitudeLeg.ASCENDING, 5_000L);
        now[0] += 1_400L;
        assertEquals(0.28f, timer.cue().progressNow(), EPSILON);
        now[0] += 3_600L;
        assertEquals(1.0f, timer.cue().progressNow(), EPSILON);
    }

    @Test
    void completedMotionGetsASeparateFadeTail() {
        long[] now = {1_000L};
        MotionGlyphTimer timer = new MotionGlyphTimer(() -> now[0]);
        timer.trigger(AmplitudeLeg.DESCENDING, 700L);
        MotionGlyphCue cue = timer.cue();

        now[0] += 700L;
        assertEquals(1.0f, cue.progressNow(), EPSILON);
        assertEquals(1.0f, cue.opacityNow(), EPSILON);
        now[0] += 80L;
        assertEquals(0.5f, cue.opacityNow(), EPSILON);
        now[0] += 80L;
        assertEquals(0.0f, cue.opacityNow(), EPSILON);
    }

    @Test
    void aCueKeepsTheAtomicStateOfTheTriggerThatCreatedIt() {
        long[] now = {1_000L};
        MotionGlyphTimer timer = new MotionGlyphTimer(() -> now[0]);
        timer.trigger(AmplitudeLeg.DESCENDING, 700L);
        MotionGlyphCue first = timer.cue();

        now[0] = 1_050L;
        timer.trigger(AmplitudeLeg.ASCENDING, 1_400L);
        MotionGlyphCue second = timer.cue();
        now[0] = 1_350L;

        assertEquals(AmplitudeLeg.DESCENDING, first.afterLeg());
        assertEquals(0.5f, first.progressNow(), EPSILON);
        assertEquals(AmplitudeLeg.ASCENDING, second.afterLeg());
        assertEquals(300.0f / 1_400.0f, second.progressNow(), EPSILON);
    }
}
