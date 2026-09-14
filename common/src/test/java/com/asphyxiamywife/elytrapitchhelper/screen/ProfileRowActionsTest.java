package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.screen.widget.SettingRowPainter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProfileRowActionsTest {
    private static final int LIST_X = 0;
    private static final int[] LIST_WIDTHS = {244, 260, 380, 520, 560};

    private static ProfileRowLayout layout(int listWidth, boolean deleteMode) {
        return ProfileRowLayout.of(LIST_X, listWidth, deleteMode);
    }

    @Test
    void theRowControlsNeverOverlapEachOther() {
        for (int listWidth : LIST_WIDTHS) {
            for (boolean deleteMode : new boolean[] {false, true}) {
                ProfileRowLayout row = layout(listWidth, deleteMode);
                assertTrue(row.chevronX() + row.chevronWidth() <= row.activeX(),
                        () -> "the chevron runs into the radio at " + listWidth);
                assertTrue(row.activeX() + row.activeWidth() <= row.nameX(),
                        () -> "the radio runs into the name at " + listWidth);
                assertTrue(row.nameRight() <= row.actionX(),
                        () -> "the name overlaps the action at " + listWidth
                                + " with deleteMode=" + deleteMode);
            }
        }
    }

    @Test
    void theChevronGutterSurvivesDeleteMode() {
        for (int listWidth : LIST_WIDTHS) {
            ProfileRowLayout normal = layout(listWidth, false);
            ProfileRowLayout deleting = layout(listWidth, true);
            assertEquals(normal.chevronX(), deleting.chevronX());
            assertEquals(normal.chevronWidth(), deleting.chevronWidth());
            assertEquals(normal.activeX(), deleting.activeX());
            assertEquals(normal.nameX(), deleting.nameX());
        }
    }

    @Test
    void theChevronFitsItsGutter() {
        ProfileRowLayout row = layout(520, false);
        assertTrue(ProfileChevron.SIZE <= row.chevronWidth(),
                "the chevron is wider than the gutter reserved for it");
        assertTrue(ProfileChevron.SIZE <= ConfigScreen.CONTROL_HEIGHT,
                "the chevron is taller than the row it sits in");
    }

    @Test
    void theActionStaysInsideTheRow() {
        for (int listWidth : LIST_WIDTHS) {
            for (boolean deleteMode : new boolean[] {false, true}) {
                ProfileRowLayout row = layout(listWidth, deleteMode);
                assertTrue(row.actionsRight() <= LIST_X + listWidth - SettingRowPainter.ROW_PADDING,
                        () -> "the action runs past the row edge at " + listWidth
                                + " with deleteMode=" + deleteMode);
            }
        }
    }

    @Test
    void theRowContentIsInsetFromTheBand() {
        ProfileRowLayout row = layout(520, false);
        assertTrue(row.chevronX() >= LIST_X + SettingRowPainter.ROW_PADDING);
        assertTrue(row.contentX() + row.contentWidth() <= LIST_X + 520 - SettingRowPainter.ROW_PADDING);
    }

    @Test
    void theStripSpansTheNameAndTheAction() {
        for (int listWidth : LIST_WIDTHS) {
            ProfileRowLayout row = layout(listWidth, false);
            assertEquals(row.nameX(), row.stripX());
            assertEquals(row.actionsRight() - row.nameX(), row.stripWidth());
            assertTrue(row.stripX() >= row.contentX() + row.activeWidth(),
                    () -> "the strip reaches back under the radio at " + listWidth);
        }
    }

    @Test
    void theStripActionsDivideTheStrip() {
        for (int listWidth : LIST_WIDTHS) {
            ProfileRowLayout row = layout(listWidth, false);
            ProfileStripLayout strip = ProfileStripLayout.of(row.stripX(), row.stripWidth());
            for (int action = 0; action < ProfileStripLayout.ACTIONS; action++) {
                assertTrue(strip.actionWidth(action) > 0,
                        () -> "an action has no width at " + listWidth);
            }
            for (int action = 0; action < ProfileStripLayout.ACTIONS - 1; action++) {
                int index = action;
                assertTrue(strip.actionX(index) + strip.actionWidth(index) <= strip.actionX(index + 1),
                        () -> "strip action " + index + " runs into the next at " + listWidth);
            }
            assertEquals(row.stripX(), strip.actionX(0));
            assertEquals(row.actionsRight(), strip.actionsRight());
        }
    }

    @Test
    void theStripActionsAreEvenlySized() {
        for (int listWidth : LIST_WIDTHS) {
            ProfileRowLayout row = layout(listWidth, false);
            ProfileStripLayout strip = ProfileStripLayout.of(row.stripX(), row.stripWidth());
            int first = strip.actionWidth(0);
            for (int action = 1; action < ProfileStripLayout.ACTIONS; action++) {
                int index = action;
                assertTrue(Math.abs(strip.actionWidth(index) - first) <= 1,
                        () -> "strip action " + index + " is a different size at " + listWidth);
            }
        }
    }
}
