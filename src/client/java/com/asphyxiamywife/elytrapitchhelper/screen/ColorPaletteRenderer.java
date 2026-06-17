package com.asphyxiamywife.elytrapitchhelper.screen;

import static com.asphyxiamywife.elytrapitchhelper.screen.ColorMath.TWO_PI;
import static com.asphyxiamywife.elytrapitchhelper.screen.ColorMath.diskToSquare;
import static com.asphyxiamywife.elytrapitchhelper.screen.ColorMath.hsvToRgb;
import static com.asphyxiamywife.elytrapitchhelper.screen.ColorMath.hueFromVector;
import static com.asphyxiamywife.elytrapitchhelper.screen.ColorMath.squareToDisk;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;

final class ColorPaletteRenderer implements AutoCloseable {
    private DynamicTexture paletteTexture;
    private int paletteTextureSize;
    private double paletteTextureHue = -1.0;
    private DynamicTexture hueMarkerTexture;
    private int hueMarkerRadius;
    private DynamicTexture shadeMarkerTexture;
    private int shadeMarkerRadius;

    void render(GuiGraphicsExtractor context, ColorEditorLayout layout, double hue, double saturation,
            double value, boolean disabled) {
        ensurePaletteTexture(layout, hue);
        if (paletteTexture != null) {
            context.blit(paletteTexture.getTextureView(), paletteTexture.getSampler(), layout.paletteX(),
                    layout.paletteY(), layout.paletteX() + layout.paletteSize(),
                    layout.paletteY() + layout.paletteSize(), 0.0f, 1.0f, 0.0f, 1.0f);
        }

        if (disabled) {
            context.fill(layout.paletteX(), layout.paletteY(), layout.paletteX() + layout.paletteSize(),
                    layout.paletteY() + layout.paletteSize(), 0x99000000);
            return;
        }

        PaletteGeometry geometry = geometry(layout);
        int centerX = layout.paletteX() + (int) Math.round(geometry.centerOffset());
        int centerY = layout.paletteY() + (int) Math.round(geometry.centerOffset());
        double hueAngle = (1.0 - hue) * TWO_PI;
        int hueMarkerX = (int) Math.round(centerX + Math.cos(hueAngle) * geometry.markerRadius());
        int hueMarkerY = (int) Math.round(centerY + Math.sin(hueAngle) * geometry.markerRadius());
        drawMarker(context, hueMarkerX, hueMarkerY, Math.max(5, layout.paletteSize() / 22), true);

        double[] shadePoint = squareToDisk(saturation * 2.0 - 1.0, 1.0 - value * 2.0);
        int shadeMarkerX = (int) Math.round(centerX + shadePoint[0] * geometry.shadeRadius());
        int shadeMarkerY = (int) Math.round(centerY + shadePoint[1] * geometry.shadeRadius());
        drawMarker(context, shadeMarkerX, shadeMarkerY, Math.max(4, layout.paletteSize() / 28), false);
    }

    static PaletteGeometry geometry(ColorEditorLayout layout) {
        double centerOffset = (layout.paletteSize() - 1.0) * 0.5;
        double outerRadius = layout.paletteSize() * 0.5;
        double ringThickness = Math.max(11.0, layout.paletteSize() * 0.145);
        double innerRadius = outerRadius - ringThickness;
        double shadeRadius = Math.max(12.0, innerRadius - Math.max(5.0, layout.paletteSize() * 0.035));
        double markerRadius = (outerRadius + innerRadius) * 0.5;
        return new PaletteGeometry(centerOffset, outerRadius, innerRadius, shadeRadius, markerRadius);
    }

    @Override
    public void close() {
        if (paletteTexture != null) {
            paletteTexture.close();
            paletteTexture = null;
            paletteTextureSize = 0;
            paletteTextureHue = -1.0;
        }
        closeMarkerTextures();
    }

