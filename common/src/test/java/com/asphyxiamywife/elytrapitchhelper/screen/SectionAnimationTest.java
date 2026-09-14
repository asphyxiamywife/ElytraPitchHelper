package com.asphyxiamywife.elytrapitchhelper.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SectionAnimationTest {
    private static final int CHILD_ROWS = 9;
    private static final long DURATION = SectionAnimation.durationFor(CHILD_ROWS);

    private static SectionAnimation started(long startMillis) {
        SectionAnimation animation = new SectionAnimation();
        animation.advance(startMillis);
        return animation;
    }

    @Test
    void anUntrackedSectionSettlesToTheStoredState() {
        SectionAnimation animation = new SectionAnimation();
        animation.syncTo(ConfigCategory.VOID, true);

        assertEquals(1.0f, animation.progress(ConfigCategory.VOID));
        assertTrue(animation.showsChildren(ConfigCategory.VOID));
        assertFalse(animation.animating());
    }

    @Test
    void theFirstFoldOfASettledSectionStartsFromWhereItIs() {
        SectionAnimation animation = started(0L);
        animation.syncTo(ConfigCategory.VOID, true);
        animation.setExpanded(ConfigCategory.VOID, false, CHILD_ROWS);

        assertEquals(1.0f, animation.progress(ConfigCategory.VOID));
        animation.advance(10L);
        assertTrue(animation.progress(ConfigCategory.VOID) < 1.0f);
    }

    @Test
    void syncLeavesASectionThatIsStillTravelling() {
        SectionAnimation animation = started(0L);
        animation.settle(ConfigCategory.VOID, false);
        animation.setExpanded(ConfigCategory.VOID, true, CHILD_ROWS);
        animation.advance(DURATION / 2);
        float midway = animation.progress(ConfigCategory.VOID);

        animation.syncTo(ConfigCategory.VOID, true);

        assertEquals(midway, animation.progress(ConfigCategory.VOID));
    }

    @Test
    void theSmallestSectionIsVisibleForLongEnoughToRead() {
        long duration = SectionAnimation.durationFor(1);
        float firstVisible = 1.0f;
        for (int i = 0; i <= 1000; i++) {
            float progress = i / 1000.0f;
            if (SectionAnimation.rowAlpha(progress) > 0.0f) {
                firstVisible = progress;
                break;
            }
        }

        long visibleMillis = Math.round((1.0f - firstVisible) * duration);
        assertTrue(visibleMillis >= 75L,
                () -> "a one-row unfold is only visible for " + visibleMillis + "ms");
    }

    @Test
    void unfoldTimeGrowsWithTheSectionAndStaysClamped() {
        assertTrue(SectionAnimation.durationFor(10) > SectionAnimation.durationFor(1));
        assertEquals(SectionAnimation.MIN_DURATION_MILLIS, SectionAnimation.durationFor(0));
        assertEquals(SectionAnimation.MAX_DURATION_MILLIS, SectionAnimation.durationFor(500));
        assertTrue(SectionAnimation.durationFor(-5) >= SectionAnimation.MIN_DURATION_MILLIS);
    }

    @Test
    void settlingSkipsTheTravelEntirely() {
        SectionAnimation animation = new SectionAnimation();
        animation.settle(ConfigCategory.VOID, true);

        assertEquals(1.0f, animation.progress(ConfigCategory.VOID));
        assertFalse(animation.animating());
    }

    @Test
    void aFoldRunsToItsTargetAndStops() {
        SectionAnimation animation = started(1000L);
        animation.settle(ConfigCategory.VOID, false);
        animation.setExpanded(ConfigCategory.VOID, true, CHILD_ROWS);

        assertTrue(animation.animating());
        animation.advance(1000L + DURATION / 2);
        float halfway = animation.progress(ConfigCategory.VOID);
        assertTrue(halfway > 0.0f && halfway < 1.0f, () -> "expected mid-travel, got " + halfway);

        animation.advance(1000L + DURATION * 2);
        assertEquals(1.0f, animation.progress(ConfigCategory.VOID));
        assertFalse(animation.animating());
    }

    @Test
    void reversingMidFoldTurnsAroundRatherThanRestarting() {
        SectionAnimation animation = started(0L);
        animation.settle(ConfigCategory.VOID, false);
        animation.setExpanded(ConfigCategory.VOID, true, CHILD_ROWS);
        animation.advance(DURATION / 2);
        float opened = animation.progress(ConfigCategory.VOID);

        animation.setExpanded(ConfigCategory.VOID, false, CHILD_ROWS);
        animation.advance(DURATION / 2 + 20L);

        assertTrue(animation.progress(ConfigCategory.VOID) < opened,
                "reversing should walk back from where it got to");
    }

    @Test
    void rowsStayInvisibleUntilTheGapCanHoldThem() {
        assertEquals(0.0f, SectionAnimation.rowAlpha(0.0f));
        assertEquals(0.0f, SectionAnimation.rowAlpha(0.25f));
        assertEquals(1.0f, SectionAnimation.rowAlpha(1.0f));
        assertTrue(SectionAnimation.rowAlpha(0.85f) > 0.0f);
        assertTrue(SectionAnimation.rowAlpha(0.45f) < 0.2f);
    }

    @Test
    void heightRunsFromNothingToAFullRow() {
        assertEquals(0.0f, SectionAnimation.heightFactor(0.0f));
        assertEquals(1.0f, SectionAnimation.heightFactor(1.0f));
        assertTrue(SectionAnimation.heightFactor(0.5f) > 0.0f);
        assertTrue(SectionAnimation.heightFactor(0.5f) < 1.0f);
    }

    @Test
    void rowsOverlapOnlyWhileTooFaintToRead() {
        for (int i = 0; i <= 100; i++) {
            float progress = i / 100.0f;
            float height = SectionAnimation.heightFactor(progress);
            float alpha = SectionAnimation.rowAlpha(progress);
            if (height <= 0.3f) {
                assertEquals(0.0f, alpha,
                        () -> "rows should be hidden outright at height factor " + height);
            } else if (height <= 0.5f) {
                assertTrue(alpha < 0.25f,
                        () -> "packed over 2:1 at " + height + " but drawn at " + alpha);
            }
        }
    }

    @Test
    void childrenOnlyJoinTheListOnceTheSectionHasHeight() {
        SectionAnimation animation = started(0L);
        animation.settle(ConfigCategory.VOID, false);

        assertFalse(animation.showsChildren(ConfigCategory.VOID));
        animation.setExpanded(ConfigCategory.VOID, true, CHILD_ROWS);
        animation.advance(10L);
        assertTrue(animation.showsChildren(ConfigCategory.VOID));
    }

    @Test
    void aStalledClockDoesNotMoveAnything() {
        SectionAnimation animation = started(500L);
        animation.settle(ConfigCategory.VOID, false);
        animation.setExpanded(ConfigCategory.VOID, true, CHILD_ROWS);

        assertFalse(animation.advance(500L));
        assertEquals(0.0f, animation.progress(ConfigCategory.VOID));
    }
}
