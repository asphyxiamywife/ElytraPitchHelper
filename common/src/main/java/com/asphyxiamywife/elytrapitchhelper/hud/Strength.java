package com.asphyxiamywife.elytrapitchhelper.hud;

@FunctionalInterface
interface Strength {
    Strength ZERO = () -> 0.0f;

    float value();
}
