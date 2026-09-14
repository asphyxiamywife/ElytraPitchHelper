package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import com.asphyxiamywife.elytrapitchhelper.client.ClientConfigStore;
import com.asphyxiamywife.elytrapitchhelper.flight.ElytraDetector;
import com.asphyxiamywife.elytrapitchhelper.flight.FlightLevelBounds;
import com.asphyxiamywife.elytrapitchhelper.flight.PlayerFlightSeam;
import com.asphyxiamywife.elytrapitchhelper.flight.PlayerFlightState;
import com.asphyxiamywife.elytrapitchhelper.flight.ViewMode;
import com.asphyxiamywife.elytrapitchhelper.flight.WorldFlightSeam;
import com.asphyxiamywife.elytrapitchhelper.flight.WorldFlightState;
import com.asphyxiamywife.elytrapitchhelper.util.MonotonicClock;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.phys.AABB;

import java.util.Objects;

public final class PitchGuideHud {
    private static final float MIN_VISIBLE_ALPHA = 0.01f;
    static final int FALLBACK_MIN_BUILD_Y = FlightLevelBounds.FALLBACK_MIN_BUILD_Y;
    static final int FALLBACK_MAX_BUILD_Y = FlightLevelBounds.FALLBACK_MAX_BUILD_Y;
    static final int DEFAULT_VOID_OFFSET_BELOW_MIN_Y = FlightLevelBounds.DEFAULT_VOID_OFFSET_BELOW_MIN_Y;

    private final PlayerFlightSeam flightAdapter;
    private final AmplitudeTracker amplitudeTracker = new AmplitudeTracker();
    private final VoidProximityTracker voidProximityTracker = new VoidProximityTracker();
    private final FlightDiagnostics flightDiagnostics = new FlightDiagnostics();
    private final GuideLineRenderer guideLineRenderer = new GuideLineRenderer();
    private final MotionGlyphRenderer motionGlyphRenderer = new MotionGlyphRenderer();
    private final WorldFlightSeam worldSeam = new WorldFlightSeam();
    private final GuiFiller guiFiller = new GuiFiller();
    private long cachedConfigRevision = Long.MIN_VALUE;
    private Config cachedConfig;
    private String trackedVoidDimensionKey;
    private volatile HudFrameState frameState = HudFrameState.NONE;

    public PitchGuideHud(ElytraDetector elytraDetector) {
        this.flightAdapter = new PlayerFlightSeam(elytraDetector);
    }

    public void tick(Minecraft minecraft) {
        ClientConfigStore.EffectiveSnapshot snapshot = ClientConfigStore.effectiveSnapshot();
        if (cachedConfig == null || cachedConfigRevision != snapshot.viewRevision()) {
            cachedConfig = snapshot.config();
            cachedConfigRevision = snapshot.viewRevision();
        }
        Config config = cachedConfig;
        flightDiagnostics.configure(config);
        if (!config.enabled) {
            clearFrame();
            return;
        }

        if (minecraft == null || minecraft.player == null) {
            clearFrame();
            return;
        }
        PlayerFlightState flight = flightAdapter.capturePlayer(minecraft.player);
        ViewMode viewMode = viewMode(minecraft.options.getCameraType());
        if (!HudVisibility.canRender(config, viewMode,
                flight.hasUsableElytra(config.visibility().anyElytraGlide()),
                flight.hasFireworkRocket(), flight.fallFlyingTicks())) {
            clearFrame();
            return;
        }

        WorldFlightState world = worldSeam.captureWorld(minecraft.player, flight);
        frameState = simulate(config, snapshot.hudRenderState(), flight, world);
    }

    public void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        HudFrameState current = frameState;
        if (!current.visible()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft == null ? null : minecraft.player;
        if (player == null || minecraft.screen != null) {
            return;
        }
        guiFiller.context = context;
        try {
            renderGuides(guiFiller, context.guiWidth(), context.guiHeight(), current,
                    player.getViewXRot(tickCounter.getGameTimeDeltaPartialTick(true)));
        } finally {
            guiFiller.context = null;
        }
    }

    public void invalidateGroundColumn(int blockX, int blockZ) {
        worldSeam.invalidateColumn(blockX, blockZ);
    }

    public void invalidateGroundChunk(int chunkX, int chunkZ) {
        worldSeam.invalidateChunk(chunkX, chunkZ);
    }

