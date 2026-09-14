package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;

import java.util.function.LongSupplier;

final class MotionGlyphTimer {
    private static final long POST_MOTION_FADE_MILLIS = 160L;

    private final LongSupplier currentTimeMillis;
    private volatile State state = State.NONE;

    MotionGlyphTimer(LongSupplier currentTimeMillis) {
        this.currentTimeMillis = currentTimeMillis;
    }

    void trigger(AmplitudeLeg leg, long targetDurationMillis) {
        state = new State(leg, currentTimeMillis.getAsLong(), Math.max(1L, targetDurationMillis));
    }

    void reset() {
        state = State.NONE;
    }

    MotionGlyphCue cue() {
        State snapshot = state;
        return snapshot.afterLeg() == AmplitudeLeg.NONE
                ? MotionGlyphCue.NONE
                : new MotionGlyphCue(snapshot.afterLeg(),
                        () -> progress(snapshot), () -> opacity(snapshot));
    }

    private float progress(State snapshot) {
        if (snapshot.afterLeg() == AmplitudeLeg.NONE) {
            return 1.0f;
        }
        long age = currentTimeMillis.getAsLong() - snapshot.startedMillis();
        if (age < 0L) {
            return 1.0f;
        }
        return MathUtil.clamp(age / (float) snapshot.durationMillis(), 0.0f, 1.0f);
    }

    private float opacity(State snapshot) {
        long age = currentTimeMillis.getAsLong() - snapshot.startedMillis();
        if (snapshot.afterLeg() == AmplitudeLeg.NONE || age < 0L) {
            return 0.0f;
        }
        if (age <= snapshot.durationMillis()) {
            return 1.0f;
        }
        float fadeProgress = (age - snapshot.durationMillis()) / (float) POST_MOTION_FADE_MILLIS;
        return 1.0f - MathUtil.smoothstep(MathUtil.clamp01(fadeProgress));
    }

    private record State(AmplitudeLeg afterLeg, long startedMillis, long durationMillis) {
        private static final State NONE = new State(AmplitudeLeg.NONE, 0L, 1L);
    }
}
