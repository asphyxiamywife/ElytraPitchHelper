package com.asphyxiamywife.elytrapitchhelper.screen;

import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

final class RowScrollController {
    interface Host {
        void rebuildWidgets();

        void blurFocusedEditBox();

        void setScreenDragging(boolean dragging);
    }

    private final Host host;
    private final BooleanSupplier active;
    private final Supplier<ProfileScrollGeometry> geometry;
    private final IntSupplier maxScroll;
    private final IntSupplier viewportHeight;
    private final int wheelStep;
    private final boolean snapToRows;
    private int scroll;
    private boolean dragging;
    private double grabOffset;

    RowScrollController(Host host, BooleanSupplier active, Supplier<ProfileScrollGeometry> geometry,
            IntSupplier maxScroll, IntSupplier viewportHeight, int wheelStep, boolean snapToRows) {
        this.host = host;
        this.active = active;
        this.geometry = geometry;
        this.maxScroll = maxScroll;
        this.viewportHeight = viewportHeight;
        this.wheelStep = wheelStep;
        this.snapToRows = snapToRows;
    }

    int scroll() {
        return scroll;
    }

    boolean dragging() {
        return dragging;
    }

    void set(int nextScroll) {
        nextScroll = clamp(nextScroll);
        if (nextScroll != scroll) {
            scroll = nextScroll;
            host.rebuildWidgets();
        }
    }

    void setDirect(int nextScroll) {
        scroll = nextScroll;
    }

    void restore(int rememberedScroll) {
        scroll = clamp(rememberedScroll);
    }

    boolean handleWheel(double scrollY) {
        if (!active.getAsBoolean() || scrollY == 0.0) {
            return false;
        }
        double distance = scrollY * wheelStep;
        int step = (int) (distance < 0 ? Math.floor(distance) : Math.ceil(distance));
        return moveTo(scroll - step);
    }

    boolean scrollBy(int rows) {
        return active.getAsBoolean() && moveTo(scroll + rows * ConfigScreen.ROW_HEIGHT);
    }

    boolean scrollToEdge(boolean top) {
        return active.getAsBoolean() && moveTo(top ? 0 : maxScroll.getAsInt());
    }

    boolean startDrag(MouseButtonEvent event) {
        if (!active.getAsBoolean() || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        ProfileScrollGeometry current = geometry.get();
        if (current == null || !current.contains(event.x(), event.y())) {
            return false;
        }

        host.blurFocusedEditBox();
        dragging = true;
        host.setScreenDragging(true);
        grabOffset = current.containsScroller(event.y())
                ? event.y() - current.scrollerY()
                : current.scrollerHeight() / 2.0;
        scrollToMouse(event.y());
        return true;
    }

    boolean drag(MouseButtonEvent event) {
        if (!dragging || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        scrollToMouse(event.y());
        return true;
    }

    boolean release(MouseButtonEvent event) {
        if (!dragging || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        stopDrag();
        return true;
    }

    void scrollIntoView(int row) {
        int top = row * ConfigScreen.ROW_HEIGHT;
        int bottom = top + ConfigScreen.ROW_HEIGHT;
        if (top < scroll) {
            scroll = top;
        } else if (bottom > scroll + viewportHeight.getAsInt()) {
            scroll = bottom - viewportHeight.getAsInt();
        }
        scroll = clamp(scroll);
    }

    private boolean moveTo(int nextScroll) {
        nextScroll = clamp(nextScroll);
        if (nextScroll == scroll) {
            return false;
        }
        scroll = nextScroll;
        host.rebuildWidgets();
        return true;
    }

    private void scrollToMouse(double mouseY) {
        ProfileScrollGeometry current = geometry.get();
        if (current == null || current.movableHeight() <= 0 || current.maxScrollAmount() <= 0) {
            stopDrag();
            return;
        }

        double scrollerTop = Math.max(0.0,
                Math.min(current.movableHeight(), mouseY - current.y() - grabOffset));
        set((int) Math.round(scrollerTop * current.maxScrollAmount() / current.movableHeight()));
    }

    private void stopDrag() {
        dragging = false;
        host.setScreenDragging(false);
    }

    private int clamp(int value) {
        if (snapToRows) {
            value = Math.round((float) value / ConfigScreen.ROW_HEIGHT) * ConfigScreen.ROW_HEIGHT;
        }
        return Math.max(0, Math.min(value, maxScroll.getAsInt()));
    }
}
