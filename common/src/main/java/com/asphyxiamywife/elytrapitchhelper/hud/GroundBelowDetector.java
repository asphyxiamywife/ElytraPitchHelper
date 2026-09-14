package com.asphyxiamywife.elytrapitchhelper.hud;

final class GroundBelowDetector {
    static final int DEFAULT_LOOKUP_BUDGET = 2_048;

    private final ColumnEvidenceCache evidence = new ColumnEvidenceCache();
    private final int lookupBudget;

    GroundBelowDetector() {
        this(DEFAULT_LOOKUP_BUDGET);
    }

    GroundBelowDetector(int lookupBudget) {
        this.lookupBudget = Math.max(1, lookupBudget);
    }

    GroundSupport detect(ColumnProbe probe, String dimensionKey, int floorY, int ceilingY,
            int minX, int maxX, int minZ, int maxZ, long nowMillis) {
        evidence.configure(dimensionKey, floorY);
        if (ceilingY < floorY) {
            return GroundSupport.UNSUPPORTED;
        }

        boolean unresolved = false;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                GroundSupport known = cheapAnswer(probe, x, z, floorY, ceilingY, nowMillis);
                if (known == GroundSupport.SUPPORTED) {
                    return GroundSupport.SUPPORTED;
                }
                unresolved |= known == GroundSupport.UNKNOWN;
            }
        }
        if (!unresolved) {
            return GroundSupport.UNSUPPORTED;
        }

        int remaining = lookupBudget;
        unresolved = false;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!probe.isLoaded(x, z)) {
                    unresolved = true;
                    continue;
                }
                if (evidence.answer(x, z, ceilingY, nowMillis) == GroundSupport.UNSUPPORTED) {
                    continue;
                }

                int walkCeilingY = walkCeilingY(probe, x, z, ceilingY);
                if (walkCeilingY < floorY) {
                    continue;
                }
                if (remaining <= 0) {
                    unresolved = true;
                    continue;
                }

                ColumnScan scan = probe.scan(x, z, evidence.resumeY(x, z, nowMillis), walkCeilingY, remaining);
                remaining -= scan.lookups();
                ColumnScan proved = extendedToSurface(scan, walkCeilingY, ceilingY);
                evidence.record(x, z, proved, nowMillis);
                if (proved.foundSupport()) {
                    return GroundSupport.SUPPORTED;
                }
                unresolved |= proved.provedEmptyThroughY() < ceilingY;
            }
        }
        return unresolved ? GroundSupport.UNKNOWN : GroundSupport.UNSUPPORTED;
    }

    private GroundSupport cheapAnswer(ColumnProbe probe, int blockX, int blockZ,
            int floorY, int ceilingY, long nowMillis) {
        if (!probe.isLoaded(blockX, blockZ)) {
            evidence.forget(blockX, blockZ);
            return GroundSupport.UNKNOWN;
        }

        int surfaceTopY = probe.surfaceTopY(blockX, blockZ);
        if (surfaceTopY < floorY) {
            return GroundSupport.UNSUPPORTED;
        }
        if (surfaceTopY <= ceilingY) {
            if (probe.collidesAt(blockX, surfaceTopY, blockZ)) {
                return GroundSupport.SUPPORTED;
            }
            if (surfaceTopY <= floorY) {
                return GroundSupport.UNSUPPORTED;
            }
        }
        GroundSupport cached = evidence.answer(blockX, blockZ, ceilingY, nowMillis);
        if (cached == GroundSupport.SUPPORTED) {
            int supportY = evidence.supportY(blockX, blockZ, nowMillis);
            if (probe.collidesAt(blockX, supportY, blockZ)) {
                return GroundSupport.SUPPORTED;
            }
            evidence.forget(blockX, blockZ);
            return GroundSupport.UNKNOWN;
        }
        return cached;
    }

    private static int walkCeilingY(ColumnProbe probe, int blockX, int blockZ, int ceilingY) {
        int surfaceTopY = probe.surfaceTopY(blockX, blockZ);
        return surfaceTopY <= ceilingY ? surfaceTopY - 1 : ceilingY;
    }

    private static ColumnScan extendedToSurface(ColumnScan scan, int walkCeilingY, int ceilingY) {
        if (scan.foundSupport() || scan.provedEmptyThroughY() < walkCeilingY) {
            return scan;
        }
        return new ColumnScan(scan.supportY(), Math.max(scan.provedEmptyThroughY(), ceilingY), scan.lookups());
    }

    void reset() {
        evidence.reset();
    }

    void invalidateColumn(int blockX, int blockZ) {
        evidence.forget(blockX, blockZ);
    }

    void invalidateChunk(int chunkX, int chunkZ) {
        evidence.forgetChunk(chunkX, chunkZ);
    }
}
