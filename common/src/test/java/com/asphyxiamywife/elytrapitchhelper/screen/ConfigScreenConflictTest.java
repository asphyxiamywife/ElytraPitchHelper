package com.asphyxiamywife.elytrapitchhelper.screen;

import static com.asphyxiamywife.elytrapitchhelper.config.ConfigTestFixtures.fs;

import com.asphyxiamywife.elytrapitchhelper.client.ClientConfigStore;
import com.asphyxiamywife.elytrapitchhelper.client.ClientConfigStoreTestSupport;
import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.ConfigConflictException;
import com.asphyxiamywife.elytrapitchhelper.config.ConfigSaveException;
import com.asphyxiamywife.elytrapitchhelper.testing.FaultingConfigFileSystem;
import com.asphyxiamywife.elytrapitchhelper.testing.IsolatedConfigRoot;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfigScreenConflictTest {
    @IsolatedConfigRoot
    Path configRoot;
    private boolean configStoreConfigured;

    @AfterEach
    void clearState() {
        if (configStoreConfigured) {
            ClientConfigStore.reloadFromDisk();
        }
    }

    @Test
    void failedAsyncFlushReportsFailureWithoutNavigating() throws Exception {
        configStoreConfigured = true;
        ClientConfigStore.reloadFromDisk();
        Config edited = ClientConfigStore.get().copy();
        edited.enabled = !edited.enabled;
        AtomicInteger overlays = new AtomicInteger();
        TestHost host = new TestHost(edited, overlays);
        ConfigScreenSaveController controller = new ConfigScreenSaveController(host);
        FaultingConfigFileSystem.current().failWritesWhen(path -> true);
        controller.scheduleSave();
        AtomicBoolean navigated = new AtomicBoolean();
        controller.flushAsyncIfPending(() -> navigated.set(true));
        assertTrue(host.overlayShown.await(10L, TimeUnit.SECONDS));
        assertTrue(controller.pending());
        assertTrue(ClientConfigStore.hasUnsavedChanges());
        assertFalse(navigated.get());
        assertEquals(1, overlays.get());
    }

    @Test
    void lifecycleCancellationDoesNotPresentASaveFailure() {
        configStoreConfigured = true;
        ClientConfigStore.reloadFromDisk();
        AtomicInteger overlays = new AtomicInteger();
        ConfigScreenSaveController controller = new ConfigScreenSaveController(
                new TestHost(ClientConfigStore.get().copy(), overlays));

        controller.handleAsyncSaveResult(new ClientConfigStore.AsyncSaveResult(
                new ClientConfigStore.SaveResult(ClientConfigStore.SaveOutcome.CANCELLED, null, null),
                1L, false));

        assertEquals(0, overlays.get());
    }

    @Test
    void conflictBaselineFailureIsHandledOncePerPendingGeneration() throws IOException {
        configStoreConfigured = true;
        ClientConfigStore.reloadFromDisk();
        Config baseline = ClientConfigStore.get().copy();
        baseline.enabled = false;
        ClientConfigStoreTestSupport.save(baseline);
        Config edited = baseline.copy();
        edited.enabled = true;
        ClientConfigStore.update(edited);
        writeExternalSortMode(Config.PROFILE_SORT_NAME);
        assertThrows(ConfigConflictException.class, () -> ClientConfigStoreTestSupport.save(edited));

        AtomicInteger captures = new AtomicInteger();
        AtomicInteger overlays = new AtomicInteger();
        java.util.function.Function<java.util.List<Path>, Config.ConflictBaseline> capture = paths -> {
            captures.incrementAndGet();
            throw new ConfigSaveException("Injected baseline failure");
        };
        TestHost host = new TestHost(edited, overlays);
        ConfigScreenSaveController controller = new ConfigScreenSaveController(
                host, capture);

        for (int i = 0; i < 20; i++) {
            controller.flushIfDue();
        }

        assertEquals(1, captures.get());
        assertEquals(1, overlays.get());

        controller.scheduleSave();
        writeExternalSortMode(Config.PROFILE_SORT_NAME);
        assertThrows(ConfigConflictException.class, () -> ClientConfigStoreTestSupport.save(host.config()));
        for (int i = 0; i < 20; i++) {
            controller.flushIfDue();
        }

        assertEquals(2, captures.get());
        assertEquals(2, overlays.get());
    }

    @Test
    void backgroundRetryCommitReconcilesLiveSavedMetadata() throws Exception {
        configStoreConfigured = true;
        ClientConfigStore.reloadFromDisk();
        Config baseline = ClientConfigStore.get().copy();
        baseline.enabled = false;
        ClientConfigStoreTestSupport.save(baseline);
        Config edited = baseline.copy();
        edited.enabled = true;
        TestHost host = new TestHost(edited, new AtomicInteger());
        ConfigScreenSaveController controller = new ConfigScreenSaveController(host);
        controller.scheduleSave();
        Path lockPath = Config.getConfigDirectory(fs()).resolve(".internal/config-save.lock");
        Files.createDirectories(lockPath.getParent());

        try (FileChannel channel = FileChannel.open(lockPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock ignored = channel.lock()) {
            assertEquals(ClientConfigStore.SaveOutcome.FAILED,
                    ClientConfigStore.saveAsync(edited).get(10L, TimeUnit.SECONDS).result().outcome());
        }

        assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                ClientConfigStore.saveAsync(ClientConfigStore.pendingSaveConfig())
                        .get(10L, TimeUnit.SECONDS).result().outcome());
        Config committed = ClientConfigStore.snapshot().config();
        assertFalse(host.config().hasSameMainConfigFileMetadata(committed));

        controller.flushIfDue();

        assertTrue(host.config().hasSameMainConfigFileMetadata(committed));
        assertTrue(host.config().hasSameProfileMetadata(committed));
        host.config().enabled = false;
        controller.scheduleSave();
        CountDownLatch committedAgain = new CountDownLatch(1);
        controller.flushAsyncIfPending(committedAgain::countDown);
        assertTrue(committedAgain.await(10L, TimeUnit.SECONDS));
        assertFalse(controller.pending());
        assertFalse(ClientConfigStore.snapshot().config().enabled);
    }

    @Test
    void asyncNavigationRunsOnlyAfterTheLatestGenerationCommits() throws Exception {
        configStoreConfigured = true;
        ClientConfigStore.reloadFromDisk();
        Config edited = ClientConfigStore.get().copy();
        edited.enabled = !edited.enabled;
        TestHost host = new TestHost(edited, new AtomicInteger());
        ConfigScreenSaveController controller = new ConfigScreenSaveController(host);
        controller.scheduleSave();
        CountDownLatch navigated = new CountDownLatch(1);

        controller.flushAsyncIfPending(navigated::countDown);

        assertTrue(navigated.await(10L, TimeUnit.SECONDS));
        assertFalse(controller.pending());
        assertFalse(ClientConfigStore.hasUnsavedChanges());
        assertEquals(edited.enabled, ClientConfigStore.snapshot().config().enabled);
        assertTrue(host.config().hasSameMainConfigFileMetadata(
                ClientConfigStore.snapshot().config()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void completingSaveAdoptsNewerValuesBeforeTheNextEditorSave(boolean asyncCompletion) throws Exception {
        configStoreConfigured = true;
        ClientConfigStore.reloadFromDisk();
        TestHost host = new TestHost(ClientConfigStore.get().copy(), new AtomicInteger());
        ConfigScreenSaveController controller = new ConfigScreenSaveController(host);
        host.config.enabled = !host.config.enabled;
        controller.scheduleSave();

        Config latest = ClientConfigStore.get().copy();
        latest.usedSettingsSearch = !host.config.usedSettingsSearch;
        latest.replaceProfile(0, latest.profile(0).withName("Newer committed profile"));
        ClientConfigStore.AsyncSaveResult result = ClientConfigStore.saveAsync(latest)
                .get(10, TimeUnit.SECONDS);
        assertEquals(ClientConfigStore.SaveOutcome.COMMITTED, result.result().outcome());
        if (asyncCompletion) {
            controller.handleAsyncSaveResult(result);
        } else {
            controller.flushIfDue();
        }

        assertFalse(controller.pending());
        assertFalse(controller.hasExternalRevision());
        assertTrue(host.config.hasSameState(ClientConfigStore.snapshot().config()));
        assertEquals(1, host.historyClears);
        assertEquals(1, host.profileSyncs);
        assertEquals(1, host.widgetRebuilds);

        host.config.profileSortMode = Config.PROFILE_SORT_NAME;
        controller.scheduleSave();
        CountDownLatch saved = new CountDownLatch(1);
        controller.flushAsyncIfPending(saved::countDown);
        assertTrue(saved.await(10, TimeUnit.SECONDS));
        Config disk = Config.reloadFromDisk(fs());
        assertEquals(latest.usedSettingsSearch, disk.usedSettingsSearch);
        assertEquals("Newer committed profile", disk.profileName(0));
        assertEquals(1, host.historyClears, "An ordinary save must preserve undo history");
        assertEquals(1, host.widgetRebuilds, "An ordinary save must not rebuild the editor");
    }

    @Test
    void supersededAsyncNavigationReattachesToLatestGeneration() throws Exception {
        configStoreConfigured = true;
        ClientConfigStore.reloadFromDisk();
        Config first = ClientConfigStore.get().copy();
        first.enabled = !first.enabled;
        CountDownLatch firstWriteStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstWrite = new CountDownLatch(1);
        AtomicBoolean blockFirstMove = new AtomicBoolean(true);
        FaultingConfigFileSystem.current().beforeAtomicMove(() -> {
                    if (!blockFirstMove.compareAndSet(true, false)) {
                        return;
                    }
                    firstWriteStarted.countDown();
                    try {
                        releaseFirstWrite.await();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(interrupted);
                    }
                });

        ClientConfigStore.update(first);
        CompletableFuture<ClientConfigStore.AsyncSaveResult> firstSave = ClientConfigStore.saveAsync(first);
        assertTrue(firstWriteStarted.await(10L, TimeUnit.SECONDS));

        TestHost host = new TestHost(first.copy(), new AtomicInteger());
        ConfigScreenSaveController controller = new ConfigScreenSaveController(host);
        Config screenEdit = first.copy();
        screenEdit.profileSortMode = Config.PROFILE_SORT_NAME;
        host.config = screenEdit;
        controller.scheduleSave();
        CountDownLatch navigated = new CountDownLatch(1);
        controller.flushAsyncIfPending(navigated::countDown);

        Config latest = ClientConfigStore.get().copy();
        latest.usedSettingsSearch = !latest.usedSettingsSearch;
        ClientConfigStore.update(latest);
        CompletableFuture<ClientConfigStore.AsyncSaveResult> latestSave = ClientConfigStore.saveAsync(latest);
        releaseFirstWrite.countDown();

        assertTrue(navigated.await(10L, TimeUnit.SECONDS));
        assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                firstSave.get(10L, TimeUnit.SECONDS).result().outcome());
        assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                latestSave.get(10L, TimeUnit.SECONDS).result().outcome());
        assertFalse(ClientConfigStore.hasUnsavedChanges());
        assertEquals(latest.usedSettingsSearch, ClientConfigStore.snapshot().config().usedSettingsSearch);
    }

    @Test
    void keepingInGameProfileChangeRetriesWithTheApprovedDiskBaseline() throws IOException {
        configStoreConfigured = true;
        ClientConfigStore.reloadFromDisk();
        Config baseline = ClientConfigStore.get().copy();
        ClientConfigStoreTestSupport.save(baseline);

        Config edited = baseline.copy();
        edited.replaceProfile(0, edited.profile(0).withName("In-game edit"));
        TestHost host = new TestHost(edited, new AtomicInteger());
        ConfigScreenSaveController controller = new ConfigScreenSaveController(host);
        controller.scheduleSave();

        Path profilePath = edited.getProfilePath(fs(), 0);
        long loadedAt = Files.getLastModifiedTime(profilePath).toMillis();
        String externalJson = Files.readString(profilePath, StandardCharsets.UTF_8)
                .replace(baseline.profileName(0), "External edit");
        Files.writeString(profilePath, externalJson, StandardCharsets.UTF_8);
        Files.setLastModifiedTime(profilePath, FileTime.fromMillis(loadedAt + 10_000L));
        ConfigConflictException conflict = assertThrows(ConfigConflictException.class,
                () -> ClientConfigStoreTestSupport.save(host.config()));

        Config.ConflictBaseline approval = Config.captureConflictBaseline(
                fs(), conflict.conflictPaths());
        controller.acceptConflictAndRetry(approval);

        ClientConfigStoreTestSupport.savePending();
        controller.flushIfDue();
        assertFalse(ClientConfigStore.hasUnsavedChanges());
        assertNull(ClientConfigStore.pendingSaveConflict());
        assertTrue(Files.readString(profilePath, StandardCharsets.UTF_8).contains("In-game edit"));
    }

    @Test
    void obsoleteFailureMustNotReplaceNewerSuccessfulState() throws Exception {
        ClientConfigStore.initialize();
        TestHost host = new TestHost(ClientConfigStore.get(), new AtomicInteger());
        ConfigScreenSaveController controller = new ConfigScreenSaveController(host);
        host.config.enabled = !host.config.enabled;
        controller.scheduleSave();
        FaultingConfigFileSystem.current().failWritesWhen(path -> true);
        var oldFailure = ClientConfigStore.saveAsync(host.config).get(10, TimeUnit.SECONDS);
        assertEquals(ClientConfigStore.SaveOutcome.FAILED, oldFailure.result().outcome());
        FaultingConfigFileSystem.current().failWritesWhen(null);
        host.config.profileSortMode = Config.PROFILE_SORT_NAME;
        controller.scheduleSave();
        assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                ClientConfigStore.saveAsync(host.config).get(10, TimeUnit.SECONDS).result().outcome());
        controller.flushIfDue();
        assertFalse(controller.pending());
        controller.handleAsyncSaveResult(oldFailure);
        assertEquals(0, host.overlays.get());
    }

    @Test
    void delayedClientCompletionPreservesTheNextEditAndNavigatesOnce() throws Exception {
        ClientConfigStore.initialize();
        TestHost host = new TestHost(ClientConfigStore.get(), new AtomicInteger());
        var callbacks = new java.util.concurrent.LinkedBlockingQueue<Runnable>();
        host.completionExecutor = callbacks::add;
        ConfigScreenSaveController controller = new ConfigScreenSaveController(host);
        host.config.enabled = !host.config.enabled;
        controller.scheduleSave();
        AtomicInteger navigated = new AtomicInteger();
        controller.flushAsyncIfPending(navigated::incrementAndGet);
        Runnable first = callbacks.poll(5, TimeUnit.SECONDS);
        assertTrue(first != null);
        assertFalse(ClientConfigStore.hasUnsavedChanges());
        host.config.profileSortMode = Config.PROFILE_SORT_NAME;
        controller.scheduleSave();
        first.run();
        Runnable second = callbacks.poll(5, TimeUnit.SECONDS);
        assertTrue(second != null);
        second.run();
        controller.flushIfDue();
        assertEquals(1, navigated.get());
        assertEquals(0, host.overlays.get());
        assertEquals(Config.PROFILE_SORT_NAME, Config.reloadFromDisk(fs()).profileSortMode);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void completionRetainsActionsOnlyInsideTheOriginatingChildWorkflow(boolean owned) throws Exception {
        ClientConfigStore.initialize();
        TestHost host = new TestHost(ClientConfigStore.get(), new AtomicInteger());
        var callbacks = new java.util.concurrent.LinkedBlockingQueue<Runnable>();
        host.completionExecutor = callbacks::add;
        var origin = screenWithoutMinecraft(ConfigScreen.class, null);
        host.currentScreen = origin;
        ConfigScreenSaveController controller = new ConfigScreenSaveController(host);
        host.config.enabled = !host.config.enabled;
        controller.scheduleSave();
        controller.explicitSave();
        controller.leaveAfterSaving();
        Runnable callback = callbacks.poll(5, TimeUnit.SECONDS);
        assertTrue(callback != null);
        var palette = screenWithoutMinecraft(CommandPaletteScreen.class, owned ? origin : null);
        var appearance = screenWithoutMinecraft(CommandPaletteAppearanceScreen.class, palette);
        host.currentScreen = screenWithoutMinecraft(ColorEditorScreen.class, appearance);
        callback.run();
        assertEquals(owned ? 1 : 0, host.leaves);
        assertEquals(owned ? 1 : 0, host.overlays.get());
        controller.flushIfDue();
        assertEquals(owned ? 1 : 0, host.leaves);
    }

    private static <T extends net.minecraft.client.gui.screens.Screen> T screenWithoutMinecraft(
            Class<T> type, net.minecraft.client.gui.screens.Screen parent) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        var unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        T screen = type.cast(unsafeClass.getMethod("allocateInstance", Class.class)
                .invoke(unsafeField.get(null), type));
        if (screen instanceof ConfigWorkflowChild) {
            var field = type.getDeclaredField("lastScreen");
            field.setAccessible(true);
            field.set(screen, parent);
        }
        return screen;
    }

    @Test
    void leavingAWorkflowCancelsItsDelayedNavigation() throws Exception {
        ClientConfigStore.initialize();
        TestHost host = new TestHost(ClientConfigStore.get(), new AtomicInteger());
        var callbacks = new java.util.concurrent.LinkedBlockingQueue<Runnable>();
        host.completionExecutor = callbacks::add;
        ConfigScreenSaveController controller = new ConfigScreenSaveController(host);
        host.config.enabled = !host.config.enabled;
        controller.scheduleSave();
        AtomicInteger navigated = new AtomicInteger();
        controller.flushAsyncIfPending(navigated::incrementAndGet);
        Runnable callback = callbacks.poll(5, TimeUnit.SECONDS);
        assertTrue(callback != null);
        controller.workflowRemoved();
        callback.run();
        assertEquals(0, navigated.get());
    }

    @Test
    void leaveAfterFailureWaitsForRealWorkThenSucceeds() throws Exception {
        ClientConfigStore.initialize();
        TestHost host = new TestHost(ClientConfigStore.get(), new AtomicInteger());
        ConfigScreenSaveController controller = new ConfigScreenSaveController(host);
        host.config.enabled = !host.config.enabled;
        controller.scheduleSave();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        FaultingConfigFileSystem.current().beforeAtomicMove(() -> {
            started.countDown();
            try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
            catch (InterruptedException failure) { throw new AssertionError(failure); }
        });
        try {
            var saving = ClientConfigStore.flushPending();
            assertTrue(started.await(5, TimeUnit.SECONDS));
            var status = ClientConfigStore.saveStatus();
            assertFalse(controller.tryLeaveAfterFailure(status));
            assertEquals(0, host.leaves);
            release.countDown();
            saving.get(5, TimeUnit.SECONDS);
            assertTrue(controller.tryLeaveAfterFailure(status));
            assertEquals(1, host.leaves);
        } finally { release.countDown(); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"edit", "delete", "approve"})
    void replacedStoreDoesNotAcceptOldScreenSubmissions(String action) {
        ClientConfigStore.initialize();
        var oldStore = ClientConfigStore.store();
        TestHost host = new TestHost(oldStore.get(), new AtomicInteger());
        ConfigScreenSaveController controller = new ConfigScreenSaveController(host);
        var baseline = Config.captureConflictBaseline(oldStore.fileSystem(), java.util.List.of(oldStore.configPath()));
        var replacement = new com.asphyxiamywife.elytrapitchhelper.client.ConfigStore(oldStore.fileSystem());
        ClientConfigStore.setStore(replacement);
        replacement.initialize();
        Config expected = replacement.get();
        host.config.enabled = !expected.enabled;
        switch (action) {
            case "edit" -> controller.scheduleSave();
            case "delete" -> controller.scheduleProfileDeletes(java.util.List.of(host.config.activeProfile().fileName()));
            case "approve" -> controller.acceptConflictAndRetry(baseline);
            default -> throw new AssertionError(action);
        }
        assertFalse(replacement.hasUnsavedChanges());
        assertTrue(expected.hasSameState(replacement.get()));
        assertTrue(replacement.pendingProfileDeletes().isEmpty());
        assertEquals(0, host.overlays.get());
    }

    @Test
    void replacedStoreCannotCompleteOldScreenThroughPollingOrRefresh() throws Exception {
        ClientConfigStore.initialize();
        var oldStore = ClientConfigStore.store();
        TestHost host = new TestHost(oldStore.get(), new AtomicInteger());
        var callbacks = new java.util.concurrent.LinkedBlockingQueue<Runnable>();
        host.completionExecutor = callbacks::add;
        ConfigScreenSaveController controller = new ConfigScreenSaveController(host);
        host.config.enabled = !host.config.enabled;
        controller.scheduleSave();
        AtomicInteger navigated = new AtomicInteger();
        controller.flushAsyncIfPending(navigated::incrementAndGet);
        Runnable callback = callbacks.poll(5, TimeUnit.SECONDS);
        assertTrue(callback != null);
        Config previousHost = host.config;
        var replacement = new com.asphyxiamywife.elytrapitchhelper.client.ConfigStore(oldStore.fileSystem());
        ClientConfigStore.setStore(replacement);
        replacement.initialize();
        controller.flushIfDue();
        controller.loadSnapshot();
        controller.refreshSnapshot();
        controller.flushAsyncIfPending(navigated::incrementAndGet);
        callback.run();
        assertEquals(0, navigated.get());
        assertTrue(previousHost == host.config);
        assertEquals(0, host.overlays.get());
        assertEquals(0, host.historyClears);
        Config newEdit = replacement.get();
        newEdit.usedSettingsSearch = true;
        replacement.update(newEdit);
        var status = replacement.saveStatus();
        controller.leaveAfterSaving();
        assertEquals(1, host.leaves);
        assertEquals(status, replacement.saveStatus());
        assertTrue(replacement.get().usedSettingsSearch);
    }

    @Test
    void replacingStoreInOneCommitActionCancelsRemainingActions() throws Exception {
        ClientConfigStore.initialize();
        var oldStore = ClientConfigStore.store();
        TestHost host = new TestHost(oldStore.get(), new AtomicInteger());
        var callbacks = new java.util.concurrent.LinkedBlockingQueue<Runnable>();
        host.completionExecutor = callbacks::add;
        ConfigScreenSaveController controller = new ConfigScreenSaveController(host);
        host.config.enabled = !host.config.enabled;
        controller.scheduleSave();
        AtomicInteger actions = new AtomicInteger();
        controller.flushAsyncIfPending(() -> {
            actions.incrementAndGet();
            ClientConfigStore.setStore(new com.asphyxiamywife.elytrapitchhelper.client.ConfigStore(oldStore.fileSystem()));
        });
        controller.flushAsyncIfPending(actions::incrementAndGet);
        Runnable callback = callbacks.poll(5, TimeUnit.SECONDS);
        assertTrue(callback != null);
        callback.run();
        assertEquals(1, actions.get());
        assertEquals(0, host.overlays.get());
    }

    private void writeExternalSortMode(int sortMode) throws IOException {
        Path path = Config.getConfigPath(fs());
        String json = Files.readString(path, StandardCharsets.UTF_8);
        String updated = json.replaceFirst(
                "\"profileSortMode\"\\s*:\\s*\\d+",
                "\"profileSortMode\": " + sortMode);
        Files.writeString(path, updated, StandardCharsets.UTF_8);
        Files.setLastModifiedTime(path,
                FileTime.fromMillis(Files.getLastModifiedTime(path).toMillis() + 10_000L));
    }

    private static final class TestHost implements ConfigScreenSaveController.Host {
        private Config config;
        private net.minecraft.client.gui.screens.Screen currentScreen;
        private int historyClears;
        private int profileSyncs;
        private int widgetRebuilds;
        private int leaves;
        private java.util.concurrent.Executor completionExecutor = Runnable::run;
        private final AtomicInteger overlays;
        private final CountDownLatch overlayShown = new CountDownLatch(1);

        private TestHost(Config config, AtomicInteger overlays) {
            this.config = config;
            this.overlays = overlays;
        }

        @Override
        public Config config() {
            return config;
        }

        @Override
        public void replaceConfig(Config config) {
            this.config = config;
        }

        @Override
        public Minecraft minecraftClient() {
            return null;
        }

        @Override
        public net.minecraft.client.gui.screens.Screen currentSaveScreen() {
            return currentScreen;
        }

        @Override
        public java.util.concurrent.Executor saveCompletionExecutor() {
            return completionExecutor;
        }

        @Override
        public void resetHistoryBaseline() {
        }

        @Override
        public void reconcileResetUndo() {
        }

        @Override
        public void clearHistory() {
            historyClears++;
        }

        @Override
        public void syncEditingProfileIndex() {
            profileSyncs++;
        }

        @Override
        public void rebuildWidgets() {
            widgetRebuilds++;
        }

        @Override
        public void sendOverlay(Component message) {
            overlays.incrementAndGet();
            overlayShown.countDown();
        }

        @Override
        public void leaveConfigWorkflow() {
            leaves++;
        }
    }
}
