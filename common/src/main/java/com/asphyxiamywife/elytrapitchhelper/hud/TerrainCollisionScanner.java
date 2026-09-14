package com.asphyxiamywife.elytrapitchhelper.hud;

final class TerrainCollisionScanner {
    @FunctionalInterface
    interface CollisionLookup {
        boolean hasCollision(int x, int y, int z);
    }

    private TerrainCollisionScanner() {
    }

    static ColumnScan scanColumn(CollisionLookup lookup, int blockX, int blockZ,
            int fromY, int throughY, int budget) {
        int lookups = 0;
        for (int y = fromY; y <= throughY; y++) {
            if (lookups >= budget) {
                return new ColumnScan(ColumnScan.NO_SUPPORT, y - 1, lookups);
            }
            lookups++;
            if (lookup.hasCollision(blockX, y, blockZ)) {
                return new ColumnScan(y, y - 1, lookups);
            }
        }
        return new ColumnScan(ColumnScan.NO_SUPPORT, Math.max(fromY - 1, throughY), lookups);
    }
}