    private void clearFrame() {
        resetTrackers();
        frameState = HudFrameState.NONE;
    }

    private void resetTrackers() {
        amplitudeTracker.reset();
        resetVoidSimulation();
    }

    private void resetVoidSimulation() {
        voidProximityTracker.reset();
        worldSeam.reset();
        trackedVoidDimensionKey = null;
    }

    private HudFrameState simulate(Config config, HudRenderState renderState, PlayerFlightState flight,
            WorldFlightState world) {

        float pitch = flight.pitch();
        VoidProximity voidProximity = VoidProximity.NONE;
        if (config.voidWarning().enabled() && world.hasLevel()) {
            String dimKey = world.dimensionKey();
            Integer voidYOverride = config.voidWarning().dimensionYOverrides().get(dimKey);
            int minBuildY = world.minBuildY();
            int voidY = resolvedVoidY(voidYOverride, minBuildY);
            voidProximity = updateVoidProximity(config, dimKey,
                    flight.y(), flight.verticalSpeed(), voidY);
            if (shouldClearVoidWarningForTerrain(voidProximityTracker, voidProximity)) {
                AABB box = flight.boundingBox();
                int minX = (int) Math.floor(box.minX + 1.0E-7);
                int maxX = (int) Math.floor(box.maxX - 1.0E-7);
                int minZ = (int) Math.floor(box.minZ + 1.0E-7);
                int maxZ = (int) Math.floor(box.maxZ - 1.0E-7);
                int scanTopY = terrainScanTopY(world.maxBuildY(),
                        flight.y());
                int scanBottomY = terrainScanBottomY(minBuildY, voidYOverride);
                if (world.terrain().detect(scanBottomY, scanTopY, minX, maxX, minZ, maxZ,
                        MonotonicClock.millis()) == GroundSupport.SUPPORTED) {
                    voidProximityTracker.suppress();
                    voidProximity = VoidProximity.NONE;
                }
            }
        } else {
            resetVoidSimulation();
        }

        boolean forceShowDescending = voidProximity.warning > 0.01f
                || voidProximityTracker.pulseStrength().value() > 0.01f;
        AmplitudeCue amplitudeCue = amplitudeTracker.update(config, flight.y(),
                flight.horizontalSpeed(),
                isGuideVisible(config.activeProfile(), pitch, AmplitudeLeg.DESCENDING,
                        forceShowDescending),
                isGuideVisible(config.activeProfile(), pitch, AmplitudeLeg.ASCENDING, false));
        MotionGlyphCue motionGlyphCue = amplitudeTracker.motionGlyphCue();
        if (flightDiagnostics.enabled()) {
            flightDiagnostics.record(config, amplitudeCue, motionGlyphCue, flight.pitch(),
                    flight.verticalSpeed(), flight.y(),
                    flight.horizontalSpeed());
        }

        return new HudFrameState(config.activeProfile(), renderState, amplitudeCue, voidProximity,
                amplitudeTracker.flashStrength(), voidProximityTracker.pulseStrength(),
                motionGlyphCue);
    }

    void renderGuides(GuideLineRenderer.Filler filler, int guiWidth, int guiHeight,
            HudFrameState state, float pitch) {
        Profile profile = state.profile();
        int centerX = guiWidth / 2;
        int centerY = guiHeight / 2;
        AmplitudeCue amplitudeCue = state.amplitudeCue();
        VoidProximity voidProximity = state.voidProximity();
        float flash = state.flashNow();
        float voidPulse = state.voidPulseNow();
        boolean warnActive = voidProximity.warning > 0.01f || voidPulse > 0.01f;

        renderLineForTarget(filler, profile, state.renderState(), centerX, centerY, pitch,
                profile.pitch().targetDownMinecraft(),
                cueAmountForLeg(amplitudeCue, AmplitudeLeg.DESCENDING),
                flashForLeg(amplitudeCue, AmplitudeLeg.DESCENDING, flash),
                voidProximity.warning, voidPulse, warnActive);

        renderLineForTarget(filler, profile, state.renderState(), centerX, centerY, pitch,
                profile.pitch().targetUpMinecraft(),
                cueAmountForLeg(amplitudeCue, AmplitudeLeg.ASCENDING),
                flashForLeg(amplitudeCue, AmplitudeLeg.ASCENDING, flash),
                0.0f, 0.0f, false);

        if (profile.amplitude().motionGlyphsEnabled()) {
            motionGlyphRenderer.draw(filler, centerX, centerY, profile.line().lengthPixels(),
                    state.motionGlyph(), state.renderState().cue());
        }
    }

