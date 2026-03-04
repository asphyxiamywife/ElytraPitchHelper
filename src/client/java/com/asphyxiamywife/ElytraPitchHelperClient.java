package com.asphyxiamywife.elytrapitchhelper;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.CameraType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

public class ElytraPitchHelperClient implements ClientModInitializer {
	private static Config CONFIG;
	private static KeyMapping toggleKeyBinding;

	@Override
	public void onInitializeClient() {
		CONFIG = Config.load();
		HudRenderCallback.EVENT.register(this::onHudRender);

		toggleKeyBinding = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.elytrapitchhelper.toggle",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN,
				KeyMapping.Category.MISC));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKeyBinding.consumeClick()) {
				CONFIG.enabled = !CONFIG.enabled;
				CONFIG.save();
				// if (client.player != null) {
				//	Component message = Component
				//	        .translatable("message.elytrapitchhelper.toggle." + (CONFIG.enabled ? "on" : "off"));
				//	client.player.displayClientMessage(message, true);
				// }
			}
		});

		startConfigWatcher();
	}

	private void startConfigWatcher() {
		Thread t = new Thread(() -> {
			try (java.nio.file.WatchService watchService = java.nio.file.FileSystems.getDefault().newWatchService()) {
				java.nio.file.Path configPath = Config.getConfigPath();
				java.nio.file.Path parent = configPath.getParent();
				if (parent == null)
					return;

				if (!java.nio.file.Files.exists(parent)) {
					java.nio.file.Files.createDirectories(parent);
				}

				parent.register(watchService, java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY);

				while (true) {
					java.nio.file.WatchKey key = watchService.take();
					for (java.nio.file.WatchEvent<?> event : key.pollEvents()) {
						if (event.context() instanceof java.nio.file.Path && ((java.nio.file.Path) event.context())
								.getFileName().equals(configPath.getFileName())) {
							reloadConfig();
						}
					}
					if (!key.reset()) {
						break;
					}
				}
			} catch (java.io.IOException | InterruptedException e) {
			}
		}, "ElytraPitchHelper Config Watcher");
		t.setDaemon(true);
		t.start();
	}

	public static void reloadConfig() {
		CONFIG = Config.load();
	}

	public static Config getConfig() {
		return CONFIG;
	}

	private void onHudRender(GuiGraphics context, DeltaTracker tickCounter) {
		if (!CONFIG.enabled) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.player == null || mc.options.getCameraType() != CameraType.FIRST_PERSON
				|| mc.screen != null) {
			return;
		}
		if (!hasUsableElytra(mc)) {
			return;
		}
		if (CONFIG.showOnlyWithFirework) {
			boolean hasFirework = mc.player.getMainHandItem().is(Items.FIREWORK_ROCKET)
					|| mc.player.getItemInHand(net.minecraft.world.InteractionHand.OFF_HAND).is(Items.FIREWORK_ROCKET);
			if (!hasFirework) {
				return;
			}
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

	private boolean hasUsableElytra(Minecraft mc) {
		ItemStack chest = mc.player.getItemBySlot(EquipmentSlot.CHEST);
		if (!chest.is(Items.ELYTRA)) {
			return false;
		}
		return chest.getDamageValue() < chest.getMaxDamage() - 1;
	}

	private float computeAlpha(float diff) {
		if (diff > CONFIG.toleranceDegrees)
			return 0.0f;
		return 0.15f + 0.85f * (1.0f - (diff / CONFIG.toleranceDegrees));
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	private void drawGuideLine(GuiGraphics ctx, int cx, int guideY, float alpha) {
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
