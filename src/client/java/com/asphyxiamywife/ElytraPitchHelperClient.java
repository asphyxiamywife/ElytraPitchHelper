package com.asphyxiamywife.elytrapitchhelper;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class ElytraPitchHelperClient implements ClientModInitializer {
	private static Config CONFIG;
	private static KeyBinding toggleKeyBinding;

	@Override
	public void onInitializeClient() {
		CONFIG = Config.load();
		HudRenderCallback.EVENT.register(this::onHudRender);

		toggleKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.elytrapitchhelper.toggle",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN,
				"key.categories.elytrapitchhelper"));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKeyBinding != null && toggleKeyBinding.wasPressed()) {
				CONFIG.enabled = !CONFIG.enabled;
				CONFIG.save();
				if (client.player != null) {
					Text message = Text
							.translatable("message.elytrapitchhelper.toggle." + (CONFIG.enabled ? "on" : "off"));
					client.player.sendMessage(message, true);
				}
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

	private void onHudRender(DrawContext context, float tickDelta) {
		if (!CONFIG.enabled) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.player == null || mc.options.getPerspective() != Perspective.FIRST_PERSON
				|| mc.currentScreen != null) {
			return;
		}
		if (!hasUsableElytra(mc)) {
			return;
		}
		if (CONFIG.showOnlyWithFirework) {
			boolean hasFirework = mc.player.getMainHandStack().isOf(Items.FIREWORK_ROCKET)
					|| mc.player.getOffHandStack().isOf(Items.FIREWORK_ROCKET);
			if (!hasFirework) {
				return;
			}
		}
		if (!mc.player.isFallFlying()) {
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
