package com.asphyxiamywife.elytrapitchhelper.flight;

import net.minecraft.world.phys.AABB;

public record PlayerFlightState(float pitch, double y, double verticalSpeed,
        double horizontalSpeed, AABB boundingBox, int fallFlyingTicks,
        boolean equippedUsableElytra, boolean hasFireworkRocket) {

    public boolean hasUsableElytra(boolean anyElytraGlide) {
        return equippedUsableElytra || anyElytraGlide && fallFlyingTicks > 0;
    }
}
