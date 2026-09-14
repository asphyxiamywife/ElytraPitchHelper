package com.asphyxiamywife.elytrapitchhelper.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProfileListLayoutTest {
    @Test
    void readOnlyOffsetMovesRowsAndReducesVisibleCapacity() {
        ProfileListLayout writable = ProfileListLayout.of(500, 300, 20, false, 9999);
        ProfileListLayout readOnly = ProfileListLayout.of(500, 300, 20, true, 9999);

        assertEquals(64 + ConfigScreen.ROW_HEIGHT, writable.startY());
        assertEquals(64 + ConfigScreen.ROW_HEIGHT + ConfigScreen.READ_ONLY_CONTENT_OFFSET,
                readOnly.startY());
        assertTrue(readOnly.visibleRows() < writable.visibleRows());
        assertEquals(readOnly.maxScroll(), readOnly.scroll());
    }

    @Test
    void theHeaderHasRoomForBothOfItsRows() {
        for (boolean readOnly : new boolean[] {false, true}) {
            ProfileListLayout layout = ProfileListLayout.of(500, 300, 20, readOnly, 0);
            int headerTop = 38 + layout.contentOffset();
            assertTrue(headerTop + ConfigScreen.ROW_HEIGHT * 2 <= layout.startY(),
                    () -> "header overlaps the list when readOnly=" + readOnly);
        }
    }

    @Test
    void aShortWindowLosesRowsRatherThanTheFooter() {
        for (int height = 120; height < 260; height++) {
            for (boolean readOnly : new boolean[] {false, true}) {
                ProfileListLayout layout = ProfileListLayout.of(500, height, 200, readOnly, 0);
                int listBottom = layout.startY() + layout.visibleRows() * ConfigScreen.ROW_HEIGHT;
                int footerTop = height - ProfileListLayout.ACTION_ROW_OFFSET - ConfigScreen.CONTROL_GAP;
                int screenHeight = height;
                assertTrue(layout.visibleRows() == 0 || listBottom <= footerTop,
                        () -> "list paints over the footer at height " + screenHeight
                                + ", readOnly=" + readOnly);
            }
        }
    }

    @Test
    void aNarrowWindowKeepsRowsOnScreenInsteadOfCentringAWiderBlock() {
        for (int width = 40; width <= 400; width++) {
            ProfileListLayout layout = ProfileListLayout.of(width, 400, 4, false, 0);
            int screenWidth = width;
            assertTrue(layout.startX() >= 0, () -> "rows start off the left edge at width " + screenWidth);
            assertTrue(layout.startX() + layout.contentWidth() <= width,
                    () -> "rows run off the right edge at width " + screenWidth);
        }
    }

    @Test
    void theListFillsTheSpaceAboveTheFooter() {
        for (int height = 260; height <= 720; height++) {
            for (boolean readOnly : new boolean[] {false, true}) {
                ProfileListLayout layout = ProfileListLayout.of(500, height, 200, readOnly, 0);
                int listBottom = layout.startY() + layout.visibleRows() * ConfigScreen.ROW_HEIGHT;
                int footerTop = height - ProfileListLayout.ACTION_ROW_OFFSET - ConfigScreen.CONTROL_GAP;
                int screenHeight = height;
                assertTrue(listBottom <= footerTop,
                        () -> "list runs into the footer at height " + screenHeight
                                + ", readOnly=" + readOnly);
                assertTrue(footerTop - listBottom < ConfigScreen.ROW_HEIGHT,
                        () -> "a whole row of space is going spare at height " + screenHeight
                                + ", readOnly=" + readOnly);
            }
        }
    }

    @Test
    void theOpenStripAddsExactlyOneRowOfScroll() {
        ProfileListPlan closed = ProfileListPlan.closed(20);
        ProfileListPlan open = ProfileListPlan.of(20, 7);
        ProfileListLayout without = ProfileListLayout.of(500, 400, closed.slotCount(), false, 0);
        ProfileListLayout with = ProfileListLayout.of(500, 400, open.slotCount(), false, 0);

        assertEquals(without.visibleRows(), with.visibleRows());
        assertEquals(without.startY(), with.startY());
        assertEquals(without.maxScroll() + ConfigScreen.ROW_HEIGHT, with.maxScroll());
        assertEquals(0, with.maxScroll() % ConfigScreen.ROW_HEIGHT,
                "the scroll no longer lands on row boundaries");
    }

    @Test
    void theStripSlotIsAlwaysReachable() {
        for (int selected = 0; selected < 20; selected++) {
            ProfileListPlan plan = ProfileListPlan.of(20, selected);
            ProfileListLayout layout = ProfileListLayout.of(500, 400, plan.slotCount(), false,
                    Integer.MAX_VALUE);
            int lastVisibleSlot = layout.scrollRow() + layout.visibleRows() - 1;
            int slot = plan.stripSlot();
            assertTrue(slot <= lastVisibleSlot,
                    () -> "the strip at slot " + slot + " cannot be scrolled into view");
        }
    }

    @Test
    void scrollbarWidthAndPositionAreConditional() {
        ProfileListLayout shortList = ProfileListLayout.of(500, 300, 2, false, 0);
        ProfileListLayout longList = ProfileListLayout.of(500, 300, 20, false, 0);

        assertFalse(shortList.needsScrollbar());
        assertEquals(shortList.contentWidth(), shortList.listWidth());
        assertTrue(longList.needsScrollbar());
        assertTrue(longList.listWidth() < longList.contentWidth());
        assertEquals(longList.startX() + longList.listWidth() + ConfigScreen.CONTROL_GAP,
                longList.scrollbarX());
    }
}
