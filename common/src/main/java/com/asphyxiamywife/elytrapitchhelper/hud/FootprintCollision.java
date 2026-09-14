package com.asphyxiamywife.elytrapitchhelper.hud;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.HashSet;
import java.util.Set;

final class FootprintCollision {
    private AABB footprint;
    private final Set<Long> missedColumns = new HashSet<>();

    void bind(AABB next, GroundBelowDetector detector) {
        if (footprint == null || next == null
                || footprint.minY != next.minY
                || footprint.minX != next.minX || footprint.maxX != next.maxX
                || footprint.minZ != next.minZ || footprint.maxZ != next.maxZ) {
            for (long key : missedColumns) {
                detector.invalidateColumn((int) (key >> 32), (int) key);
            }
            missedColumns.clear();
        }
        footprint = next;
    }

    boolean intersects(VoxelShape shape, int blockX, int blockY, int blockZ) {
        if (shape.isEmpty() || footprint == null) {
            return false;
        }
        if (shape == Shapes.block() && blockY + 1.0 <= footprint.minY + 1.0E-7
                && blockX + 1.0 > footprint.minX && blockX < footprint.maxX
                && blockZ + 1.0 > footprint.minZ && blockZ < footprint.maxZ) {
            return true;
        }
        for (AABB box : shape.toAabbs()) {
            if (box.maxY + blockY <= footprint.minY + 1.0E-7
                    && box.maxX + blockX > footprint.minX && box.minX + blockX < footprint.maxX
                    && box.maxZ + blockZ > footprint.minZ && box.minZ + blockZ < footprint.maxZ) {
                return true;
            }
        }
        missedColumns.add(((long) blockX << 32) | (blockZ & 0xffffffffL));
        return false;
    }
}
