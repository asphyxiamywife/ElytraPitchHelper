package com.asphyxiamywife.elytrapitchhelper.hud;

final class VoidProximity {
    static final VoidProximity NONE = new VoidProximity(0.0f, 0.0f);

    final float warning;
    final float pulse;

    VoidProximity(float warning, float pulse) {
        this.warning = warning;
        this.pulse = pulse;
    }

    boolean isActive() {
        return warning > 0.01f || pulse > 0.01f;
    }
}
