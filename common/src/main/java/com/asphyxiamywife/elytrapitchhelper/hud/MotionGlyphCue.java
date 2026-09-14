package com.asphyxiamywife.elytrapitchhelper.hud;

record MotionGlyphCue(AmplitudeLeg afterLeg, Strength progress, Strength opacity) {
    static final MotionGlyphCue NONE = new MotionGlyphCue(
            AmplitudeLeg.NONE, () -> 1.0f, Strength.ZERO);

    MotionGlyphCue(AmplitudeLeg afterLeg, Strength progress) {
        this(afterLeg, progress, () -> progress.value() < 1.0f ? 1.0f : 0.0f);
    }

    float progressNow() {
        return progress.value();
    }

    float opacityNow() {
        return opacity.value();
    }
}
