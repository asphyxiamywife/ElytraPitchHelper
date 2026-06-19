package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigHistoryTest {
    private static final ConfigHistory.Context LIST_CONTEXT = new ConfigHistory.Context(false, null,
            ConfigCategory.GENERAL, null, 0, false, List.of());

    @Test
    void undoAndRedoRestoreFullSnapshots() {
        ConfigHistory history = new ConfigHistory();
        Config before = new Config();
        Config after = before.copy();
        after.enabled = false;

        history.record("enabled", before, LIST_CONTEXT, after, LIST_CONTEXT, false, 100L);

        ConfigHistory.Restore undone = history.undo();
        assertTrue(undone.config().enabled);
        ConfigHistory.Restore redone = history.redo();
        assertFalse(redone.config().enabled);
    }

    @Test
    void coalescedEditsKeepFirstBeforeAndLatestAfter() {
        ConfigHistory history = new ConfigHistory();
        Config original = new Config();
        Config first = original.copy();
        first.profileSortMode = Config.PROFILE_SORT_NAME;
        Config second = first.copy();
        second.profileSortMode = Config.PROFILE_SORT_MODIFIED;

        history.record("profile-name", original, LIST_CONTEXT, first, LIST_CONTEXT, true, 100L);
        history.record("profile-name", first, LIST_CONTEXT, second, LIST_CONTEXT, true, 200L);

        assertEquals(Config.PROFILE_SORT_CREATED, history.undo().config().profileSortMode);
        assertNull(history.undo());
        assertEquals(Config.PROFILE_SORT_MODIFIED, history.redo().config().profileSortMode);
    }

    @Test
    void newEditClearsRedoAndBoundaryStopsCoalescing() {
        ConfigHistory history = new ConfigHistory();
        Config original = new Config();
        Config first = original.copy();
        first.enabled = false;
        history.record("enabled", original, LIST_CONTEXT, first, LIST_CONTEXT, true, 100L);
        history.breakCoalescing();

        Config second = first.copy();
        second.profileSortMode = Config.PROFILE_SORT_NAME;
        history.record("enabled", first, LIST_CONTEXT, second, LIST_CONTEXT, true, 200L);
        ConfigHistory.Restore boundaryUndo = history.undo();
        assertEquals(Config.PROFILE_SORT_CREATED, boundaryUndo.config().profileSortMode);
        assertFalse(boundaryUndo.config().enabled);
        assertTrue(history.undo().config().enabled);

        Config replacement = first.copy();
        replacement.profileSortMode = Config.PROFILE_SORT_MODIFIED;
        history.record("sort", first, LIST_CONTEXT, replacement, LIST_CONTEXT, false, 300L);
        assertNull(history.redo());
    }

    @Test
    void transactionCanBeCancelledWithoutCreatingEntry() {
        ConfigHistory history = new ConfigHistory();
        Config config = new Config();
        history.begin("slider", config, LIST_CONTEXT);
        history.commit(config, LIST_CONTEXT, false, false, 100L);
        assertNull(history.undo());
    }

    @Test
    void resetStateStopsMatchingAfterGlobalUndoRestoresPreviousProfile() {
        Config resetApplied = new Config();
        resetApplied.selectProfile(0);
        String profileFile = resetApplied.profile(0).fileName;
        Config restoredBeforeReset = resetApplied.copy();
        restoredBeforeReset.profile(0).pitch.targetUpMinecraft = -12.0f;

        assertTrue(ConfigScreen.resetStateMatches(resetApplied, resetApplied.copy(), profileFile));
        assertFalse(ConfigScreen.resetStateMatches(restoredBeforeReset, resetApplied, profileFile));
    }
}
