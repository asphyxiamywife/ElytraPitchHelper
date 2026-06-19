package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.VoidWarningSettings;
import com.asphyxiamywife.elytrapitchhelper.client.ClientConfigStore;
import com.asphyxiamywife.elytrapitchhelper.flight.ElytraDetector;
import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class PitchGuideHud {
    private static final float MIN_VISIBLE_ALPHA = 0.01f;

    private final ElytraDetector elytraDetector;
    private final AmplitudeTracker amplitudeTracker = new AmplitudeTracker();
    private final VoidProximityTracker voidProximityTracker = new VoidProximityTracker();
    private final GuideLineRenderer guideLineRenderer = new GuideLineRenderer();
    private final TerrainScanCache terrainScanCache = new TerrainScanCache();
    private ResourceKey<Level> cachedDimension;
    private String cachedDimensionKey;

    public PitchGuideHud(ElytraDetector elytraDetector) {
        this.elytraDetector = elytraDetector;
    }

    public void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        Config config = ClientConfigStore.get();
        if (!config.enabled) {
            amplitudeTracker.reset();
            voidProximityTracker.reset();
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            amplitudeTracker.reset();
            voidProximityTracker.reset();
            return;
        }
        if (minecraft.screen != null) {
            return;
        }
        if (!canRenderGuides(minecraft, config)) {
            amplitudeTracker.reset();
            voidProximityTracker.reset();
            return;
        }

        renderGuides(context, config, minecraft);
    }

    private void renderGuides(GuiGraphicsExtractor context, Config config, Minecraft minecraft) {
        Player player = minecraft.player;
        float pitch = player.getXRot();
        int centerX = context.guiWidth() / 2;
        int centerY = context.guiHeight() / 2;

        AmplitudeCue amplitudeCue = amplitudeTracker.update(config, player.getY(),
                player.getDeltaMovement().horizontalDistance());

        VoidProximity voidProximity = VoidProximity.NONE;
        if (config.voidWarning.enabled && minecraft.level != null) {
            String dimKey = dimensionKey(minecraft.level);
            Integer override = config.voidWarning.dimensionYOverrides.get(dimKey);
            int voidY = override != null ? override : 0;
            voidProximity = voidProximityTracker.update(config,
                    player.getY(), player.getDeltaMovement().y(), voidY);
            if (shouldClearVoidWarningForTerrain(voidProximityTracker, voidProximity)
                    && terrainScanCache.collisionBelow(dimKey,
                            (int) Math.floor(player.getX()), (int) Math.floor(player.getZ()), voidY,
                            System.currentTimeMillis(), () -> hasCollisionBlockBelow(player, minecraft.level, voidY))) {
                voidProximityTracker.reset();
                voidProximity = VoidProximity.NONE;
            }
        }

        boolean warnActive = voidProximity.isActive();
        boolean hideAscendingLine = hidesAscendingLineForVoidWarning(config, voidProximity);

        renderLineForTarget(context, config, centerX, centerY, pitch,
                config.pitch.targetDownMinecraft, AmplitudeLeg.DESCENDING,
                amplitudeCue, voidProximity, warnActive);

        if (!hideAscendingLine) {
            renderLineForTarget(context, config, centerX, centerY, pitch,
                    config.pitch.targetUpMinecraft, AmplitudeLeg.ASCENDING,
                    amplitudeCue, VoidProximity.NONE, false);
        }
    }

    static boolean hidesAscendingLineForVoidWarning(Config config, VoidProximity voidProximity) {
        return voidProximity.isActive()
                && config.voidWarning.mode == VoidWarningSettings.MODE_PREDICTED_TIME;
    }

    static boolean shouldClearVoidWarningForTerrain(VoidProximityTracker tracker, VoidProximity voidProximity) {
        return voidProximity.isActive() && tracker.isWarningRelevant();
    }

    private String dimensionKey(Level level) {
        ResourceKey<Level> dimension = level.dimension();
        if (!dimension.equals(cachedDimension)) {
            cachedDimension = dimension;
            cachedDimensionKey = dimension.identifier().toString();
        }
        return cachedDimensionKey;
    }

    private void renderLineForTarget(GuiGraphicsExtractor context, Config config, int centerX, int centerY,
            float pitch, float targetPitch, AmplitudeLeg leg, AmplitudeCue amplitudeCue,
            VoidProximity voidProximity, boolean forceShow) {
        float diff = targetPitch - pitch;
        int maxOffset = forceShow && config.voidWarning.maxOffsetOverride
                ? config.voidWarning.customMaxOffsetPixels
                : config.pitch.maxOffsetPixels;
        int offset = (int) Math.round(MathUtil.clamp(diff * config.pitch.offsetPerDegree,
                -maxOffset, maxOffset));
        float effectiveTolerance = forceShow && config.voidWarning.toleranceOverride
                ? config.voidWarning.customToleranceDegrees
                : config.pitch.toleranceDegrees;
        float alpha = computeAlpha(effectiveTolerance, Math.abs(diff));
        if (forceShow && !config.voidWarning.toleranceOverride) {
            alpha = Math.max(alpha, 0.6f);
        }
        if (alpha > MIN_VISIBLE_ALPHA) {
            guideLineRenderer.draw(context, centerX, centerY + offset, alpha,
                    amplitudeCue.forLeg(leg), voidProximity, config);
        }
    }

    private boolean canRenderGuides(Minecraft minecraft, Config config) {
        return HudVisibility.canRender(config, minecraft.options.getCameraType() == CameraType.FIRST_PERSON,
                elytraDetector.hasUsableElytra(minecraft.player, config.visibility.anyElytraGlide),
                hasFireworkRocket(minecraft.player),
                minecraft.player.getFallFlyingTicks());
    }

    private static boolean hasFireworkRocket(Player player) {
        return hasFireworkRocket(player.getMainHandItem(), player.getItemInHand(InteractionHand.OFF_HAND));
    }

    static boolean hasFireworkRocket(ItemStack mainHand, ItemStack offHand) {
        return mainHand.is(Items.FIREWORK_ROCKET) || offHand.is(Items.FIREWORK_ROCKET);
    }

    private static boolean hasCollisionBlockBelow(Player player, Level level, int voidY) {
        AABB box = player.getBoundingBox();
        int minX = (int) Math.floor(box.minX + 1.0E-7);
        int maxX = (int) Math.floor(box.maxX - 1.0E-7);
        int minZ = (int) Math.floor(box.minZ + 1.0E-7);
        int maxZ = (int) Math.floor(box.maxZ - 1.0E-7);
        int startY = (int) Math.floor(player.getY()) - 1;
        if (startY < voidY) {
            return false;
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = startY; y >= voidY; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.getCollisionShape(level, pos).isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static float computeAlpha(float toleranceDegrees, float diff) {
        if (diff > toleranceDegrees) {
            return 0.0f;
        }
        return 0.15f + 0.85f * (1.0f - (diff / toleranceDegrees));
    }
}
