package com.asphyxiamywife.elytrapitchhelper.client;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.ConfigFileSystem;
import com.asphyxiamywife.elytrapitchhelper.config.ConfigConflictException;
import com.asphyxiamywife.elytrapitchhelper.config.ConfigSaveException;
import com.asphyxiamywife.elytrapitchhelper.config.LocalCommitBaselines;
import com.asphyxiamywife.elytrapitchhelper.config.NioConfigFileSystem;
import com.asphyxiamywife.elytrapitchhelper.config.ProfileFileNames;
import com.asphyxiamywife.elytrapitchhelper.hud.HudRenderState;
import com.asphyxiamywife.elytrapitchhelper.platform.PlatformServices;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.asphyxiamywife.elytrapitchhelper.client.ClientConfigStore.AsyncSaveResult;
import static com.asphyxiamywife.elytrapitchhelper.client.ClientConfigStore.EffectiveSnapshot;
import static com.asphyxiamywife.elytrapitchhelper.client.ClientConfigStore.PendingConflict;
import static com.asphyxiamywife.elytrapitchhelper.client.ClientConfigStore.SaveOutcome;
import static com.asphyxiamywife.elytrapitchhelper.client.ClientConfigStore.SavePhase;
import static com.asphyxiamywife.elytrapitchhelper.client.ClientConfigStore.SaveResult;
import static com.asphyxiamywife.elytrapitchhelper.client.ClientConfigStore.SaveStatus;
import static com.asphyxiamywife.elytrapitchhelper.client.ClientConfigStore.Snapshot;

public final class ConfigStore implements AutoCloseable {
    private static final long SYNCHRONOUS_SAVE_TIMEOUT_MILLIS = 5_000L;
    private static final long CLOSE_DRAIN_TIMEOUT_MILLIS = 2_000L;
    static final long SHUTDOWN_SAVE_TIMEOUT_MILLIS = 3_000L;
    private final LocalCommitBaselines localBaselines = new LocalCommitBaselines();
    private final Object initializeLock = new Object();
    private final Object publishLock = new Object();
    private final AtomicInteger activeSaves = new AtomicInteger();
    private final AtomicLong revision = new AtomicLong();
    private final AtomicLong configViewRevision = new AtomicLong();
    private final AtomicLong stateRevision = new AtomicLong();
    private final AtomicLong pendingGeneration = new AtomicLong();
    private final Map<Long, CompletableFuture<AsyncSaveResult>> asyncRequests =
            new ConcurrentHashMap<>();
    private final ConfigFileSystem fileSystem;
    private final ConfigSaveWorker saveWorker = new ConfigSaveWorker(
            this::persist, this::applyAsyncCompletion, this::rebaseQueuedRequest);

    private volatile Snapshot snapshot;
    private volatile EffectiveSnapshot effectiveSnapshot;
    private volatile PendingSave pendingSave;

    public ConfigStore() {
        this(NioConfigFileSystem.rootedAt(PlatformServices.configDirectory()));
    }

    public ConfigStore(ConfigFileSystem fileSystem) {
        this.fileSystem = java.util.Objects.requireNonNull(fileSystem, "fileSystem");
    }

    public Path configRoot() {
        return fileSystem.configRoot();
    }

    public Path configPath() {
        return Config.getConfigPath(fileSystem);
    }

    public Path configDirectory() {
        return Config.getConfigDirectory(fileSystem);
    }

    public Path profileDirectory() {
        return Config.getProfileDirectory(fileSystem);
    }

    public ConfigFileSystem fileSystem() {
        return fileSystem;
    }

    boolean isSaveWorkerThread() {
        return saveWorker.isWorkerThread();
    }

    @Override
    public void close() {
        saveWorker.shutdownAndAwaitTermination(CLOSE_DRAIN_TIMEOUT_MILLIS);
        if (!asyncRequests.isEmpty()) {
            throw new IllegalStateException("Config submissions are still completing; the store cannot be released");
        }
    }