    private void ensurePaletteTexture(ColorEditorLayout layout, double hue) {
        if (paletteTexture != null && paletteTextureSize == layout.paletteSize()
                && Math.abs(paletteTextureHue - hue) < 0.000001) {
            return;
        }

        if (paletteTexture == null || paletteTextureSize != layout.paletteSize()) {
            close();
            NativeImage image = new NativeImage(layout.paletteSize(), layout.paletteSize(), true);
            writePalettePixels(image, layout, hue);
            paletteTexture = new DynamicTexture(() -> "elytrapitchhelper color palette", image);
            paletteTextureSize = layout.paletteSize();
        } else {
            writePalettePixels(paletteTexture.getPixels(), layout, hue);
            paletteTexture.upload();
        }
        paletteTextureHue = hue;
    }

    private void writePalettePixels(NativeImage image, ColorEditorLayout layout, double hue) {
        PaletteGeometry geometry = geometry(layout);

        for (int y = 0; y < layout.paletteSize(); y++) {
            for (int x = 0; x < layout.paletteSize(); x++) {
                double dx = x + 0.5 - geometry.centerOffset();
                double dy = y + 0.5 - geometry.centerOffset();
                double distance = Math.sqrt(dx * dx + dy * dy);
                int argb = 0;

                if (distance <= geometry.outerRadius() && distance >= geometry.innerRadius()) {
                    argb = 0xFF000000 | hsvToRgb(hueFromVector(dx, dy), 1.0, 1.0);
                } else if (distance <= geometry.shadeRadius()) {
                    double[] square = diskToSquare(dx / geometry.shadeRadius(), dy / geometry.shadeRadius());
                    double sampleSaturation = ColorMath.clamp01((square[0] + 1.0) * 0.5);
                    double sampleValue = ColorMath.clamp01((1.0 - square[1]) * 0.5);
                    argb = 0xFF000000 | hsvToRgb(hue, sampleSaturation, sampleValue);
                }

                image.setPixel(x, y, argb);
            }
        }
    }

    private void drawMarker(GuiGraphicsExtractor context, int centerX, int centerY, int radius, boolean hueMarker) {
        DynamicTexture markerTexture = markerTexture(radius, hueMarker);
        int halfSize = markerTexture.getPixels().getWidth() / 2;
        context.blit(markerTexture.getTextureView(), markerTexture.getSampler(), centerX - halfSize,
                centerY - halfSize, centerX + halfSize + 1, centerY + halfSize + 1, 0.0f, 1.0f, 0.0f, 1.0f);
    }

    private DynamicTexture markerTexture(int radius, boolean hueMarker) {
        DynamicTexture texture = hueMarker ? hueMarkerTexture : shadeMarkerTexture;
        int cachedRadius = hueMarker ? hueMarkerRadius : shadeMarkerRadius;
        if (texture != null && cachedRadius == radius) {
            return texture;
        }

        if (texture != null) {
            texture.close();
        }

        NativeImage image = markerImage(radius);
        texture = new DynamicTexture(() -> "elytrapitchhelper color marker", image);
        if (hueMarker) {
            hueMarkerTexture = texture;
            hueMarkerRadius = radius;
        } else {
            shadeMarkerTexture = texture;
            shadeMarkerRadius = radius;
        }
        return texture;
    }

    private NativeImage markerImage(int radius) {
        int halfSize = radius + 3;
        int size = halfSize * 2 + 1;
        NativeImage image = new NativeImage(size, size, true);
        double whiteInner = Math.max(0, radius - 1.6);
        double whiteOuter = radius + 0.6;
        double blackInner = Math.max(0, radius - 0.6);
        double blackOuter = radius + 1.9;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double dx = x - halfSize;
                double dy = y - halfSize;
                double distance = Math.sqrt(dx * dx + dy * dy);
                int argb = 0;
                if (distance >= whiteInner && distance <= whiteOuter) {
                    argb = 0xFFFFFFFF;
                } else if (distance >= blackInner && distance <= blackOuter) {
                    argb = 0xFF000000;
                }
                image.setPixel(x, y, argb);
            }
        }
        return image;
    }

    private void closeMarkerTextures() {
        if (hueMarkerTexture != null) {
            hueMarkerTexture.close();
            hueMarkerTexture = null;
            hueMarkerRadius = 0;
        }
        if (shadeMarkerTexture != null) {
            shadeMarkerTexture.close();
            shadeMarkerTexture = null;
            shadeMarkerRadius = 0;
        }
    }
}
