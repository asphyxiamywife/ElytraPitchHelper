package com.asphyxiamywife.elytrapitchhelper;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> new SimpleConfigScreen(parent);
	}

	static class SimpleConfigScreen extends Screen {
		private final Screen parent;

		private TextFieldWidget upField;
		private TextFieldWidget downField;
		private TextFieldWidget tolField;
		private TextFieldWidget maxPixField;
		private TextFieldWidget opdField;
		private TextFieldWidget lineColorField;
		private TextFieldWidget tickColorField;

		private IntSlider lineR;
		private IntSlider lineG;
		private IntSlider lineB;
		private IntSlider tickR;
		private IntSlider tickG;
		private IntSlider tickB;

		private String statusMessage = "";
		private long statusUntilMs = 0L;

		protected SimpleConfigScreen(Screen parent) {
			super(Text.of("Elytra Pitch Helper Config"));
			this.parent = parent;
		}

		@Override
		protected void init() {
			int centerX = this.width / 2;
			int y = this.height / 6;

			Config cfg = ElytraPitchHelperClient.getConfig();

			int fieldWidth = 240;
			int leftX = centerX - fieldWidth / 2;

			upField = new TextFieldWidget(this.textRenderer, leftX, y, fieldWidth, 20, Text.of("Up (-deg)"));
			upField.setText(Float.toString(cfg.targetUpMinecraft));
			this.addDrawableChild(upField);
			y += 24;

			downField = new TextFieldWidget(this.textRenderer, leftX, y, fieldWidth, 20, Text.of("Down (+deg)"));
			downField.setText(Float.toString(cfg.targetDownMinecraft));
			this.addDrawableChild(downField);
			y += 24;

			tolField = new TextFieldWidget(this.textRenderer, leftX, y, fieldWidth, 20, Text.of("Tolerance (deg)"));
			tolField.setText(Float.toString(cfg.toleranceDegrees));
			this.addDrawableChild(tolField);
			y += 24;

			maxPixField = new TextFieldWidget(this.textRenderer, leftX, y, fieldWidth, 20, Text.of("Max offset (px)"));
			maxPixField.setText(Integer.toString(cfg.maxOffsetPixels));
			this.addDrawableChild(maxPixField);
			y += 24;

			opdField = new TextFieldWidget(this.textRenderer, leftX, y, fieldWidth, 20, Text.of("Offset per degree"));
			opdField.setText(Float.toString(cfg.offsetPerDegree));
			this.addDrawableChild(opdField);
			y += 28;

			lineColorField = new TextFieldWidget(this.textRenderer, leftX, y, fieldWidth, 20, Text.of("Line RGB (hex)"));
			lineColorField.setText(String.format("%06X", cfg.lineColorRgb & 0xFFFFFF));
			this.addDrawableChild(lineColorField);
			y += 22;

			int lc = cfg.lineColorRgb;
			int lr = (lc >> 16) & 0xFF, lg = (lc >> 8) & 0xFF, lb = lc & 0xFF;
			int sliderWidth = (fieldWidth - 12) / 3;
			lineR = addDrawableChild(new IntSlider(leftX, y, sliderWidth, 20, Text.of("R"), lr, v -> onLineSliderChanged()));
			lineG = addDrawableChild(new IntSlider(leftX + sliderWidth + 6, y, sliderWidth, 20, Text.of("G"), lg, v -> onLineSliderChanged()));
			lineB = addDrawableChild(new IntSlider(leftX + (sliderWidth + 6) * 2, y, sliderWidth, 20, Text.of("B"), lb, v -> onLineSliderChanged()));
			y += 36;

			tickColorField = new TextFieldWidget(this.textRenderer, leftX, y, fieldWidth, 20, Text.of("Tick RGB (hex)"));
			tickColorField.setText(String.format("%06X", cfg.centerTickColorRgb & 0xFFFFFF));
			this.addDrawableChild(tickColorField);
			y += 22;

			int tc = cfg.centerTickColorRgb;
			int tr = (tc >> 16) & 0xFF, tg = (tc >> 8) & 0xFF, tb = tc & 0xFF;
			tickR = addDrawableChild(new IntSlider(leftX, y, sliderWidth, 20, Text.of("R"), tr, v -> onTickSliderChanged()));
			tickG = addDrawableChild(new IntSlider(leftX + sliderWidth + 6, y, sliderWidth, 20, Text.of("G"), tg, v -> onTickSliderChanged()));
			tickB = addDrawableChild(new IntSlider(leftX + (sliderWidth + 6) * 2, y, sliderWidth, 20, Text.of("B"), tb, v -> onTickSliderChanged()));
			y += 40;

			this.addDrawableChild(ButtonWidget.builder(Text.of("Apply & Save"), b -> {
				if (applyFromFields()) {
					ElytraPitchHelperClient.getConfig().save();
					showStatus("Saved", 2000);
				} else {
					showStatus("Invalid values", 2000);
				}
			}).dimensions(centerX - 100, y, 200, 20).build());

			y += 24;
			this.addDrawableChild(ButtonWidget.builder(Text.of("Reload Config"), b -> {
				ElytraPitchHelperClient.reloadConfig();
				Config nc = ElytraPitchHelperClient.getConfig();
				upField.setText(Float.toString(nc.targetUpMinecraft));
				downField.setText(Float.toString(nc.targetDownMinecraft));
				tolField.setText(Float.toString(nc.toleranceDegrees));
				maxPixField.setText(Integer.toString(nc.maxOffsetPixels));
				opdField.setText(Float.toString(nc.offsetPerDegree));
				lineColorField.setText(String.format("%06X", nc.lineColorRgb & 0xFFFFFF));
				tickColorField.setText(String.format("%06X", nc.centerTickColorRgb & 0xFFFFFF));
				setLineSlidersFromColor(nc.lineColorRgb);
				setTickSlidersFromColor(nc.centerTickColorRgb);
				showStatus("Reloaded", 1500);
			}).dimensions(centerX - 100, y, 200, 20).build());

			y += 24;
			this.addDrawableChild(ButtonWidget.builder(Text.of("Reset to Defaults"), b -> {
				Config d = new Config();
				upField.setText(Float.toString(d.targetUpMinecraft));
				downField.setText(Float.toString(d.targetDownMinecraft));
				tolField.setText(Float.toString(d.toleranceDegrees));
				maxPixField.setText(Integer.toString(d.maxOffsetPixels));
				opdField.setText(Float.toString(d.offsetPerDegree));
				lineColorField.setText(String.format("%06X", d.lineColorRgb & 0xFFFFFF));
				tickColorField.setText(String.format("%06X", d.centerTickColorRgb & 0xFFFFFF));
				setLineSlidersFromColor(d.lineColorRgb);
				setTickSlidersFromColor(d.centerTickColorRgb);
				if (applyFromFields()) {
					ElytraPitchHelperClient.getConfig().save();
				}
				showStatus("Defaults restored", 2000);
			}).dimensions(centerX - 100, y, 200, 20).build());

			y += 24;
			this.addDrawableChild(ButtonWidget.builder(Text.of("Done"), b -> {
				MinecraftClient.getInstance().setScreen(parent);
			}).dimensions(centerX - 100, y, 200, 20).build());
		}

		@Override
		public void close() {
			MinecraftClient.getInstance().setScreen(parent);
		}

		private boolean applyFromFields() {
			try {
				Config cfg = ElytraPitchHelperClient.getConfig();
				cfg.targetUpMinecraft = Float.parseFloat(upField.getText().trim());
				cfg.targetDownMinecraft = Float.parseFloat(downField.getText().trim());
				cfg.toleranceDegrees = Float.parseFloat(tolField.getText().trim());
				cfg.maxOffsetPixels = Integer.parseInt(maxPixField.getText().trim());
				cfg.offsetPerDegree = Float.parseFloat(opdField.getText().trim());
				int line = getLineColorFromSliders();
				int tick = getTickColorFromSliders();
				if (line < 0) line = (int)Long.parseLong(lineColorField.getText().trim(), 16);
				if (tick < 0) tick = (int)Long.parseLong(tickColorField.getText().trim(), 16);
				cfg.lineColorRgb = line & 0xFFFFFF;
				cfg.centerTickColorRgb = tick & 0xFFFFFF;
				return true;
			} catch (Exception ignored) {
				return false;
			}
		}

		private void setLineSlidersFromColor(int color) {
			int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
			if (lineR != null) lineR.setValue(r);
			if (lineG != null) lineG.setValue(g);
			if (lineB != null) lineB.setValue(b);
		}

		private void setTickSlidersFromColor(int color) {
			int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
			if (tickR != null) tickR.setValue(r);
			if (tickG != null) tickG.setValue(g);
			if (tickB != null) tickB.setValue(b);
		}

		private int getLineColorFromSliders() {
			if (lineR == null) return -1;
			return (lineR.getInt() << 16) | (lineG.getInt() << 8) | lineB.getInt();
		}

		private int getTickColorFromSliders() {
			if (tickR == null) return -1;
			return (tickR.getInt() << 16) | (tickG.getInt() << 8) | tickB.getInt();
		}

		private void onLineSliderChanged() {
			int c = getLineColorFromSliders();
			if (c >= 0) lineColorField.setText(String.format("%06X", c));
		}

		private void onTickSliderChanged() {
			int c = getTickColorFromSliders();
			if (c >= 0) tickColorField.setText(String.format("%06X", c));
		}

		private void showStatus(String msg, long durationMs) {
			this.statusMessage = msg;
			this.statusUntilMs = System.currentTimeMillis() + durationMs;
		}

		@Override
		public void render(DrawContext context, int mouseX, int mouseY, float delta) {
			super.render(context, mouseX, mouseY, delta);
			int centerX = this.width / 2;
			int fieldWidth = 240;
			int leftX = centerX - fieldWidth / 2;

			int labelColor = 0xC0C0C0;
			context.drawText(this.textRenderer, Text.of("Up target (-deg)"), leftX, upField.getY() - 10, labelColor, true);
			context.drawText(this.textRenderer, Text.of("Down target (+deg)"), leftX, downField.getY() - 10, labelColor, true);
			context.drawText(this.textRenderer, Text.of("Tolerance: fade range around target"), leftX, tolField.getY() - 10, labelColor, true);
			context.drawText(this.textRenderer, Text.of("Max pixel offset from center"), leftX, maxPixField.getY() - 10, labelColor, true);
			context.drawText(this.textRenderer, Text.of("Pixels per degree (sensitivity)"), leftX, opdField.getY() - 10, labelColor, true);

			context.drawText(this.textRenderer, Text.of("Line color (hex)"), leftX, lineColorField.getY() - 10, labelColor, true);
			int linePreview = 0xFF000000 | getColorFromHexField(lineColorField, ElytraPitchHelperClient.getConfig().lineColorRgb);
			int linePreviewY = (lineB != null ? lineB.getY() + lineB.getHeight() + 4 : lineColorField.getY() + 26);
			context.fill(leftX + 1, linePreviewY, leftX + fieldWidth - 1, linePreviewY + 6, linePreview);

			context.drawText(this.textRenderer, Text.of("Tick color (hex)"), leftX, tickColorField.getY() - 10, labelColor, true);
			int tickPreview = 0xFF000000 | getColorFromHexField(tickColorField, ElytraPitchHelperClient.getConfig().centerTickColorRgb);
			int tickPreviewY = (tickB != null ? tickB.getY() + tickB.getHeight() + 4 : tickColorField.getY() + 26);
			context.fill(leftX + 1, tickPreviewY, leftX + fieldWidth - 1, tickPreviewY + 6, tickPreview);


			if (!statusMessage.isEmpty() && System.currentTimeMillis() < statusUntilMs) {
				int w = this.textRenderer.getWidth(statusMessage);
				context.drawText(this.textRenderer, statusMessage, centerX - (w / 2), this.height - 40, 0x55FF55, true);
			}
		}

		private int getColorFromHexField(TextFieldWidget field, int fallback) {
			try {
				return (int)Long.parseLong(field.getText().trim(), 16) & 0xFFFFFF;
			} catch (Exception e) {
				return fallback & 0xFFFFFF;
			}
		}
	}

	static class IntSlider extends SliderWidget {
		private final java.util.function.IntConsumer onChanged;

		public IntSlider(int x, int y, int width, int height, Text text, int initial, java.util.function.IntConsumer onChanged) {
			super(x, y, width, height, text, 0.0);
			this.onChanged = onChanged;
			setValue(initial);
		}

		public void setValue(int v) {
			this.value = clamp01(v / 255.0);
			this.updateMessage();
		}

		public int getInt() {
			return (int)Math.round(this.value * 255.0);
		}

		@Override
		protected void updateMessage() {
			String base = this.getMessage().getString();
			String prefix = base.isEmpty() ? "" : base.substring(0, 1);
			this.setMessage(Text.of(prefix + ": " + getInt()));
		}

		@Override
		protected void applyValue() {
			if (onChanged != null) onChanged.accept(getInt());
		}

		private static double clamp01(double v) { return Math.max(0.0, Math.min(1.0, v)); }
	}
}


