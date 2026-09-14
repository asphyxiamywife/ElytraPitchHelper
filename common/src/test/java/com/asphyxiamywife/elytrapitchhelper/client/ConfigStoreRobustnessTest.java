package com.asphyxiamywife.elytrapitchhelper.client;

import com.asphyxiamywife.elytrapitchhelper.config.*;
import com.asphyxiamywife.elytrapitchhelper.testing.IsolatedConfigRoot;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

final class ConfigStoreRobustnessTest {
    @IsolatedConfigRoot Path root;

    @Test void rejectedSaveMustNotRemainInSavingPhase() throws Exception {
        ClientConfigStore.initialize();
        Config edited = ClientConfigStore.get();
        edited.enabled = !edited.enabled;
        assertTrue(ClientConfigStore.shutdownPersistenceCoordinator());
        var result = ClientConfigStore.saveAsync(edited).get(5, TimeUnit.SECONDS);
        assertEquals(ClientConfigStore.SaveOutcome.CANCELLED, result.result().outcome());
        assertNotEquals(ClientConfigStore.SavePhase.SAVING, ClientConfigStore.saveStatus().phase());
        assertEquals(ClientConfigStore.SaveOutcome.CANCELLED,
                ClientConfigStore.discardPendingSave(result.pendingGeneration()).outcome());
        assertFalse(ClientConfigStore.hasUnsavedChanges());
        assertEquals(!edited.enabled, ClientConfigStore.get().enabled);
    }

