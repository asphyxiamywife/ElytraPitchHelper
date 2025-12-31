package com.asphyxiamywife.elytrapitchhelper;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ElytraPitchHelperClient implements ClientModInitializer {
	private static Config CONFIG;

	@Override
	public void onInitializeClient() {
		CONFIG = Config.load();
		HudRenderCallback.EVENT.register(this::onHudRender);
	}

	public static void reloadConfig() {
		CONFIG = Config.load();
	}

	public static Config getConfig() {
		return CONFIG;
	}

	private void onHudRender(GuiGraphics context, DeltaTracker tickCounter) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.player == null || mc.options.getCameraType() != CameraType.FIRST_PERSON || mc.screen != null) {
			return;
		}
		if (!hasUsableElytra(mc)) {
			return;
		}
		if (mc.player.getFallFlyingTicks() <= 0) {
			return;
		}
		float pitch = mc.player.getXRot();
		int w = context.guiWidth();
		int h = context.guiHeight();
		int cx = w / 2;
		int cy = h / 2;

		float diffUp = CONFIG.targetUpMinecraft - pitch;
		float diffDown = CONFIG.targetDownMinecraft - pitch;

		int offsetUp = (int) Math.round(clamp(diffUp * CONFIG.offsetPerDegree, -CONFIG.maxOffsetPixels, CONFIG.maxOffsetPixels));
		int offsetDown = (int) Math.round(clamp(diffDown * CONFIG.offsetPerDegree, -CONFIG.maxOffsetPixels, CONFIG.maxOffsetPixels));

		float alphaUp = computeAlpha(Math.abs(CONFIG.targetUpMinecraft - pitch));
		float alphaDown = computeAlpha(Math.abs(CONFIG.targetDownMinecraft - pitch));

		if (alphaUp > 0.01f) {
			drawGuideLine(context, cx, cy + offsetUp, alphaUp);
		}
		if (alphaDown > 0.01f) {
			drawGuideLine(context, cx, cy + offsetDown, alphaDown);
		}
	}

	private boolean hasUsableElytra(Minecraft mc) {
		ItemStack chest = mc.player.getItemBySlot(EquipmentSlot.CHEST);
		if (!chest.is(Items.ELYTRA)) {
			return false;
		}
		return chest.getDamageValue() < chest.getMaxDamage() - 1;
	}

	private float computeAlpha(float diff) {
		if (diff > CONFIG.toleranceDegrees) return 0.0f;
		return 0.15f + 0.85f * (1.0f - (diff / CONFIG.toleranceDegrees));
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	private void drawGuideLine(GuiGraphics ctx, int cx, int guideY, float alpha) {
		int length = 26;
		int thickness = 2;
		int x = cx - length / 2;
		int a = (int)(alpha * 255.0f) & 0xFF;
		int color = (a << 24) | (CONFIG.lineColorRgb & 0x00FFFFFF);
		ctx.fill(x, guideY, x + length, guideY + thickness, color);
		// small center tick
		ctx.fill(cx - 1, guideY - 3, cx + 1, guideY + 3, (a << 24) | (CONFIG.centerTickColorRgb & 0x00FFFFFF));
	}
}
