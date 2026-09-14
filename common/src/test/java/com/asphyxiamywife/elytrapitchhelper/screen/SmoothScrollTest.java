package com.asphyxiamywife.elytrapitchhelper.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SmoothScrollTest {
    @Test
    void easingIsIndependentOfFrameRate() {
        SmoothScroll frequent = new SmoothScroll();
        SmoothScroll sparse = new SmoothScroll();
        frequent.wheel(120, 500, 1000);
        sparse.wheel(120, 500, 1000);
        for (long time = 1010; time < 1135; time += 10) frequent.advance(time, 500);
        assertEquals(sparse.advance(1135, 500), frequent.advance(1135, 500));
        assertEquals(114, frequent.advance(1135, 500));
        assertEquals(120, frequent.advance(2000, 500));
    }

    @Test
    void fractionalGesturesAccumulate() {
        SmoothScroll scroll = new SmoothScroll();
        for (int i = 0; i < 10; i++) scroll.wheel(0.12, 500, 1000 + i);
        assertEquals(1, scroll.advance(2000, 500));
    }

    @Test
    void clampsAndReversesAtEdgesWithoutStoredOverscroll() {
        SmoothScroll scroll = new SmoothScroll();
        scroll.wheel(1000, 100, 1000);
        assertEquals(100, scroll.advance(2000, 100));
        assertFalse(scroll.wheel(12, 100, 2000));
        assertTrue(scroll.wheel(-12, 100, 2000));
        assertEquals(88, scroll.advance(3000, 100));
        assertEquals(20, scroll.advance(3010, 20));
    }

    @Test
    void directPositionCancelsPendingMotion() {
        SmoothScroll scroll = new SmoothScroll();
        scroll.wheel(120, 500, 1000);
        scroll.reset(50);
        assertEquals(50, scroll.advance(2000, 500));
        assertEquals(50, scroll.advance(3000, 500));
    }
}
