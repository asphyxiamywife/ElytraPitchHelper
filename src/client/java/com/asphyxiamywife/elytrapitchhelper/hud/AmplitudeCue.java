package com.asphyxiamywife.elytrapitchhelper.hud;

final class AmplitudeCue {
    static final AmplitudeCue NONE = new AmplitudeCue(AmplitudeLeg.NONE, 0.0f, AmplitudeLeg.NONE, 0.0f);

    final AmplitudeLeg leg;
    final float amount;
    final AmplitudeLeg flashLeg;
    final float flash;

    AmplitudeCue(AmplitudeLeg leg, float amount, AmplitudeLeg flashLeg, float flash) {
        this.leg = leg;
        this.amount = amount;
        this.flashLeg = flashLeg;
        this.flash = flash;
    }

    AmplitudeCue forLeg(AmplitudeLeg candidate) {
        float lineAmount = leg == candidate ? amount : 0.0f;
        float lineFlash = flashLeg == candidate ? flash : 0.0f;
        if (lineAmount <= 0.01f && lineFlash <= 0.01f) {
            return NONE;
        }
        return new AmplitudeCue(candidate, lineAmount, candidate, lineFlash);
    }
}
