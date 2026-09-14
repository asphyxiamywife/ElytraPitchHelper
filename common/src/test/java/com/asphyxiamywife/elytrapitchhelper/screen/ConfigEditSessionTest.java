package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfigEditSessionTest {
    @Test
    void profileEditSavesNewStateAndCanUndoAndRedo() {
        Fixture fixture = new Fixture();
        Config before = fixture.config.copy();

        fixture.edits.changeProfile("name", "rename", () -> fixture.config.setProfileName(0, "Edited"));

        assertEquals("Edited", fixture.saved.getFirst().profile(0).name());
        assertEquals(List.of("name"), fixture.usedSettings);
        assertFalse(fixture.resetAvailable);
        assertTrue(before.hasSameState(fixture.history.undo().config()));
        assertEquals("Edited", fixture.history.redo().config().profile(0).name());
        assertFalse(fixture.history.isActionActive());
    }

    @Test
    void unchangedPaletteActionPreservesRedoAndResetWithoutSaving() {
        Fixture fixture = new Fixture();
        fixture.edits.changeConfig("enabled", () -> fixture.config.enabled = !fixture.config.enabled);
        fixture.history.undo();
        fixture.saved.clear();

        fixture.edits.changeProfileIfChanged(null, "unsupported-toggle", () -> false);

        assertTrue(fixture.saved.isEmpty());
        assertTrue(fixture.usedSettings.isEmpty());
        assertTrue(fixture.resetAvailable);
        assertTrue(fixture.history.canRedo());
        assertFalse(fixture.history.canUndo());
        assertFalse(fixture.history.isActionActive());
    }

    @Test
    void globalEditPreservesProfileResetAndRemainsUndoable() {
        Fixture fixture = new Fixture();
        boolean enabled = fixture.config.enabled;

        fixture.edits.changeConfig("enabled", () -> fixture.config.enabled = !enabled);

        assertEquals(!enabled, fixture.saved.getFirst().enabled);
        assertTrue(fixture.resetAvailable);
        assertTrue(fixture.usedSettings.isEmpty());
        assertEquals(enabled, fixture.history.undo().config().enabled);
    }

    @Test
    void continuousEditsKeepTheirExistingHistoryBoundary() {
        Fixture fixture = new Fixture();
        Config before = fixture.config.copy();
        fixture.historyAdapter.begin("continuous-edit");
        fixture.config.setProfileName(0, "First");
        fixture.edits.profileChanged("name");
        fixture.config.setProfileName(0, "Second");
        fixture.edits.profileChanged("name");

        assertTrue(fixture.history.isActionActive());
        assertFalse(fixture.history.canUndo());
        fixture.historyAdapter.commit(true, false);
        assertEquals(2, fixture.saved.size());
        assertTrue(before.hasSameState(fixture.history.undo().config()));
        assertFalse(fixture.history.canUndo());
        assertEquals("Second", fixture.history.redo().config().profile(0).name());
    }

    private static final class Fixture {
        final Config config = new Config();
        final ConfigHistory history = new ConfigHistory();
        final List<Config> saved = new ArrayList<>();
        final List<String> usedSettings = new ArrayList<>();
        boolean resetAvailable = true;
        final ConfigEditSession.History historyAdapter = new ConfigEditSession.History() {
            @Override
            public void begin(String actionKey) {
                history.begin(actionKey, config, null);
            }

            @Override
            public void commit(boolean changed, boolean coalesce) {
                history.commit(config, null, changed, coalesce, 0L);
            }
        };
        final ConfigEditSession edits = new ConfigEditSession(historyAdapter,
                () -> resetAvailable = false, key -> saved.add(config.copy()), usedSettings::add);
    }
}
