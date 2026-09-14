package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProfileListSelectionControllerTest {
    @Test
    void clickingARowTogglesItsStripAndStripGapsKeepSelection() {
        Fixture fixture = new Fixture();
        ProfileListLayout layout = fixture.layout();

        assertTrue(fixture.selection.selectAt(layout, layout.startX(), layout.startY()));
        String selected = fixture.config.profile(0).fileName();
        assertEquals(selected, fixture.selection.selectedFile());
        assertTrue(fixture.selection.plan().stripOpen());
        assertEquals(List.of("scroll:0", "scroll:1", "rebuild"), fixture.effects);

        layout = fixture.layout();
        assertTrue(fixture.selection.selectAt(layout, layout.startX(),
                layout.startY() + ConfigScreen.ROW_HEIGHT));
        assertEquals(selected, fixture.selection.selectedFile());
        assertEquals(3, fixture.effects.size());

        assertTrue(fixture.selection.selectAt(layout, layout.startX(), layout.startY()));
        assertNull(fixture.selection.selectedFile());
        assertFalse(fixture.selection.plan().stripOpen());
    }

    @Test
    void viewportEdgesDoNotSelectProfilesAndScrollOffsetsAreRespected() {
        Fixture fixture = new Fixture();
        for (int i = 0; i < 8; i++) {
            fixture.config.createProfile();
        }
        ProfileListLayout layout = ProfileListLayout.of(400, 220,
                fixture.selection.plan().slotCount(), false, ConfigScreen.ROW_HEIGHT);

        assertFalse(fixture.selection.selectAt(layout, layout.startX() - 1, layout.startY()));
        assertFalse(fixture.selection.selectAt(layout, layout.startX() + layout.listWidth(), layout.startY()));
        assertFalse(fixture.selection.selectAt(layout, layout.startX(), layout.startY() - 1));
        assertFalse(fixture.selection.selectAt(layout, layout.startX(),
                layout.startY() + layout.viewportHeight()));
        assertTrue(fixture.effects.isEmpty());

        assertTrue(fixture.selection.selectAt(layout, layout.startX(), layout.startY()));
        assertEquals(fixture.config.profile(1).fileName(), fixture.selection.selectedFile());
    }

    @Test
    void restoredSelectionUsesLiveConfigAndDeleteModeHidesItsStrip() {
        Fixture fixture = new Fixture();
        String selected = fixture.config.createProfile().fileName();
        fixture.selection.restore(selected);
        assertTrue(fixture.effects.isEmpty());

        fixture.config = fixture.config.copy();
        fixture.config.setProfileName(fixture.config.profileIndexByFileName(selected), "AAAA");
        fixture.config.cycleProfileSortMode();
        assertEquals(fixture.config.profileIndexByFileName(selected), fixture.selection.plan().selectedIndex());

        fixture.deleteMode = true;
        assertFalse(fixture.selection.plan().stripOpen());
        assertEquals(selected, fixture.selection.selectedFile());
        fixture.deleteMode = false;
        assertTrue(fixture.selection.plan().stripOpen());
        fixture.config.stageDeleteProfile(fixture.config.profileIndexByFileName(selected));
        assertFalse(fixture.selection.plan().stripOpen());
        assertTrue(fixture.effects.isEmpty());
    }

    private static final class Fixture {
        Config config = new Config();
        boolean deleteMode;
        final List<String> effects = new ArrayList<>();
        final ProfileListSelectionController selection = new ProfileListSelectionController(
                () -> config, () -> deleteMode, slot -> effects.add("scroll:" + slot),
                () -> effects.add("rebuild"));

        ProfileListLayout layout() {
            return ProfileListLayout.of(400, 400, selection.plan().slotCount(), false, 0);
        }
    }
}
