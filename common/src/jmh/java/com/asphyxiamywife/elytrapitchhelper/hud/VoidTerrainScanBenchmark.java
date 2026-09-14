package com.asphyxiamywife.elytrapitchhelper.hud;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
@State(Scope.Thread)
public class VoidTerrainScanBenchmark {
    @Param({ "64", "256", "384" })
    public int scanDepth;

    private int startY;
    private int scanBottomY;
    private final BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
    private BlockState[] emptyVoid;
    private BlockState[] terrainAtPlayerHeight;

    @Setup
    public void setUp() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        startY = 319;
        scanBottomY = startY - scanDepth + 1;
        emptyVoid = new BlockState[scanDepth * 9];
        Arrays.fill(emptyVoid, Blocks.AIR.defaultBlockState());
        terrainAtPlayerHeight = emptyVoid.clone();
        terrainAtPlayerHeight[terrainAtPlayerHeight.length - 1] = Blocks.STONE.defaultBlockState();
    }

    @Benchmark
    public boolean collisionShapeOnlyEmptyVoidFullScan() {
        return scanFootprint(emptyVoid);
    }

    @Benchmark
    public boolean collisionShapeOnlyTerrainAfterFullHeightScan() {
        return scanFootprint(terrainAtPlayerHeight);
    }

    private boolean scanFootprint(BlockState[] states) {
        TerrainCollisionScanner.CollisionLookup lookup = (x, y, z) -> hasCollision(states, x, y, z);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (TerrainCollisionScanner.scanColumn(lookup, x, z,
                        scanBottomY, startY, scanDepth).foundSupport()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasCollision(BlockState[] states, int x, int y, int z) {
        int index = ((y - scanBottomY) * 3 + (x + 1)) * 3 + (z + 1);
        position.set(x, y, z);
        return !states[index].getCollisionShape(EmptyBlockGetter.INSTANCE, position).isEmpty();
    }
}
