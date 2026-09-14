package com.asphyxiamywife.elytrapitchhelper.config;

import static com.asphyxiamywife.elytrapitchhelper.config.ConfigTestFixtures.fs;

import com.asphyxiamywife.elytrapitchhelper.client.ClientConfigStore;
import com.asphyxiamywife.elytrapitchhelper.client.ClientConfigStoreTestSupport;
import com.asphyxiamywife.elytrapitchhelper.client.ConfigStore;
import com.asphyxiamywife.elytrapitchhelper.testing.IsolatedConfigRoot;
import com.asphyxiamywife.elytrapitchhelper.testing.FaultingConfigFileSystem;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClientConfigStoreTest {
    private static final Gson COMPACT_GSON = new Gson();

    @IsolatedConfigRoot
    Path configRoot;

    @BeforeEach
    void reloadConfigStore() {
        ClientConfigStore.reloadFromDisk();
    }

    @AfterEach
    void clearConfigStore() {
        ClientConfigStore.reloadFromDisk();
    }

    @Test
    void failedUserReloadPreservesLiveConfigurationPendingEditsAndRevisions() {
        Config baseline = ClientConfigStore.get().copy();
        baseline.enabled = false;
        ClientConfigStoreTestSupport.save(baseline);
        Config pending = baseline.copy();
        pending.enabled = true;
        ClientConfigStore.update(pending);
        long revision = ClientConfigStore.revision();
        long viewRevision = ClientConfigStore.effectiveSnapshot().viewRevision();
        long generation = ClientConfigStore.saveStatus().pendingGeneration();
        FaultingConfigFileSystem.current().failListsWhen(Config.getProfileDirectory(fs())::equals);
        try {
            assertFalse(ClientConfigStore.reloadFromDisk());
            assertTrue(ClientConfigStore.get().enabled);
            assertFalse(ClientConfigStore.snapshot().config().enabled);
            assertFalse(ClientConfigStore.get().isReadOnly());
            assertTrue(ClientConfigStore.pendingSaveConfig().enabled);
            assertEquals(revision, ClientConfigStore.revision());
            assertEquals(viewRevision, ClientConfigStore.effectiveSnapshot().viewRevision());
            assertEquals(generation, ClientConfigStore.saveStatus().pendingGeneration());
        } finally {
            FaultingConfigFileSystem.current().failListsWhen(null);
        }
        assertTrue(ClientConfigStore.reloadFromDisk());
        assertFalse(ClientConfigStore.hasUnsavedChanges());
        assertFalse(ClientConfigStore.get().isReadOnly());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"main", "metadata", "profile", "directory"})
    void watcherKeepsLiveConfigurationOnTransientReadFailureAndRecovers(String failedFile) {
        Config baseline = ClientConfigStore.get().copy();
        baseline.enabled = false;
        ClientConfigStoreTestSupport.save(baseline);
        long revision = ClientConfigStore.revision();
        long viewRevision = ClientConfigStore.effectiveSnapshot().viewRevision();
        Path failurePath = switch (failedFile) {
            case "main" -> Config.getConfigPath(fs());
            case "metadata" -> Config.getProfileMetadataPath(fs());
            case "profile" -> baseline.getProfilePath(fs(), 0);
            default -> Config.getProfileDirectory(fs());
        };
        FaultingConfigFileSystem.current().failReadsWhen(failurePath::equals);
        FaultingConfigFileSystem.current().failListsWhen(failurePath::equals);
        try {
            assertFalse(ClientConfigStore.reloadFromDiskIfIdle());
            assertFalse(ClientConfigStore.get().enabled);
            assertFalse(ClientConfigStore.get().isReadOnly());
            assertEquals(revision, ClientConfigStore.revision());
            assertEquals(viewRevision, ClientConfigStore.effectiveSnapshot().viewRevision());
        } finally {
            FaultingConfigFileSystem.current().failReadsWhen(null);
            FaultingConfigFileSystem.current().failListsWhen(null);
        }
        assertTrue(ClientConfigStore.reloadFromDiskIfIdle());
        assertFalse(ClientConfigStore.get().isReadOnly());
    }

    @Test
    void watcherStillPublishesReadOnlyProtectionForNewerVersions() throws IOException {
        ClientConfigStoreTestSupport.save(ClientConfigStore.get().copy());
        Files.writeString(Config.getConfigPath(fs()),
                "{\"version\":" + (Config.CURRENT_VERSION + 1) + "}");
        assertTrue(ClientConfigStore.reloadFromDiskIfIdle());
        assertTrue(ClientConfigStore.get().isReadOnly());
    }

    @Test
    void replacingTheStoreProvidesFreshInitializationState() {
        ConfigStore first = ClientConfigStore.store();
        Config pending = ClientConfigStore.get().copy();
        pending.enabled = !pending.enabled;
        ClientConfigStore.update(pending);
        assertTrue(first.hasUnsavedChanges());

        ConfigStore replacement = new ConfigStore(new FaultingConfigFileSystem(configRoot));
        ClientConfigStore.setStore(replacement);
        replacement.initialize();

        assertSame(replacement, ClientConfigStore.store());
        assertNotSame(first, replacement);
        assertFalse(replacement.hasUnsavedChanges());
        assertEquals(1L, replacement.revision());
    }

    @Test
    void saveNowPropagatesSaveFailureAndRetainsLatestAttemptWithoutPublishingIt() throws IOException {
        Config current = new Config();
        current.enabled = false;
        ClientConfigStoreTestSupport.save(current);
        long revision = ClientConfigStore.revision();

        FaultingConfigFileSystem.current().failWritesWhen(path -> true);

        Config next = ClientConfigStore.get().copy();
        next.enabled = true;
        ConfigTestFixtures.updateActiveProfile(next,
                profile -> profile.withLine(profile.line().withColorRgb(0x123456)));

        assertThrows(ConfigSaveException.class, () -> ClientConfigStoreTestSupport.save(next));
        assertTrue(ClientConfigStore.get().enabled);
        assertTrue(ClientConfigStore.pendingSaveConfig().enabled);
        assertEquals(ClientConfigStore.SavePhase.FAILED, ClientConfigStore.saveStatus().phase());
        assertFalse(ClientConfigStore.snapshot().config().enabled);
        assertTrue(ClientConfigStore.effectiveSnapshot().config().enabled);
        assertEquals(0x123456, ClientConfigStore.effectiveSnapshot().hudRenderState().line().rgb());
        assertEquals(revision, ClientConfigStore.revision());
    }

    @Test
    void watcherReloadOfJustSavedStateDoesNotCreateExternalRevision() {
        Config current = ClientConfigStore.get().copy();
        current.enabled = !current.enabled;
        ClientConfigStoreTestSupport.save(current);
        long savedRevision = ClientConfigStore.revision();

        assertTrue(ClientConfigStore.reloadFromDiskIfIdle());
        assertEquals(savedRevision, ClientConfigStore.revision());
    }

    @Test
    void watcherReloadIsUnaffectedByAnotherInstanceHoldingTheSaveLock() throws IOException {
        Config baseline = ClientConfigStore.get().copy();
        baseline.enabled = false;
        ClientConfigStoreTestSupport.save(baseline);
        Path lockPath = ConfigSaveLock.lockPathForTests(fs());
        Files.createDirectories(lockPath.getParent());

        try (FileChannel channel = FileChannel.open(lockPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock ignored = channel.lock()) {
            assertTrue(ClientConfigStore.reloadFromDiskIfIdle());
            assertFalse(ClientConfigStore.get().enabled);
            assertFalse(ClientConfigStore.get().isReadOnly());

            Config edited = ClientConfigStore.get().copy();
            edited.enabled = true;
            ConfigSaveException failure = assertThrows(ConfigSaveException.class,
                    () -> ClientConfigStoreTestSupport.save(edited));
            assertTrue(failure.isRetryable());
        }

        assertTrue(ClientConfigStore.hasUnsavedChanges());
        assertFalse(ClientConfigStore.reloadFromDiskIfIdle());
        assertTrue(ClientConfigStore.pendingSaveConfig().enabled);
        assertFalse(ClientConfigStore.get().isReadOnly());
    }

    @Test
    void pendingSaveRetriesAfterAnotherInstanceReleasesTheSaveLock() throws Exception {
        Config baseline = ClientConfigStore.get().copy();
        baseline.enabled = false;
        ClientConfigStoreTestSupport.save(baseline);
        Config edited = baseline.copy();
        edited.enabled = true;
        Path lockPath = ConfigSaveLock.lockPathForTests(fs());
        Files.createDirectories(lockPath.getParent());
        long pendingViewRevision;

        try (FileChannel channel = FileChannel.open(lockPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock ignored = channel.lock()) {
            ConfigSaveException failure = assertThrows(ConfigSaveException.class,
                    () -> ClientConfigStoreTestSupport.save(edited));
            assertTrue(failure.isRetryable());
            assertTrue(ClientConfigStore.hasUnsavedChanges());
            assertFalse(ClientConfigStore.snapshot().config().enabled);
            assertTrue(ClientConfigStore.get().enabled);
            assertTrue(ClientConfigStore.pendingSaveConfig().enabled);
            pendingViewRevision = ClientConfigStore.effectiveSnapshot().viewRevision();
        }

        ClientConfigStoreTestSupport.save(ClientConfigStore.pendingSaveConfig());

        assertFalse(ClientConfigStore.hasUnsavedChanges());
        assertTrue(ClientConfigStore.get().enabled);
        assertTrue(ClientConfigStore.snapshot().config().enabled);
        assertEquals(pendingViewRevision, ClientConfigStore.effectiveSnapshot().viewRevision());
        JsonObject saved = ConfigFiles.GSON.fromJson(
                Files.readString(Config.getConfigPath(fs()), StandardCharsets.UTF_8), JsonObject.class);
        assertTrue(saved.get("enabled").getAsBoolean());
    }

    @Test
    void backgroundConflictRemainsPendingForTheConfigScreen() throws Exception {
        Config baseline = ClientConfigStore.get().copy();
        baseline.enabled = false;
        ClientConfigStoreTestSupport.save(baseline);
        Config edited = baseline.copy();
        edited.enabled = true;
        ConfigTestFixtures.updateActiveProfile(edited,
                profile -> profile.withLine(profile.line().withColorRgb(0x654321)));
        Path lockPath = ConfigSaveLock.lockPathForTests(fs());
        Files.createDirectories(lockPath.getParent());

        try (FileChannel channel = FileChannel.open(lockPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock ignored = channel.lock()) {
            assertThrows(ConfigSaveException.class, () -> ClientConfigStoreTestSupport.save(edited));
        }
        Path configPath = Config.getConfigPath(fs());
        JsonObject external = ConfigFiles.GSON.fromJson(
                Files.readString(configPath, StandardCharsets.UTF_8), JsonObject.class);
        external.addProperty("profileSortMode", Config.PROFILE_SORT_NAME);
        ConfigFiles.writeJsonAtomic(fs(), configPath, external);

        assertThrows(ConfigConflictException.class,
                () -> ClientConfigStoreTestSupport.save(ClientConfigStore.pendingSaveConfig()));

        ConfigConflictException conflict = ClientConfigStore.pendingSaveConflict().conflict();
        assertEquals(List.of(configPath), conflict.conflictPaths());
        assertTrue(ClientConfigStore.hasUnsavedChanges());
        assertTrue(ClientConfigStore.pendingSaveConfig().enabled);
        assertTrue(ClientConfigStore.get().enabled);
        assertFalse(ClientConfigStore.snapshot().config().enabled);
        assertEquals(0x654321, ClientConfigStore.effectiveSnapshot().hudRenderState().line().rgb());
    }

    @Test
    void reloadDiscardRevertsEffectiveHudToPersistedState() {
        Config baseline = ClientConfigStore.get().copy();
        ConfigTestFixtures.updateActiveProfile(baseline,
                profile -> profile.withLine(profile.line().withColorRgb(0x112233)));
        ClientConfigStoreTestSupport.save(baseline);
        Config pending = baseline.copy();
        ConfigTestFixtures.updateActiveProfile(pending,
                profile -> profile.withLine(profile.line().withColorRgb(0x445566)));
        ClientConfigStore.update(pending);
        long pendingRevision = ClientConfigStore.effectiveSnapshot().viewRevision();

        ClientConfigStore.reloadFromDisk();

        assertEquals(0x112233, ClientConfigStore.get().line().colorRgb());
        assertEquals(0x112233, ClientConfigStore.effectiveSnapshot().hudRenderState().line().rgb());
        assertTrue(ClientConfigStore.effectiveSnapshot().viewRevision() > pendingRevision);
        assertFalse(ClientConfigStore.hasUnsavedChanges());
    }

    @Test
    void laterActionBuildsOnThePendingConfigAfterSaveLockFailure() throws IOException {
        Config baseline = ClientConfigStore.get().copy();
        baseline.enabled = false;
        baseline.profileSortMode = Config.PROFILE_SORT_CREATED;
        ClientConfigStoreTestSupport.save(baseline);
        Path lockPath = ConfigSaveLock.lockPathForTests(fs());
        Files.createDirectories(lockPath.getParent());

        try (FileChannel channel = FileChannel.open(lockPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock ignored = channel.lock()) {
            Config firstEdit = ClientConfigStore.get().copy();
            firstEdit.enabled = true;
            assertThrows(ConfigSaveException.class, () -> ClientConfigStoreTestSupport.save(firstEdit));

            Config laterEdit = ClientConfigStore.get().copy();
            assertTrue(laterEdit.enabled);
            laterEdit.profileSortMode = Config.PROFILE_SORT_NAME;
            assertThrows(ConfigSaveException.class, () -> ClientConfigStoreTestSupport.save(laterEdit));
        }

        Config pending = ClientConfigStore.pendingSaveConfig();
        assertTrue(pending.enabled);
        assertEquals(Config.PROFILE_SORT_NAME, pending.profileSortMode);
        assertFalse(ClientConfigStore.snapshot().config().enabled);
        assertEquals(Config.PROFILE_SORT_CREATED, ClientConfigStore.snapshot().config().profileSortMode);
        ClientConfigStoreTestSupport.savePending();
    }

    @Test
    void newerConflictReplacesOlderRetryableFailure() throws IOException {
        Config baseline = ClientConfigStore.get().copy();
        baseline.enabled = false;
        baseline.profileSortMode = Config.PROFILE_SORT_CREATED;
        ClientConfigStoreTestSupport.save(baseline);
        Path lockPath = ConfigSaveLock.lockPathForTests(fs());
        Files.createDirectories(lockPath.getParent());

        try (FileChannel channel = FileChannel.open(lockPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock ignored = channel.lock()) {
            Config older = baseline.copy();
            older.enabled = true;
            assertThrows(ConfigSaveException.class, () -> ClientConfigStoreTestSupport.save(older));
        }

        Path configPath = Config.getConfigPath(fs());
        JsonObject external = ConfigFiles.GSON.fromJson(
                Files.readString(configPath, StandardCharsets.UTF_8), JsonObject.class);
        external.addProperty("profileSortMode", Config.PROFILE_SORT_MODIFIED);
        ConfigFiles.writeJsonAtomic(fs(), configPath, external);

        Config newer = ClientConfigStore.get().copy();
        newer.profileSortMode = Config.PROFILE_SORT_NAME;
        assertThrows(ConfigConflictException.class, () -> ClientConfigStoreTestSupport.save(newer));

        Config pending = ClientConfigStore.pendingSaveConfig();
        assertTrue(pending.enabled);
        assertEquals(Config.PROFILE_SORT_NAME, pending.profileSortMode);
        assertEquals(ClientConfigStore.SavePhase.CONFLICTED, ClientConfigStore.saveStatus().phase());
    }

    @Test
    void newerNonRetryableFailureReplacesOlderRetryableFailure() throws IOException {
        Config baseline = ClientConfigStore.get().copy();
        baseline.enabled = false;
        baseline.profileSortMode = Config.PROFILE_SORT_CREATED;
        ClientConfigStoreTestSupport.save(baseline);
        Path lockPath = ConfigSaveLock.lockPathForTests(fs());
        Files.createDirectories(lockPath.getParent());

        try (FileChannel channel = FileChannel.open(lockPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock ignored = channel.lock()) {
            Config older = baseline.copy();
            older.enabled = true;
            assertThrows(ConfigSaveException.class, () -> ClientConfigStoreTestSupport.save(older));
        }

        Config newer = ClientConfigStore.get().copy();
        newer.profileSortMode = Config.PROFILE_SORT_NAME;
        FaultingConfigFileSystem.current().failWritesWhen(path -> path.equals(Config.getConfigPath(fs())));
        ConfigSaveException failure = assertThrows(ConfigSaveException.class,
                () -> ClientConfigStoreTestSupport.save(newer));
        assertFalse(failure.isRetryable());

        Config pending = ClientConfigStore.pendingSaveConfig();
        assertTrue(pending.enabled);
        assertEquals(Config.PROFILE_SORT_NAME, pending.profileSortMode);
        assertEquals(ClientConfigStore.SavePhase.FAILED, ClientConfigStore.saveStatus().phase());

        FaultingConfigFileSystem.current().failWritesWhen(null);
        ClientConfigStoreTestSupport.savePending();
    }

    @Test
    void newerInMemoryEditReplacesTheOlderFailedAttempt() throws IOException {
        Config baseline = ClientConfigStore.get().copy();
        baseline.enabled = false;
        baseline.profileSortMode = Config.PROFILE_SORT_CREATED;
        ClientConfigStoreTestSupport.save(baseline);
        Path lockPath = ConfigSaveLock.lockPathForTests(fs());
        Files.createDirectories(lockPath.getParent());

        try (FileChannel channel = FileChannel.open(lockPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock ignored = channel.lock()) {
            Config failed = baseline.copy();
            failed.enabled = true;
            assertThrows(ConfigSaveException.class, () -> ClientConfigStoreTestSupport.save(failed));
        }

        Config newer = ClientConfigStore.get().copy();
        newer.profileSortMode = Config.PROFILE_SORT_NAME;
        ClientConfigStore.update(newer);

        assertTrue(ClientConfigStore.pendingSaveConfig().enabled);
        assertEquals(Config.PROFILE_SORT_NAME, ClientConfigStore.pendingSaveConfig().profileSortMode);
        assertEquals(ClientConfigStore.SavePhase.IDLE, ClientConfigStore.saveStatus().phase());
    }

    @Test
    void cancelingProfileDeletionAfterAFailedSaveKeepsTheProfile() throws IOException {
        Config baseline = ClientConfigStore.get().copy();
        baseline.createProfile();
        ClientConfigStoreTestSupport.save(baseline);
        Config deletedConfig = baseline.copy();
        StagedProfileDelete deleted = deletedConfig.stageDeleteProfile(0);
        assertTrue(deleted != null);
        Path lockPath = ConfigSaveLock.lockPathForTests(fs());
        Files.createDirectories(lockPath.getParent());

        try (FileChannel channel = FileChannel.open(lockPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock ignored = channel.lock()) {
            assertThrows(ConfigSaveException.class,
                    () -> ClientConfigStoreTestSupport.save(deletedConfig, List.of(deleted.fileName())));
        }

        Config restored = baseline.copy();
        ClientConfigStore.update(restored, List.of(), Set.of(deleted.fileName()));

        assertEquals(List.of(), ClientConfigStore.pendingProfileDeletes());
        assertEquals(ClientConfigStore.SavePhase.IDLE, ClientConfigStore.saveStatus().phase());
    }

    @Test
    void shutdownFlushPersistsPendingConfigAndProfileDeletionImmediately() throws IOException {
        Config baseline = ClientConfigStore.get().copy();
        baseline.createProfile();
        ClientConfigStoreTestSupport.save(baseline);
        Config edited = baseline.copy();
        StagedProfileDelete deleted = edited.stageDeleteProfile(0);
        assertTrue(deleted != null);
        Path deletedPath = Config.getProfileDirectory(fs()).resolve(deleted.fileName());
        Path lockPath = ConfigSaveLock.lockPathForTests(fs());
        Files.createDirectories(lockPath.getParent());

        try (FileChannel channel = FileChannel.open(lockPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock ignored = channel.lock()) {
            assertThrows(ConfigSaveException.class,
                    () -> ClientConfigStoreTestSupport.save(edited, List.of(deleted.fileName())));
            assertTrue(Files.exists(deletedPath));
            assertEquals(2, ClientConfigStore.snapshot().config().profileCount());
            assertEquals(1, ClientConfigStore.get().profileCount());
        }

        ClientConfigStoreTestSupport.savePending();
        assertFalse(Files.exists(deletedPath));
        assertFalse(ClientConfigStore.hasUnsavedChanges());
        assertEquals(1, ClientConfigStore.get().profileCount());
    }

    @Test
    void retryingPendingSaveReportsFailureWhenTheFinalSaveFails() throws IOException {
        Config baseline = ClientConfigStore.get().copy();
        baseline.createProfile();
        ClientConfigStoreTestSupport.save(baseline);
        Config edited = baseline.copy();
        edited.enabled = !baseline.enabled;
        StagedProfileDelete deleted = edited.stageDeleteProfile(0);
        assertTrue(deleted != null);
        ClientConfigStore.update(edited, List.of(deleted.fileName()));
        FaultingConfigFileSystem.current().failWritesWhen(path -> path.equals(Config.getConfigPath(fs())));

        assertThrows(ConfigSaveException.class, ClientConfigStoreTestSupport::savePending);

        assertTrue(ClientConfigStore.hasUnsavedChanges());
        assertEquals(edited.enabled, ClientConfigStore.pendingSaveConfig().enabled);

        FaultingConfigFileSystem.current().failWritesWhen(null);
        ClientConfigStoreTestSupport.save(ClientConfigStore.get());
        assertFalse(ClientConfigStore.hasUnsavedChanges());
    }

    @Test
    void explicitReloadPublishesTheSavedConfigRatherThanReadOnlyDefaults() throws IOException {
        Config baseline = ClientConfigStore.get().copy();
        baseline.enabled = false;
        ClientConfigStoreTestSupport.save(baseline);
        Config edited = baseline.copy();
        edited.enabled = true;
        ClientConfigStore.update(edited);
        assertTrue(ClientConfigStore.hasUnsavedChanges());
        Path lockPath = ConfigSaveLock.lockPathForTests(fs());
        Files.createDirectories(lockPath.getParent());

        try (FileChannel channel = FileChannel.open(lockPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock ignored = channel.lock()) {
            assertTrue(ClientConfigStore.reloadFromDisk());

            assertFalse(ClientConfigStore.get().enabled);
            assertFalse(ClientConfigStore.get().isReadOnly());
            assertFalse(ClientConfigStore.hasUnsavedChanges());
        }
    }

    @Test
    void publishedConfigAccessorsReturnDefensiveCopies() {
        ClientConfigStore.Snapshot published = ClientConfigStore.snapshot();
        long revision = published.revision();
        boolean enabled = published.config().enabled;
        int lineColor = published.config().line().colorRgb();

        Config fromGet = ClientConfigStore.get();
        Config fromSnapshot = published.config();
        fromGet.enabled = !enabled;
        ConfigTestFixtures.updateActiveProfile(fromGet, profile -> profile.withLine(
                profile.line().withColorRgb(profile.line().colorRgb() ^ 0x00FFFFFF)));
        fromSnapshot.enabled = !enabled;
        ConfigTestFixtures.updateActiveProfile(fromSnapshot, profile -> profile.withLine(
                profile.line().withColorRgb(profile.line().colorRgb() ^ 0x00555555)));

        Config stillPublished = ClientConfigStore.get();
        assertEquals(enabled, stillPublished.enabled);
        assertEquals(lineColor, stillPublished.line().colorRgb());
        assertEquals(revision, ClientConfigStore.revision());
        assertNotSame(fromGet, stillPublished);
        assertNotSame(fromSnapshot, ClientConfigStore.snapshot().config());
        assertSame(published.hudRenderState(), ClientConfigStore.snapshot().hudRenderState());
    }

    @Test
    void publishedEditsRemainMarkedUnsavedUntilPersisted() {
        Config edited = ClientConfigStore.get().copy();
        edited.enabled = !edited.enabled;

        ClientConfigStore.update(edited);

        assertTrue(ClientConfigStore.hasUnsavedChanges());
        assertFalse(ClientConfigStore.reloadFromDiskIfIdle());
        ClientConfigStoreTestSupport.save(ClientConfigStore.get().copy());
        assertFalse(ClientConfigStore.hasUnsavedChanges());
    }

    @Test
    void asynchronousStructuralEditRebasesOntoEarlierCommitAndRunsNext() throws Exception {
        Config baseline = ClientConfigStore.get().copy();
        baseline.enabled = false;
        ClientConfigStoreTestSupport.save(baseline);
        Config first = baseline.copy();
        first.enabled = true;
        ClientConfigStore.update(first);

        CountDownLatch firstPublicationStarted = new CountDownLatch(1);
        CountDownLatch allowFirstPublication = new CountDownLatch(1);
        AtomicBoolean blockOnce = new AtomicBoolean(true);
        FaultingConfigFileSystem.current().failWritesWhen(path -> {
            if (path.equals(Config.getConfigPath(fs())) && blockOnce.compareAndSet(true, false)) {
                firstPublicationStarted.countDown();
                try {
                    if (!allowFirstPublication.await(5L, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting to release first publication");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
            }
            return false;
        });

        CompletableFuture<ClientConfigStore.AsyncSaveResult> firstSave =
                ClientConfigStore.saveAsync(first);
        assertTrue(firstPublicationStarted.await(5L, TimeUnit.SECONDS));

        Config structural = first.copy();
        structural.createProfile();
        ClientConfigStore.update(structural);
        CompletableFuture<ClientConfigStore.AsyncSaveResult> structuralSave =
                ClientConfigStore.saveAsync(structural);
        allowFirstPublication.countDown();

        ClientConfigStore.AsyncSaveResult firstResult = firstSave.get(10L, TimeUnit.SECONDS);
        ClientConfigStore.AsyncSaveResult structuralResult =
                structuralSave.get(10L, TimeUnit.SECONDS);

        assertEquals(ClientConfigStore.SaveOutcome.COMMITTED, firstResult.result().outcome());
        assertFalse(firstResult.latestEditsCommitted());
        assertEquals(ClientConfigStore.SaveOutcome.COMMITTED, structuralResult.result().outcome());
        assertTrue(structuralResult.latestEditsCommitted());

        Config disk = Config.reloadFromDisk(fs());
        assertTrue(disk.enabled);
        assertEquals(structural.profileCount(), disk.profileCount());
        assertFalse(ClientConfigStore.hasUnsavedChanges());
    }

    @Test
    void discardRefusesAQueuedSaveAfterItsAncestorCommits() throws Exception {
        Config baseline = ClientConfigStore.get().copy();
        baseline.enabled = false;
        ClientConfigStoreTestSupport.save(baseline);
        CountDownLatch[] started = {new CountDownLatch(1), new CountDownLatch(1)};
        CountDownLatch[] release = {new CountDownLatch(1), new CountDownLatch(1)};
        AtomicInteger publications = new AtomicInteger();
        FaultingConfigFileSystem faulting = FaultingConfigFileSystem.current();
        faulting.failWritesWhen(path -> {
            if (path.equals(Config.getConfigPath(fs()))) {
                int index = publications.getAndIncrement();
                if (index < started.length) {
                    started[index].countDown();
                    try {
                        if (!release[index].await(10L, TimeUnit.SECONDS)) {
                            throw new AssertionError("Timed out waiting to release publication " + index);
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(interrupted);
                    }
                }
            }
            return false;
        });

        try {
            Config first = baseline.copy();
            first.enabled = true;
            CompletableFuture<ClientConfigStore.AsyncSaveResult> firstSave = ClientConfigStore.saveAsync(first);
            assertTrue(started[0].await(5L, TimeUnit.SECONDS));

            Config second = first.copy();
            second.enabled = false;
            CompletableFuture<ClientConfigStore.AsyncSaveResult> secondSave = ClientConfigStore.saveAsync(second);
            release[0].countDown();
            assertTrue(started[1].await(5L, TimeUnit.SECONDS));

            ClientConfigStore.SaveStatus status = ClientConfigStore.saveStatus();
            ClientConfigStore.SaveResult discard = ClientConfigStore.discardPendingSave(status.pendingGeneration());
            assertEquals(ClientConfigStore.SaveOutcome.FAILED, discard.outcome());
            assertEquals(ClientConfigStore.SavePhase.SAVING, status.phase());
            assertTrue(ClientConfigStore.hasUnsavedChanges());

            release[1].countDown();
            assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                    firstSave.get(5L, TimeUnit.SECONDS).result().outcome());
            assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                    secondSave.get(5L, TimeUnit.SECONDS).result().outcome());
            assertFalse(Config.reloadFromDisk(fs()).enabled);
            assertFalse(ClientConfigStore.hasUnsavedChanges());
        } finally {
            for (CountDownLatch latch : release) {
                latch.countDown();
            }
            faulting.failWritesWhen(null);
        }
    }

    @Test
    void queuedSameFileEditRebasesOntoEarlierAsyncCommit() throws Exception {
        Config baseline = ClientConfigStore.get().copy();
        baseline.enabled = false;
        ClientConfigStoreTestSupport.save(baseline);
        Config first = baseline.copy();
        first.enabled = true;
        ClientConfigStore.update(first);

        CountDownLatch firstPublicationStarted = new CountDownLatch(1);
        CountDownLatch allowFirstPublication = new CountDownLatch(1);
        AtomicBoolean blockOnce = new AtomicBoolean(true);
        FaultingConfigFileSystem.current().failWritesWhen(path -> {
            if (path.equals(Config.getConfigPath(fs())) && blockOnce.compareAndSet(true, false)) {
                firstPublicationStarted.countDown();
                try {
                    if (!allowFirstPublication.await(5L, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting to release first publication");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
            }
            return false;
        });

        CompletableFuture<ClientConfigStore.AsyncSaveResult> firstSave =
                ClientConfigStore.saveAsync(first);
        assertTrue(firstPublicationStarted.await(5L, TimeUnit.SECONDS));

        Config second = first.copy();
        second.enabled = false;
        ClientConfigStore.update(second);
        CompletableFuture<ClientConfigStore.AsyncSaveResult> secondSave =
                ClientConfigStore.saveAsync(second);
        allowFirstPublication.countDown();

        ClientConfigStore.AsyncSaveResult firstResult = firstSave.get(10L, TimeUnit.SECONDS);
        ClientConfigStore.AsyncSaveResult secondResult = secondSave.get(10L, TimeUnit.SECONDS);

        assertEquals(ClientConfigStore.SaveOutcome.COMMITTED, firstResult.result().outcome());
        assertFalse(firstResult.latestEditsCommitted());
        assertEquals(ClientConfigStore.SaveOutcome.COMMITTED, secondResult.result().outcome());
        assertTrue(secondResult.latestEditsCommitted());
        assertFalse(Config.reloadFromDisk(fs()).enabled);
        assertFalse(ClientConfigStore.hasUnsavedChanges());
        assertNull(ClientConfigStore.pendingSaveConflict());
    }

    @Test
    void replacedQueuedSaveCompletesAsSupersededWithItsOriginalGeneration() throws Exception {
        Config baseline = ClientConfigStore.get().copy();
        baseline.enabled = false;
        ClientConfigStoreTestSupport.save(baseline);
        Config first = baseline.copy();
        first.enabled = true;
        ClientConfigStore.update(first);

        CountDownLatch firstPublicationStarted = new CountDownLatch(1);
        CountDownLatch allowFirstPublication = new CountDownLatch(1);
        AtomicBoolean blockOnce = new AtomicBoolean(true);
        FaultingConfigFileSystem.current().failWritesWhen(path -> {
            if (path.equals(Config.getConfigPath(fs())) && blockOnce.compareAndSet(true, false)) {
                firstPublicationStarted.countDown();
                try {
                    if (!allowFirstPublication.await(5L, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting to release first publication");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
            }
            return false;
        });

        try {
            CompletableFuture<ClientConfigStore.AsyncSaveResult> firstSave =
                    ClientConfigStore.saveAsync(first);
            assertTrue(firstPublicationStarted.await(5L, TimeUnit.SECONDS));

            Config replaced = first.copy();
            replaced.profileSortMode = Config.PROFILE_SORT_NAME;
            ClientConfigStore.update(replaced);
            long replacedGeneration = ClientConfigStore.saveStatus().pendingGeneration();
            CompletableFuture<ClientConfigStore.AsyncSaveResult> replacedSave =
                    ClientConfigStore.saveAsync(replaced);

            Config newest = replaced.copy();
            newest.usedSettingsSearch = true;
            ClientConfigStore.update(newest);
            CompletableFuture<ClientConfigStore.AsyncSaveResult> newestSave =
                    ClientConfigStore.saveAsync(newest);

            ClientConfigStore.AsyncSaveResult superseded =
                    replacedSave.get(1L, TimeUnit.SECONDS);
            assertEquals(ClientConfigStore.SaveOutcome.SUPERSEDED,
                    superseded.result().outcome());
            assertEquals(replacedGeneration, superseded.pendingGeneration());
            assertFalse(superseded.latestEditsCommitted());
            assertFalse(newestSave.isDone());

            allowFirstPublication.countDown();
            assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                    firstSave.get(10L, TimeUnit.SECONDS).result().outcome());
            ClientConfigStore.AsyncSaveResult newestResult =
                    newestSave.get(10L, TimeUnit.SECONDS);
            assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                    newestResult.result().outcome());
            assertTrue(newestResult.latestEditsCommitted());

            Config disk = Config.reloadFromDisk(fs());
            assertTrue(disk.enabled);
            assertEquals(Config.PROFILE_SORT_NAME, disk.profileSortMode);
            assertTrue(disk.usedSettingsSearch);
            assertFalse(ClientConfigStore.hasUnsavedChanges());
        } finally {
            allowFirstPublication.countDown();
        }
    }

    @Test
    void queuedProfileDeleteUsesEarlierProfileCommitAsItsDeletionBaseline() throws Exception {
        Config baseline = ClientConfigStore.get().copy();
        baseline.createProfile();
        ClientConfigStoreTestSupport.save(baseline);
        Config first = ClientConfigStore.get().copy();
        String deletedFile = first.profile(0).fileName();
        first.replaceProfile(0, first.profile(0).withName("Committed before queued deletion"));
        first.enabled = !first.enabled;
        ClientConfigStore.update(first);

        CountDownLatch firstPublicationStarted = new CountDownLatch(1);
        CountDownLatch allowFirstPublication = new CountDownLatch(1);
        AtomicBoolean blockOnce = new AtomicBoolean(true);
        FaultingConfigFileSystem.current().failWritesWhen(path -> {
            if (path.equals(Config.getConfigPath(fs())) && blockOnce.compareAndSet(true, false)) {
                firstPublicationStarted.countDown();
                try {
                    if (!allowFirstPublication.await(5L, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting to release first publication");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
            }
            return false;
        });

        CompletableFuture<ClientConfigStore.AsyncSaveResult> firstSave =
                ClientConfigStore.saveAsync(first);
        assertTrue(firstPublicationStarted.await(5L, TimeUnit.SECONDS));

        Config deleting = first.copy();
        StagedProfileDelete deleted = deleting.stageDeleteProfile(0);
        assertTrue(deleted != null);
        assertEquals(deletedFile, deleted.fileName());
        ClientConfigStore.update(deleting, List.of(deleted.fileName()));
        CompletableFuture<ClientConfigStore.AsyncSaveResult> deleteSave =
                ClientConfigStore.saveAsync(deleting, List.of(deleted.fileName()));
        allowFirstPublication.countDown();

        assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                firstSave.get(10L, TimeUnit.SECONDS).result().outcome());
        ClientConfigStore.AsyncSaveResult deleteResult =
                deleteSave.get(10L, TimeUnit.SECONDS);
        assertEquals(ClientConfigStore.SaveOutcome.COMMITTED, deleteResult.result().outcome());
        assertNull(ClientConfigStore.pendingSaveConflict());
        assertFalse(Files.exists(Config.getProfileDirectory(fs()).resolve(deletedFile)));
        assertFalse(ClientConfigStore.hasUnsavedChanges());
    }

    @Test
    void queuedProfileEditConsumesEarlierSaveSuppression() throws Exception {
        Config baseline = ClientConfigStore.get().copy();
        ClientConfigStoreTestSupport.save(baseline);
        Config first = ClientConfigStore.get().copy();
        first.skipNextProfileSave = true;
        first.enabled = !first.enabled;
        ClientConfigStore.update(first);

        CountDownLatch firstPublicationStarted = new CountDownLatch(1);
        CountDownLatch allowFirstPublication = new CountDownLatch(1);
        AtomicBoolean blockOnce = new AtomicBoolean(true);
        FaultingConfigFileSystem.current().failWritesWhen(path -> {
            if (path.equals(Config.getConfigPath(fs())) && blockOnce.compareAndSet(true, false)) {
                firstPublicationStarted.countDown();
                try {
                    if (!allowFirstPublication.await(5L, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting to release first publication");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
            }
            return false;
        });

        CompletableFuture<ClientConfigStore.AsyncSaveResult> firstSave =
                ClientConfigStore.saveAsync(first);
        assertTrue(firstPublicationStarted.await(5L, TimeUnit.SECONDS));

        Config second = first.copy();
        second.replaceProfile(0, second.profile(0).withName("Queued profile edit"));
        ClientConfigStore.update(second);
        CompletableFuture<ClientConfigStore.AsyncSaveResult> secondSave =
                ClientConfigStore.saveAsync(second);
        allowFirstPublication.countDown();

        assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                firstSave.get(10L, TimeUnit.SECONDS).result().outcome());
        assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                secondSave.get(10L, TimeUnit.SECONDS).result().outcome());
        assertEquals("Queued profile edit", Config.reloadFromDisk(fs()).profileName(0));
        assertFalse(ClientConfigStore.hasUnsavedChanges());
    }

    @Test
    void forcedReloadRefusesWhileAsynchronousPersistenceIsActive() throws Exception {
        Config baseline = ClientConfigStore.get().copy();
        baseline.enabled = false;
        ClientConfigStoreTestSupport.save(baseline);
        Config edited = baseline.copy();
        edited.enabled = true;
        ClientConfigStore.update(edited);

        CountDownLatch publicationStarted = new CountDownLatch(1);
        CountDownLatch allowPublication = new CountDownLatch(1);
        AtomicBoolean blockOnce = new AtomicBoolean(true);
        FaultingConfigFileSystem.current().failWritesWhen(path -> {
            if (path.equals(Config.getConfigPath(fs())) && blockOnce.compareAndSet(true, false)) {
                publicationStarted.countDown();
                try {
                    if (!allowPublication.await(5L, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting to release publication");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
            }
            return false;
        });

        CompletableFuture<ClientConfigStore.AsyncSaveResult> save =
                ClientConfigStore.saveAsync(edited);
        assertTrue(publicationStarted.await(5L, TimeUnit.SECONDS));

        assertFalse(ClientConfigStore.reloadFromDisk());
        assertTrue(ClientConfigStore.get().enabled);
        assertTrue(ClientConfigStore.hasUnsavedChanges());

        allowPublication.countDown();
        assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                save.get(10L, TimeUnit.SECONDS).result().outcome());
        assertTrue(Config.reloadFromDisk(fs()).enabled);
    }

    @Test
    void directoryForceFailureFailsTheSaveAndKeepsTheEditPending() {
        Config baseline = ClientConfigStore.get().copy();
        baseline.enabled = false;
        ClientConfigStoreTestSupport.save(baseline);
        Config edited = baseline.copy();
        edited.enabled = true;
        Path configPath = Config.getConfigPath(fs()).toAbsolutePath().normalize();
        FaultingConfigFileSystem.current().failForcesWhen(
                path -> path.toAbsolutePath().normalize().equals(configPath));

        ClientConfigStore.SaveResult result = ClientConfigStoreTestSupport.trySave(edited, List.of());
        ClientConfigStore.SaveStatus status = ClientConfigStore.saveStatus();

        assertEquals(ClientConfigStore.SaveOutcome.FAILED, result.outcome());
        assertEquals(ClientConfigStore.SavePhase.FAILED, status.phase());
        assertFalse(ClientConfigStore.snapshot().config().enabled);
        assertTrue(ClientConfigStore.hasUnsavedChanges());
    }

    @Test
    void saveSucceedsWhereTheFilesystemCannotSyncDirectories() {
        Config baseline = ClientConfigStore.get().copy();
        baseline.enabled = false;
        ClientConfigStoreTestSupport.save(baseline);
        Config edited = baseline.copy();
        edited.enabled = true;
        FaultingConfigFileSystem.current().skipDirectorySyncWhen(path -> true);

        ClientConfigStore.SaveResult result = ClientConfigStoreTestSupport.trySave(edited, List.of());

        assertEquals(ClientConfigStore.SaveOutcome.COMMITTED, result.outcome());
        assertTrue(Config.reloadFromDisk(fs()).enabled);
        assertFalse(ClientConfigStore.hasUnsavedChanges());
    }

    @Test
    void idleDiscardIsGenerationGuardedAndSucceedsForTheCurrentGeneration() {
        Config baseline = ClientConfigStore.get().copy();
        ClientConfigStoreTestSupport.save(baseline);
        Config first = baseline.copy();
        first.enabled = !first.enabled;
        ClientConfigStore.update(first);
        long firstGeneration = ClientConfigStore.saveStatus().pendingGeneration();
        Config newer = first.copy();
        newer.profileSortMode = Config.PROFILE_SORT_NAME;
        ClientConfigStore.update(newer);
        long newerGeneration = ClientConfigStore.saveStatus().pendingGeneration();

        assertEquals(ClientConfigStore.SaveOutcome.FAILED,
                ClientConfigStore.discardPendingSave(firstGeneration).outcome());
        assertEquals(ClientConfigStore.SaveOutcome.CANCELLED,
                ClientConfigStore.discardPendingSave(newerGeneration).outcome());
        assertFalse(ClientConfigStore.hasUnsavedChanges());
        assertEquals(baseline.enabled, ClientConfigStore.get().enabled);
    }

    @Test
    void discardRefusesAnInFlightGeneration() throws Exception {
        Config edited = ClientConfigStore.get().copy();
        edited.enabled = !edited.enabled;
        CountDownLatch writeStarted = new CountDownLatch(1);
        CountDownLatch allowWrite = new CountDownLatch(1);
        AtomicBoolean blockOnce = new AtomicBoolean(true);
        FaultingConfigFileSystem.current().beforeAtomicMove(() -> {
            if (!blockOnce.compareAndSet(true, false)) {
                return;
            }
            writeStarted.countDown();
            try {
                if (!allowWrite.await(5L, TimeUnit.SECONDS)) {
                    throw new AssertionError("Timed out waiting to release write");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
        });

        CompletableFuture<ClientConfigStore.AsyncSaveResult> save =
                ClientConfigStore.saveAsync(edited);
        assertTrue(writeStarted.await(5L, TimeUnit.SECONDS));
        long generation = ClientConfigStore.saveStatus().pendingGeneration();

        assertEquals(ClientConfigStore.SaveOutcome.FAILED,
                ClientConfigStore.discardPendingSave(generation).outcome());
        assertTrue(ClientConfigStore.hasUnsavedChanges());

        allowWrite.countDown();
        assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                save.get(10L, TimeUnit.SECONDS).result().outcome());
    }

    @Test
    void pendingProfileDeletesSurviveUntilAnyLaterSave() {
        Config config = ClientConfigStore.get().copy();
        config.createProfile();
        ClientConfigStoreTestSupport.save(config);
        StagedProfileDelete deleted = config.stageDeleteProfile(0);
        assertTrue(deleted != null);
        Path deletedPath = Config.getProfileDirectory(fs()).resolve(deleted.fileName());

        ClientConfigStore.update(config, List.of(deleted.fileName()));

        assertEquals(List.of(deleted.fileName()), ClientConfigStore.pendingProfileDeletes());
        ClientConfigStoreTestSupport.save(ClientConfigStore.get().copy());
        assertFalse(Files.exists(deletedPath));
        assertEquals(List.of(), ClientConfigStore.pendingProfileDeletes());
        assertFalse(ClientConfigStore.hasUnsavedChanges());
    }

    @Test
    void reloadDiskCancellationRemovesPendingProfileDeleteCaseInsensitively() {
        Config config = ClientConfigStore.get().copy();
        ClientConfigStore.update(config, List.of("Foo.json"));

        ClientConfigStore.update(config, List.of(), Set.of("foo.json"));

        assertEquals(List.of(), ClientConfigStore.pendingProfileDeletes());
    }

    @Test
    void reloadDiskCancellationKeepsUnicodeDistinctPendingDelete() {
        Config config = ClientConfigStore.get().copy();
        ClientConfigStore.update(config, List.of("İ.json"));

        ClientConfigStore.update(config, List.of(), Set.of("i.json"));

        assertEquals(List.of("İ.json"), ClientConfigStore.pendingProfileDeletes());
    }

    @Test
    void updatePublishedDuringSaveRemainsUnsavedAfterward() throws Exception {
        Config saving = ClientConfigStore.get().copy();
        saving.enabled = !saving.enabled;
        Config newer = saving.copy();
        newer.replaceProfile(0, newer.profile(0).withName("Newer concurrent edit"));
        CountDownLatch writeStarted = new CountDownLatch(1);
        CountDownLatch allowWrite = new CountDownLatch(1);
        AtomicBoolean blockedOnce = new AtomicBoolean();
        FaultingConfigFileSystem.current().failWritesWhen(path -> {
            if (blockedOnce.compareAndSet(false, true)) {
                writeStarted.countDown();
                try {
                    return !allowWrite.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return true;
                }
            }
            return false;
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> save = executor.submit(() -> ClientConfigStoreTestSupport.save(saving));
            assertTrue(writeStarted.await(5, TimeUnit.SECONDS));
            Future<?> update = executor.submit(() ->
                    ClientConfigStore.update(newer, List.of("later-delete.json")));
            update.get(10, TimeUnit.SECONDS);

            allowWrite.countDown();
            save.get(10, TimeUnit.SECONDS);

            assertEquals("Newer concurrent edit", ClientConfigStore.get().profileName(0));
            assertEquals(List.of("later-delete.json"), ClientConfigStore.pendingProfileDeletes());
            assertTrue(ClientConfigStore.hasUnsavedChanges());
        } finally {
            allowWrite.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void conflictingProfileSaveKeepsPublishedEditMarkedUnsaved() throws IOException {
        Config baseline = ClientConfigStore.get().copy();
        ClientConfigStoreTestSupport.save(baseline);

        Config edited = ClientConfigStore.get().copy();
        edited.replaceProfile(0, edited.profile(0).withName("In-game edit"));
        ClientConfigStore.update(edited);
        long editedRevision = ClientConfigStore.revision();

        Path profilePath = edited.getProfilePath(fs(), 0);
        long loadedAt = Files.getLastModifiedTime(profilePath).toMillis();
        String externalJson = Files.readString(profilePath, StandardCharsets.UTF_8)
                .replace(baseline.profileName(0), "External edit");
        Files.writeString(profilePath, externalJson, StandardCharsets.UTF_8);
        Files.setLastModifiedTime(profilePath, FileTime.fromMillis(loadedAt + 10_000L));

        assertThrows(ProfileConflictException.class, () -> ClientConfigStoreTestSupport.save(edited));
        assertEquals(editedRevision, ClientConfigStore.revision());
        assertEquals("In-game edit", ClientConfigStore.get().profileName(0));
        assertFalse(ClientConfigStore.reloadFromDiskIfIdle());
    }

    @Test
    void conflictingMainConfigSaveKeepsPublishedEditMarkedUnsaved() throws IOException {
        Config baseline = ClientConfigStore.get().copy();
        baseline.enabled = true;
        ClientConfigStoreTestSupport.save(baseline);

        Config edited = ClientConfigStore.get().copy();
        edited.enabled = false;
        ClientConfigStore.update(edited);
        long editedRevision = ClientConfigStore.revision();

        Path configPath = Config.getConfigPath(fs());
        long loadedAt = Files.getLastModifiedTime(configPath).toMillis();
        JsonObject externalJson = ConfigFiles.GSON.fromJson(Files.readString(configPath, StandardCharsets.UTF_8),
                JsonObject.class);
        externalJson.addProperty("profileSortMode", Config.PROFILE_SORT_NAME);
        ConfigFiles.writeJsonAtomic(fs(), configPath, externalJson);
        Files.setLastModifiedTime(configPath, FileTime.fromMillis(loadedAt + 10_000L));

        ConfigConflictException conflict = assertThrows(ConfigConflictException.class,
                () -> ClientConfigStoreTestSupport.save(edited));

        assertEquals(List.of(configPath), conflict.conflictPaths());
        assertEquals(editedRevision, ClientConfigStore.revision());
        assertFalse(ClientConfigStore.get().enabled);
        assertFalse(ClientConfigStore.reloadFromDiskIfIdle());
        JsonObject savedJson = ConfigFiles.GSON.fromJson(Files.readString(configPath, StandardCharsets.UTF_8),
                JsonObject.class);
        assertEquals(Config.PROFILE_SORT_NAME, savedJson.get("profileSortMode").getAsInt());
    }

    @Test
    void approvedMainConfigSnapshotOverwritesTheChangeUserSaw() throws IOException {
        Config baseline = ClientConfigStore.get().copy();
        baseline.enabled = true;
        ClientConfigStoreTestSupport.save(baseline);

        Config edited = ClientConfigStore.get().copy();
        edited.enabled = false;
        Path configPath = Config.getConfigPath(fs());
        JsonObject externalJson = ConfigFiles.GSON.fromJson(Files.readString(configPath, StandardCharsets.UTF_8),
                JsonObject.class);
        externalJson.addProperty("profileSortMode", Config.PROFILE_SORT_NAME);
        ConfigFiles.writeJsonAtomic(fs(), configPath, externalJson);
        Config.ConflictBaseline approval = Config.captureConflictBaseline(fs(), List.of(configPath));
        edited.acceptConflictBaseline(fs(), approval);

        ClientConfigStoreTestSupport.save(edited);

        JsonObject savedJson = ConfigFiles.GSON.fromJson(Files.readString(configPath, StandardCharsets.UTF_8),
                JsonObject.class);
        assertFalse(savedJson.get("enabled").getAsBoolean());
        assertEquals(Config.PROFILE_SORT_CREATED, savedJson.get("profileSortMode").getAsInt());
    }

    @Test
    void approvalDoesNotCoverAChangeMadeAfterConfirmationOpened() throws IOException {
        Config baseline = ClientConfigStore.get().copy();
        baseline.enabled = true;
        ClientConfigStoreTestSupport.save(baseline);

        Config edited = ClientConfigStore.get().copy();
        edited.enabled = false;
        Path configPath = Config.getConfigPath(fs());
        JsonObject firstExternal = ConfigFiles.GSON.fromJson(
                Files.readString(configPath, StandardCharsets.UTF_8), JsonObject.class);
        firstExternal.addProperty("profileSortMode", Config.PROFILE_SORT_NAME);
        ConfigFiles.writeJsonAtomic(fs(), configPath, firstExternal);
        Config.ConflictBaseline approval = Config.captureConflictBaseline(fs(), List.of(configPath));

        JsonObject laterExternal = firstExternal.deepCopy();
        laterExternal.addProperty("profileSortMode", Config.PROFILE_SORT_MODIFIED);
        ConfigFiles.writeJsonAtomic(fs(), configPath, laterExternal);
        edited.acceptConflictBaseline(fs(), approval);

        assertThrows(ConfigConflictException.class, () -> ClientConfigStoreTestSupport.save(edited));
        JsonObject preserved = ConfigFiles.GSON.fromJson(
                Files.readString(configPath, StandardCharsets.UTF_8), JsonObject.class);
        assertEquals(Config.PROFILE_SORT_MODIFIED, preserved.get("profileSortMode").getAsInt());
    }

    @Test
    void publicOverwriteApprovalDoesNotCoverLaterExternalBytes() throws IOException {
        Config baseline = ClientConfigStore.get().copy();
        ClientConfigStoreTestSupport.save(baseline);
        Config edited = ClientConfigStore.get().copy();
        edited.enabled = !edited.enabled;
        Path configPath = Config.getConfigPath(fs());
        JsonObject firstExternal = ConfigFiles.GSON.fromJson(
                Files.readString(configPath, StandardCharsets.UTF_8), JsonObject.class);
        firstExternal.addProperty("profileSortMode", Config.PROFILE_SORT_NAME);
        ConfigFiles.writeJsonAtomic(fs(), configPath, firstExternal);
        Config.ConflictBaseline approval = Config.captureConflictBaseline(fs(), List.of(configPath));

        JsonObject laterExternal = firstExternal.deepCopy();
        laterExternal.addProperty("profileSortMode", Config.PROFILE_SORT_MODIFIED);
        ConfigFiles.writeJsonAtomic(fs(), configPath, laterExternal);

        assertThrows(ConfigConflictException.class,
                () -> edited.saveOverwritingExternalChanges(fs(), approval));
        JsonObject preserved = ConfigFiles.GSON.fromJson(
                Files.readString(configPath, StandardCharsets.UTF_8), JsonObject.class);
        assertEquals(Config.PROFILE_SORT_MODIFIED, preserved.get("profileSortMode").getAsInt());
    }

    @Test
    void equivalentExternalRewriteRefreshesLoadedTimestamp() throws IOException {
        Config baseline = ClientConfigStore.get().copy();
        ClientConfigStoreTestSupport.save(baseline);
        long baselineRevision = ClientConfigStore.revision();
        Path profilePath = baseline.getProfilePath(fs(), 0);
        long loadedAt = Files.getLastModifiedTime(profilePath).toMillis();

        String unchangedJson = Files.readString(profilePath, StandardCharsets.UTF_8);
        Files.writeString(profilePath, unchangedJson, StandardCharsets.UTF_8);
        Files.setLastModifiedTime(profilePath, FileTime.fromMillis(loadedAt + 10_000L));
        assertTrue(ClientConfigStore.reloadFromDiskIfIdle());
        assertTrue(ClientConfigStore.revision() > baselineRevision);

        Config edited = ClientConfigStore.get().copy();
        edited.replaceProfile(0, edited.profile(0).withName("Later in-game edit"));
        ClientConfigStoreTestSupport.save(edited);

        assertEquals("Later in-game edit", ClientConfigStore.get().profileName(0));
    }

    @Test
    void equivalentExternalMainConfigRewriteRefreshesLoadedFingerprint() throws IOException {
        Config baseline = ClientConfigStore.get().copy();
        ClientConfigStoreTestSupport.save(baseline);
        long baselineRevision = ClientConfigStore.revision();
        Path configPath = Config.getConfigPath(fs());
        long loadedAt = Files.getLastModifiedTime(configPath).toMillis();

        JsonObject json = ConfigFiles.GSON.fromJson(Files.readString(configPath, StandardCharsets.UTF_8),
                JsonObject.class);
        Files.writeString(configPath, COMPACT_GSON.toJson(json), StandardCharsets.UTF_8);
        Files.setLastModifiedTime(configPath, FileTime.fromMillis(loadedAt + 10_000L));
        assertTrue(ClientConfigStore.reloadFromDiskIfIdle());
        assertTrue(ClientConfigStore.revision() > baselineRevision);

        Config edited = ClientConfigStore.get().copy();
        edited.enabled = !edited.enabled;
        ClientConfigStoreTestSupport.save(edited);

        assertEquals(edited.enabled, ClientConfigStore.get().enabled);
    }
}
