package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.VoidWarningSettings;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HudLogicFuzzTest {
    @FuzzTest(maxDuration = "30s")
    void stateMachinesKeepOutputsBounded(FuzzedDataProvider data) {
        Config config = validConfig(data);
        fuzzAmplitudeTracker(data, config);
        fuzzVoidTracker(data, config);
        fuzzTerrainCache(data);
        fuzzVisibility(data, config);
    }

    private static Config validConfig(FuzzedDataProvider data) {
        Config config = new Config();
        config.amplitude.enabled = true;
        config.amplitude.triggerMode = data.consumeInt(
                Config.AMPLITUDE_TRIGGER_HEIGHT, Config.AMPLITUDE_TRIGGER_EITHER);
        config.amplitude.downBlocks = data.consumeInt(1, 1024);
        config.amplitude.upBlocks = data.consumeInt(1, 1024);
        config.amplitude.toleranceBlocks = data.consumeInt(0, 128);
        config.amplitude.downVelocity = data.consumeRegularFloat(0.1f, 10.0f);
        config.amplitude.upVelocity = data.consumeRegularFloat(0.0f,
                Math.max(0.0f, config.amplitude.downVelocity - 0.1f));

        config.voidWarning.enabled = true;
        config.voidWarning.mode = data.consumeInt(
                VoidWarningSettings.MODE_PREDICTED_TIME, VoidWarningSettings.MODE_SIMPLE_HEIGHT);
        config.voidWarning.lookaheadSeconds = data.consumeRegularFloat(1.0f, 15.0f);
        config.voidWarning.simpleWarningBlocks = data.consumeInt(1, 512);
        return config;
    }

    private static void fuzzAmplitudeTracker(FuzzedDataProvider data, Config config) {
        AmplitudeTracker tracker = new AmplitudeTracker();
        double y = data.consumeRegularDouble(-1024.0, 1024.0);
        int steps = data.consumeInt(1, 64);
        for (int i = 0; i < steps; i++) {
            y += data.consumeRegularDouble(-64.0, 64.0);
            double speed = data.consumeRegularDouble(0.0, 32.0);
            config.amplitude.enabled = data.consumeBoolean();
            AmplitudeCue cue = tracker.update(config, y, speed);
            assertCue(cue);
            assertFilteredCue(cue.forLeg(AmplitudeLeg.ASCENDING), AmplitudeLeg.ASCENDING);
            assertFilteredCue(cue.forLeg(AmplitudeLeg.DESCENDING), AmplitudeLeg.DESCENDING);
            if (!config.amplitude.enabled) {
                assertSame(AmplitudeCue.NONE, cue);
            }
        }
    }

    private static void fuzzVoidTracker(FuzzedDataProvider data, Config config) {
        VoidProximityTracker tracker = new VoidProximityTracker();
        int steps = data.consumeInt(1, 64);
        for (int i = 0; i < steps; i++) {
            config.voidWarning.enabled = data.consumeBoolean();
            config.voidWarning.mode = data.consumeInt(
                    VoidWarningSettings.MODE_PREDICTED_TIME, VoidWarningSettings.MODE_SIMPLE_HEIGHT);
            double y = data.consumeRegularDouble(-1024.0, 1024.0);
            double velocityY = data.consumeRegularDouble(-32.0, 32.0);
            int voidY = data.consumeInt(VoidWarningSettings.MIN_VOID_Y, VoidWarningSettings.MAX_VOID_Y);
            VoidProximity proximity = tracker.update(config, y, velocityY, voidY);
            assertUnit(proximity.warning);
            assertUnit(proximity.pulse);
            assertEquals(proximity.warning > 0.01f || proximity.pulse > 0.01f, proximity.isActive());
            assertEquals(proximity.isActive()
                            && config.voidWarning.mode == VoidWarningSettings.MODE_PREDICTED_TIME,
                    PitchGuideHud.hidesAscendingLineForVoidWarning(config, proximity));
            assertEquals(proximity.isActive() && tracker.isWarningRelevant(),
                    PitchGuideHud.shouldClearVoidWarningForTerrain(tracker, proximity));
            if (!config.voidWarning.enabled) {
                assertSame(VoidProximity.NONE, proximity);
            }
        }
    }

    private static void fuzzTerrainCache(FuzzedDataProvider data) {
        TerrainScanCache cache = new TerrainScanCache();
        String lastDimension = null;
        int lastX = 0;
        int lastZ = 0;
        int lastVoidY = 0;
        long lastScanMillis = 0L;
        boolean lastResult = false;
        boolean hasResult = false;
        int[] scanCount = {0};

        int steps = data.consumeInt(1, 64);
        for (int i = 0; i < steps; i++) {
            String dimension = data.consumeString(32);
            int x = data.consumeInt(-30_000_000, 30_000_000);
            int z = data.consumeInt(-30_000_000, 30_000_000);
            int voidY = data.consumeInt(VoidWarningSettings.MIN_VOID_Y, VoidWarningSettings.MAX_VOID_Y);
            long nowMillis = data.consumeLong(0L, 1_000_000L);
            boolean scannedResult = data.consumeBoolean();
            boolean needsScan = !hasResult || !Objects.equals(lastDimension, dimension)
                    || lastX != x || lastZ != z || lastVoidY != voidY
                    || nowMillis < lastScanMillis
                    || nowMillis - lastScanMillis >= TerrainScanCache.RESCAN_INTERVAL_MILLIS;
            int beforeScans = scanCount[0];

            boolean actual = cache.collisionBelow(dimension, x, z, voidY, nowMillis, () -> {
                scanCount[0]++;
                return scannedResult;
            });

            assertEquals(beforeScans + (needsScan ? 1 : 0), scanCount[0]);
            if (needsScan) {
                lastDimension = dimension;
                lastX = x;
                lastZ = z;
                lastVoidY = voidY;
                lastScanMillis = nowMillis;
                lastResult = scannedResult;
                hasResult = true;
            }
            assertEquals(lastResult, actual);
        }
    }

    private static void fuzzVisibility(FuzzedDataProvider data, Config config) {
        config.visibility.showInThirdPerson = data.consumeBoolean();
        config.visibility.showOnlyWithFirework = data.consumeBoolean();
        boolean firstPerson = data.consumeBoolean();
        boolean usableElytra = data.consumeBoolean();
        boolean firework = data.consumeBoolean();
        int flyingTicks = data.consumeInt();
        boolean expected = (config.visibility.showInThirdPerson || firstPerson)
                && usableElytra
                && (!config.visibility.showOnlyWithFirework || firework)
                && flyingTicks > 0;
        assertEquals(expected, HudVisibility.canRender(config, firstPerson, usableElytra, firework, flyingTicks));
    }

    private static void assertCue(AmplitudeCue cue) {
        assertNotNull(cue);
        assertNotNull(cue.leg);
        assertNotNull(cue.flashLeg);
        assertUnit(cue.amount);
        assertUnit(cue.flash);
    }

    private static void assertFilteredCue(AmplitudeCue cue, AmplitudeLeg expectedLeg) {
        assertCue(cue);
        if (cue != AmplitudeCue.NONE) {
            assertEquals(expectedLeg, cue.leg);
            assertEquals(expectedLeg, cue.flashLeg);
        }
    }

    private static void assertUnit(float value) {
        assertTrue(Float.isFinite(value) && value >= 0.0f && value <= 1.0f,
                () -> value + " is outside [0, 1]");
    }
}
