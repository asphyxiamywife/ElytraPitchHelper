package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingReset;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingSpec;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingsRegistry;
import com.asphyxiamywife.elytrapitchhelper.util.MonotonicClock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigHistoryTest {
    private static final ConfigHistory.Context LIST_CONTEXT = new ConfigHistory.Context(false, null,
            ConfigCategory.GENERAL, null, 0, 0, false, null, List.of(), null);

    @Test
    void coalescedKeyboardEditsRestoreSettingFocusAndScrollOnUndoAndRedo() {
        ConfigHistory history = new ConfigHistory();
        Config before = new Config();
        Config after = before.copy();
        after.enabled = false;
        ConfigHistory.Context context = new ConfigHistory.Context(true, before.profile(0).fileName(),
                ConfigCategory.AMPLITUDE, null, 0, 240, false, null, List.of(), "amplitude_up_velocity");

        history.begin("amplitude_up_velocity", before, context);
        history.commit(after, context, true, true, 100L);
        history.begin("amplitude_up_velocity", after, context);
        history.commit(before, context, true, true, 200L);

        ConfigHistory.Restore undone = history.undo();
        assertEquals("amplitude_up_velocity", undone.context().focusedSetting());
        assertEquals(240, undone.context().editorScroll());
        assertNull(history.undo());
        ConfigHistory.Restore redone = history.redo();
        assertEquals("amplitude_up_velocity", redone.context().focusedSetting());
        assertEquals(240, redone.context().editorScroll());
    }

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
    void backwardClockStepDoesNotCoalesceUnrelatedEdits() {
        ConfigHistory history = new ConfigHistory();
        Config original = new Config();
        Config first = original.copy();
        first.profileSortMode = Config.PROFILE_SORT_NAME;
        Config second = first.copy();
        second.profileSortMode = Config.PROFILE_SORT_MODIFIED;

        history.record("profile-name", original, LIST_CONTEXT, first, LIST_CONTEXT, true, 1_000L);
        history.record("profile-name", first, LIST_CONTEXT, second, LIST_CONTEXT, true, 500L);

        assertEquals(Config.PROFILE_SORT_NAME, history.undo().config().profileSortMode);
        assertEquals(Config.PROFILE_SORT_CREATED, history.undo().config().profileSortMode);
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
    void aNewGestureCommitsAnInterruptedGestureBeforeStarting() {
        ConfigHistory history = new ConfigHistory();
        Config original = new Config();
        Config first = original.copy();
        first.profileSortMode = Config.PROFILE_SORT_NAME;
        Config second = first.copy();
        second.enabled = false;

        history.begin("first-slider", original, LIST_CONTEXT);
        history.begin("second-slider", first, LIST_CONTEXT);
        history.commit(second, LIST_CONTEXT, true, false, 200L);

        assertTrue(history.undo().config().enabled);
        assertEquals(Config.PROFILE_SORT_CREATED, history.undo().config().profileSortMode);
    }

    @Test
    void interruptedGestureUsesTheSameClockAsItsSuccessor() {
        ConfigHistory history = new ConfigHistory();
        Config original = new Config();
        Config first = original.copy();
        first.profileSortMode = Config.PROFILE_SORT_NAME;
        Config second = first.copy();
        second.profileSortMode = Config.PROFILE_SORT_MODIFIED;

        history.begin("slider", original, LIST_CONTEXT);
        history.begin("slider", first, LIST_CONTEXT);
        history.commit(second, LIST_CONTEXT, true, true, MonotonicClock.millis());

        assertEquals(Config.PROFILE_SORT_CREATED, history.undo().config().profileSortMode);
        assertNull(history.undo(), "the interrupted and successor gestures should coalesce");
    }

    @Test
    void resetStateStopsMatchingAfterGlobalUndoRestoresPreviousProfile() {
        Config resetApplied = new Config();
        resetApplied.selectProfile(0);
        String profileFile = resetApplied.profile(0).fileName();
        Config restoredBeforeReset = resetApplied.copy();
        restoredBeforeReset.replaceProfile(0, restoredBeforeReset.profile(0).withPitch(
                restoredBeforeReset.profile(0).pitch().withTargetUpMinecraft(-12.0f)));

        assertTrue(ConfigScreen.resetStateMatches(resetApplied, resetApplied.copy(), profileFile));
        assertFalse(ConfigScreen.resetStateMatches(restoredBeforeReset, resetApplied, profileFile));
    }

    @Test
    void resettingDiveSpeedCoordinatesThePairAndUndoRestoresBothValues() {
        ConfigHistory history = new ConfigHistory();
        Config edited = new Config();
        SettingSpec<Float> up = floatSpec("amplitude_up_velocity");
        SettingSpec<Float> down = floatSpec("amplitude_down_velocity");
        edited.replaceProfile(0, up.apply(edited.profile(0), 4.0f));
        assertEquals(4.05f, edited.profile(0).amplitude().downVelocity());
        assertEquals(4.0f, edited.profile(0).amplitude().upVelocity());

        Config reset = edited.copy();
        SettingReset.Unit unit = SettingReset.forSetting(down.id()).orElseThrow();
        reset.replaceProfile(0, SettingReset.reset(unit, reset.profile(0), Config.defaultProfileTemplate()));
        history.record("reset-" + down.id(), edited, LIST_CONTEXT, reset, LIST_CONTEXT, false, 100L);

        assertEquals(2.2f, reset.profile(0).amplitude().downVelocity());
        assertEquals(2.15f, reset.profile(0).amplitude().upVelocity(),
                "Reset speed follows the reset Dive speed instead of blocking the reset");

        Config restored = history.undo().config();
        assertEquals(4.05f, restored.profile(0).amplitude().downVelocity());
        assertEquals(4.0f, restored.profile(0).amplitude().upVelocity());
    }

    @SuppressWarnings("unchecked")
    private static SettingSpec<Float> floatSpec(String id) {
        return (SettingSpec<Float>) SettingsRegistry.byId(id);
    }
}
