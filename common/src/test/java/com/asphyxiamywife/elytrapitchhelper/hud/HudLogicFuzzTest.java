package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.ConfigTestFixtures;
import com.asphyxiamywife.elytrapitchhelper.config.VoidWarningSettings;
import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

import static com.asphyxiamywife.elytrapitchhelper.config.FuzzGenerators.validConfig;
import static com.asphyxiamywife.elytrapitchhelper.testing.RangeAssertions.assertUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HudLogicFuzzTest {
    @FuzzTest(maxDuration = "30s")
    void stateMachinesKeepOutputsBounded(FuzzedDataProvider data) {
        Config config = validConfig(data);
        fuzzAmplitudeTracker(data, config);
        fuzzVoidTracker(data, config);
        fuzzGroundBelow(data);
        fuzzVisibility(data, config);
    }

    private static void fuzzAmplitudeTracker(FuzzedDataProvider data, Config config) {
        AmplitudeTracker tracker = new AmplitudeTracker();
        double y = data.consumeRegularDouble(-1024.0, 1024.0);
        int steps = data.consumeInt(1, 64);
        for (int i = 0; i < steps; i++) {
            y += data.consumeRegularDouble(-64.0, 64.0);
            double speed = data.consumeRegularDouble(0.0, 32.0);
            ConfigTestFixtures.updateActiveProfile(config, p -> p.withAmplitude(p.amplitude().withEnabled(data.consumeBoolean())));
            AmplitudeCue cue = tracker.update(config, y, speed);
            assertCue(cue);
            assertFilteredCue(cue, AmplitudeLeg.ASCENDING);
            assertFilteredCue(cue, AmplitudeLeg.DESCENDING);
            if (!config.amplitude().enabled()) {
                assertSame(AmplitudeCue.NONE, cue);
            }
        }
    }

    private static void fuzzVoidTracker(FuzzedDataProvider data, Config config) {
        VoidProximityTracker tracker = new VoidProximityTracker();
        int steps = data.consumeInt(1, 64);
        for (int i = 0; i < steps; i++) {
            ConfigTestFixtures.updateActiveProfile(config, p -> p.withVoidWarning(p.voidWarning().withEnabled(data.consumeBoolean())));
            ConfigTestFixtures.updateActiveProfile(config, p -> p.withVoidWarning(
                    p.voidWarning().withMode(data.consumeInt(
                            VoidWarningSettings.MODE_PREDICTED_TIME,
                            VoidWarningSettings.MODE_SIMPLE_HEIGHT))));
            double y = data.consumeRegularDouble(-1024.0, 1024.0);
            double velocityY = data.consumeRegularDouble(-32.0, 32.0);
            int voidY = data.consumeInt(VoidWarningSettings.MIN_VOID_Y, VoidWarningSettings.MAX_VOID_Y);
            VoidProximity proximity = tracker.update(config, y, velocityY, voidY);
            assertUnit(proximity.warning);
            assertUnit(proximity.pulse);
            assertEquals(proximity != VoidProximity.NONE, proximity.isActive());
            assertFalse(proximity == VoidProximity.NONE
                    && PitchGuideHud.shouldClearVoidWarningForTerrain(tracker, proximity));
            if (!config.voidWarning().enabled()) {
                assertSame(VoidProximity.NONE, proximity);
            }
        }
    }

    private static final String[] DIMENSIONS = {
            "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end", ""};

    private static int driftCoordinate(FuzzedDataProvider data, int current) {
        return switch (data.consumeInt(0, 11)) {
            case 0 -> current + data.consumeInt(-16, 16);
            case 1 -> data.consumeInt(-30_000_000, 30_000_000);
            case 2 -> data.consumeBoolean() ? -30_000_000 : 30_000_000;
            default -> current;
        };
    }

    private static void fuzzGroundBelow(FuzzedDataProvider data) {
        SlabColumnWorld world = new SlabColumnWorld(data.consumeInt(), data.consumeInt(0, 3));
        int lookupBudget = data.consumeInt(1, 4_096);
        GroundBelowDetector detector = new GroundBelowDetector(lookupBudget);

        String dimension = DIMENSIONS[0];
        int minX = 0;
        int minZ = 0;
        int floorY = -64;
        int ceilingY = 64;
        long nowMillis = 0L;

        int steps = data.consumeInt(1, 64);
        for (int i = 0; i < steps; i++) {
            if (data.consumeBoolean()) {
                dimension = DIMENSIONS[data.consumeInt(0, DIMENSIONS.length - 1)];
            }
            minX = driftCoordinate(data, minX);
            minZ = driftCoordinate(data, minZ);
            int maxX = minX + data.consumeInt(0, 1);
            int maxZ = minZ + data.consumeInt(0, 1);
            if (data.consumeBoolean()) {
                floorY = data.consumeInt(VoidWarningSettings.MIN_VOID_Y, VoidWarningSettings.MAX_VOID_Y);
            }
            if (data.consumeBoolean()) {
                ceilingY = MathUtil.clamp(ceilingY + data.consumeInt(-64, 64),
                        VoidWarningSettings.MIN_VOID_Y, 1024);
            }
            nowMillis = Math.max(0L, nowMillis
                    + data.consumeLong(-2 * ColumnEvidenceCache.RESCAN_INTERVAL_MILLIS,
                            2 * ColumnEvidenceCache.RESCAN_INTERVAL_MILLIS));
            if (data.consumeBoolean()) {
                detector.reset();
            }

            world.beginTick();
            GroundSupport actual = detector.detect(world, dimension, floorY, ceilingY,
                    minX, maxX, minZ, maxZ, nowMillis);
            boolean truth = world.hasGround(floorY, ceilingY, minX, maxX, minZ, maxZ);

            assertNotNull(actual);
            if (actual == GroundSupport.SUPPORTED) {
                assertTrue(truth, "support was reported where the world has none");
            } else if (actual == GroundSupport.UNSUPPORTED) {
                assertFalse(truth, "ground the world does have was reported as absent");
            } else {
                assertTrue(world.tickScanLookups() == lookupBudget || world.sawUnloaded(),
                        "unknown is only for a scan the budget cut short or a chunk that is not there");
            }
        }
    }

    private static final class SlabColumnWorld implements ColumnProbe {
        private static final int NO_SURFACE = VoidWarningSettings.MIN_VOID_Y - 1;

        private final int seed;
        private final int loadedMask;
        private int tickScanLookups;
        private boolean sawUnloaded;

        SlabColumnWorld(int seed, int loadedMask) {
            this.seed = seed;
            this.loadedMask = loadedMask;
        }

        void beginTick() {
            tickScanLookups = 0;
            sawUnloaded = false;
        }

        int tickScanLookups() {
            return tickScanLookups;
        }

        boolean sawUnloaded() {
            return sawUnloaded;
        }

        boolean hasGround(int floorY, int ceilingY, int minX, int maxX, int minZ, int maxZ) {
            if (ceilingY < floorY) {
                return false;
            }
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!isLoaded(x, z)) {
                        continue;
                    }
                    long column = column(x, z);
                    if (hasSlab(column) && slabHi(column) >= floorY && slabLo(column) <= ceilingY) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public boolean isLoaded(int blockX, int blockZ) {
            boolean loaded = loadedMask == 0 || ((blockX * 13 + blockZ * 7) & loadedMask) != 0;
            sawUnloaded |= !loaded;
            return loaded;
        }

        @Override
        public int surfaceTopY(int blockX, int blockZ) {
            long column = column(blockX, blockZ);
            if (hasLayer(column)) {
                return layerHi(column);
            }
            return hasSlab(column) ? slabHi(column) : NO_SURFACE;
        }

        @Override
        public boolean collidesAt(int blockX, int blockY, int blockZ) {
            return collides(column(blockX, blockZ), blockY);
        }

        @Override
        public ColumnScan scan(int blockX, int blockZ, int fromY, int throughY, int budget) {
            long column = column(blockX, blockZ);
            return TerrainCollisionScanner.scanColumn((x, y, z) -> {
                tickScanLookups++;
                return collides(column, y);
            }, blockX, blockZ, fromY, throughY, budget);
        }

        private static boolean collides(long column, int y) {
            return hasSlab(column) && y >= slabLo(column) && y <= slabHi(column);
        }

        private long column(int blockX, int blockZ) {
            int h = blockX * 0x9E3779B9 + blockZ * 0x85EBCA6B + seed;
            h ^= h >>> 15;
            h *= 0x2545F491;
            return h ^ (h >>> 13);
        }

        private static boolean hasSlab(long column) {
            return (column & 1L) == 0L;
        }

        private static int slabLo(long column) {
            return VoidWarningSettings.MIN_VOID_Y + (int) ((column >>> 1) & 511L);
        }

        private static int slabHi(long column) {
            return slabLo(column) + (int) ((column >>> 10) & 15L);
        }

        private static boolean hasLayer(long column) {
            return (column & 0x4000L) == 0L;
        }

        private static int layerHi(long column) {
            int base = hasSlab(column) ? slabHi(column) : VoidWarningSettings.MIN_VOID_Y;
            return base + 1 + (int) ((column >>> 16) & 63L);
        }
    }

    private static void fuzzVisibility(FuzzedDataProvider data, Config config) {
        ConfigTestFixtures.updateActiveProfile(config, p -> p.withVisibility(p.visibility().withShowInThirdPerson(data.consumeBoolean())));
        ConfigTestFixtures.updateActiveProfile(config, p -> p.withVisibility(p.visibility().withShowOnlyWithFirework(data.consumeBoolean())));
        boolean firstPerson = data.consumeBoolean();
        boolean usableElytra = data.consumeBoolean();
        boolean firework = data.consumeBoolean();
        int flyingTicks = data.consumeInt();
        boolean expected = (config.visibility().showInThirdPerson() || firstPerson)
                && usableElytra
                && (!config.visibility().showOnlyWithFirework() || firework)
                && flyingTicks > 0;
        assertEquals(expected, HudVisibility.canRender(config, firstPerson, usableElytra, firework, flyingTicks));
    }

    private static void assertCue(AmplitudeCue cue) {
        assertNotNull(cue);
        assertNotNull(cue.leg);
        assertNotNull(cue.flashLeg);
        assertUnit(cue.amount);
    }

    private static void assertFilteredCue(AmplitudeCue cue, AmplitudeLeg expectedLeg) {
        float amount = PitchGuideHud.cueAmountForLeg(cue, expectedLeg);
        float flash = PitchGuideHud.flashForLeg(cue, expectedLeg, 0.5f);
        assertUnit(amount);
        assertUnit(flash);
        if (cue.leg != expectedLeg) {
            assertEquals(0.0f, amount);
        }
        if (cue.flashLeg != expectedLeg) {
            assertEquals(0.0f, flash);
        }
    }
}
