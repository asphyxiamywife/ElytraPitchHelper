package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;

public record ScrollbarMetrics(int thumbY, int thumbHeight, int travel, int maxScroll) {
    static ScrollbarMetrics of(int trackY, int trackHeight, int contentHeight, int scroll, int maxScroll) {
        return of(trackY, trackHeight, trackHeight, contentHeight, scroll, maxScroll, 32, 8);
    }

    public static ScrollbarMetrics of(int trackY, int trackHeight, int viewportHeight, int contentHeight,
            int scroll, int maxScroll, int minimumThumbHeight, int endPadding) {
        int safeContentHeight = Math.max(1, contentHeight);
        int maximumThumbHeight = Math.max(0, trackHeight - endPadding);
        int thumbHeight = MathUtil.clamp(
                (int) (trackHeight * (long) viewportHeight / safeContentHeight),
                Math.min(minimumThumbHeight, maximumThumbHeight),
                maximumThumbHeight);
        int travel = Math.max(0, trackHeight - thumbHeight);
        int safeMaxScroll = Math.max(0, maxScroll);
        int clampedScroll = MathUtil.clamp(scroll, 0, safeMaxScroll);
        int thumbY = safeMaxScroll == 0
                ? trackY
                : trackY + (int) (clampedScroll * (long) travel / safeMaxScroll);
        return new ScrollbarMetrics(thumbY, thumbHeight, travel, safeMaxScroll);
    }
}
