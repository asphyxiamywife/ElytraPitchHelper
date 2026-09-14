package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.config.Profile;

record HudFrameState(
        Profile profile,
        HudRenderState renderState,
        AmplitudeCue amplitudeCue,
        VoidProximity voidProximity,
        Strength flash,
        Strength voidPulse,
        MotionGlyphCue motionGlyph) {
    static final HudFrameState NONE = new HudFrameState(
            null, null, AmplitudeCue.NONE, VoidProximity.NONE, Strength.ZERO, Strength.ZERO,
            MotionGlyphCue.NONE);

    HudFrameState(Profile profile, HudRenderState renderState, AmplitudeCue amplitudeCue,
            VoidProximity voidProximity, Strength flash, Strength voidPulse) {
        this(profile, renderState, amplitudeCue, voidProximity, flash, voidPulse,
                MotionGlyphCue.NONE);
    }

    float flashNow() {
        return flash.value();
    }

    float voidPulseNow() {
        return voidPulse.value();
    }

    boolean visible() {
        return profile != null;
    }
}
