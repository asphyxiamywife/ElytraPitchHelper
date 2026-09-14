package com.asphyxiamywife.elytrapitchhelper.hud;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FootprintCollisionTest {
    private static final VoxelShape POST = Shapes.box(0.375, 0, 0.375, 0.625, 1.5, 0.625);

    @Test
    void feetBlockIncludesSlabsButRejectsSurfacesAboveFeet() {
        FootprintCollision collision = new FootprintCollision();
        GroundBelowDetector detector = new GroundBelowDetector();
        collision.bind(new AABB(0.2, 64.75, 0.2, 0.8, 65.35, 0.8), detector);
        assertTrue(collision.intersects(Shapes.box(0, 0, 0, 1, 0.5, 1), 0, 64, 0));
        assertFalse(collision.intersects(Shapes.block(), 0, 64, 0));
        assertFalse(collision.intersects(Shapes.box(0, 0.8, 0, 1, 1, 1), 0, 64, 0));
        assertEquals(64, PitchGuideHud.terrainScanTopY(319, 64.75));
        assertEquals(-1, PitchGuideHud.terrainScanTopY(319, -0.25));
    }

    @Test
    void verticalMovementInvalidatesMissesAndRechecksCachedSupport() {
        FootprintCollision collision = new FootprintCollision();
        GroundBelowDetector detector = new GroundBelowDetector();
        VoxelShape slab = Shapes.box(0, 0, 0, 1, 0.5, 1);
        ColumnProbe probe = new ColumnProbe() {
            public boolean isLoaded(int x, int z) { return true; }
            public int surfaceTopY(int x, int z) { return 100; }
            public boolean collidesAt(int x, int y, int z) {
                return collision.intersects(y == 64 ? slab : Shapes.empty(), x, y, z);
            }
            public ColumnScan scan(int x, int z, int from, int through, int budget) {
                return TerrainCollisionScanner.scanColumn(this::collidesAt, x, z, from, through, budget);
            }
        };
        double[] heights = {64.25, 64.75, 64.5, 64.25};
        GroundSupport[] expected = {GroundSupport.UNSUPPORTED, GroundSupport.SUPPORTED,
                GroundSupport.SUPPORTED, GroundSupport.UNSUPPORTED};
        for (int i = 0; i < heights.length; i++) {
            double feetY = heights[i];
            collision.bind(new AABB(0.2, feetY, 0.2, 0.8, feetY + 0.6, 0.8), detector);
            assertEquals(expected[i], detector.detect(probe, "test", 0,
                    PitchGuideHud.terrainScanTopY(319, feetY), 0, 0, 0, 0, 100 + i));
        }
    }

    @Test
    void partialBlocksMustOverlapTheFootprintWithPositiveArea() {
        FootprintCollision collision = new FootprintCollision();
        GroundBelowDetector detector = new GroundBelowDetector();
        collision.bind(new AABB(-0.4, 10, 0.2, 0.2, 11, 0.8), detector);
        assertFalse(collision.intersects(POST, 0, 0, 0));
        assertTrue(collision.intersects(Shapes.block(), 0, 0, 0));
        collision.bind(new AABB(-0.225, 10, 0.2, 0.375, 11, 0.8), detector);
        assertFalse(collision.intersects(POST, 0, 0, 0), "touching the edge is not support");
        collision.bind(new AABB(-0.2, 10, 0.2, 0.4, 11, 0.8), detector);
        assertTrue(collision.intersects(POST, 0, 0, 0));
        assertFalse(collision.intersects(Shapes.empty(), 0, 0, 0));
    }

    @Test
    void emptySpaceBetweenShapeComponentsDoesNotSupportThePlayer() {
        FootprintCollision collision = new FootprintCollision();
        collision.bind(new AABB(-3.7, 10, -2.7, -3.3, 11, -2.3), new GroundBelowDetector());
        VoxelShape sides = Shapes.or(Shapes.box(0, 0, 0, 0.2, 1, 1),
                Shapes.box(0.8, 0, 0, 1, 1, 1));
        assertFalse(collision.intersects(sides, -4, 0, -3));
        assertTrue(collision.intersects(POST, -4, 0, -3));
    }

    @Test
    void movingWithinAColumnInvalidatesCachedMissesUnderAnOverhang() {
        FootprintCollision collision = new FootprintCollision();
        GroundBelowDetector detector = new GroundBelowDetector();
        ColumnProbe probe = new ColumnProbe() {
            public boolean isLoaded(int x, int z) { return true; }
            public int surfaceTopY(int x, int z) { return 100; }
            public boolean collidesAt(int x, int y, int z) {
                return collision.intersects(y == 2 ? POST : Shapes.empty(), x, y, z);
            }
            public ColumnScan scan(int x, int z, int from, int through, int budget) {
                return TerrainCollisionScanner.scanColumn(this::collidesAt, x, z, from, through, budget);
            }
        };
        collision.bind(new AABB(-0.4, 10, 0.2, 0.2, 11, 0.8), detector);
        assertEquals(GroundSupport.UNSUPPORTED,
                detector.detect(probe, "test", 0, 9, 0, 0, 0, 0, 100));
        collision.bind(new AABB(-0.2, 10, 0.2, 0.4, 11, 0.8), detector);
        assertEquals(GroundSupport.SUPPORTED,
                detector.detect(probe, "test", 0, 9, 0, 0, 0, 0, 101));
        collision.bind(new AABB(-0.4, 10, 0.2, 0.2, 11, 0.8), detector);
        assertEquals(GroundSupport.UNSUPPORTED,
                detector.detect(probe, "test", 0, 9, 0, 0, 0, 0, 102));
    }
}