    public void initialize() {
        if (snapshot != null) {
            return;
        }

        synchronized (initializeLock) {
            if (snapshot == null) {
                Config loaded = Config.load(fileSystem);
                synchronized (publishLock) {
                    publishLocked(loaded);
                }
            }
        }
    }

    public boolean shutdownPersistenceCoordinator() {
        synchronized (publishLock) {
            PendingSave current = pendingSave;
            if (current != null && !asyncRequests.containsKey(current.generation())) {
                saveAsync(current.config(), current.deletedProfileFiles());
            }
        }
        return saveWorker.shutdown(SHUTDOWN_SAVE_TIMEOUT_MILLIS) && pendingSave == null;
    }

    public boolean reloadFromDisk() {
        return reloadFromDisk(false);
    }

    public boolean reloadFromDiskIfIdle() {
        return reloadFromDisk(true);
    }

    void saveNow(Config nextConfig, List<String> deletedProfileFiles) {
        AsyncSaveResult result = awaitSave(
                saveAsync(nextConfig, deletedProfileFiles), SYNCHRONOUS_SAVE_TIMEOUT_MILLIS);
        switch (result.result().outcome()) {
            case COMMITTED -> nextConfig.syncSavedProfileMetadataFrom(snapshot().config());
            case CONFLICTED -> throw result.result().conflict();
            case SUPERSEDED -> throw new ConfigSaveException("Config save was superseded by newer edits");
            case FAILED, CANCELLED -> {
                ConfigSaveException failure = result.result().failure();
                throw failure == null
                        ? new ConfigSaveException("Config save did not complete")
                        : failure;
            }
        }
    }

    public SaveResult trySet(Config nextConfig, List<String> deletedProfileFiles) {
        try {
            saveNow(nextConfig, deletedProfileFiles);
            return new SaveResult(SaveOutcome.COMMITTED, null, null);
        } catch (ConfigConflictException conflict) {
            return new SaveResult(SaveOutcome.CONFLICTED, conflict, null);
        } catch (ConfigSaveException failure) {
            return new SaveResult(SaveOutcome.FAILED, null, failure);
        }
    }

    public CompletableFuture<AsyncSaveResult> saveAsync(Config nextConfig) {
        return saveAsync(nextConfig, List.of());
    }

    public CompletableFuture<AsyncSaveResult> saveAsync(
            Config nextConfig, List<String> deletedProfileFiles) {
        Submission submission;
        synchronized (publishLock) {
            PendingSave current = pendingSave;
            Config requested = localBaselines.prepareSubmission(nextConfig,
                    effectiveSnapshot == null ? null : effectiveSnapshot.publishedConfig());
            List<String> mergedDeletes = mergeDeletions(
                    current == null ? List.of() : current.deletedProfileFiles(),
                    deletedProfileFiles, Set.of());
            if (current == null
                    || !current.config().hasSameQueuedSaveRequest(requested)
                    || !current.deletedProfileFiles().equals(mergedDeletes)) {
                updateLocked(requested, deletedProfileFiles, Set.of());
            }
            localBaselines.accepted(nextConfig);
            submission = prepareSubmissionLocked();
        }
        return dispatch(submission);
    }

    public CompletableFuture<AsyncSaveResult> flushPending() {
        Submission submission;
        synchronized (publishLock) {
            if (pendingSave == null) {
                return CompletableFuture.completedFuture(new AsyncSaveResult(
                        new SaveResult(SaveOutcome.COMMITTED, null, null), pendingGeneration.get(), true, this));
            }
            submission = prepareSubmissionLocked();
        }
        return dispatch(submission);
    }

