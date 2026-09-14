package com.asphyxiamywife.elytrapitchhelper.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ScrollbarMetricsTest {
    @Test
    void clampsScrollAndMapsItsEndpointsOntoTheTrack() {
        ScrollbarMetrics start = ScrollbarMetrics.of(10, 100, 50, 200, -5, 150, 8, 0);
        ScrollbarMetrics end = ScrollbarMetrics.of(10, 100, 50, 200, 999, 150, 8, 0);

        assertEquals(10, start.thumbY());
        assertEquals(25, start.thumbHeight());
        assertEquals(85, end.thumbY());
    }

    @Test
    void zeroScrollRangePinsTheThumbToTrackStart() {
        ScrollbarMetrics metrics = ScrollbarMetrics.of(20, 80, 80, 80, 40, 0, 8, 0);

        assertEquals(20, metrics.thumbY());
        assertEquals(80, metrics.thumbHeight());
        assertEquals(0, metrics.travel());
    }
}
