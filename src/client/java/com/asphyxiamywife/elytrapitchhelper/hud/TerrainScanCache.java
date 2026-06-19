package com.asphyxiamywife.elytrapitchhelper.hud;

import java.util.Objects;
import java.util.function.BooleanSupplier;

final class TerrainScanCache {
    static final long RESCAN_INTERVAL_MILLIS = 1_000L;

    private String dimensionKey;
    private int blockX;
    private int blockZ;
    private int voidY;
    private long scannedAtMillis;
    private boolean collisionBelow;
    private boolean hasResult;

    boolean collisionBelow(String currentDimensionKey, int currentBlockX, int currentBlockZ, int currentVoidY,
            long nowMillis, BooleanSupplier scanner) {
        if (needsScan(currentDimensionKey, currentBlockX, currentBlockZ, currentVoidY, nowMillis)) {
            collisionBelow = scanner.getAsBoolean();
            dimensionKey = currentDimensionKey;
            blockX = currentBlockX;
            blockZ = currentBlockZ;
            voidY = currentVoidY;
            scannedAtMillis = nowMillis;
            hasResult = true;
        }
        return collisionBelow;
    }

    private boolean needsScan(String currentDimensionKey, int currentBlockX, int currentBlockZ, int currentVoidY,
            long nowMillis) {
        return !hasResult
                || !Objects.equals(dimensionKey, currentDimensionKey)
                || blockX != currentBlockX
                || blockZ != currentBlockZ
                || voidY != currentVoidY
                || nowMillis < scannedAtMillis
                || nowMillis - scannedAtMillis >= RESCAN_INTERVAL_MILLIS;
    }
}
