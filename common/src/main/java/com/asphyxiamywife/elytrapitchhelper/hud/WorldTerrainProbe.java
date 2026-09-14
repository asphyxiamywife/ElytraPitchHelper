package com.asphyxiamywife.elytrapitchhelper.hud;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public final class WorldTerrainProbe {
    private final GroundBelowDetector groundBelow = new GroundBelowDetector();
    private final LevelColumnProbe columnProbe = new LevelColumnProbe();

    public GroundSupport detect(Level level, AABB footprint, String dimensionKey,
            int scanBottomY, int scanTopY, int minX, int maxX, int minZ, int maxZ, long nowMillis) {
        return groundBelow.detect(columnProbe.bind(level, footprint, groundBelow), dimensionKey,
                scanBottomY, scanTopY, minX, maxX, minZ, maxZ, nowMillis);
    }

    public void invalidateColumn(int blockX, int blockZ) {
        groundBelow.invalidateColumn(blockX, blockZ);
    }

    public void invalidateChunk(int chunkX, int chunkZ) {
        groundBelow.invalidateChunk(chunkX, chunkZ);
    }

    public void resetDetector() {
        groundBelow.reset();
    }

    public void reset() {
        groundBelow.reset();
        columnProbe.bind(null, null, groundBelow);
    }
}
