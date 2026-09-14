package com.asphyxiamywife.elytrapitchhelper.screen;

import static com.asphyxiamywife.elytrapitchhelper.screen.ColorMath.TWO_PI;
import static com.asphyxiamywife.elytrapitchhelper.screen.ColorMath.diskToSquare;
import static com.asphyxiamywife.elytrapitchhelper.screen.ColorMath.hsvToRgb;
import static com.asphyxiamywife.elytrapitchhelper.screen.ColorMath.hueFromVector;
import static com.asphyxiamywife.elytrapitchhelper.screen.ColorMath.squareToDisk;

import com.asphyxiamywife.elytrapitchhelper.ModConstants;
import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

final class ColorPaletteRenderer implements AutoCloseable {
    private static final int PALETTE_TEXTURE_SCALE = 2;
    private static final int MARKER_TEXTURE_SCALE = 4;
    private static final Identifier PALETTE_TEXTURE_ID = ModConstants.id("color_editor/palette");
    private static final Identifier HUE_MARKER_TEXTURE_ID = ModConstants.id("color_editor/hue_marker");
    private static final Identifier SHADE_MARKER_TEXTURE_ID = ModConstants.id("color_editor/shade_marker");

    private DynamicTexture paletteTexture;
    private int paletteTextureSize;
    private double paletteTextureHue = -1.0;
    private DynamicTexture hueMarkerTexture;
    private int hueMarkerRadius;
    private DynamicTexture shadeMarkerTexture;
    private int shadeMarkerRadius;

    void render(GuiGraphics context, ColorEditorLayout layout, double hue, double saturation,
            double value, boolean disabled) {
        if (layout.paletteSize() <= 0) {
            return;
        }
        ensurePaletteTexture(layout, hue);
        if (paletteTexture != null) {
            context.blit(PALETTE_TEXTURE_ID, layout.paletteX(), layout.paletteY(),
                    layout.paletteX() + layout.paletteSize(), layout.paletteY() + layout.paletteSize(),
                    0.0f, 1.0f, 0.0f, 1.0f);
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
        double centerOffset = layout.paletteSize() * 0.5;
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
            release(PALETTE_TEXTURE_ID, paletteTexture);
            paletteTexture = null;
            paletteTextureSize = 0;
            paletteTextureHue = -1.0;
        }
        closeMarkerTextures();
    }

    private void ensurePaletteTexture(ColorEditorLayout layout, double hue) {
        int textureSize = layout.paletteSize() * PALETTE_TEXTURE_SCALE;
        if (paletteTexture != null && paletteTextureSize == textureSize
                && Math.abs(paletteTextureHue - hue) < 0.000001) {
            return;
        }

        if (paletteTexture == null || paletteTextureSize != textureSize) {
            close();
            NativeImage image = new NativeImage(textureSize, textureSize, true);
            writePalettePixels(image, layout, hue);
            paletteTexture = new DynamicTexture(() -> "elytrapitchhelper color palette", image);
            Minecraft.getInstance().getTextureManager().register(PALETTE_TEXTURE_ID, paletteTexture);
            paletteTextureSize = textureSize;
        } else {
            writePalettePixels(paletteTexture.getPixels(), layout, hue);
            paletteTexture.upload();
        }
        paletteTextureHue = hue;
    }

