package com.asphyxiamywife.elytrapitchhelper.screen;

import static com.asphyxiamywife.elytrapitchhelper.util.MathUtil.clamp01;
import static com.asphyxiamywife.elytrapitchhelper.screen.ColorMath.diskToSquare;
import static com.asphyxiamywife.elytrapitchhelper.screen.ColorMath.hsvToRgb;
import static com.asphyxiamywife.elytrapitchhelper.screen.ColorMath.hueFromVector;
import static com.asphyxiamywife.elytrapitchhelper.screen.ColorMath.rgbToHsv;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import com.mojang.blaze3d.platform.InputConstants;

final class ColorEditorPaletteController {
    interface Host {
        ColorEditorLayout colorEditorLayout();

        boolean paletteDisabled();

        void blurHexBox();

        void beginPaletteHistory();

        void endPaletteHistory();

        void setPaletteDragging(boolean dragging);

        void applyPaletteColor(int color);
    }

    private enum DragTarget {
        NONE,
        HUE,
        SHADE
    }

    private final Host host;
    private final ColorPaletteRenderer renderer = new ColorPaletteRenderer();
    private DragTarget dragTarget = DragTarget.NONE;
    private double hue;
    private double saturation;
    private double value;

    ColorEditorPaletteController(Host host) {
        this.host = host;
    }

    void render(GuiGraphicsExtractor context, ColorEditorLayout layout, int mouseX, int mouseY) {
        renderer.render(context, layout, hue, saturation, value, host.paletteDisabled());
        if (ScreenGeometry.contains(layout.paletteX(), layout.paletteY(), layout.paletteSize(),
                layout.paletteSize(), mouseX, mouseY)) {
            context.requestCursor(host.paletteDisabled() ? CursorTypes.NOT_ALLOWED : CursorTypes.POINTING_HAND);
        }
    }

    void close() {
        renderer.close();
    }

    void finishOpenDrag() {
        if (dragTarget != DragTarget.NONE) {
            host.endPaletteHistory();
            dragTarget = DragTarget.NONE;
        }
    }

    boolean startDrag(MouseButtonEvent event) {
        if (host.paletteDisabled() || event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        DragTarget hit = hit(event.x(), event.y());
        if (hit == DragTarget.NONE) {
            return false;
        }
        host.blurHexBox();
        host.beginPaletteHistory();
        dragTarget = hit;
        host.setPaletteDragging(true);
        updateColor(event.x(), event.y(), hit);
        return true;
    }

    boolean drag(MouseButtonEvent event) {
        if (dragTarget == DragTarget.NONE || event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        updateColor(event.x(), event.y(), dragTarget);
        return true;
    }

    boolean release(MouseButtonEvent event) {
        if (dragTarget == DragTarget.NONE || event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        dragTarget = DragTarget.NONE;
        host.setPaletteDragging(false);
        host.endPaletteHistory();
        return true;
    }

    void syncFromColor(int red, int green, int blue, boolean resetHueWhenGray) {
        double[] hsv = rgbToHsv(red, green, blue);
        if (resetHueWhenGray || hsv[1] > 0.0001) {
            hue = hsv[0];
        }
        saturation = hsv[1];
        value = hsv[2];
    }

    private DragTarget hit(double mouseX, double mouseY) {
        ColorEditorLayout layout = host.colorEditorLayout();
        if (layout.paletteSize() <= 0) {
            return DragTarget.NONE;
        }
        PaletteGeometry geometry = ColorPaletteRenderer.geometry(layout);
        double dx = mouseX - layout.paletteX() - geometry.centerOffset();
        double dy = mouseY - layout.paletteY() - geometry.centerOffset();
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance <= geometry.shadeRadius()) {
            return DragTarget.SHADE;
        }
        if (distance >= geometry.innerRadius() && distance <= geometry.outerRadius()) {
            return DragTarget.HUE;
        }
        return DragTarget.NONE;
    }

    private void updateColor(double mouseX, double mouseY, DragTarget target) {
        ColorEditorLayout layout = host.colorEditorLayout();
        PaletteGeometry geometry = ColorPaletteRenderer.geometry(layout);
        double dx = mouseX - layout.paletteX() - geometry.centerOffset();
        double dy = mouseY - layout.paletteY() - geometry.centerOffset();
        if (target == DragTarget.HUE) {
            hue = hueFromVector(dx, dy);
        } else {
            double shadeX = dx / geometry.shadeRadius();
            double shadeY = dy / geometry.shadeRadius();
            double distance = Math.sqrt(shadeX * shadeX + shadeY * shadeY);
            if (distance > 1.0) {
                shadeX /= distance;
                shadeY /= distance;
            }
            double[] square = diskToSquare(shadeX, shadeY);
            saturation = clamp01((square[0] + 1.0) * 0.5);
            value = clamp01((1.0 - square[1]) * 0.5);
        }
        host.applyPaletteColor(hsvToRgb(hue, saturation, value));
    }
}
