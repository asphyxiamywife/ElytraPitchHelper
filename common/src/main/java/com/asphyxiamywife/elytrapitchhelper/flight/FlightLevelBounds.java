package com.asphyxiamywife.elytrapitchhelper.flight;

import net.minecraft.world.level.LevelHeightAccessor;

public final class FlightLevelBounds {
    public static final int FALLBACK_MIN_BUILD_Y = 0;
    public static final int FALLBACK_MAX_BUILD_Y = 319;

    public static final int DEFAULT_VOID_OFFSET_BELOW_MIN_Y = 48;

    private FlightLevelBounds() {}

    public static int minBuildY(LevelHeightAccessor level) {
        try {
            return level.getMinY();
        } catch (RuntimeException | LinkageError ignored) {
            return FALLBACK_MIN_BUILD_Y;
        }
    }

    public static int maxBuildY(LevelHeightAccessor level) {
        int minBuildY = minBuildY(level);
        try {
            return Math.max(level.getMaxY(), minBuildY);
        } catch (RuntimeException | LinkageError ignored) {
            return maxBuildYFromHeight(level, minBuildY);
        }
    }

    private static int maxBuildYFromHeight(LevelHeightAccessor level, int minBuildY) {
        try {
            int height = level.getHeight();
            if (height > 0) {
                return minBuildY + height - 1;
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
        return Math.max(FALLBACK_MAX_BUILD_Y, minBuildY);
    }

    public static int resolvedVoidY(Integer override, int minBuildY) {
        return override != null ? override : defaultVoidY(minBuildY);
    }

    public static int defaultVoidY(int minBuildY) {
        return minBuildY - DEFAULT_VOID_OFFSET_BELOW_MIN_Y;
    }

    public static int terrainScanBottomY(int minBuildY, Integer voidYOverride) {
        return voidYOverride != null ? Math.max(minBuildY, voidYOverride) : minBuildY;
    }

    public static int terrainScanTopY(int maxBuildY, double playerY) {
        return Math.min((int) Math.floor(playerY), maxBuildY);
    }

}