    private void writePalettePixels(NativeImage image, ColorEditorLayout layout, double hue) {
        PaletteGeometry geometry = geometry(layout);
        double textureScale = image.getWidth() / (double) layout.paletteSize();

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                double dx = (x + 0.5) / textureScale - geometry.centerOffset();
                double dy = (y + 0.5) / textureScale - geometry.centerOffset();
                double distance = Math.sqrt(dx * dx + dy * dy);
                int argb = 0;
                int ringAlpha = alphaForRing(distance, geometry, textureScale);

                if (ringAlpha > 0) {
                    argb = ringAlpha << 24 | hsvToRgb(hueFromVector(dx, dy), 1.0, 1.0);
                } else {
                    int shadeAlpha = alphaForOuterEdge(distance, geometry.shadeRadius(), textureScale);
                    if (shadeAlpha <= 0) {
                        image.setPixel(x, y, argb);
                        continue;
                    }
                    double[] square = diskToSquare(dx / geometry.shadeRadius(), dy / geometry.shadeRadius());
                    double sampleSaturation = MathUtil.clamp01((square[0] + 1.0) * 0.5);
                    double sampleValue = MathUtil.clamp01((1.0 - square[1]) * 0.5);
                    argb = shadeAlpha << 24 | hsvToRgb(hue, sampleSaturation, sampleValue);
                }

                image.setPixel(x, y, argb);
            }
        }
    }

    private static int alphaForRing(double distance, PaletteGeometry geometry, double textureScale) {
        double outerCoverage = edgeCoverage(geometry.outerRadius() - distance, textureScale);
        double innerCoverage = edgeCoverage(distance - geometry.innerRadius(), textureScale);
        return (int) Math.round(Math.min(outerCoverage, innerCoverage) * 255.0);
    }

    private static int alphaForOuterEdge(double distance, double radius, double textureScale) {
        return (int) Math.round(edgeCoverage(radius - distance, textureScale) * 255.0);
    }

    private static double edgeCoverage(double signedDistance, double textureScale) {
        return MathUtil.clamp01(signedDistance * textureScale + 0.5);
    }

    private void drawMarker(GuiGraphics context, int centerX, int centerY, int radius, boolean hueMarker) {
        DynamicTexture markerTexture = markerTexture(radius, hueMarker);
        int halfSize = radius + 3;
        Identifier textureId = hueMarker ? HUE_MARKER_TEXTURE_ID : SHADE_MARKER_TEXTURE_ID;
        context.blit(textureId, centerX - halfSize, centerY - halfSize,
                centerX + halfSize + 1, centerY + halfSize + 1, 0.0f, 1.0f, 0.0f, 1.0f);
    }

    private DynamicTexture markerTexture(int radius, boolean hueMarker) {
        DynamicTexture texture = hueMarker ? hueMarkerTexture : shadeMarkerTexture;
        int cachedRadius = hueMarker ? hueMarkerRadius : shadeMarkerRadius;
        if (texture != null && cachedRadius == radius) {
            return texture;
        }

        if (texture != null) {
            release(hueMarker ? HUE_MARKER_TEXTURE_ID : SHADE_MARKER_TEXTURE_ID, texture);
        }

        NativeImage image = markerImage(radius);
        texture = new DynamicTexture(() -> "elytrapitchhelper color marker", image);
        Minecraft.getInstance().getTextureManager().register(
                hueMarker ? HUE_MARKER_TEXTURE_ID : SHADE_MARKER_TEXTURE_ID, texture);
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
        int size = (halfSize * 2 + 1) * MARKER_TEXTURE_SCALE;
        NativeImage image = new NativeImage(size, size, true);
        double whiteInner = Math.max(0, radius - 1.6);
        double whiteOuter = radius + 0.6;
        double blackInner = Math.max(0, radius - 0.6);
        double blackOuter = radius + 1.9;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double dx = (x + 0.5) / MARKER_TEXTURE_SCALE - halfSize - 0.5;
                double dy = (y + 0.5) / MARKER_TEXTURE_SCALE - halfSize - 0.5;
                double distance = Math.sqrt(dx * dx + dy * dy);
                double blackCoverage = annulusCoverage(distance, blackInner, blackOuter,
                        MARKER_TEXTURE_SCALE);
                double whiteCoverage = annulusCoverage(distance, whiteInner, whiteOuter,
                        MARKER_TEXTURE_SCALE);
                image.setPixel(x, y, compositeWhiteOverBlack(whiteCoverage, blackCoverage));
            }
        }
        return image;
    }

    private static double annulusCoverage(double distance, double innerRadius, double outerRadius,
            double textureScale) {
        double innerCoverage = edgeCoverage(distance - innerRadius, textureScale);
        double outerCoverage = edgeCoverage(outerRadius - distance, textureScale);
        return Math.min(innerCoverage, outerCoverage);
    }

    private static int compositeWhiteOverBlack(double whiteCoverage, double blackCoverage) {
        double alpha = whiteCoverage + blackCoverage * (1.0 - whiteCoverage);
        if (alpha <= 0.0) {
            return 0;
        }

        int alphaChannel = (int) Math.round(alpha * 255.0);
        int colorChannel = (int) Math.round(whiteCoverage / alpha * 255.0);
        return alphaChannel << 24 | colorChannel << 16 | colorChannel << 8 | colorChannel;
    }

    private void closeMarkerTextures() {
        if (hueMarkerTexture != null) {
            release(HUE_MARKER_TEXTURE_ID, hueMarkerTexture);
            hueMarkerTexture = null;
            hueMarkerRadius = 0;
        }
        if (shadeMarkerTexture != null) {
            release(SHADE_MARKER_TEXTURE_ID, shadeMarkerTexture);
            shadeMarkerTexture = null;
            shadeMarkerRadius = 0;
        }
    }

    private static void release(Identifier id, DynamicTexture texture) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.getTextureManager().release(id);
        } else {
            texture.close();
        }
    }
}
