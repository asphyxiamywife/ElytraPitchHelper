package com.asphyxiamywife.elytrapitchhelper;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

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

	private void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.player == null || mc.options.getPerspective() != Perspective.FIRST_PERSON
				|| mc.currentScreen != null) {
			return;
		}
		if (!hasUsableElytra(mc)) {
			return;
		}
		if (mc.player.getGlidingTicks() <= 0) {
			return;
		}
		float pitch = mc.player.getPitch();
		int w = context.getScaledWindowWidth();
		int h = context.getScaledWindowHeight();
		int cx = w / 2;
		int cy = h / 2;

		float diffUp = CONFIG.targetUpMinecraft - pitch;
		float diffDown = CONFIG.targetDownMinecraft - pitch;

		int offsetUp = (int) Math
				.round(clamp(diffUp * CONFIG.offsetPerDegree, -CONFIG.maxOffsetPixels, CONFIG.maxOffsetPixels));
		int offsetDown = (int) Math
				.round(clamp(diffDown * CONFIG.offsetPerDegree, -CONFIG.maxOffsetPixels, CONFIG.maxOffsetPixels));

		float alphaUp = computeAlpha(Math.abs(CONFIG.targetUpMinecraft - pitch));
		float alphaDown = computeAlpha(Math.abs(CONFIG.targetDownMinecraft - pitch));

		if (alphaUp > 0.01f) {
			drawGuideLine(context, cx, cy + offsetUp, alphaUp);
		}
		if (alphaDown > 0.01f) {
			drawGuideLine(context, cx, cy + offsetDown, alphaDown);
		}
	}

	private boolean hasUsableElytra(MinecraftClient mc) {
		ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
		if (chest.isOf(Items.ELYTRA)) {
			return chest.getDamage() < chest.getMaxDamage() - 1;
		}
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("trinkets")) {
			ItemStack trinketElytra = TrinketsIntegration.getElytraItem(mc.player);
			if (!trinketElytra.isEmpty()) {
				return trinketElytra.getDamage() < trinketElytra.getMaxDamage() - 1;
			}
		}
		return false;
	}

	private float computeAlpha(float diff) {
		if (diff > CONFIG.toleranceDegrees)
			return 0.0f;
		return 0.15f + 0.85f * (1.0f - (diff / CONFIG.toleranceDegrees));
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	private void drawGuideLine(DrawContext ctx, int cx, int guideY, float alpha) {
		int length = 26;
		int thickness = 2;
		int x = cx - length / 2;
		int a = (int) (alpha * 255.0f) & 0xFF;
		int color = (a << 24) | (CONFIG.lineColorRgb & 0x00FFFFFF);
		ctx.fill(x, guideY, x + length, guideY + thickness, color);
		// small center tick
		ctx.fill(cx - 1, guideY - 3, cx + 1, guideY + 3, (a << 24) | (CONFIG.centerTickColorRgb & 0x00FFFFFF));
	}
}
