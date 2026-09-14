package com.asphyxiamywife.elytrapitchhelper.screen;

import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.input.MouseButtonEvent;

final class ConfigProfileScrollController {
    private final Host host;
    private final RowScrollController rows;

    ConfigProfileScrollController(Host host) {
        this.host = host;
        this.rows = new RowScrollController(host, () -> !host.editingProfile(), this::geometry,
                host::maxProfileScroll, host::profileViewportHeight,
                ConfigScreen.ROW_HEIGHT, true);
    }

    int scroll() {
        return rows.scroll();
    }

    void set(int nextScroll) {
        rows.set(nextScroll);
    }

    void setDirect(int nextScroll) {
        rows.setDirect(nextScroll);
    }

    void restore(int rememberedScroll) {
        rows.restore(rememberedScroll);
    }

    boolean handleWheel(double scrollY) {
        return rows.handleWheel(scrollY);
    }

    boolean dragging() {
        return rows.dragging();
    }

    boolean startDrag(MouseButtonEvent event) {
        return rows.startDrag(event);
    }

    boolean drag(MouseButtonEvent event) {
        return rows.drag(event);
    }

    boolean release(MouseButtonEvent event) {
        return rows.release(event);
    }

    void scrollIntoView(int slot) {
        rows.scrollIntoView(slot);
    }

    private ProfileScrollGeometry geometry() {
        ProfileListLayout layout = ProfileListLayout.of(host.screenWidth(), host.screenHeight(),
                host.profileSlotCount(), host.readOnly(), rows.scroll());
        if (host.editingProfile() || !layout.needsScrollbar()) {
            return null;
        }

        int scrollbarHeight = layout.visibleRows() * ConfigScreen.ROW_HEIGHT;
        int contentHeight = host.profileSlotCount() * ConfigScreen.ROW_HEIGHT;
        int maxScrollAmount = Math.max(0, contentHeight - scrollbarHeight);
        ScrollbarMetrics metrics = ScrollbarMetrics.of(layout.startY(), scrollbarHeight,
                contentHeight, rows.scroll(), maxScrollAmount);
        return new ProfileScrollGeometry(layout.scrollbarX(), layout.startY(),
                AbstractScrollArea.SCROLLBAR_WIDTH, scrollbarHeight,
                metrics.thumbY(), metrics.thumbHeight(), metrics.maxScroll(), metrics.travel());
    }

    interface Host extends RowScrollController.Host {
        boolean editingProfile();

        int profileSlotCount();

        int profileViewportHeight();

        int maxProfileScroll();

        int screenWidth();

        int screenHeight();

        boolean readOnly();
    }
}
