package com.asphyxiamywife.elytrapitchhelper;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;

public class ElytraPitchHelperClient implements ClientModInitializer {
	// In Minecraft, pitch increases when looking down and is negative when looking up.
	// To feel like an aircraft ladder where positive is "nose up", we set explicit
	// targets in Minecraft's coordinate system: up = -40, down = +40.
	private static final float TARGET_UP_MINECRAFT = -40.0f;
	private static final float TARGET_DOWN_MINECRAFT = 40.0f;
	private static final float TOLERANCE = 6.0f;
	private static final int MAX_OFFSET_PIXELS = 42;
	private static final float OFFSET_PER_DEGREE = 2.0f;

	@Override
	public void onInitializeClient() {
		HudRenderCallback.EVENT.register(this::onHudRender);
	}

	private void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.player == null || mc.options.getPerspective() != Perspective.FIRST_PERSON || mc.currentScreen != null) {
			return;
		}
		if (!(mc.player.isFallFlying() && hasUsableElytra(mc))) {
			return;
		}
		float tickDelta = tickCounter.getTickDelta(false);
		float pitch = mc.player.getPitch(tickDelta);
		int w = context.getScaledWindowWidth();
		int h = context.getScaledWindowHeight();
		int cx = w / 2;
		int cy = h / 2;

		// Compute offsets using Minecraft's pitch convention (down is positive).
		// We want the lines to move like the horizon ladder: when you look up (pitch decreases),
		// the ladder moves down on screen (positive screen Y). So we use: offset = k * (target - pitch).
		float diffUp = TARGET_UP_MINECRAFT - pitch;      // target -40 (nose up)
		float diffDown = TARGET_DOWN_MINECRAFT - pitch;  // target +40 (nose down)

		int offsetUp = (int) Math.round(clamp(diffUp * OFFSET_PER_DEGREE, -MAX_OFFSET_PIXELS, MAX_OFFSET_PIXELS));
		int offsetDown = (int) Math.round(clamp(diffDown * OFFSET_PER_DEGREE, -MAX_OFFSET_PIXELS, MAX_OFFSET_PIXELS));

		float alphaUp = computeAlpha(Math.abs(TARGET_UP_MINECRAFT - pitch));
		float alphaDown = computeAlpha(Math.abs(TARGET_DOWN_MINECRAFT - pitch));

		if (alphaUp > 0.01f) {
			drawGuideLine(context, cx, cy + offsetUp, alphaUp);
		}
		if (alphaDown > 0.01f) {
			drawGuideLine(context, cx, cy + offsetDown, alphaDown);
		}
	}

	private boolean hasUsableElytra(MinecraftClient mc) {
		ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
		return chest.getItem() instanceof ElytraItem && ElytraItem.isUsable(chest);
	}

	private float computeAlpha(float diff) {
		if (diff > TOLERANCE) return 0.0f;
		return 0.15f + 0.85f * (1.0f - (diff / TOLERANCE));
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	private void drawGuideLine(DrawContext ctx, int cx, int guideY, float alpha) {
		int length = 26;
		int thickness = 2;
		int x = cx - length / 2;
		int a = (int)(alpha * 255.0f) & 0xFF;
		int color = (a << 24) | 0x00FFFFFF;
		ctx.fill(x, guideY, x + length, guideY + thickness, color);
		// small center tick
		ctx.fill(cx - 1, guideY - 3, cx + 1, guideY + 3, (a << 24) | 0x00FF80FF);
	}
}