    static boolean shouldClearVoidWarningForTerrain(VoidProximityTracker tracker, VoidProximity voidProximity) {
        return voidProximity.isActive() && tracker.isWarningRelevant();
    }

    VoidProximity updateVoidProximity(
            Config config, String dimensionKey, double currentY, double rawVy, int voidY) {
        if (!Objects.equals(trackedVoidDimensionKey, dimensionKey)) {
            trackedVoidDimensionKey = dimensionKey;
            voidProximityTracker.reset();
            worldSeam.resetDetector();
        }
        return voidProximityTracker.update(config, currentY, rawVy, voidY);
    }

    static int resolvedVoidY(Integer override, int minBuildY) {
        return FlightLevelBounds.resolvedVoidY(override, minBuildY);
    }

    public static int defaultVoidY(int minBuildY) {
        return FlightLevelBounds.defaultVoidY(minBuildY);
    }

    static int terrainScanBottomY(int minBuildY, Integer voidYOverride) {
        return FlightLevelBounds.terrainScanBottomY(minBuildY, voidYOverride);
    }

    static int terrainScanTopY(int maxBuildY, double playerY) {
        return FlightLevelBounds.terrainScanTopY(maxBuildY, playerY);
    }

    static int minBuildY(LevelHeightAccessor level) {
        return FlightLevelBounds.minBuildY(level);
    }

    static int maxBuildY(LevelHeightAccessor level) {
        return FlightLevelBounds.maxBuildY(level);
    }

    private void renderLineForTarget(GuideLineRenderer.Filler filler, Profile profile,
            HudRenderState renderState,
            int centerX, int centerY, float pitch, float targetPitch, float cueAmount, float flash,
            float warning, float warningPulse, boolean forceShow) {
        float diff = targetPitch - pitch;
        int offset = GuidePolicy.offset(profile, pitch, targetPitch, forceShow);
        float effectiveTolerance = forceShow && profile.voidWarning().toleranceOverride()
                ? profile.voidWarning().customToleranceDegrees()
                : profile.pitch().toleranceDegrees();
        float alpha = computeAlpha(effectiveTolerance, Math.abs(diff));
        if (forceShow && !profile.voidWarning().toleranceOverride()) {
            alpha = Math.max(alpha, 0.6f);
        }
        if (alpha > MIN_VISIBLE_ALPHA) {
            guideLineRenderer.draw(filler, centerX, centerY + offset, alpha,
                    cueAmount, flash, warning, warningPulse, profile.line(), renderState);
        }
    }

    static float cueAmountForLeg(AmplitudeCue cue, AmplitudeLeg leg) {
        return GuidePolicy.cueAmountForLeg(cue, leg);
    }

    static float flashForLeg(AmplitudeCue cue, AmplitudeLeg leg, float currentFlash) {
        return GuidePolicy.flashForLeg(cue, leg, currentFlash);
    }

    static boolean isGuideVisible(Profile profile, float pitch, AmplitudeLeg leg) {
        return GuidePolicy.isGuideVisible(profile, pitch, leg);
    }

    static boolean isGuideVisible(Profile profile, float pitch, AmplitudeLeg leg, boolean forceShow) {
        return GuidePolicy.isGuideVisible(profile, pitch, leg, forceShow);
    }

    private static ViewMode viewMode(CameraType cameraType) {
        return switch (cameraType) {
            case FIRST_PERSON -> ViewMode.FIRST_PERSON;
            case THIRD_PERSON_BACK -> ViewMode.THIRD_PERSON_BACK;
            case THIRD_PERSON_FRONT -> ViewMode.THIRD_PERSON_FRONT;
        };
    }

    private static final class GuiFiller implements GuideLineRenderer.Filler {
        private GuiGraphicsExtractor context;

        @Override
        public void fill(int x, int y, int x2, int y2, int argb) {
            context.fill(x, y, x2, y2, argb);
        }
    }

    static float computeAlpha(float toleranceDegrees, float diff) {
        return GuidePolicy.computeAlpha(toleranceDegrees, diff);
    }
}
