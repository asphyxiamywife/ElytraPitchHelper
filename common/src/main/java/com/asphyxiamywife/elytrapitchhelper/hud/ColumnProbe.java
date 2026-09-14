package com.asphyxiamywife.elytrapitchhelper.hud;

interface ColumnProbe {
    boolean isLoaded(int blockX, int blockZ);

    int surfaceTopY(int blockX, int blockZ);

    boolean collidesAt(int blockX, int blockY, int blockZ);

    ColumnScan scan(int blockX, int blockZ, int fromY, int throughY, int budget);
}
