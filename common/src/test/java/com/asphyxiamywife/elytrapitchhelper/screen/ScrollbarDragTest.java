package com.asphyxiamywife.elytrapitchhelper.screen;

import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ScrollbarDragTest {
    private static final int EDITOR_ROWS = 40;
    private static final int EDITOR_WIDTH = 400;
    private static final int EDITOR_HEIGHT = 300;
    private static final int PROFILE_COUNT = 30;
    private static final int PROFILE_WIDTH = 500;
    private static final int PROFILE_HEIGHT = 400;

    @Test
    void grabbingThePaintedThumbLeavesTheEditorWhereItIs() {
        ProfileEditorLayout layout = ProfileEditorLayout.of(EDITOR_WIDTH, EDITOR_HEIGHT, EDITOR_ROWS, false, 0);
        int track = layout.scrollbarHeight();
        int content = layout.contentHeight();
        int slack = scrollPerThumbPixel(track, content, layout.maxScroll()) + 1;

        for (int scroll : new int[] {0, 96, 192, 384, 600, layout.maxScroll()}) {
            EditorHost host = new EditorHost();
            ConfigEditorScrollController controller = new ConfigEditorScrollController(host);
            controller.setDirect(scroll);

            int y = paintedThumbY(layout.startY(), track, content, scroll) + 5;
            assertTrue(controller.startDrag(press(layout.scrollbarX(), y)),
                    "the thumb should take the click at scroll " + scroll);
            assertEquals(scroll, controller.scroll(), slack,
                    "taking hold of the thumb moved the list at scroll " + scroll);
            assertTrue(host.dragging);
        }
    }

    @Test
    void grabbingThePaintedThumbLeavesTheProfileListWhereItIs() {
        ProfileListLayout layout = ProfileListLayout.of(PROFILE_WIDTH, PROFILE_HEIGHT, PROFILE_COUNT, false, 0);
        int track = layout.viewportHeight();
        int content = PROFILE_COUNT * ConfigScreen.ROW_HEIGHT;

        for (int row = 0; row <= PROFILE_COUNT - layout.visibleRows(); row++) {
            int scroll = row * ConfigScreen.ROW_HEIGHT;
            ProfileHost host = new ProfileHost();
            ConfigProfileScrollController controller = new ConfigProfileScrollController(host);
            controller.setDirect(scroll);

            int y = paintedThumbY(layout.startY(), track, content, scroll) + 5;
            assertTrue(controller.startDrag(press(layout.scrollbarX(), y)),
                    "the thumb should take the click at row " + row);
            assertEquals(scroll, controller.scroll(), ConfigScreen.ROW_HEIGHT / 2.0,
                    "taking hold of the thumb moved the list at row " + row);
        }
    }

    @Test
    void draggingTheThumbToEitherEndReachesBothLimits() {
        ProfileEditorLayout layout = ProfileEditorLayout.of(EDITOR_WIDTH, EDITOR_HEIGHT, EDITOR_ROWS, false, 0);
        EditorHost host = new EditorHost();
        ConfigEditorScrollController controller = new ConfigEditorScrollController(host);

        assertTrue(controller.startDrag(press(layout.scrollbarX(), layout.startY() + 2)));
        assertEquals(0, controller.scroll());
        assertTrue(controller.drag(press(layout.scrollbarX(), layout.startY() + layout.scrollbarHeight())));
        assertEquals(layout.maxScroll(), controller.scroll());
        assertTrue(controller.drag(press(layout.scrollbarX(), layout.startY())));
        assertEquals(0, controller.scroll());

        assertTrue(controller.release(press(layout.scrollbarX(), layout.startY())));
        assertTrue(!host.dragging);
    }

    @Test
    void clicksBesideTheTrackAreNotADrag() {
        ProfileEditorLayout layout = ProfileEditorLayout.of(EDITOR_WIDTH, EDITOR_HEIGHT, EDITOR_ROWS, false, 0);
        ConfigEditorScrollController controller = new ConfigEditorScrollController(new EditorHost());

        assertTrue(!controller.startDrag(press(layout.scrollbarX() - 4, layout.startY() + 20)));
        assertTrue(!controller.startDrag(press(layout.scrollbarX(), layout.startY() - 4)));
    }

    private static MouseButtonEvent press(double x, double y) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
    }

    private static int scrollPerThumbPixel(int trackHeight, int contentHeight, int maxScroll) {
        int travel = Math.max(1, trackHeight - paintedThumbHeight(trackHeight, contentHeight));
        return maxScroll / travel;
    }

    private static int paintedThumbHeight(int trackHeight, int contentHeight) {
        return Math.min(Math.max((int) ((float) (trackHeight * trackHeight) / contentHeight), 32),
                trackHeight - 8);
    }

    private static int paintedThumbY(int trackY, int trackHeight, int contentHeight, int scroll) {
        int maxScroll = Math.max(0, contentHeight - trackHeight);
        if (maxScroll == 0) {
            return trackY;
        }
        int travel = trackHeight - paintedThumbHeight(trackHeight, contentHeight);
        return Math.max(trackY, scroll * travel / maxScroll + trackY);
    }

    private static final class EditorHost implements ConfigEditorScrollController.Host {
        boolean dragging;

        @Override
        public void layoutEditorRows() {}

        public void rebuildWidgets() {
        }

        @Override
        public void blurFocusedEditBox() {
        }

        @Override
        public void setScreenDragging(boolean dragging) {
            this.dragging = dragging;
        }

        @Override
        public boolean editingProfile() {
            return true;
        }

        @Override
        public int editorRowCount() {
            return EDITOR_ROWS;
        }

        @Override
        public int editorContentHeight() {
            return EDITOR_ROWS * ConfigScreen.ROW_HEIGHT;
        }

        @Override
        public int screenWidth() {
            return EDITOR_WIDTH;
        }

        @Override
        public int screenHeight() {
            return EDITOR_HEIGHT;
        }

        @Override
        public boolean readOnly() {
            return false;
        }
    }

    private static final class ProfileHost implements ConfigProfileScrollController.Host {
        @Override
        public void rebuildWidgets() {
        }

        @Override
        public void blurFocusedEditBox() {
        }

        @Override
        public void setScreenDragging(boolean dragging) {
        }

        @Override
        public boolean editingProfile() {
            return false;
        }

        @Override
        public int profileSlotCount() {
            return PROFILE_COUNT;
        }

        @Override
        public int profileViewportHeight() {
            return layout().viewportHeight();
        }

        @Override
        public int maxProfileScroll() {
            return layout().maxScroll();
        }

        @Override
        public int screenWidth() {
            return PROFILE_WIDTH;
        }

        @Override
        public int screenHeight() {
            return PROFILE_HEIGHT;
        }

        @Override
        public boolean readOnly() {
            return false;
        }

        private ProfileListLayout layout() {
            return ProfileListLayout.of(PROFILE_WIDTH, PROFILE_HEIGHT, PROFILE_COUNT, false, 0);
        }
    }
}
