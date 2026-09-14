package com.asphyxiamywife.elytrapitchhelper.hud;

final class FakeColumnProbe implements ColumnProbe {
    @FunctionalInterface
    interface Blocks {
        boolean at(int x, int y, int z);
    }

    @FunctionalInterface
    interface Loaded {
        boolean at(int x, int z);
    }

    static final Blocks EMPTY = (x, y, z) -> false;
    static final Loaded ALL_LOADED = (x, z) -> true;

    private final Blocks colliding;
    private Blocks nonAir;
    private Loaded loaded;
    private int worldFloorY = -64;
    private int worldTopY = 1_024;
    private int lookups;
    private int surfaceReads;
    private int scanCalls;

    FakeColumnProbe(Blocks colliding) {
        this(colliding, ALL_LOADED);
    }

    FakeColumnProbe(Blocks colliding, Loaded loaded) {
        this.colliding = colliding;
        this.nonAir = colliding;
        this.loaded = loaded;
    }

    FakeColumnProbe withNonCollidingAt(Blocks extra) {
        Blocks previous = nonAir;
        nonAir = (x, y, z) -> previous.at(x, y, z) || extra.at(x, y, z);
        return this;
    }

    FakeColumnProbe withWorld(int floorY, int topY) {
        worldFloorY = floorY;
        worldTopY = topY;
        return this;
    }

    void setLoaded(Loaded currentlyLoaded) {
        loaded = currentlyLoaded;
    }

    int lookups() {
        return lookups;
    }

    int takeLookups() {
        int taken = lookups;
        lookups = 0;
        return taken;
    }

    int takeSurfaceReads() {
        int taken = surfaceReads;
        surfaceReads = 0;
        return taken;
    }

    int scanCalls() {
        return scanCalls;
    }

    @Override
    public boolean isLoaded(int blockX, int blockZ) {
        return loaded.at(blockX, blockZ);
    }

    @Override
    public int surfaceTopY(int blockX, int blockZ) {
        surfaceReads++;
        for (int y = worldTopY; y >= worldFloorY; y--) {
            if (nonAir.at(blockX, y, blockZ)) {
                return y;
            }
        }
        return worldFloorY - 1;
    }

    @Override
    public boolean collidesAt(int blockX, int blockY, int blockZ) {
        lookups++;
        return colliding.at(blockX, blockY, blockZ);
    }

    @Override
    public ColumnScan scan(int blockX, int blockZ, int fromY, int throughY, int budget) {
        scanCalls++;
        return TerrainCollisionScanner.scanColumn((x, y, z) -> {
            lookups++;
            return colliding.at(x, y, z);
        }, blockX, blockZ, fromY, throughY, budget);
    }
}