    private Submission prepareSubmissionLocked() {
        PendingSave current = pendingSave;
        CompletableFuture<AsyncSaveResult> existing = asyncRequests.get(current.generation());
        if (existing != null) {
            return new Submission(null, existing);
        }
        if (current.phase() == SavePhase.FAILED || current.phase() == SavePhase.CONFLICTED) {
            current = new PendingSave(current.config(), current.deletedProfileFiles(),
                    pendingGeneration.incrementAndGet(), SavePhase.IDLE, null);
        }
        pendingSave = current.withPhase(SavePhase.SAVING);
        refreshEffectiveSnapshotLocked();
        ConfigSaveWorker.Request request = new ConfigSaveWorker.Request(
                current.generation(), current.config(), current.deletedProfileFiles());
        CompletableFuture<AsyncSaveResult> result = new CompletableFuture<>();
        asyncRequests.put(current.generation(), result);
        return new Submission(request, result);
    }

    private CompletableFuture<AsyncSaveResult> dispatch(Submission submission) {
        ConfigSaveWorker.Request request = submission.request();
        CompletableFuture<AsyncSaveResult> result = submission.result();
        if (request == null) {
            return result;
        }
        saveWorker.submit(request)
                .thenApply(this::toAsyncSaveResult)
                .whenComplete((completed, failure) -> {
                    asyncRequests.remove(request.requestId(), result);
                    if (failure == null) {
                        result.complete(completed);
                    } else {
                        result.completeExceptionally(failure);
                    }
                });
        return result;
    }

    private record Submission(ConfigSaveWorker.Request request, CompletableFuture<AsyncSaveResult> result) {
    }

    public ClientConfigStore.SaveObservation saveObservation() {
        synchronized (publishLock) {
            return new ClientConfigStore.SaveObservation(pendingGeneration.get(), saveStatus());
        }
    }

