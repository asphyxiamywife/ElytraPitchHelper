package com.asphyxiamywife.elytrapitchhelper.client;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.ConfigConflictException;
import com.asphyxiamywife.elytrapitchhelper.config.ConfigSaveException;
import com.asphyxiamywife.elytrapitchhelper.hud.HudRenderState;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;

public final class ClientConfigStore {
    private static final ReentrantReadWriteLock STORE_LOCK = new ReentrantReadWriteLock();
    private static volatile ConfigStore store = new ConfigStore();

    private ClientConfigStore() {
    }

    public static ConfigStore store() {
        return store;
    }

    public static void setStore(ConfigStore replacement) {
        ConfigStore current = store;
        if (current.isSaveWorkerThread() || STORE_LOCK.getReadHoldCount() > 0) {
            if (replacement == current) {
                return;
            }
            throw new IllegalStateException("Cannot replace the config store from an active store operation or save callback");
        }
        STORE_LOCK.writeLock().lock();
        try {
            ConfigStore next = replacement == null ? new ConfigStore() : replacement;
            ConfigStore previous = store;
            if (previous != next) {
                previous.close();
                store = next;
            }
        } finally {
            STORE_LOCK.writeLock().unlock();
        }
    }

    public static void initialize() {
        runWithStore(ConfigStore::initialize);
    }

    public static boolean shutdownPersistenceCoordinator() {
        return callWithStore(ConfigStore::shutdownPersistenceCoordinator);
    }

    public static boolean reloadFromDisk() {
        return callWithStore(ConfigStore::reloadFromDisk);
    }

    public static boolean reloadFromDiskIfIdle() {
        return callWithStore(ConfigStore::reloadFromDiskIfIdle);
    }

    static void saveNow(Config nextConfig, List<String> deletedProfileFiles) {
        runWithStore(current -> current.saveNow(nextConfig, deletedProfileFiles));
    }

    public static SaveResult trySet(Config nextConfig, List<String> deletedProfileFiles) {
        return callWithStore(current -> current.trySet(nextConfig, deletedProfileFiles));
    }

    public static CompletableFuture<AsyncSaveResult> saveAsync(Config nextConfig) {
        return callWithStore(current -> current.saveAsync(nextConfig));
    }

    public static CompletableFuture<AsyncSaveResult> saveAsync(
            Config nextConfig, List<String> deletedProfileFiles) {
        return callWithStore(current -> current.saveAsync(nextConfig, deletedProfileFiles));
    }

    public static CompletableFuture<AsyncSaveResult> flushPending() {
        return callWithStore(ConfigStore::flushPending);
    }

    public static void update(Config nextConfig) {
        runWithStore(current -> current.update(nextConfig));
    }

    public static void update(Config nextConfig, List<String> deletedProfileFiles) {
        runWithStore(current -> current.update(nextConfig, deletedProfileFiles));
    }

    public static void update(Config nextConfig, List<String> deletedProfileFiles,
            Set<String> canceledProfileDeletes) {
        runWithStore(current -> current.update(nextConfig, deletedProfileFiles, canceledProfileDeletes));
    }

    public static boolean hasUnsavedChanges() {
        return callWithStore(ConfigStore::hasUnsavedChanges);
    }

    public static List<String> pendingProfileDeletes() {
        return callWithStore(ConfigStore::pendingProfileDeletes);
    }

    public static Config pendingSaveConfig() {
        return callWithStore(ConfigStore::pendingSaveConfig);
    }

    public static SaveStatus saveStatus() {
        return callWithStore(ConfigStore::saveStatus);
    }

    public static SaveResult discardPendingSave(long expectedGeneration) {
        return callWithStore(current -> current.discardPendingSave(expectedGeneration));
    }

    public static PendingConflict pendingSaveConflict() {
        return callWithStore(ConfigStore::pendingSaveConflict);
    }

    public static Config get() {
        return callWithStore(ConfigStore::get);
    }

    public static Snapshot snapshot() {
        return callWithStore(ConfigStore::snapshot);
    }

    public static EffectiveSnapshot effectiveSnapshot() {
        return callWithStore(ConfigStore::effectiveSnapshot);
    }

    public static long revision() {
        return callWithStore(ConfigStore::revision);
    }

    public static long stateRevision() {
        return callWithStore(ConfigStore::stateRevision);
    }

    public static long configViewRevision() {
        return callWithStore(ConfigStore::configViewRevision);
    }

    private static void runWithStore(Consumer<ConfigStore> action) {
        callWithStore(current -> {
            action.accept(current);
            return null;
        });
    }

    private static <T> T callWithStore(Function<ConfigStore, T> action) {
        ConfigStore current = store;
        if (current.isSaveWorkerThread()) {
            return action.apply(current);
        }
        STORE_LOCK.readLock().lock();
        try {
            return action.apply(store);
        } finally {
            STORE_LOCK.readLock().unlock();
        }
    }

    public record Snapshot(Config config, HudRenderState hudRenderState, long revision) {
        @Override
        public Config config() {
            return config.copy();
        }

        Config publishedConfig() {
            return config;
        }
    }

    public record EffectiveSnapshot(Config config, HudRenderState hudRenderState, long viewRevision) {
        @Override
        public Config config() {
            return config.copy();
        }

        Config publishedConfig() {
            return config;
        }
    }

    public enum SavePhase {
        IDLE,
        SAVING,
        RETRYING,
        CONFLICTED,
        FAILED
    }

    public enum SaveOutcome {
        COMMITTED,
        CONFLICTED,
        FAILED,
        SUPERSEDED,
        CANCELLED
    }

    public record SaveStatus(SavePhase phase, long pendingGeneration) {
    }

    public record SaveResult(SaveOutcome outcome,
            ConfigConflictException conflict, ConfigSaveException failure) {
    }

    public record AsyncSaveResult(SaveResult result, long pendingGeneration, boolean latestEditsCommitted,
            ConfigStore store) {
        public AsyncSaveResult(SaveResult result, long pendingGeneration, boolean latestEditsCommitted) {
            this(result, pendingGeneration, latestEditsCommitted, null);
        }

        public boolean isCurrentAtDelivery() {
            ConfigStore current = ClientConfigStore.store();
            if (store == null || current != store) {
                return false;
            }
            SaveObservation observation = current.saveObservation();
            if (ClientConfigStore.store() != current || observation.latestGeneration() != pendingGeneration) {
                return false;
            }
            return switch (result.outcome()) {
                case COMMITTED -> observation.status().pendingGeneration() < 0;
                case FAILED, CONFLICTED -> observation.status().pendingGeneration() == pendingGeneration;
                case CANCELLED, SUPERSEDED -> false;
            };
        }
    }

    public record SaveObservation(long latestGeneration, SaveStatus status) {
    }

    public record PendingConflict(long pendingGeneration, ConfigConflictException conflict) {
    }
}
