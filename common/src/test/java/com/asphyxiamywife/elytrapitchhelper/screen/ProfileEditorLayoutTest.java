package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import com.asphyxiamywife.elytrapitchhelper.config.SectionCollapseSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProfileEditorLayoutTest {
    private static final int ALL_ROWS = ProfileEditorPlan.rowCount(new Profile(), "",
            new SectionCollapseSettings().expandedAll(), key -> key);

    @Test
    void readOnlyOffsetMovesRowsAndReducesVisibleCapacity() {
        ProfileEditorLayout writable = ProfileEditorLayout.of(500, 300, ALL_ROWS, false, 9999);
        ProfileEditorLayout readOnly = ProfileEditorLayout.of(500, 300, ALL_ROWS, true, 9999);

        assertEquals(ProfileEditorLayout.LIST_TOP, writable.startY());
        assertEquals(ProfileEditorLayout.LIST_TOP + ConfigScreen.READ_ONLY_CONTENT_OFFSET,
                readOnly.startY());
        assertTrue(readOnly.visibleRows() < writable.visibleRows());
        assertEquals(readOnly.maxScroll(), readOnly.scroll());
    }

    @Test
    void scrollbarWidthAndPositionAreConditional() {
        ProfileEditorLayout shortList = ProfileEditorLayout.of(500, 600, 2, false, 0);
        ProfileEditorLayout longList = ProfileEditorLayout.of(500, 200, ALL_ROWS, false, 0);

        assertFalse(shortList.needsScrollbar());
        assertTrue(longList.needsScrollbar());
        assertEquals(longList.startX() + longList.rowWidth() + ConfigScreen.CONTROL_GAP,
                longList.scrollbarX());
        assertTrue(longList.scrollbarX()
                <= (500 - longList.contentWidth()) / 2 + longList.contentWidth() - 6);
    }

    @Test
    void rowsNeverOutgrowTheVanillaOptionWidth() {
        assertEquals(ProfileEditorLayout.MAX_ROW_WIDTH,
                ProfileEditorLayout.of(1920, 600, ALL_ROWS, false, 0).rowWidth());
        assertTrue(ProfileEditorLayout.of(320, 600, ALL_ROWS, false, 0).rowWidth()
                <= ProfileEditorLayout.of(320, 600, ALL_ROWS, false, 0).contentWidth());
    }

    @Test
    void everyRowSharesOneBoxWhateverItsDepth() {
        ProfileEditorLayout layout = ProfileEditorLayout.of(500, 600, ALL_ROWS, false, 0);

        for (int depth = 0; depth <= 2; depth++) {
            assertEquals(layout.startX(), layout.rowX(depth), "depth " + depth + " moved the row");
            assertEquals(layout.rowWidth(), layout.rowWidth(depth), "depth " + depth + " resized the row");
        }
    }

    @Test
    void theListStaysInsideTheViewport() {
        for (int[] screen : new int[][] {{426, 240}, {341, 256}, {320, 240}, {480, 270}}) {
            for (boolean readOnly : new boolean[] {false, true}) {
                ProfileEditorLayout layout = ProfileEditorLayout.of(screen[0], screen[1], ALL_ROWS,
                        readOnly, 0);
                int listBottom = layout.startY() + layout.visibleRows() * ConfigScreen.ROW_HEIGHT;
                assertTrue(listBottom <= screen[1] - ConfigScreen.FOOTER_OFFSET,
                        () -> "list overlaps the footer at " + screen[0] + "x" + screen[1]);
                assertTrue(layout.needsScrollbar() || layout.totalRows() == layout.visibleRows(),
                        () -> "rows are hidden with no scrollbar at " + screen[0] + "x" + screen[1]);
            }
        }
    }

    @Test
    void everyRowIsWhollyVisibleAtSomeScrollPosition() {
        ProfileEditorLayout base = ProfileEditorLayout.of(341, 256, ALL_ROWS, false, 0);
        for (int index = 0; index < ALL_ROWS; index++) {
            boolean reachable = false;
            for (int scroll = 0; scroll <= base.maxScroll(); scroll += ProfileEditorLayout.WHEEL_STEP) {
                ProfileEditorLayout layout = ProfileEditorLayout.of(341, 256, ALL_ROWS, false, scroll);
                if (showsWholeRow(layout, layout.rowY(index))) {
                    reachable = true;
                    break;
                }
            }
            if (!reachable) {
                ProfileEditorLayout atEnd = ProfileEditorLayout.of(
                        341, 256, ALL_ROWS, false, base.maxScroll());
                reachable = showsWholeRow(atEnd, atEnd.rowY(index));
            }
            int row = index;
            assertTrue(reachable, () -> "row " + row + " cannot be scrolled wholly into view");
        }
    }

    @Test
    void aCutRowDrawsButTakesNoClicks() {
        ProfileEditorLayout layout = ProfileEditorLayout.of(500, 300, ALL_ROWS, false, 0);
        int lastFitting = layout.viewportBottom() - ConfigScreen.CONTROL_HEIGHT;

        assertTrue(showsWholeRow(layout, lastFitting));
        assertTrue(layout.drawsRow(lastFitting + 1));
        assertFalse(showsWholeRow(layout, lastFitting + 1));
        assertTrue(layout.drawsRow(layout.viewportBottom() - 1));
        assertFalse(showsWholeRow(layout, layout.viewportBottom() - 1));
        assertFalse(layout.drawsRow(layout.viewportBottom()));
        assertFalse(layout.drawsRow(layout.startY() - ConfigScreen.CONTROL_HEIGHT));
        assertTrue(layout.drawsRow(layout.startY() - 1));
        assertFalse(showsWholeRow(layout, layout.startY() - 1));
    }

    @Test
    void theViewportEndsWhereTheRowsDo() {
        ProfileEditorLayout layout = ProfileEditorLayout.of(500, 300, ALL_ROWS, false, 0);

        assertTrue(layout.viewportContains(layout.startY()));
        assertTrue(layout.viewportContains(layout.viewportBottom() - 1));
        assertFalse(layout.viewportContains(layout.startY() - 1));
        assertFalse(layout.viewportContains(layout.viewportBottom()));
        int cut = layout.viewportBottom() - 1;
        assertTrue(layout.drawsRow(cut));
        assertFalse(layout.viewportContains(cut + ConfigScreen.CONTROL_HEIGHT - 1));
    }

    private static boolean showsWholeRow(ProfileEditorLayout layout, double y) {
        return y >= layout.startY()
                && y + ConfigScreen.CONTROL_HEIGHT <= layout.viewportBottom();
    }

    @Test
    void thePanelKeepsItsSizeWhateverTheScroll() {
        ProfileEditorLayout base = ProfileEditorLayout.of(500, 300, ALL_ROWS, false, 0);
        for (int scroll = 0; scroll <= base.maxScroll(); scroll++) {
            ProfileEditorLayout layout = ProfileEditorLayout.of(500, 300, ALL_ROWS, false, scroll);
            double firstTop = Double.MAX_VALUE;
            double lastBottom = -Double.MAX_VALUE;
            for (int index = 0; index < ALL_ROWS; index++) {
                double y = layout.rowY(index);
                if (!layout.drawsRow(y)) {
                    continue;
                }
                firstTop = Math.min(firstTop, y);
                lastBottom = Math.max(lastBottom, y + ConfigScreen.CONTROL_HEIGHT);
            }

            int offset = scroll;
            assertEquals(layout.startY(), layout.scrimTop(firstTop, true),
                    () -> "panel top moved at scroll " + offset);
            assertEquals(layout.viewportBottom(), layout.scrimBottom(lastBottom, true),
                    () -> "panel bottom moved at scroll " + offset);
        }
    }

    @Test
    void aListThatCannotScrollKeepsItsNaturalEdges() {
        ProfileEditorLayout layout = ProfileEditorLayout.of(500, 600, 3, false, 0);

        assertFalse(layout.needsScrollbar());
        int lastBottom = layout.rowY(2) + ConfigScreen.CONTROL_HEIGHT;
        assertEquals(lastBottom + 1, layout.scrimBottom(lastBottom, true));
        assertTrue(layout.scrimBottom(lastBottom, true) < layout.viewportBottom());
    }

    @Test
    void theScrollLimitComesDownWithAFoldingSection() {
        int viewport = ProfileEditorLayout.of(500, 300, ALL_ROWS, false, 0).visibleRows()
                * ConfigScreen.ROW_HEIGHT;
        int expanded = ALL_ROWS * ConfigScreen.ROW_HEIGHT;
        int collapsed = expanded - 10 * ConfigScreen.ROW_HEIGHT;

        int previous = Integer.MAX_VALUE;
        int biggestStep = 0;
        for (int step = 0; step <= 10; step++) {
            int height = expanded - (expanded - collapsed) * step / 10;
            ProfileEditorLayout layout =
                    ProfileEditorLayout.of(500, 300, ALL_ROWS, height, false, 9999);
            assertEquals(Math.max(0, height - viewport), layout.maxScroll());
            if (previous != Integer.MAX_VALUE) {
                biggestStep = Math.max(biggestStep, previous - layout.scroll());
            }
            previous = layout.scroll();
        }

        assertTrue(biggestStep < 10 * ConfigScreen.ROW_HEIGHT,
                () -> "the fold still drops the scroll in one go");
    }

    @Test
    void countAndHeightAgreeWhenNothingIsFolding() {
        ProfileEditorLayout counted = ProfileEditorLayout.of(500, 300, ALL_ROWS, false, 40);
        ProfileEditorLayout measured = ProfileEditorLayout.of(500, 300, ALL_ROWS,
                ALL_ROWS * ConfigScreen.ROW_HEIGHT, false, 40);

        assertEquals(counted, measured);
        assertEquals(ALL_ROWS * ConfigScreen.ROW_HEIGHT, counted.contentHeight());
    }

    @Test
    void aWheelNotchIsSmallerThanARow() {
        assertTrue(ProfileEditorLayout.WHEEL_STEP < ConfigScreen.ROW_HEIGHT);
        assertTrue(ProfileEditorLayout.WHEEL_STEP > 0);
    }

    @Test
    void theVisibleFractionMeasuresTheCut() {
        ProfileEditorLayout layout = ProfileEditorLayout.of(500, 300, ALL_ROWS, false, 0);

        assertEquals(1.0, layout.visibleFraction(layout.startY()), 1.0E-9);
        assertEquals(0.0, layout.visibleFraction(layout.viewportBottom()), 1.0E-9);
        assertEquals(0.5,
                layout.visibleFraction(layout.viewportBottom() - ConfigScreen.CONTROL_HEIGHT / 2.0),
                1.0E-9);
        assertEquals(0.0, layout.visibleFraction(layout.startY() - ConfigScreen.CONTROL_HEIGHT),
                1.0E-9);
    }

    @Test
    void emptyListNeedsNoScrollbar() {
        ProfileEditorLayout layout = ProfileEditorLayout.of(500, 300, 0, false, 5);

        assertEquals(0, layout.totalRows());
        assertEquals(0, layout.visibleRows());
        assertEquals(0, layout.scroll());
        assertFalse(layout.needsScrollbar());
    }
}