    private AsyncSaveResult awaitSave(
            CompletableFuture<AsyncSaveResult> save, long timeoutMillis) {
        try {
            return save.get(Math.max(0L, timeoutMillis), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ConfigSaveException("Interrupted while waiting for config save", interrupted);
        } catch (TimeoutException timeout) {
            throw new ConfigSaveException("Timed out waiting for config save", timeout);
        } catch (ExecutionException execution) {
            Throwable cause = execution.getCause();
            if (cause instanceof ConfigSaveException saveFailure) {
                throw saveFailure;
            }
            if (cause instanceof ConfigConflictException conflict) {
                throw conflict;
            }
            throw new ConfigSaveException("Config save completion failed", cause);
        }
    }

    public void update(Config nextConfig) {
        update(nextConfig, List.of());
    }

    public void update(Config nextConfig, List<String> deletedProfileFiles) {
        update(nextConfig, deletedProfileFiles, Set.of());
    }

    public void update(Config nextConfig, List<String> deletedProfileFiles,
            Set<String> canceledProfileDeletes) {
        synchronized (publishLock) {
            updateLocked(localBaselines.prepareSubmission(nextConfig,
                    effectiveSnapshot == null ? null : effectiveSnapshot.publishedConfig()),
                    deletedProfileFiles, canceledProfileDeletes);
            localBaselines.accepted(nextConfig);
        }
    }

    private void updateLocked(Config nextConfig, List<String> deletedProfileFiles,
            Set<String> canceledProfileDeletes) {
        PendingSave previous = pendingSave;
        List<String> mergedDeletes = mergeDeletions(
                previous == null ? List.of() : previous.deletedProfileFiles(),
                deletedProfileFiles, canceledProfileDeletes);
        long generation = pendingGeneration.incrementAndGet();
        PendingSave updated = new PendingSave(localBaselines.reconcile(nextConfig), mergedDeletes,
                generation, SavePhase.IDLE, null);
        pendingSave = updated;
        refreshEffectiveSnapshotLocked();
    }

    public boolean hasUnsavedChanges() {
        return pendingSave != null;
    }

    public List<String> pendingProfileDeletes() {
        synchronized (publishLock) {
            PendingSave current = pendingSave;
            return current == null ? List.of() : current.deletedProfileFiles();
        }
    }

    public Config pendingSaveConfig() {
        synchronized (publishLock) {
            PendingSave current = pendingSave;
            return current == null ? null : current.config();
        }
    }

    public SaveStatus saveStatus() {
        synchronized (publishLock) {
            PendingSave current = pendingSave;
            if (current == null) {
                return new SaveStatus(SavePhase.IDLE, -1L);
            }
            return new SaveStatus(current.phase(), current.generation());
        }
    }

    public SaveResult discardPendingSave(long expectedGeneration) {
        synchronized (publishLock) {
            PendingSave current = pendingSave;
            if (current == null) {
                return new SaveResult(SaveOutcome.COMMITTED, null, null);
            }
            if (current.generation() != expectedGeneration) {
                return new SaveResult(SaveOutcome.FAILED, null, null);
            }
            if (current.phase() == SavePhase.SAVING || current.phase() == SavePhase.RETRYING) {
                return new SaveResult(SaveOutcome.FAILED, null, null);
            }
            pendingSave = null;
            refreshEffectiveSnapshotLocked();
            return new SaveResult(SaveOutcome.CANCELLED, null, null);
        }
    }

    public PendingConflict pendingSaveConflict() {
        synchronized (publishLock) {
            PendingSave current = pendingSave;
            return current == null || current.conflict() == null
                    ? null
                    : new PendingConflict(current.generation(), current.conflict());
        }
    }

    private static boolean sameProfileFile(String first, String second) {
        String firstKey = ProfileFileNames.comparisonKey(first);
        return firstKey != null && firstKey.equals(ProfileFileNames.comparisonKey(second));
    }

    public Config get() {
        return effectiveSnapshot().config();
    }

    public Snapshot snapshot() {
        Snapshot current = snapshot;
        if (current == null) {
            initialize();
            current = snapshot;
        }
        return current;
    }

    public EffectiveSnapshot effectiveSnapshot() {
        EffectiveSnapshot current = effectiveSnapshot;
        if (current == null) {
            initialize();
            current = effectiveSnapshot;
        }
        return current;
    }

    public long revision() {
        return revision.get();
    }

    public long configViewRevision() {
        return configViewRevision.get();
    }

    public long stateRevision() {
        return stateRevision.get();
    }

    private boolean reloadFromDisk(boolean skipDuringSave) {
        if (skipDuringSave && (activeSaves.get() > 0 || pendingSave != null)) {
            return false;
        }

        if (!skipDuringSave) {
            synchronized (publishLock) {
                if (!asyncRequests.isEmpty() || !saveWorker.isIdle()) {
                    return false;
                }
                var loaded = Config.tryReloadFromDisk(fileSystem);
                if (loaded.isEmpty()) {
                    return false;
                }
                publishLocked(loaded.get());
                pendingSave = null;
                refreshEffectiveSnapshotLocked();
            }
            return true;
        }

        long observedRevision = revision.get();
        return Config.tryReloadFromDisk(fileSystem)
                .map(loaded -> publishIfUnchanged(loaded, observedRevision))
                .orElse(false);
    }

    private void applyAsyncCompletion(ConfigSaveWorker.Completion completion) {
        ConfigSaveWorker.Request request = completion.request();
        ConfigSaveWorker.Attempt attempt = completion.attempt();
        synchronized (publishLock) {
            PendingSave current = pendingSave;
            boolean currentGeneration = current != null
                    && current.generation() == request.requestId();

            if (!completion.terminal()) {
                if (currentGeneration) {
                    pendingSave = current.withAttempt(SavePhase.RETRYING, null);
                    refreshEffectiveSnapshotLocked();
                }
                return;
            }

            switch (attempt.outcome()) {
                case COMMITTED -> {
                    if (attempt.savedConfig() != null) {
                        localBaselines.committed(request.config(), attempt.savedConfig());
                    }
                    if (currentGeneration) {
                        pendingSave = null;
                    } else if (current != null) {
                        pendingSave = current.afterAncestorCommit(request.deletedProfileFiles());
                    }
                    Config saved = attempt.savedConfig();
                    if (saved != null) {
                        publishLocked(saved);
                    }
                }
                case CONFLICTED -> {
                    if (currentGeneration) {
                        pendingSave = current.withAttempt(SavePhase.CONFLICTED, attempt.conflict());
                        refreshEffectiveSnapshotLocked();
                    }
                }
                case FAILED, CANCELLED -> {
                    if (currentGeneration) {
                        pendingSave = current.withAttempt(SavePhase.FAILED, null);
                        refreshEffectiveSnapshotLocked();
                    }
                }
                case SUPERSEDED -> {
                }
            }
        }
    }

    private ConfigSaveWorker.Request rebaseQueuedRequest(ConfigSaveWorker.Request request,
            ConfigSaveWorker.Completion ancestor) {
        synchronized (publishLock) {
            PendingSave current = pendingSave;
            if (current != null && current.generation() == request.requestId()) {
                return new ConfigSaveWorker.Request(
                        request.requestId(), current.config(), current.deletedProfileFiles());
            }
            if (ancestor.attempt().outcome() != SaveOutcome.COMMITTED) {
                return request;
            }
            PendingSave rebased = new PendingSave(request.config(), request.deletedProfileFiles(),
                    request.requestId(), SavePhase.SAVING, null).afterAncestorCommit(
                            ancestor.request().deletedProfileFiles());
            return new ConfigSaveWorker.Request(
                    request.requestId(), rebased.config(), rebased.deletedProfileFiles());
        }
    }

    private ConfigSaveWorker.Attempt persist(ConfigSaveWorker.Request request) {
        Config saveSnapshot = request.config();
        activeSaves.incrementAndGet();
        try {
            if (request.deletedProfileFiles().isEmpty()) {
                saveSnapshot.save(fileSystem);
            } else {
                saveSnapshot.saveDeletingProfileFiles(fileSystem, request.deletedProfileFiles());
            }
        } catch (ConfigConflictException conflict) {
            return new ConfigSaveWorker.Attempt(SaveOutcome.CONFLICTED, conflict, null, null, false);
        } catch (ConfigSaveException failure) {
            return new ConfigSaveWorker.Attempt(
                    SaveOutcome.FAILED, null, failure, null, failure.isRetryable());
        } finally {
            activeSaves.decrementAndGet();
        }
        return new ConfigSaveWorker.Attempt(SaveOutcome.COMMITTED, null, null, saveSnapshot, false);
    }

    private AsyncSaveResult toAsyncSaveResult(ConfigSaveWorker.Completion completion) {
        ConfigSaveWorker.Attempt attempt = completion.attempt();
        boolean latest;
        synchronized (publishLock) {
            PendingSave current = pendingSave;
            latest = attempt.outcome() == SaveOutcome.COMMITTED
                    && pendingGeneration.get() == completion.request().requestId()
                    && current == null;
        }
        SaveResult terminalResult = new SaveResult(
                attempt.outcome(), attempt.conflict(), attempt.failure());
        return new AsyncSaveResult(terminalResult, completion.request().requestId(), latest, this);
    }

    private static List<String> mergeDeletions(List<String> existing, List<String> added,
            Set<String> canceled) {
        Set<String> canceledKeys = new HashSet<>();
        for (String canceledFile : canceled) {
            String key = ProfileFileNames.comparisonKey(canceledFile);
            if (key != null) {
                canceledKeys.add(key);
            }
        }

        LinkedHashMap<String, String> merged = new LinkedHashMap<>();
        for (String pending : existing) {
            String key = deletionKey(pending);
            if (!canceledKeys.contains(key)) {
                merged.putIfAbsent(key, pending);
            }
        }
        for (String addedFile : added) {
            merged.putIfAbsent(deletionKey(addedFile), addedFile);
        }
        return List.copyOf(merged.values());
    }

    private static String deletionKey(String fileName) {
        String key = ProfileFileNames.comparisonKey(fileName);

        return key != null ? key : " raw:" + fileName;
    }

    private long publishLocked(Config nextConfig) {
        long nextRevision = revision.incrementAndGet();
        Config publishedConfig = nextConfig.copy();
        localBaselines.attach(publishedConfig);
        snapshot = new Snapshot(publishedConfig, HudRenderState.from(publishedConfig), nextRevision);
        refreshEffectiveSnapshotLocked();
        return nextRevision;
    }

    private void refreshEffectiveSnapshotLocked() {
        Snapshot persisted = snapshot;
        if (persisted == null) {
            return;
        }
        PendingSave pending = pendingSave;
        Config effectiveConfig = pending == null ? persisted.publishedConfig() : pending.config();
        EffectiveSnapshot current = effectiveSnapshot;
        boolean changed = current == null
                || !current.publishedConfig().hasSameState(effectiveConfig);
        long viewRevision = changed
                ? configViewRevision.incrementAndGet()
                : current.viewRevision();
        Config copied = effectiveConfig.copy();
        localBaselines.attach(copied);
        localBaselines.publishedProfiles(persisted.publishedConfig(), copied);
        effectiveSnapshot = new EffectiveSnapshot(
                copied, HudRenderState.from(copied), viewRevision);
        if (current == null || !current.publishedConfig().hasSamePersistedSnapshot(copied)) {
            stateRevision.incrementAndGet();
        }
    }

    private boolean publishIfUnchanged(Config nextConfig, long observedRevision) {
        synchronized (publishLock) {
            if (activeSaves.get() == 0 && pendingSave == null && revision.get() == observedRevision) {
                Snapshot currentSnapshot = snapshot;
                Config currentConfig = currentSnapshot == null ? null : currentSnapshot.publishedConfig();
                if (currentConfig != null && currentConfig.hasSamePersistedSnapshot(nextConfig)) {
                    return true;
                }
                long nextRevision = revision.incrementAndGet();
                Config publishedConfig = nextConfig.copy();
                localBaselines.attach(publishedConfig);
                snapshot = new Snapshot(publishedConfig, HudRenderState.from(publishedConfig), nextRevision);
                refreshEffectiveSnapshotLocked();
                return true;
            }
        }
        return false;
    }

    private final class PendingSave {
        private final Config config;
        private final List<String> deletedProfileFiles;
        private final long generation;
        private final SavePhase phase;
        private final ConfigConflictException conflict;

        private PendingSave(Config config, List<String> deletedProfileFiles, long generation,
                SavePhase phase, ConfigConflictException conflict) {
            this(config.copy(), List.copyOf(deletedProfileFiles), generation, phase, conflict, true);
        }

        private PendingSave(Config config, List<String> deletedProfileFiles, long generation,
                SavePhase phase, ConfigConflictException conflict, boolean owned) {
            this.config = owned ? config : config.copy();
            this.deletedProfileFiles = owned ? deletedProfileFiles : List.copyOf(deletedProfileFiles);
            this.generation = generation;
            this.phase = phase;
            this.conflict = conflict;
        }

        private Config config() {
            return config.copy();
        }

        private List<String> deletedProfileFiles() {
            return deletedProfileFiles;
        }

        private long generation() {
            return generation;
        }

        private SavePhase phase() {
            return phase;
        }

        private ConfigConflictException conflict() {
            return conflict;
        }

        private PendingSave withPhase(SavePhase updatedPhase) {
            return new PendingSave(config, deletedProfileFiles, generation, updatedPhase, conflict, true);
        }

        private PendingSave withAttempt(SavePhase attemptedPhase, ConfigConflictException attemptedConflict) {
            return new PendingSave(config, deletedProfileFiles, generation,
                    attemptedPhase, attemptedConflict, true);
        }

        private PendingSave afterAncestorCommit(List<String> committedDeletes) {
            List<String> remainingDeletes = deletedProfileFiles.stream()
                    .filter(pending -> committedDeletes.stream()
                            .noneMatch(committed -> sameProfileFile(pending, committed)))
                    .toList();
            Config rebased = localBaselines.reconcile(config);
            SavePhase rebasedPhase = phase == SavePhase.SAVING || phase == SavePhase.RETRYING
                    ? phase : SavePhase.IDLE;
            return new PendingSave(rebased, remainingDeletes, generation, rebasedPhase, null, true);
        }
    }
}
