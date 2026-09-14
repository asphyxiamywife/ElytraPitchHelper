package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import net.minecraft.client.Minecraft;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class ConfigScreenHistoryControllerTest {
    @Test
    void undoAndRedoPreserveUnrelatedCurrentSettingsAndUsage() {
        Host host = new Host();
        ConfigScreenHistoryController history = new ConfigScreenHistoryController(host);
        history.resetBaseline();
        boolean before = host.config.visibility().showInThirdPerson();
        boolean firework = host.config.visibility().showOnlyWithFirework();
        history.begin("show_in_third_person");
        host.config.replaceProfile(0, host.config.profile(0).withVisibility(
                host.config.visibility().withShowInThirdPerson(!before)));
        history.commit(true, false);
        host.config.enabled = false;
        host.config.replaceProfile(0, host.config.profile(0).withVisibility(
                host.config.visibility().withShowOnlyWithFirework(!firework)));
        host.config.commandPaletteUsage = host.config.commandPaletteUsage.recordUse("enabled", 1000);
        host.config.sectionCollapse = host.config.sectionCollapse.expandedAll();
        host.config.usedSettingsSearch = true;
        assertTrue(history.restore(false));
        assertEquals(before, host.config.visibility().showInThirdPerson());
        assertEquals(!firework, host.config.visibility().showOnlyWithFirework());
        assertFalse(host.config.enabled);
        assertTrue(host.config.usedSettingsSearch);
        assertTrue(host.config.commandPaletteUsage.actions().containsKey("enabled"));
        assertTrue(host.config.sectionCollapse.collapsed().isEmpty());
        host.config.profileSortMode = Config.PROFILE_SORT_NAME;
        assertTrue(history.restore(true));
        assertEquals(!before, host.config.visibility().showInThirdPerson());
        assertEquals(!firework, host.config.visibility().showOnlyWithFirework());
        assertEquals(Config.PROFILE_SORT_NAME, host.config.profileSortMode);
        assertFalse(host.config.enabled);
        assertEquals(2, host.saves);
        assertEquals(2, host.contextRestores);
    }

    @Test
    void undoPreservesProfilesAddedOutsideTheRecordedAction() {
        Host host = new Host();
        ConfigScreenHistoryController history = new ConfigScreenHistoryController(host);
        history.resetBaseline();
        history.begin("enabled");
        host.config.enabled = false;
        history.commit(true, false);
        String added = host.config.createProfile().fileName();
        assertTrue(history.restore(false));
        assertTrue(host.config.enabled);
        assertTrue(host.config.profileIndexByFileName(added) >= 0);
        assertTrue(history.restore(true));
        assertFalse(host.config.enabled);
        assertTrue(host.config.profileIndexByFileName(added) >= 0);
    }

    @Test
    void undoDoesNotRestoreAnUnrelatedDeletedProfile() {
        Host host = new Host();
        String removed = host.config.createProfile().fileName();
        ConfigScreenHistoryController history = new ConfigScreenHistoryController(host);
        history.resetBaseline();
        history.begin("enabled");
        host.config.enabled = false;
        history.commit(true, false);
        host.config.stageDeleteProfile(host.config.profileIndexByFileName(removed));
        assertTrue(history.restore(false));
        assertTrue(host.config.enabled);
        assertEquals(-1, host.config.profileIndexByFileName(removed));
        assertTrue(history.restore(true));
        assertEquals(-1, host.config.profileIndexByFileName(removed));
    }

    @Test
    void coalescingDoesNotAbsorbInterveningUnrelatedChanges() {
        ConfigHistory history = new ConfigHistory();
        Host host = new Host();
        Config before = host.config.copy();
        Config first = before.copy();
        first.profileSortMode = Config.PROFILE_SORT_NAME;
        history.record("sort", before, host.historyContext(), first, host.historyContext(), true, 100);
        Config outside = first.copy();
        outside.enabled = false;
        Config second = outside.copy();
        second.profileSortMode = Config.PROFILE_SORT_MODIFIED;
        history.record("sort", outside, host.historyContext(), second, host.historyContext(), true, 200);
        var undo = history.undo();
        undo.config().prepareHistoryRestoreFrom(undo.actionSource(), second);
        assertFalse(undo.config().enabled);
        assertEquals(Config.PROFILE_SORT_NAME, undo.config().profileSortMode);
        assertTrue(history.canUndo());
    }

    @Test
    void undoOfDeletedTargetLeavesDocumentAndHistoryIntact() {
        Host host = new Host();
        String file = host.config.createProfile().fileName();
        ConfigScreenHistoryController history = new ConfigScreenHistoryController(host);
        history.resetBaseline();
        history.begin("rename");
        int index = host.config.profileIndexByFileName(file);
        host.config.replaceProfile(index, host.config.profile(index).withName("Renamed"));
        history.commit(true, false);
        host.config.stageDeleteProfile(host.config.profileIndexByFileName(file));
        Config current = host.config.copy();
        assertFalse(history.restore(false));
        assertTrue(current.hasSameState(host.config));
        assertTrue(history.canUndo());
        assertFalse(history.canRedo());
        assertEquals(0, host.saves);
        assertEquals(0, host.contextRestores);
    }

    @Test
    void redoOfCreationCannotReplaceAnOccupiedFilename() {
        Host host = new Host();
        ConfigScreenHistoryController history = new ConfigScreenHistoryController(host);
        history.resetBaseline();
        history.begin("create");
        var original = host.config.createProfile();
        history.commit(true, false);
        assertTrue(history.restore(false));
        var replacement = host.config.createProfile();
        int index = host.config.profileIndexByFileName(replacement.fileName());
        host.config.replaceProfile(index, replacement.withFileName(original.fileName()).withName("Replacement"));
        Config current = host.config.copy();
        assertFalse(history.restore(true));
        assertTrue(current.hasSameState(host.config));
        assertFalse(history.canUndo());
        assertTrue(history.canRedo());
        assertEquals(1, host.saves);
    }

    private static final class Host implements ConfigScreenHistoryController.Host {
        private final Config config = new Config().copy();
        private int saves;
        private int contextRestores;
        public Config config() { return config; }
        public ConfigHistory.Context historyContext() {
            return new ConfigHistory.Context(false, null, ConfigCategory.GENERAL,
                    null, 0, 0, false, null, List.of(), null);
        }
        public void restoreHistoryContext(ConfigHistory.Context context) { contextRestores++; }
        public void syncEditingProfileIndex() {}
        public void reconcileResetUndo() {}
        public void saveInternal(String key) { saves++; }
        public boolean savePending() { return false; }
        public boolean hasExternalRevision() { return false; }
        public void loadConfigSnapshot() {}
        public void rebuildWidgets() {}
        public void returnToConfigScreen() {}
        public Minecraft minecraftClient() { return null; }
    }
}
