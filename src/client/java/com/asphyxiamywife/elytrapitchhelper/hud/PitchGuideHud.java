package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.client.ClientConfigStore;
import com.asphyxiamywife.elytrapitchhelper.flight.ElytraDetector;
import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

public final class PitchGuideHud {
    private static final float MIN_VISIBLE_ALPHA = 0.01f;

    private final ElytraDetector elytraDetector;
    private final AmplitudeTracker amplitudeTracker = new AmplitudeTracker();
    private final GuideLineRenderer guideLineRenderer = new GuideLineRenderer();

    public PitchGuideHud(ElytraDetector elytraDetector) {
        this.elytraDetector = elytraDetector;
    }

    public void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        Config config = ClientConfigStore.get();
        if (!config.enabled) {
            amplitudeTracker.reset();
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            amplitudeTracker.reset();
            return;
        }
        if (minecraft.gui.screen() != null) {
            return;
        }
        if (!canRenderGuides(minecraft, config)) {
            amplitudeTracker.reset();
            return;
        }

        renderGuides(context, config, minecraft.player);
    }

    private void renderGuides(GuiGraphicsExtractor context, Config config, Player player) {
        float pitch = player.getXRot();
        int centerX = context.guiWidth() / 2;
        int centerY = context.guiHeight() / 2;
        AmplitudeCue amplitudeCue = amplitudeTracker.update(config, player.getY(),
                player.getDeltaMovement().horizontalDistance());

        renderLineForTarget(context, config, centerX, centerY, pitch, config.targetUpMinecraft, AmplitudeLeg.ASCENDING,
                amplitudeCue);
        renderLineForTarget(context, config, centerX, centerY, pitch, config.targetDownMinecraft,
                AmplitudeLeg.DESCENDING, amplitudeCue);
    }

    private void renderLineForTarget(GuiGraphicsExtractor context, Config config, int centerX, int centerY, float pitch,
            float targetPitch, AmplitudeLeg leg, AmplitudeCue amplitudeCue) {
        float diff = targetPitch - pitch;
        int offset = (int) Math.round(MathUtil.clamp(diff * config.offsetPerDegree, -config.maxOffsetPixels,
                config.maxOffsetPixels));
        float alpha = computeAlpha(config, Math.abs(diff));
        if (alpha > MIN_VISIBLE_ALPHA) {
            guideLineRenderer.draw(context, centerX, centerY + offset, alpha, amplitudeCue.forLeg(leg), config);
        }
    }

    private boolean canRenderGuides(Minecraft minecraft, Config config) {
        if (!config.showInThirdPerson && minecraft.options.getCameraType() != CameraType.FIRST_PERSON) {
            return false;
        }
        if (!elytraDetector.hasUsableElytra(minecraft.player)) {
            return false;
        }
        if (config.showOnlyWithFirework && !hasFireworkRocket(minecraft.player)) {
            return false;
        }
        return minecraft.player.getFallFlyingTicks() > 0;
    }

    private static boolean hasFireworkRocket(Player player) {
        return player.getMainHandItem().is(Items.FIREWORK_ROCKET)
                || player.getItemInHand(InteractionHand.OFF_HAND).is(Items.FIREWORK_ROCKET);
    }

    private static float computeAlpha(Config config, float diff) {
        if (diff > config.toleranceDegrees) {
            return 0.0f;
        }
        return 0.15f + 0.85f * (1.0f - (diff / config.toleranceDegrees));
    }
}
