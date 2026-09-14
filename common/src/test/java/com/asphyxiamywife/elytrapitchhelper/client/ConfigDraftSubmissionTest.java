package com.asphyxiamywife.elytrapitchhelper.client;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.testing.IsolatedConfigRoot;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfigDraftSubmissionTest {
    @IsolatedConfigRoot Path root;

    @Test
    void resubmittingAcceptedDraftPreservesNewerPendingEdits() throws Exception {
        ClientConfigStore.initialize();
        Config older = ClientConfigStore.get();
        older.enabled = false;
        assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                ClientConfigStore.saveAsync(older).get(10, TimeUnit.SECONDS).result().outcome());
        assertFalse(ClientConfigStore.get().enabled);

        Config newerEdit = ClientConfigStore.get();
        newerEdit.usedSettingsSearch = true;
        ClientConfigStore.update(newerEdit);

        assertTrue(ClientConfigStore.get().usedSettingsSearch);
        assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                ClientConfigStore.saveAsync(older).get(10, TimeUnit.SECONDS).result().outcome());
        assertTrue(ClientConfigStore.get().usedSettingsSearch);
        assertTrue(Config.reloadFromDisk(ClientConfigStore.store().fileSystem()).usedSettingsSearch);
    }

    @Test
    void independentDraftsMergeProfileSettingsAndMainValues() throws Exception {
        ClientConfigStore.initialize();
        Config first = ClientConfigStore.get();
        Config second = first.copy();
        boolean originalThirdPerson = first.visibility().showInThirdPerson();
        boolean originalFirework = first.visibility().showOnlyWithFirework();
        first.enabled = !first.enabled;
        first.replaceProfile(first.activeProfileIndex, first.activeProfile().withVisibility(
                first.visibility().withShowInThirdPerson(!originalThirdPerson)));
        ClientConfigStore.update(first);
        second.usedSettingsSearch = true;
        second.replaceProfile(second.activeProfileIndex, second.activeProfile().withVisibility(
                second.visibility().withShowOnlyWithFirework(!originalFirework)));
        assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                ClientConfigStore.saveAsync(second).get(10, TimeUnit.SECONDS).result().outcome());
        Config saved = Config.reloadFromDisk(ClientConfigStore.store().fileSystem());
        assertEquals(first.enabled, saved.enabled);
        assertTrue(saved.usedSettingsSearch);
        assertEquals(!originalThirdPerson, saved.visibility().showInThirdPerson());
        assertEquals(!originalFirework, saved.visibility().showOnlyWithFirework());
    }

    @Test
    void acceptedObjectAndEarlierCopyKeepIndependentEditComparisons() throws Exception {
        ClientConfigStore.initialize();
        Config first = ClientConfigStore.get();
        first.enabled = !first.enabled;
        Config fork = first.copy();
        ClientConfigStore.update(first);
        Config newer = ClientConfigStore.get();
        newer.enabled = !newer.enabled;
        newer.usedSettingsSearch = true;
        ClientConfigStore.update(newer);
        ClientConfigStore.update(first);
        assertEquals(newer.enabled, ClientConfigStore.get().enabled);
        ClientConfigStore.update(fork);
        assertEquals(fork.enabled, ClientConfigStore.get().enabled);
        assertTrue(ClientConfigStore.get().usedSettingsSearch);
        assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                ClientConfigStore.flushPending().get(10, TimeUnit.SECONDS).result().outcome());
    }

    @Test
    void historyRestoreExplicitlyComparesAgainstCurrentValues() {
        ClientConfigStore.initialize();
        Config history = ClientConfigStore.get();
        Config edit = ClientConfigStore.get();
        edit.enabled = !edit.enabled;
        ClientConfigStore.update(edit);
        history.prepareSnapshotRestoreFrom(ClientConfigStore.get());
        ClientConfigStore.update(history);
        assertEquals(history.enabled, ClientConfigStore.get().enabled);
    }

    @Test
    void independentMapEntriesAndSectionFoldsSurviveStaleSubmission() throws Exception {
        ClientConfigStore.initialize();
        Config first = ClientConfigStore.get();
        Config second = ClientConfigStore.get();
        var firstDimension = "test:first";
        var secondDimension = "test:second";
        first.replaceProfile(first.activeProfileIndex, first.activeProfile().withVoidWarning(
                first.voidWarning().withDimensionYOverrides(java.util.Map.of(firstDimension, -32))));
        second.replaceProfile(second.activeProfileIndex, second.activeProfile().withVoidWarning(
                second.voidWarning().withDimensionYOverrides(java.util.Map.of(secondDimension, -64))));
        first.commandPaletteUsage = first.commandPaletteUsage.recordUse("enabled", 1000);
        second.commandPaletteUsage = second.commandPaletteUsage.recordUse("show_in_third_person", 1000);
        var categories = com.asphyxiamywife.elytrapitchhelper.screen.ConfigCategory.values();
        first.sectionCollapse = first.sectionCollapse.toggled(categories[0]);
        second.sectionCollapse = second.sectionCollapse.toggled(categories[1]);
        ClientConfigStore.update(first);
        assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                ClientConfigStore.saveAsync(second).get(10, TimeUnit.SECONDS).result().outcome());
        Config saved = Config.reloadFromDisk(ClientConfigStore.store().fileSystem());
        assertEquals(-32, saved.voidWarning().dimensionYOverrides().get(firstDimension));
        assertEquals(-64, saved.voidWarning().dimensionYOverrides().get(secondDimension));
        assertEquals(first.sectionCollapse.isCollapsed(categories[0]), saved.sectionCollapse.isCollapsed(categories[0]));
        assertEquals(second.sectionCollapse.isCollapsed(categories[1]), saved.sectionCollapse.isCollapsed(categories[1]));
        assertTrue(saved.commandPaletteUsage.actions().containsKey("enabled"));
        assertTrue(saved.commandPaletteUsage.actions().containsKey("show_in_third_person"));
    }

    @Test
    void oldReadOnlyDraftBecomesWritableWithoutApprovingExternalBytes() throws Exception {
        ClientConfigStore.initialize();
        var store = ClientConfigStore.store();
        String writable = java.nio.file.Files.readString(store.configPath());
        var json = com.google.gson.JsonParser.parseString(writable).getAsJsonObject();
        json.addProperty("version", 999);
        java.nio.file.Files.writeString(store.configPath(), json.toString());
        assertTrue(store.reloadFromDiskIfIdle());
        Config draft = store.get();
        assertTrue(draft.isReadOnly());
        java.nio.file.Files.writeString(store.configPath(), writable);
        assertTrue(store.reloadFromDiskIfIdle());
        draft.enabled = !draft.enabled;
        store.update(draft);
        assertFalse(store.get().isReadOnly());
        assertEquals(ClientConfigStore.SaveOutcome.CONFLICTED,
                store.flushPending().get(5, TimeUnit.SECONDS).result().outcome());
        assertEquals(writable, java.nio.file.Files.readString(store.configPath()));
        Config approved = store.get();
        approved.acceptConflictBaseline(store.fileSystem(),
                Config.captureConflictBaseline(store.fileSystem(), java.util.List.of(store.configPath())));
        assertCommitted(approved);
        assertEquals(draft.enabled, Config.reloadFromDisk(store.fileSystem()).enabled);
    }

    @Test
    void oldWritableDraftCannotClearReadOnlyStateAfterReload() throws Exception {
        ClientConfigStore.initialize();
        Config draft = ClientConfigStore.get();
        var store = ClientConfigStore.store();
        var json = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(store.configPath()))
                .getAsJsonObject();
        json.addProperty("version", 999);
        String external = json.toString();
        java.nio.file.Files.writeString(store.configPath(), external);
        assertTrue(store.reloadFromDiskIfIdle());
        draft.enabled = !draft.enabled;
        ClientConfigStore.update(draft);
        assertTrue(ClientConfigStore.get().isReadOnly());
        assertEquals(external, java.nio.file.Files.readString(store.configPath()));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void staleDraftCannotOverwriteOrDeleteRecreatedProfile(boolean delete) throws Exception {
        ClientConfigStore.initialize();
        Config setup = ClientConfigStore.get();
        String file = setup.createProfile().fileName();
        assertCommitted(setup);
        Config stale = ClientConfigStore.get();
        Config removal = ClientConfigStore.get();
        removal.stageDeleteProfile(removal.profileIndexByFileName(file));
        assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                ClientConfigStore.saveAsync(removal, java.util.List.of(file)).get(5, TimeUnit.SECONDS).result().outcome());
        Config replacement = ClientConfigStore.get();
        assertEquals(file, replacement.createProfile().fileName());
        assertCommitted(replacement);
        Path path = ClientConfigStore.store().profileDirectory().resolve(file);
        String replacementBytes = java.nio.file.Files.readString(path);
        if (delete) {
            stale.stageDeleteProfile(stale.profileIndexByFileName(file));
        } else {
            int index = stale.profileIndexByFileName(file);
            stale.replaceProfile(index, stale.profile(index).withName("Old draft edit"));
        }
        var result = ClientConfigStore.saveAsync(stale, delete ? java.util.List.of(file) : java.util.List.of())
                .get(5, TimeUnit.SECONDS).result();
        assertEquals(ClientConfigStore.SaveOutcome.CONFLICTED, result.outcome());
        assertEquals(replacementBytes, java.nio.file.Files.readString(path));
        Config sibling = stale.copy();
        stale.acceptConflictBaseline(ClientConfigStore.store().fileSystem(),
                Config.captureConflictBaseline(ClientConfigStore.store().fileSystem(), java.util.List.of(path)));
        assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                ClientConfigStore.saveAsync(stale, delete ? java.util.List.of(file) : java.util.List.of())
                        .get(5, TimeUnit.SECONDS).result().outcome());
        if (!delete) {
            int index = sibling.profileIndexByFileName(file);
            sibling.replaceProfile(index, sibling.profile(index).withName("Unapproved sibling edit"));
            assertEquals(ClientConfigStore.SaveOutcome.CONFLICTED,
                    ClientConfigStore.saveAsync(sibling).get(5, TimeUnit.SECONDS).result().outcome());
            assertEquals("Old draft edit", Config.reloadFromDisk(ClientConfigStore.store().fileSystem())
                    .profileName(index));
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void independentDraftsAllocateDistinctProfileFiles(boolean duplicate, boolean firstAlreadySaved) throws Exception {
        ClientConfigStore.initialize();
        Config first = ClientConfigStore.get();
        Config second = first.copy();
        int initialCount = first.profileCount();
        String firstFile = (duplicate ? first.duplicateProfile(0) : first.createProfile()).fileName();
        int firstIndex = first.profileIndexByFileName(firstFile);
        first.replaceProfile(firstIndex, first.profile(firstIndex).withName("First draft"));
        if (firstAlreadySaved) assertCommitted(first);
        String secondFile = (duplicate ? second.duplicateProfile(0) : second.createProfile()).fileName();
        org.junit.jupiter.api.Assertions.assertNotEquals(firstFile, secondFile);
        int secondIndex = second.profileIndexByFileName(secondFile);
        second.replaceProfile(secondIndex, second.profile(secondIndex).withName("Second draft"));
        if (!firstAlreadySaved) ClientConfigStore.update(first);
        assertCommitted(second);
        Config saved = Config.reloadFromDisk(ClientConfigStore.store().fileSystem());
        assertEquals(initialCount + 2, saved.profileCount());
        assertEquals("First draft", saved.profileName(saved.profileIndexByFileName(firstFile)));
        assertEquals("Second draft", saved.profileName(saved.profileIndexByFileName(secondFile)));
        secondIndex = second.profileIndexByFileName(secondFile);
        second.replaceProfile(secondIndex, second.profile(secondIndex).withName("Second edited again"));
        assertCommitted(second);
        saved = Config.reloadFromDisk(ClientConfigStore.store().fileSystem());
        assertEquals("First draft", saved.profileName(saved.profileIndexByFileName(firstFile)));
        assertEquals("Second edited again", saved.profileName(saved.profileIndexByFileName(secondFile)));
    }

    @Test
    void creationDuringPendingDeletionUsesANewFile() throws Exception {
        ClientConfigStore.initialize();
        Config setup = ClientConfigStore.get();
        String deletedFile = setup.createProfile().fileName();
        assertCommitted(setup);
        Config removal = ClientConfigStore.get();
        removal.stageDeleteProfile(removal.profileIndexByFileName(deletedFile));
        ClientConfigStore.update(removal, java.util.List.of(deletedFile));
        Config next = ClientConfigStore.get();
        String newFile = next.createProfile().fileName();
        org.junit.jupiter.api.Assertions.assertNotEquals(deletedFile, newFile);
        assertCommitted(next);
        var directory = ClientConfigStore.store().profileDirectory();
        assertFalse(java.nio.file.Files.exists(directory.resolve(deletedFile)));
        assertTrue(java.nio.file.Files.exists(directory.resolve(newFile)));
        assertTrue(ClientConfigStore.get().profileIndexByFileName(newFile) >= 0);
    }

    @Test
    void simultaneousDraftAllocationsAreDistinct() throws Exception {
        ClientConfigStore.initialize();
        Config first = ClientConfigStore.get();
        Config second = first.copy();
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return first.createProfile().fileName();
            });
            var b = executor.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return second.createProfile().fileName();
            });
            start.countDown();
            org.junit.jupiter.api.Assertions.assertNotEquals(a.get(5, TimeUnit.SECONDS), b.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void historyDeltaPreservesCurrentProfileBaselinesForLaterEdits() throws Exception {
        ClientConfigStore.initialize();
        Config before = ClientConfigStore.get();
        Config after = before.copy();
        after.enabled = !after.enabled;
        assertCommitted(after);
        Config unrelated = ClientConfigStore.get();
        String added = unrelated.createProfile().fileName();
        assertCommitted(unrelated);
        Config undo = before.copy();
        assertTrue(undo.prepareHistoryRestoreFrom(after, ClientConfigStore.get()));
        assertCommitted(undo);
        assertEquals(before.enabled, ClientConfigStore.get().enabled);
        int index = undo.profileIndexByFileName(added);
        assertTrue(index >= 0);
        undo.replaceProfile(index, undo.profile(index).withName("Edited after undo"));
        assertCommitted(undo);
        Config disk = Config.reloadFromDisk(ClientConfigStore.store().fileSystem());
        assertEquals("Edited after undo", disk.profileName(disk.profileIndexByFileName(added)));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void historyCannotModifyARecreatedProfileIdentity(boolean undoCreation) throws Exception {
        ClientConfigStore.initialize();
        Config target = ClientConfigStore.get();
        Config created = target.copy();
        String file = created.createProfile().fileName();
        assertCommitted(created);
        Config source = created;
        if (!undoCreation) {
            target = ClientConfigStore.get();
            source = target.copy();
            int index = source.profileIndexByFileName(file);
            source.replaceProfile(index, source.profile(index).withName("Action rename"));
            assertCommitted(source);
        }
        Config removal = ClientConfigStore.get();
        removal.stageDeleteProfile(removal.profileIndexByFileName(file));
        assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                ClientConfigStore.saveAsync(removal, java.util.List.of(file)).get(5, TimeUnit.SECONDS).result().outcome());
        Config recreated = ClientConfigStore.get();
        assertEquals(file, recreated.createProfile().fileName());
        assertCommitted(recreated);
        Config current = ClientConfigStore.get();
        Config untouchedTarget = target.copy();
        assertFalse(target.prepareHistoryRestoreFrom(source, current));
        assertTrue(untouchedTarget.hasSameState(target));
        assertTrue(current.hasSameState(ClientConfigStore.get()));
        assertFalse(ClientConfigStore.hasUnsavedChanges());
    }

    @Test
    void managedReplacementRejectsWrongIdentityAndOutOfRangeIndexes() {
        ClientConfigStore.initialize();
        Config draft = ClientConfigStore.get();
        var other = draft.createProfile();
        int index = draft.profileIndexByFileName(other.fileName());
        Config unchanged = draft.copy();
        assertFalse(draft.replaceProfile(index, draft.profile(0).withName("Wrong target")));
        assertFalse(draft.replaceProfile(-1, other.withName("Negative index")));
        assertFalse(draft.replaceProfile(draft.profileCount(), other.withName("Deleted index")));
        assertFalse(draft.replaceProfile(index, other.withFileName("unrelated.json")));
        assertTrue(unchanged.hasSameState(draft));
        assertFalse(ClientConfigStore.hasUnsavedChanges());
    }

    @Test
    void managedReplacementRetainsExactFilenameAndAcceptsValues() throws Exception {
        ClientConfigStore.initialize();
        Config draft = ClientConfigStore.get();
        String file = draft.profile(0).fileName();
        assertTrue(draft.replaceProfile(0, draft.profile(0).withName("Same identity")
                .withFileName(file.toUpperCase(java.util.Locale.ROOT))));
        assertEquals(file, draft.profile(0).fileName());
        assertTrue(draft.replaceProfile(0, draft.profile(0).withName("No filename supplied").withFileName(null)));
        assertEquals(file, draft.profile(0).fileName());
        assertCommitted(draft);
        Config disk = Config.reloadFromDisk(ClientConfigStore.store().fileSystem());
        assertEquals("No filename supplied", disk.profileName(disk.profileIndexByFileName(file)));
    }

    @Test
    void detachedReplacementRetainsItsExplicitFilenameAndIndexPolicy() {
        Config detached = new Config().copy();
        assertTrue(detached.replaceProfile(-1, detached.profile(0).withFileName("explicit.json")));
        assertEquals("explicit.json", detached.profile(0).fileName());
    }

    private static void assertCommitted(Config config) throws Exception {
        assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                ClientConfigStore.saveAsync(config).get(5, TimeUnit.SECONDS).result().outcome());
    }
}
