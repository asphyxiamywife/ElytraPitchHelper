package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.ConfigTestFixtures;
import com.asphyxiamywife.elytrapitchhelper.config.VoidWarningSettings;
import com.asphyxiamywife.elytrapitchhelper.flight.ElytraDetector;
import net.minecraft.world.level.LevelHeightAccessor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PitchGuideHudTest {
    @Test
    void zeroToleranceOnlyShowsAnExactMatch() {
        assertEquals(1.0f, PitchGuideHud.computeAlpha(0.0f, 0.0f));
        assertEquals(0.0f, PitchGuideHud.computeAlpha(0.0f, 0.1f));
    }

    @Test
    void glyphStartVisibilityMatchesThePitchGuideTolerance() {
        Config config = new Config();
        float diveTarget = config.pitch().targetDownMinecraft();

        assertTrue(PitchGuideHud.isGuideVisible(config.activeProfile(), diveTarget,
                AmplitudeLeg.DESCENDING));
        assertTrue(!PitchGuideHud.isGuideVisible(config.activeProfile(),
                diveTarget + config.pitch().toleranceDegrees() + 0.1f,
                AmplitudeLeg.DESCENDING));
    }

    @Test
    void voidWarningForceShowMakesDescendingGuideVisibleToGlyphGate() {
        Config config = new Config();
        float farOutsideTolerance = config.pitch().targetDownMinecraft()
                + config.pitch().toleranceDegrees() + 30.0f;

        assertTrue(!PitchGuideHud.isGuideVisible(config.activeProfile(), farOutsideTolerance,
                AmplitudeLeg.DESCENDING));
        assertTrue(PitchGuideHud.isGuideVisible(config.activeProfile(), farOutsideTolerance,
                AmplitudeLeg.DESCENDING, true));
    }

    @Test
    void voidWarningToleranceOverrideControlsGlyphGate() {
        Config config = new Config();
        ConfigTestFixtures.updateActiveProfile(config, profile -> profile.withVoidWarning(
                profile.voidWarning()
                        .withToleranceOverride(true)
                        .withCustomToleranceDegrees(5.0f)));
        float diveTarget = config.pitch().targetDownMinecraft();

        assertTrue(PitchGuideHud.isGuideVisible(config.activeProfile(), diveTarget + 4.0f,
                AmplitudeLeg.DESCENDING, true));
        assertTrue(!PitchGuideHud.isGuideVisible(config.activeProfile(), diveTarget + 6.0f,
                AmplitudeLeg.DESCENDING, true));
    }

    @Test
    void simpleVoidWarningUsesTerrainClearCheckWhenRelevant() {
        Config config = new Config();
        ConfigTestFixtures.updateActiveProfile(config, p -> p.withVoidWarning(p.voidWarning().withMode(VoidWarningSettings.MODE_SIMPLE_HEIGHT)));
        ConfigTestFixtures.updateActiveProfile(config, p -> p.withVoidWarning(p.voidWarning().withSimpleWarningBlocks(20)));
        VoidProximityTracker tracker = new VoidProximityTracker();
        VoidProximity proximity = tracker.update(config, 5.0, 0.0, 0);

        assertTrue(PitchGuideHud.shouldClearVoidWarningForTerrain(tracker, proximity));
    }

    @Test
    void changingDimensionClearsVelocityAndWarningHistory() {
        Config config = new Config();
        PitchGuideHud hud = new PitchGuideHud(new ElytraDetector());

        assertSame(VoidProximity.NONE,
                hud.updateVoidProximity(config, "minecraft:overworld", 100.0, -1.0, 0));
        assertTrue(hud.updateVoidProximity(
                config, "minecraft:overworld", 80.0, -1.0, 0).isActive());

        assertSame(VoidProximity.NONE,
                hud.updateVoidProximity(config, "minecraft:the_nether", 80.0, -1.0, 0));
    }

    @Test
    void defaultVoidYTracksMinBuildHeightWithOffset() {
        assertEquals(-112, PitchGuideHud.defaultVoidY(-64));
        assertEquals(-48, PitchGuideHud.defaultVoidY(0));
    }

    @Test
    void dimensionOverrideWinsOverDetectedMinBuildHeight() {
        assertEquals(12, PitchGuideHud.resolvedVoidY(12, -64));
    }

    @Test
    void terrainScanStartsAtMinBuildHeightForAutoVoidFloor() {
        assertEquals(-64, PitchGuideHud.terrainScanBottomY(-64, null));
        assertEquals(0, PitchGuideHud.terrainScanBottomY(0, null));
    }

    @Test
    void terrainScanStartsAtCustomVoidFloorWhenConfigured() {
        assertEquals(-64, PitchGuideHud.terrainScanBottomY(-64, -112));
        assertEquals(12, PitchGuideHud.terrainScanBottomY(-64, 12));
    }

    @Test
    void terrainScanStopsAtPlayerWhenPlayerIsInsideBuildHeight() {
        assertEquals(70, PitchGuideHud.terrainScanTopY(319, 70));
    }

    @Test
    void terrainScanStopsAtMaxBuildHeightWhenPlayerIsAboveBuildHeight() {
        assertEquals(319, PitchGuideHud.terrainScanTopY(319, 700));
    }

    @Test
    void worldHeightHelpersUseAccessorValues() {
        LevelHeightAccessor levelHeight = LevelHeightAccessor.create(-64, 384);

        assertEquals(-64, PitchGuideHud.minBuildY(levelHeight));
        assertEquals(319, PitchGuideHud.maxBuildY(levelHeight));
    }

    @Test
    void worldHeightHelpersFallbackWhenAccessorsAreUnavailable() {
        LevelHeightAccessor unavailableHeight = new LevelHeightAccessor() {
            @Override
            public int getHeight() {
                throw new NoSuchMethodError("height unavailable");
            }

            @Override
            public int getMinY() {
                throw new NoSuchMethodError("min y unavailable");
            }
        };

        assertEquals(PitchGuideHud.FALLBACK_MIN_BUILD_Y, PitchGuideHud.minBuildY(unavailableHeight));
        assertEquals(PitchGuideHud.FALLBACK_MAX_BUILD_Y, PitchGuideHud.maxBuildY(unavailableHeight));
    }

    @Test
    void maxBuildHeightFallsBackToHeightWhenMaxYIsUnavailable() {
        LevelHeightAccessor unavailableMaxY = new LevelHeightAccessor() {
            @Override
            public int getHeight() {
                return 384;
            }

            @Override
            public int getMinY() {
                return -64;
            }

            @Override
            public int getMaxY() {
                throw new NoSuchMethodError("max y unavailable");
            }
        };

        assertEquals(319, PitchGuideHud.maxBuildY(unavailableMaxY));
    }

    @Test
    void wideVoidWarningToleranceCanReachOppositeDefaultPitchEnd() {
        Config config = new Config();
        ConfigTestFixtures.updateActiveProfile(config, p -> p.withVoidWarning(p.voidWarning().withCustomToleranceDegrees(130.0f)));
        float diffFromDefaultDiveTarget = Math.abs(config.pitch().targetDownMinecraft() - -85.0f);

        assertTrue(PitchGuideHud.computeAlpha(config.voidWarning().customToleranceDegrees(),
                diffFromDefaultDiveTarget) > 0.0f);
    }
}