    @Test void approvedProfileBaselineMustBeUsedBySaveAsync() throws Exception {
        ClientConfigStore.initialize();
        var fs = ClientConfigStore.store().fileSystem();
        Config edited = ClientConfigStore.get();
        edited.replaceProfile(0, edited.profile(0).withName("Local name"));
        Path profilePath = edited.getProfilePath(fs, 0);
        String profileJson = java.nio.file.Files.readString(profilePath);
        java.nio.file.Files.writeString(profilePath, profileJson.replace(edited.profileName(0), "External name")
                .replace(ClientConfigStore.get().profileName(0), "External name"));
        var conflict = ClientConfigStore.saveAsync(edited).get(5, TimeUnit.SECONDS);
        assertEquals(ClientConfigStore.SaveOutcome.CONFLICTED, conflict.result().outcome());
        Config approved = ClientConfigStore.pendingSaveConfig();
        approved.acceptConflictBaseline(fs, Config.captureConflictBaseline(fs, conflict.result().conflict().conflictPaths()));
        assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                ClientConfigStore.saveAsync(approved).get(5, TimeUnit.SECONDS).result().outcome());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void staleDraftRebasesOnlyLocalCommits(boolean externalEdit) throws Exception {
        ClientConfigStore.initialize();
        var fs = ClientConfigStore.store().fileSystem();
        Config draft = ClientConfigStore.get();
        draft.enabled = !draft.enabled;
        draft.replaceProfile(0, draft.profile(0).withName("First local name"));
        assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                ClientConfigStore.saveAsync(draft).get(5, TimeUnit.SECONDS).result().outcome());
        if (externalEdit) {
            Config external = Config.reloadFromDisk(fs);
            external.replaceProfile(0, external.profile(0).withName("External name"));
            external.save(fs);
        }
        draft.profileSortMode = Config.PROFILE_SORT_NAME;
        draft.replaceProfile(0, draft.profile(0).withName("Second local name"));
        ClientConfigStore.update(draft);
        assertEquals(externalEdit ? ClientConfigStore.SaveOutcome.CONFLICTED : ClientConfigStore.SaveOutcome.COMMITTED,
                ClientConfigStore.saveAsync(draft).get(5, TimeUnit.SECONDS).result().outcome());
        if (!externalEdit) {
            draft.usedSettingsSearch = true;
            draft.replaceProfile(0, draft.profile(0).withName("Third local name"));
            assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                    ClientConfigStore.saveAsync(draft).get(5, TimeUnit.SECONDS).result().outcome());
            assertEquals("Third local name", Config.reloadFromDisk(fs).profileName(0));
        }
    }

    @Test void noOpDiskObservationDoesNotApproveExternalBytesForOlderDrafts() throws Exception {
        ClientConfigStore.initialize();
        var fs = ClientConfigStore.store().fileSystem();
        Config draft = ClientConfigStore.get();
        Path main = Config.getConfigPath(fs);
        java.nio.file.Files.writeString(main, java.nio.file.Files.readString(main) + " ");
        Config sameValues = ClientConfigStore.get();
        sameValues.replaceProfile(0, sameValues.profile(0).withName("A local profile edit"));
        assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                ClientConfigStore.saveAsync(sameValues).get(5, TimeUnit.SECONDS).result().outcome());
        draft.enabled = !draft.enabled;
        assertEquals(ClientConfigStore.SaveOutcome.CONFLICTED,
                ClientConfigStore.saveAsync(draft).get(5, TimeUnit.SECONDS).result().outcome());
    }

    @Test void oldDraftCannotResurrectALocallyDeletedProfile() throws Exception {
        ClientConfigStore.initialize();
        Config setup = ClientConfigStore.get();
        String added = setup.createProfile().fileName();
        assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                ClientConfigStore.saveAsync(setup).get(5, TimeUnit.SECONDS).result().outcome());
        Config stale = ClientConfigStore.get();
        Config deletion = ClientConfigStore.get();
        deletion.stageDeleteProfile(deletion.profileIndexByFileName(added));
        assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                ClientConfigStore.saveAsync(deletion, java.util.List.of(added))
                        .get(5, TimeUnit.SECONDS).result().outcome());
        stale.enabled = !stale.enabled;
        assertEquals(ClientConfigStore.SaveOutcome.CONFLICTED,
                ClientConfigStore.saveAsync(stale).get(5, TimeUnit.SECONDS).result().outcome());
        assertFalse(java.nio.file.Files.exists(ClientConfigStore.store().profileDirectory().resolve(added)));
    }

    @Test void retryReceivesANewGenerationAndFlushDeduplicatesTheActiveRequest() throws Exception {
        ClientConfigStore.initialize();
        var fs = com.asphyxiamywife.elytrapitchhelper.testing.FaultingConfigFileSystem.current();
        Config draft = ClientConfigStore.get();
        draft.enabled = !draft.enabled;
        fs.failWritesWhen(path -> true);
        var failed = ClientConfigStore.saveAsync(draft).get(5, TimeUnit.SECONDS);
        fs.failWritesWhen(null);
        var started = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        fs.beforeAtomicMove(() -> {
            started.countDown();
            try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
            catch (InterruptedException failure) { throw new AssertionError(failure); }
        });
        try {
            var retry = ClientConfigStore.flushPending();
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertSame(retry, ClientConfigStore.flushPending());
            release.countDown();
            var committed = retry.get(5, TimeUnit.SECONDS);
            assertEquals(ClientConfigStore.SaveOutcome.COMMITTED, committed.result().outcome());
            assertTrue(committed.pendingGeneration() > failed.pendingGeneration());
        } finally { release.countDown(); }
    }

    @Test void watcherRetriesARacedReadWithoutAnotherFileEvent() throws Exception {
        ClientConfigStore.initialize();
        var store = ClientConfigStore.store();
        var fs = com.asphyxiamywife.elytrapitchhelper.testing.FaultingConfigFileSystem.current();
        Config external = Config.reloadFromDisk(fs);
        external.enabled = !external.enabled;
        external.save(fs);
        var started = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var once = new java.util.concurrent.atomic.AtomicBoolean(true);
        fs.observeReads(path -> {
            if (path.equals(store.configPath()) && once.compareAndSet(true, false)) {
                started.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException failure) { throw new AssertionError(failure); }
            }
        });
        var debouncer = new ConfigWatchDebouncer(0);
        var clock = new java.util.concurrent.atomic.AtomicLong(100);
        debouncer.recordChange(clock.get());
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var reload = executor.submit(() -> ConfigWatcher.reloadIfReady(debouncer, store, clock::get));
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                Config edit = store.get();
                edit.usedSettingsSearch = true;
                store.update(edit);
            } finally { release.countDown(); }
            reload.get(5, TimeUnit.SECONDS);
        }
        assertTrue(store.hasUnsavedChanges());
        assertTrue(debouncer.waitMillis(clock.get()) >= 0);
        assertEquals(ClientConfigStore.SaveOutcome.CANCELLED,
                store.discardPendingSave(store.saveStatus().pendingGeneration()).outcome());
        clock.addAndGet(10);
        ConfigWatcher.reloadIfReady(debouncer, store, clock::get);
        assertEquals(external.enabled, store.get().enabled);
        assertEquals(-1, debouncer.waitMillis(clock.get()));
    }

    @Test void runtimeReloadFailureRetainsThePendingWatchEvent() {
        ClientConfigStore.initialize();
        var store = ClientConfigStore.store();
        var fs = com.asphyxiamywife.elytrapitchhelper.testing.FaultingConfigFileSystem.current();
        fs.observeReads(path -> { throw new IllegalArgumentException("Injected unexpected read failure"); });
        var debouncer = new ConfigWatchDebouncer(0);
        debouncer.recordChange(100);
        ConfigWatcher.reloadIfReady(debouncer, store, () -> 100);
        assertTrue(debouncer.waitMillis(100) >= 0);
        fs.observeReads(null);
        ConfigWatcher.reloadIfReady(debouncer, store, () -> 110);
        assertEquals(-1, debouncer.waitMillis(110));
    }

    @Test void unchangedPublicationAndSavePhasesDoNotInvalidateUiState() throws Exception {
        ClientConfigStore.initialize();
        var store = ClientConfigStore.store();
        assertTrue(ClientConfigStore.shutdownPersistenceCoordinator());
        Config draft = store.get();
        draft.enabled = !draft.enabled;
        store.update(draft);
        long ui = store.stateRevision();
        long hud = store.configViewRevision();
        store.update(draft);
        assertEquals(ui, store.stateRevision());
        assertEquals(ClientConfigStore.SaveOutcome.CANCELLED,
                store.flushPending().get(5, TimeUnit.SECONDS).result().outcome());
        assertEquals(ui, store.stateRevision());
        assertEquals(hud, store.configViewRevision());
    }

    @Test void metadataOnlyReloadInvalidatesUiButNotHudRevision() throws Exception {
        ClientConfigStore.initialize();
        var store = ClientConfigStore.store();
        long hud = store.configViewRevision();
        long ui = store.stateRevision();
        Path main = store.configPath();
        java.nio.file.Files.writeString(main, java.nio.file.Files.readString(main) + " ");
        assertTrue(store.reloadFromDiskIfIdle());
        assertEquals(hud, store.configViewRevision());
        assertTrue(store.stateRevision() > ui);
    }

    @Test void fixedSeedEditSaveFailureDiscardSequencesKeepTheLatestDocument() throws Exception {
        ClientConfigStore.initialize();
        var store = ClientConfigStore.store();
        var fs = com.asphyxiamywife.elytrapitchhelper.testing.FaultingConfigFileSystem.current();
        var random = new java.util.Random(0xE17A);
        Config draft = store.get();
        var persisted = draft.document();
        long generation = store.saveObservation().latestGeneration();
        for (int i = 0; i < 64; i++) {
            switch (random.nextInt(4)) {
                case 0 -> draft.enabled = !draft.enabled;
                case 1 -> draft.usedSettingsSearch = !draft.usedSettingsSearch;
                case 2 -> draft.profileSortMode = (draft.profileSortMode + 1) % 3;
                default -> draft.replaceProfile(0, draft.profile(0).withName("Iteration " + i));
            }
            store.update(draft);
            assertTrue(store.saveObservation().latestGeneration() > generation);
            generation = store.saveObservation().latestGeneration();
            assertEquals(draft.document(), store.get().document());
            boolean fail = random.nextBoolean();
            fs.failWritesWhen(fail ? path -> true : null);
            var result = store.flushPending().get(5, TimeUnit.SECONDS);
            fs.failWritesWhen(null);
            if (result.result().outcome() == ClientConfigStore.SaveOutcome.COMMITTED) {
                persisted = store.snapshot().config().document();
                assertFalse(store.hasUnsavedChanges());
            } else {
                assertEquals(ClientConfigStore.SaveOutcome.FAILED, result.result().outcome());
                assertEquals(draft.document(), store.get().document());
                assertEquals(persisted, store.snapshot().config().document());
                assertEquals(ClientConfigStore.SaveOutcome.CANCELLED,
                        store.discardPendingSave(result.pendingGeneration()).outcome());
                assertEquals(persisted, store.get().document());
                draft = store.get();
            }
        }
    }
}
