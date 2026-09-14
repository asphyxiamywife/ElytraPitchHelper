package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import com.asphyxiamywife.elytrapitchhelper.config.SectionCollapseSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ScrollMemoryTest {
    private static final String ALPHA = "alpha.json";
    private static final String BETA = "beta.json";

    @BeforeEach
    void clearMemory() {
        ScrollMemory.rememberProfileList(0);
        ScrollMemory.forget(ALPHA);
        ScrollMemory.forget(BETA);
    }

    @Test
    void listsNeverScrolledStartAtTheTop() {
        assertEquals(0, ScrollMemory.profileList());
        assertEquals(0, ScrollMemory.editor(ALPHA));
        assertEquals(0, ScrollMemory.editor(null));
    }

    @Test
    void eachProfileKeepsItsOwnPosition() {
        ScrollMemory.rememberEditor(ALPHA, 120);
        ScrollMemory.rememberEditor(BETA, 48);

        assertEquals(120, ScrollMemory.editor(ALPHA));
        assertEquals(48, ScrollMemory.editor(BETA));
    }

    @Test
    void theProfileListIsRememberedApartFromTheEditors() {
        ScrollMemory.rememberProfileList(72);
        ScrollMemory.rememberEditor(ALPHA, 120);

        assertEquals(72, ScrollMemory.profileList());
        assertEquals(120, ScrollMemory.editor(ALPHA));
    }

    @Test
    void anAbsentProfileFileIsIgnored() {
        ScrollMemory.rememberEditor(null, 120);

        assertEquals(0, ScrollMemory.editor(null));
    }

    @Test
    void nothingIsRememberedAboveTheTop() {
        ScrollMemory.rememberProfileList(-40);
        ScrollMemory.rememberEditor(ALPHA, -40);

        assertEquals(0, ScrollMemory.profileList());
        assertEquals(0, ScrollMemory.editor(ALPHA));
    }

    @Test
    void aDeletedProfileIsForgotten() {
        ScrollMemory.rememberEditor(ALPHA, 120);
        ScrollMemory.forget(ALPHA);

        assertEquals(0, ScrollMemory.editor(ALPHA));
    }

    @Test
    void noProfileIsForgottenWhileTheGameRuns() {
        int profiles = 200;
        for (int profile = 0; profile < profiles; profile++) {
            ScrollMemory.rememberEditor("profile" + profile + ".json", profile + 1);
        }

        for (int profile = 0; profile < profiles; profile++) {
            assertEquals(profile + 1, ScrollMemory.editor("profile" + profile + ".json"),
                    "profile " + profile + " was dropped");
        }
    }

    @Test
    void restoringPastTheEndSettlesOnTheLastPage() {
        ScrollMemory.rememberEditor(ALPHA, 100_000);
        EditorHost host = new EditorHost();
        ConfigEditorScrollController controller = new ConfigEditorScrollController(host);

        controller.restore(ScrollMemory.editor(ALPHA));

        assertEquals(controller.layout().maxScroll(), controller.scroll());
    }

    @Test
    void restoringInsideTheContentKeepsThePositionExactly() {
        ScrollMemory.rememberEditor(ALPHA, 96);
        ConfigEditorScrollController controller = new ConfigEditorScrollController(new EditorHost());

        controller.restore(ScrollMemory.editor(ALPHA));

        assertEquals(96, controller.scroll());
    }

    @Test
    void unsettledFoldsMeasureTooLittleToHoldAPosition() {
        SectionAnimation animation = new SectionAnimation();
        SectionCollapseSettings collapse = new SectionCollapseSettings().expandedAll();
        ConfigEditorScrollController controller =
                new ConfigEditorScrollController(new PanelHost(collapse, animation));
        ScrollMemory.rememberEditor(ALPHA, 240);

        controller.restore(ScrollMemory.editor(ALPHA));
        assertTrue(controller.scroll() < 240,
                "an unsettled screen should not be able to hold the position - if it can, this test "
                        + "no longer guards the ordering it was written for");

        for (ConfigCategory section : ConfigCategory.values()) {
            animation.syncTo(section, !collapse.isCollapsed(section));
        }
        controller.restore(ScrollMemory.editor(ALPHA));
        assertEquals(240, controller.scroll());
    }

    private record PanelHost(SectionCollapseSettings collapse, SectionAnimation animation)
            implements ConfigEditorScrollController.Host {
        private static final Profile PROFILE = new Profile();

        @Override
        public void layoutEditorRows() {}

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
            return true;
        }

        @Override
        public int editorRowCount() {
            return ProfileEditorPlan.rowCount(PROFILE, "", collapse, key -> key, animation);
        }

        @Override
        public int editorContentHeight() {
            return ProfileEditorPlan.contentHeight(PROFILE, "", collapse, key -> key, animation, ConfigScreen.ROW_HEIGHT);
        }

        @Override
        public int screenWidth() {
            return 400;
        }

        @Override
        public int screenHeight() {
            return 300;
        }

        @Override
        public boolean readOnly() {
            return false;
        }
    }

    private static final class EditorHost implements ConfigEditorScrollController.Host {
        private static final int ROWS = 40;

        @Override
        public void layoutEditorRows() {}

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
            return true;
        }

        @Override
        public int editorRowCount() {
            return ROWS;
        }

        @Override
        public int editorContentHeight() {
            return ROWS * ConfigScreen.ROW_HEIGHT;
        }

        @Override
        public int screenWidth() {
            return 400;
        }

        @Override
        public int screenHeight() {
            return 300;
        }

        @Override
        public boolean readOnly() {
            return false;
        }
    }
}
