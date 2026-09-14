package com.asphyxiamywife.elytrapitchhelper.screen;

import net.minecraft.client.gui.components.AbstractScrollArea;
import com.asphyxiamywife.elytrapitchhelper.util.MonotonicClock;
import net.minecraft.client.input.MouseButtonEvent;

final class ConfigEditorScrollController {
    private final Host host;
    private final RowScrollController rows;
    private final SmoothScroll motion = new SmoothScroll();

    ConfigEditorScrollController(Host host) {
        this.host = host;
        this.rows = new RowScrollController(new RowScrollController.Host() {
            public void rebuildWidgets() { host.layoutEditorRows(); }
            public void blurFocusedEditBox() { host.blurFocusedEditBox(); }
            public void setScreenDragging(boolean dragging) { host.setScreenDragging(dragging); }
        }, host::editingProfile, this::geometry,
                () -> layout().maxScroll(), () -> layout().viewportHeight(),
                ProfileEditorLayout.WHEEL_STEP, false);
    }

    int scroll() {
        return rows.scroll();
    }

    void set(int nextScroll) {
        motion.reset(nextScroll);
        rows.set(nextScroll);
    }

    void setDirect(int nextScroll) {
        motion.reset(nextScroll);
        rows.setDirect(nextScroll);
    }

    void restore(int rememberedScroll) {
        rows.restore(rememberedScroll);
        motion.reset(rows.scroll());
    }

    boolean handleWheel(double scrollY) {
        if (!host.editingProfile() || !Double.isFinite(scrollY) || scrollY == 0.0) {
            return false;
        }
        return motion.wheel(-scrollY * ProfileEditorLayout.WHEEL_STEP,
                layout().maxScroll(), MonotonicClock.millis());
    }

    void advanceAnimation(long nowMillis) {
        int position = motion.advance(nowMillis, layout().maxScroll());
        rows.set(position);
    }

    boolean scrollPage(int pages) {
        motion.reset(rows.scroll());
        boolean moved = rows.scrollBy(pages * Math.max(1, layout().visibleRows()));
        motion.reset(rows.scroll());
        return moved;
    }

    boolean scrollToEdge(boolean top) {
        boolean moved = rows.scrollToEdge(top);
        motion.reset(rows.scroll());
        return moved;
    }

    boolean dragging() {
        return rows.dragging();
    }

    boolean startDrag(MouseButtonEvent event) {
        boolean started = rows.startDrag(event);
        if (started) motion.reset(rows.scroll());
        return started;
    }

    boolean drag(MouseButtonEvent event) {
        boolean dragged = rows.drag(event);
        if (dragged) motion.reset(rows.scroll());
        return dragged;
    }

    boolean release(MouseButtonEvent event) {
        return rows.release(event);
    }

    void scrollRowIntoView(int rowIndex) {
        rows.scrollIntoView(rowIndex);
        motion.reset(rows.scroll());
    }

    ProfileEditorLayout layout() {
        return ProfileEditorLayout.of(host.screenWidth(), host.screenHeight(), host.editorRowCount(),
                host.editorContentHeight(), host.readOnly(), rows.scroll());
    }

    private ProfileScrollGeometry geometry() {
        if (!host.editingProfile()) {
            return null;
        }
        ProfileEditorLayout layout = layout();
        if (!layout.needsScrollbar()) {
            return null;
        }

        int scrollbarHeight = layout.scrollbarHeight();
        int contentHeight = layout.contentHeight();
        int maxScrollAmount = Math.max(0, contentHeight - scrollbarHeight);
        ScrollbarMetrics metrics = ScrollbarMetrics.of(layout.startY(), scrollbarHeight,
                contentHeight, rows.scroll(), maxScrollAmount);
        return new ProfileScrollGeometry(layout.scrollbarX(), layout.startY(),
                AbstractScrollArea.SCROLLBAR_WIDTH, scrollbarHeight,
                metrics.thumbY(), metrics.thumbHeight(), metrics.maxScroll(), metrics.travel());
    }

    interface Host extends RowScrollController.Host {
        void layoutEditorRows();

        boolean editingProfile();

        int editorRowCount();

        int editorContentHeight();

        int screenWidth();

        int screenHeight();

        boolean readOnly();
    }
}
