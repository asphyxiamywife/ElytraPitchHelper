package com.asphyxiamywife.elytrapitchhelper.hud;

final class AmplitudeCue {
    static final AmplitudeCue NONE = new AmplitudeCue(
            AmplitudeLeg.NONE, 0.0f, AmplitudeLeg.NONE, AmplitudeLeg.NONE);

    final AmplitudeLeg leg;
    final float amount;
    final AmplitudeLeg flashLeg;
    final AmplitudeLeg glyphStartedLeg;

    AmplitudeCue(AmplitudeLeg leg, float amount, AmplitudeLeg flashLeg) {
        this(leg, amount, flashLeg, AmplitudeLeg.NONE);
    }

    AmplitudeCue(AmplitudeLeg leg, float amount, AmplitudeLeg flashLeg,
            AmplitudeLeg glyphStartedLeg) {
        this.leg = leg;
        this.amount = amount;
        this.flashLeg = flashLeg;
        this.glyphStartedLeg = glyphStartedLeg;
    }

}
