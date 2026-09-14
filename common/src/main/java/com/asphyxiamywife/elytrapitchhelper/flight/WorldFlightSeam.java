package com.asphyxiamywife.elytrapitchhelper.flight;

import com.asphyxiamywife.elytrapitchhelper.hud.WorldTerrainProbe;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class WorldFlightSeam {
    private final WorldTerrainProbe terrain = new WorldTerrainProbe();
    private ResourceKey<Level> cachedDimension;
    private String cachedDimensionKey;

    public WorldFlightState captureWorld(Player player, PlayerFlightState state) {
        Level level = player.level();
        String dimensionKey = dimensionKey(level);
        return new WorldFlightState(dimensionKey,
                FlightLevelBounds.minBuildY(level), FlightLevelBounds.maxBuildY(level),
                (bottom, top, minX, maxX, minZ, maxZ, now) -> terrain.detect(
                        level, state.boundingBox(), dimensionKey, bottom, top, minX, maxX, minZ, maxZ, now));
    }

    private String dimensionKey(Level level) {
        if (level == null) {
            return null;
        }
        ResourceKey<Level> dimension = level.dimension();
        if (!dimension.equals(cachedDimension)) {
            cachedDimension = dimension;
            cachedDimensionKey = dimension.identifier().toString();
        }
        return cachedDimensionKey;
    }

    public void invalidateColumn(int blockX, int blockZ) {
        terrain.invalidateColumn(blockX, blockZ);
    }

    public void invalidateChunk(int chunkX, int chunkZ) {
        terrain.invalidateChunk(chunkX, chunkZ);
    }

    public void resetDetector() {
        terrain.resetDetector();
    }

    public void reset() {
        terrain.reset();
    }
}
