package com.asphyxiamywife.elytrapitchhelper.hud;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;

final class LevelColumnProbe implements ColumnProbe {
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private final TerrainCollisionScanner.CollisionLookup lookup = this::hasCollision;
    private Level level;
    private final FootprintCollision collision = new FootprintCollision();

    LevelColumnProbe bind(Level currentLevel, AABB footprint, GroundBelowDetector detector) {
        collision.bind(footprint, detector);
        level = currentLevel;
        return this;
    }

    @Override
    public boolean isLoaded(int blockX, int blockZ) {
        return level != null
                && level.hasChunk(SectionPos.blockToSectionCoord(blockX), SectionPos.blockToSectionCoord(blockZ));
    }

    @Override
    public int surfaceTopY(int blockX, int blockZ) {
        return level.getHeight(Heightmap.Types.WORLD_SURFACE, blockX, blockZ) - 1;
    }

    @Override
    public boolean collidesAt(int blockX, int blockY, int blockZ) {
        return hasCollision(blockX, blockY, blockZ);
    }

    @Override
    public ColumnScan scan(int blockX, int blockZ, int fromY, int throughY, int budget) {
        return TerrainCollisionScanner.scanColumn(lookup, blockX, blockZ, fromY, throughY, budget);
    }

    private boolean hasCollision(int x, int y, int z) {
        pos.set(x, y, z);
        BlockState state = level.getBlockState(pos);
        return collision.intersects(state.getCollisionShape(level, pos), x, y, z);
    }
}
