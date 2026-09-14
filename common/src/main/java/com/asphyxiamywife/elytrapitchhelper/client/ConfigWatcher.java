package com.asphyxiamywife.elytrapitchhelper.client;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.ModConstants;
import com.asphyxiamywife.elytrapitchhelper.util.MonotonicClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class ConfigWatcher {
    static final String WATCHER_THREAD_NAME = "ElytraPitchHelper Config Watcher";
    private static final long RELOAD_DEBOUNCE_MILLIS = 250L;
    private static final long STORE_CHECK_INTERVAL_MILLIS = 500L;
    private static final long RESTART_DELAY_MILLIS = 250L;
    private static final long MAX_RESTART_DELAY_MILLIS = 30_000L;
    static final long STOP_WAIT_MILLIS = 2_000L;
    private static final Logger LOGGER = LoggerFactory.getLogger(ModConstants.MOD_ID);
    private static Thread watcherThread;
    private static WatchService activeWatchService;

    private ConfigWatcher() {
    }

    public static synchronized void start() {
        if (watcherThread != null && watcherThread.isAlive()) {
            return;
        }
        watcherThread = newWatcherThread();
        watcherThread.start();
    }

    public static void stop() {
        Thread stopping;
        WatchService watchService;
        synchronized (ConfigWatcher.class) {
            stopping = watcherThread;
            if (stopping == null) {
                return;
            }
            stopping.interrupt();
            watchService = activeWatchService;
        }
        closeQuietly(watchService);
        if (stopping != Thread.currentThread()) {
            if (!joinUntil(stopping, STOP_WAIT_MILLIS)) {
                LOGGER.warn("Config watcher did not stop within {} ms; continuing shutdown",
                        STOP_WAIT_MILLIS);
            }
        }
        synchronized (ConfigWatcher.class) {
            if (watcherThread == stopping && !stopping.isAlive()) {
                watcherThread = null;
            }
        }
    }

    static Thread newWatcherThread() {
        Thread thread = new Thread(ConfigWatcher::watchConfigFiles, WATCHER_THREAD_NAME);
        thread.setDaemon(true);
        return thread;
    }

    private static void watchConfigFiles() {
        try {
            supervise(ConfigWatcher::watchCurrentStore);
        } finally {
            synchronized (ConfigWatcher.class) {
                if (watcherThread == Thread.currentThread()) {
                    watcherThread = null;
                }
            }
        }
    }

    private static void watchCurrentStore() throws IOException, InterruptedException {
        ConfigStore observedStore = ClientConfigStore.store();
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            synchronized (ConfigWatcher.class) {
                if (watcherThread == Thread.currentThread()) {
                    activeWatchService = watchService;
                }
            }
            Path configPath = observedStore.configPath();
            Path configDirectory = configPath.getParent();
            Path profileDirectory = observedStore.profileDirectory();
            Path profileMetadataPath = Config.getProfileMetadataPath(observedStore.fileSystem());
            Path internalDirectory = profileMetadataPath.getParent();
            if (configDirectory == null) {
                throw new IOException("Config path has no parent directory: " + configPath);
            }

            restoreWatchedDirectories(watchService, configDirectory, profileDirectory, internalDirectory);
            processEvents(watchService, observedStore, configPath, profileMetadataPath,
                    configDirectory, profileDirectory, internalDirectory,
                    new ConfigWatchDebouncer(RELOAD_DEBOUNCE_MILLIS));
        } finally {
            synchronized (ConfigWatcher.class) {
                if (watcherThread == Thread.currentThread()) {
                    activeWatchService = null;
                }
            }
        }
    }

    static void supervise(WatchSession session) {
        long restartDelayMillis = RESTART_DELAY_MILLIS;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                session.watch();
                restartDelayMillis = RESTART_DELAY_MILLIS;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (IOException | RuntimeException | LinkageError failure) {
                LOGGER.warn("Config watcher failed; restarting", failure);
                try {
                    Thread.sleep(restartDelayMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
                restartDelayMillis = Math.min(MAX_RESTART_DELAY_MILLIS, restartDelayMillis * 2L);
            }
        }
    }

    private static void registerDirectories(WatchService watchService, Path configDirectory,
            Path profileDirectory, Path internalDirectory)
            throws IOException {
        configDirectory.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
        profileDirectory.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
        internalDirectory.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
    }

    static void restoreWatchedDirectories(WatchService watchService, Path configDirectory,
            Path profileDirectory, Path internalDirectory)
            throws IOException {
        Files.createDirectories(configDirectory);
        Files.createDirectories(profileDirectory);
        Files.createDirectories(internalDirectory);
        registerDirectories(watchService, configDirectory, profileDirectory, internalDirectory);
    }

    static void processEvents(WatchService watchService, ConfigStore observedStore,
            Path configPath, Path profileMetadataPath, Path configDirectory,
            Path profileDirectory, Path internalDirectory,
            ConfigWatchDebouncer debouncer) throws InterruptedException, IOException {
        while (ClientConfigStore.store() == observedStore) {
            WatchKey key = takeNextKey(watchService, debouncer);
            if (key == null) {
                reloadIfReady(debouncer, observedStore);
                continue;
            }

            Path watchedDirectory = (Path) key.watchable();
            boolean changed = false;
            for (WatchEvent<?> event : key.pollEvents()) {
                if (changedConfigFile(event, watchedDirectory, configPath, profileMetadataPath,
                        configDirectory, profileDirectory, internalDirectory)) {
                    changed = true;
                }
            }
            if (changed) {
                debouncer.recordChange(monotonicMillis());
            }
            if (!key.reset()) {
                if (ClientConfigStore.store() != observedStore) {
                    return;
                }
                restoreWatchedDirectories(watchService, configDirectory, profileDirectory, internalDirectory);
                debouncer.recordChange(monotonicMillis());
            }
            reloadIfReady(debouncer, observedStore);
        }
    }

    static long monotonicMillis() {
        return MonotonicClock.millis();
    }

    private static WatchKey takeNextKey(WatchService watchService, ConfigWatchDebouncer debouncer)
            throws InterruptedException {
        long waitMillis = debouncer.waitMillis(monotonicMillis());
        waitMillis = waitMillis < 0L
                ? STORE_CHECK_INTERVAL_MILLIS
                : Math.min(waitMillis, STORE_CHECK_INTERVAL_MILLIS);
        return watchService.poll(waitMillis, TimeUnit.MILLISECONDS);
    }

    private static void reloadIfReady(ConfigWatchDebouncer debouncer, ConfigStore observedStore) {
        reloadIfReady(debouncer, observedStore, ConfigWatcher::monotonicMillis);
    }

    static void reloadIfReady(ConfigWatchDebouncer debouncer, ConfigStore observedStore,
            java.util.function.LongSupplier clock) {
        if (ClientConfigStore.store() != observedStore || !debouncer.shouldReload(clock.getAsLong())) {
            return;
        }
        try {
            if (observedStore.reloadFromDiskIfIdle()) {
                debouncer.clearBackoff();
            } else {
                debouncer.deferReload(clock.getAsLong());
            }
        } catch (RuntimeException failure) {
            debouncer.deferReload(clock.getAsLong());
            LOGGER.warn("Could not reload Elytra Pitch Helper config after an external change", failure);
        }
    }

    static boolean changedConfigFile(WatchEvent<?> event, Path watchedDirectory,
            Path configPath, Path profileMetadataPath, Path configDirectory,
            Path profileDirectory, Path internalDirectory) {
        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
            return true;
        }
        if (!(event.context() instanceof Path changedPath)) {
            return false;
        }
        if (watchedDirectory.equals(configDirectory)) {
            return changedPath.getFileName().equals(configPath.getFileName());
        }
        if (watchedDirectory.equals(internalDirectory)) {
            return changedPath.getFileName().equals(profileMetadataPath.getFileName());
        }
        return watchedDirectory.equals(profileDirectory)
                && changedPath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json");
    }

    static boolean joinUntil(Thread thread, long timeoutMillis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0L, timeoutMillis));
        boolean interrupted = false;
        while (thread.isAlive()) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0L) {
                break;
            }
            try {
                TimeUnit.NANOSECONDS.timedJoin(thread, remainingNanos);
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        return !thread.isAlive();
    }

    private static void closeQuietly(WatchService watchService) {
        if (watchService == null) {
            return;
        }
        try {
            watchService.close();
        } catch (IOException failure) {
            LOGGER.debug("Could not close config watch service during shutdown", failure);
        }
    }

    @FunctionalInterface
    interface WatchSession {
        void watch() throws IOException, InterruptedException;
    }
}
