package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingsRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EditorSearchControllerTest {
    @Test
    void categoryJumpClearsSearchAndRevealsSectionOnce() {
        Config config = new Config();
        int[] saves = {0};
        EditorSearchController search = new EditorSearchController(() -> config,
                new SectionAnimation(), section -> 10, () -> saves[0]++);
        SettingsSearch.Entry entry = new SettingsSearch.Entry(SettingsRegistry.byId("void_warning"));
        search.setQuery("lookahead");
        search.setPendingSetting(entry);

        search.jumpToCategory(entry.category());

        assertEquals("", search.query());
        assertFalse(search.isPendingSetting(entry));
        assertFalse(config.sectionCollapse.isCollapsed(entry.category()));
        assertEquals(entry.category(), search.category());
        assertEquals(entry.category(), search.takePendingSection());
        assertNull(search.takePendingSection());
        assertEquals(1, saves[0]);
        search.jumpToCategory(entry.category());
        assertEquals(1, saves[0]);
    }

    @Test
    void widgetRebuildPreservesSearchAndPendingSetting() {
        Config config = new Config();
        EditorSearchController search = new EditorSearchController(() -> config,
                new SectionAnimation(), section -> 10, () -> {});
        SettingsSearch.Entry entry = new SettingsSearch.Entry(SettingsRegistry.byId("void_warning"));

        assertFalse(search.jumpToSetting(entry));
        search.setQuery("warning");
        search.widgetsRebuilt();

        assertEquals("warning", search.query());
        assertTrue(search.isPendingSetting(entry));
        search.setQuery(null);
        assertEquals("", search.query());
    }

    @Test
    void screenSettingDoesNotLeaveAControlFocusRequest() {
        Config config = new Config();
        EditorSearchController search = new EditorSearchController(() -> config,
                new SectionAnimation(), section -> 10, () -> {});
        SettingsSearch.Entry entry = new SettingsSearch.Entry(SettingsRegistry.byId("command_palette_appearance"));
        search.setPendingSetting(entry);
        search.setQuery("appearance");

        assertTrue(search.jumpToSetting(entry));
        assertFalse(search.isPendingSetting(entry));
        assertEquals("", search.query());
        assertFalse(config.sectionCollapse.isCollapsed(entry.category()));
    }
}
