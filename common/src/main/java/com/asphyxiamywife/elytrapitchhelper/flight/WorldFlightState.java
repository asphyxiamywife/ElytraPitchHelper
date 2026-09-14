package com.asphyxiamywife.elytrapitchhelper.flight;

import com.asphyxiamywife.elytrapitchhelper.hud.GroundSupport;

public record WorldFlightState(String dimensionKey, int minBuildY, int maxBuildY,
        TerrainProbe terrain) {
    public boolean hasLevel() {
        return dimensionKey != null;
    }

    @FunctionalInterface
    public interface TerrainProbe {
        GroundSupport detect(int scanBottomY, int scanTopY,
                int minX, int maxX, int minZ, int maxZ, long nowMillis);
    }
}
