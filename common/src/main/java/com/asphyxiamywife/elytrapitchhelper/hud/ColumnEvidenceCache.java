package com.asphyxiamywife.elytrapitchhelper.hud;

import java.util.Arrays;
import java.util.Objects;

final class ColumnEvidenceCache {
    static final long RESCAN_INTERVAL_MILLIS = 1_000L;
    private static final int CAPACITY = 16;

    private final long[] keys = new long[CAPACITY];
    private final boolean[] occupied = new boolean[CAPACITY];
    private final long[] sampledAtMillis = new long[CAPACITY];
    private final int[] supportY = new int[CAPACITY];
    private final int[] emptyThroughY = new int[CAPACITY];

    private String dimensionKey;
    private int floorY;
    private boolean configured;

    void configure(String currentDimensionKey, int currentFloorY) {
        if (configured && floorY == currentFloorY && Objects.equals(dimensionKey, currentDimensionKey)) {
            return;
        }
        reset();
        dimensionKey = currentDimensionKey;
        floorY = currentFloorY;
        configured = true;
    }

    GroundSupport answer(int blockX, int blockZ, int ceilingY, long nowMillis) {
        int slot = find(blockX, blockZ, nowMillis);
        if (slot < 0) {
            return GroundSupport.UNKNOWN;
        }
        if (supportY[slot] != ColumnScan.NO_SUPPORT && supportY[slot] <= ceilingY) {
            return GroundSupport.SUPPORTED;
        }
        if (emptyThroughY[slot] >= ceilingY) {
            return GroundSupport.UNSUPPORTED;
        }
        return GroundSupport.UNKNOWN;
    }

    int supportY(int blockX, int blockZ, long nowMillis) {
        int slot = find(blockX, blockZ, nowMillis);
        return slot < 0 ? ColumnScan.NO_SUPPORT : supportY[slot];
    }

    int resumeY(int blockX, int blockZ, long nowMillis) {
        int slot = find(blockX, blockZ, nowMillis);
        return slot < 0 ? floorY : Math.max(floorY, emptyThroughY[slot] + 1);
    }

    void record(int blockX, int blockZ, ColumnScan scan, long nowMillis) {
        int slot = slotFor(blockX, blockZ, nowMillis);
        if (scan.foundSupport()) {
            supportY[slot] = scan.supportY();
        }
        emptyThroughY[slot] = Math.max(emptyThroughY[slot], scan.provedEmptyThroughY());
    }

    void forget(int blockX, int blockZ) {
        int slot = indexOf(key(blockX, blockZ));
        if (slot >= 0) {
            clear(slot);
        }
    }

    void forgetChunk(int chunkX, int chunkZ) {
        for (int i = 0; i < CAPACITY; i++) {
            if (!occupied[i]) {
                continue;
            }
            int blockX = (int) (keys[i] >> 32);
            int blockZ = (int) keys[i];
            if ((blockX >> 4) == chunkX && (blockZ >> 4) == chunkZ) {
                clear(i);
            }
        }
    }

    void reset() {
        Arrays.fill(occupied, false);
        dimensionKey = null;
        configured = false;
    }

    private int find(int blockX, int blockZ, long nowMillis) {
        int slot = indexOf(key(blockX, blockZ));
        if (slot < 0) {
            return -1;
        }
        if (expired(slot, nowMillis)) {
            clear(slot);
            return -1;
        }
        return slot;
    }

    private int slotFor(int blockX, int blockZ, long nowMillis) {
        long key = key(blockX, blockZ);
        int slot = indexOf(key);
        if (slot >= 0 && !expired(slot, nowMillis)) {
            return slot;
        }
        if (slot < 0) {
            slot = freeSlot(nowMillis);
        }
        keys[slot] = key;
        occupied[slot] = true;
        sampledAtMillis[slot] = nowMillis;
        supportY[slot] = ColumnScan.NO_SUPPORT;
        emptyThroughY[slot] = floorY - 1;
        return slot;
    }

    private boolean expired(int slot, long nowMillis) {
        long age = nowMillis - sampledAtMillis[slot];
        return age < 0L || age >= RESCAN_INTERVAL_MILLIS;
    }

    private int freeSlot(long nowMillis) {
        int oldest = 0;
        for (int i = 0; i < CAPACITY; i++) {
            if (!occupied[i] || expired(i, nowMillis)) {
                return i;
            }
            if (sampledAtMillis[i] - sampledAtMillis[oldest] < 0L) {
                oldest = i;
            }
        }
        return oldest;
    }

    private int indexOf(long key) {
        for (int i = 0; i < CAPACITY; i++) {
            if (occupied[i] && keys[i] == key) {
                return i;
            }
        }
        return -1;
    }

    private void clear(int slot) {
        occupied[slot] = false;
    }

    private static long key(int blockX, int blockZ) {
        return ((long) blockX << 32) | (blockZ & 0xFFFFFFFFL);
    }
}
