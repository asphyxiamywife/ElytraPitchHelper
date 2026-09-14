package com.asphyxiamywife.elytrapitchhelper.client;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.ConfigFileSystem;
import com.asphyxiamywife.elytrapitchhelper.config.NioConfigFileSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.asphyxiamywife.elytrapitchhelper.client.WatchEventFixtures.modified;
import static com.asphyxiamywife.elytrapitchhelper.client.WatchEventFixtures.overflow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfigWatcherTest {
    @TempDir
    Path tempDir;

    @Test
    void detectsMainConfigAndProfileJsonChangesOnly() {
        Path configDirectory = Path.of("config", "elytra-pitch-helper");
        Path profileDirectory = configDirectory.resolve("profiles");
        Path internalDirectory = configDirectory.resolve(".internal");
        Path configPath = configDirectory.resolve("elytra-pitch-helper.json");
        Path profileMetadataPath = internalDirectory.resolve("profile-metadata.json");

        assertTrue(ConfigWatcher.changedConfigFile(modified("elytra-pitch-helper.json"), configDirectory, configPath,
                profileMetadataPath, configDirectory, profileDirectory, internalDirectory));
        assertTrue(ConfigWatcher.changedConfigFile(modified("speed.json"), profileDirectory, configPath,
                profileMetadataPath, configDirectory, profileDirectory, internalDirectory));
        assertTrue(ConfigWatcher.changedConfigFile(modified("speed.JSON"), profileDirectory, configPath,
                profileMetadataPath, configDirectory, profileDirectory, internalDirectory));
        assertTrue(ConfigWatcher.changedConfigFile(modified("profile-metadata.json"),
                internalDirectory, configPath, profileMetadataPath,
                configDirectory, profileDirectory, internalDirectory));
        assertFalse(ConfigWatcher.changedConfigFile(modified("unrelated.json"),
                internalDirectory, configPath, profileMetadataPath,
                configDirectory, profileDirectory, internalDirectory));
        assertFalse(ConfigWatcher.changedConfigFile(modified("speed.txt"), profileDirectory, configPath,
                profileMetadataPath, configDirectory, profileDirectory, internalDirectory));
        assertFalse(ConfigWatcher.changedConfigFile(modified("other.json"), configDirectory, configPath,
                profileMetadataPath, configDirectory, profileDirectory, internalDirectory));
        assertTrue(ConfigWatcher.changedConfigFile(overflow(), profileDirectory, configPath,
                profileMetadataPath, configDirectory, profileDirectory, internalDirectory));
    }

    @Test
    void debouncerReloadsAfterChangesBecomeQuiet() {
        ConfigWatchDebouncer debouncer = new ConfigWatchDebouncer(250L);

        debouncer.recordChange(1_000L);
        assertFalse(debouncer.shouldReload(1_249L));
        assertTrue(debouncer.shouldReload(1_250L));
        assertFalse(debouncer.shouldReload(1_500L));
    }

    @Test
    void debouncerExtendsWindowAfterFollowUpChange() {
        ConfigWatchDebouncer debouncer = new ConfigWatchDebouncer(250L);

        debouncer.recordChange(1_000L);
        debouncer.recordChange(1_100L);

        assertFalse(debouncer.shouldReload(1_250L));
        assertTrue(debouncer.shouldReload(1_350L));
    }

    @Test
    void debouncerReportsRemainingWaitAndClearsItAfterReload() {
        ConfigWatchDebouncer debouncer = new ConfigWatchDebouncer(250L);

        assertEquals(-1L, debouncer.waitMillis(1_000L));
        debouncer.recordChange(1_000L);
        assertEquals(250L, debouncer.waitMillis(1_000L));
        assertEquals(1L, debouncer.waitMillis(1_249L));
        assertEquals(0L, debouncer.waitMillis(1_250L));
        assertTrue(debouncer.shouldReload(1_250L));
        assertEquals(-1L, debouncer.waitMillis(1_250L));
    }

    @Test
    void negativeDebounceActsAsImmediateReload() {
        ConfigWatchDebouncer debouncer = new ConfigWatchDebouncer(-100L);

        debouncer.recordChange(42L);

        assertEquals(0L, debouncer.waitMillis(42L));
        assertTrue(debouncer.shouldReload(42L));
    }

    @Test
    void deferredReloadsBackOffInsteadOfRetryingEveryDebounceInterval() {
        ConfigWatchDebouncer debouncer = new ConfigWatchDebouncer(250L);
        long now = 1_000L;

        debouncer.recordChange(now);
        assertTrue(debouncer.shouldReload(now + 250L));

        debouncer.deferReload(now);
        assertEquals(500L, debouncer.waitMillis(now));
        debouncer.deferReload(now);
        assertEquals(1_000L, debouncer.waitMillis(now));
        debouncer.deferReload(now);
        assertEquals(2_000L, debouncer.waitMillis(now));
    }

    @Test
    void deferralBackoffStopsAtItsCeilingAndNeverStopsRetrying() {
        ConfigWatchDebouncer debouncer = new ConfigWatchDebouncer(250L);

        for (int i = 0; i < 1_000; i++) {
            debouncer.deferReload(0L);
        }

        assertEquals(ConfigWatchDebouncer.MAX_DEFERRAL_MILLIS, debouncer.waitMillis(0L));
        assertTrue(debouncer.shouldReload(ConfigWatchDebouncer.MAX_DEFERRAL_MILLIS));
    }

    @Test
    void aReloadThatRunsClearsTheDeferralBackoff() {
        ConfigWatchDebouncer debouncer = new ConfigWatchDebouncer(250L);

        debouncer.deferReload(0L);
        debouncer.deferReload(0L);
        debouncer.clearBackoff();
        debouncer.recordChange(0L);

        assertEquals(250L, debouncer.waitMillis(0L));
    }

    @Test
    void restoringWatchedDirectoriesRecreatesDeletedConfigTree() throws IOException {
        Path configDirectory = tempDir.resolve("elytra-pitch-helper");
        Path profileDirectory = configDirectory.resolve("profiles");
        Path internalDirectory = configDirectory.resolve(".internal");
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            ConfigWatcher.restoreWatchedDirectories(
                    watchService, configDirectory, profileDirectory, internalDirectory);
            Files.delete(internalDirectory);
            Files.delete(profileDirectory);
            Files.delete(configDirectory);

            ConfigWatcher.restoreWatchedDirectories(
                    watchService, configDirectory, profileDirectory, internalDirectory);
        }

        assertTrue(Files.isDirectory(configDirectory));
        assertTrue(Files.isDirectory(profileDirectory));
        assertTrue(Files.isDirectory(internalDirectory));
    }

    @Test
    void watcherThreadIsADaemonSoShutdownIsNotBlocked() {
        Thread watcher = ConfigWatcher.newWatcherThread();

        assertTrue(watcher.isDaemon(), "config watcher must be a daemon thread");
        assertEquals(ConfigWatcher.WATCHER_THREAD_NAME, watcher.getName());
        assertEquals(Thread.State.NEW, watcher.getState(), "thread factory must not start the watcher");
    }

    @Test
    void boundedJoinDoesNotHangOnAThreadThatIgnoresInterrupts() throws Exception {
        AtomicBoolean running = new AtomicBoolean(true);
        CountDownLatch started = new CountDownLatch(1);
        Thread stubborn = new Thread(() -> {
            started.countDown();
            while (running.get()) {
                try {
                    Thread.sleep(1_000L);
                } catch (InterruptedException ignored) {
                }
            }
        });
        stubborn.setDaemon(true);
        stubborn.start();
        assertTrue(started.await(1L, TimeUnit.SECONDS));

        try {
            assertFalse(ConfigWatcher.joinUntil(stubborn, 25L));
        } finally {
            running.set(false);
            stubborn.interrupt();
            stubborn.join(1_000L);
        }
        assertFalse(stubborn.isAlive());
    }

    @Test
    void supervisorRestartsAfterASessionFailure() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch restarted = new CountDownLatch(2);
        Thread supervisor = new Thread(() -> ConfigWatcher.supervise(() -> {
            restarted.countDown();
            if (attempts.incrementAndGet() == 1) {
                throw new IOException("injected watcher failure");
            }
            Thread.currentThread().interrupt();
        }));

        supervisor.start();
        assertTrue(restarted.await(30L, TimeUnit.SECONDS), "supervisor did not restart the session");
        supervisor.join(30_000L);

        assertFalse(supervisor.isAlive());
        assertEquals(2, attempts.get());
    }

    @Test
    void supervisorRestartsAfterALinkageErrorRatherThanDying() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch restarted = new CountDownLatch(2);
        Thread supervisor = new Thread(() -> ConfigWatcher.supervise(() -> {
            restarted.countDown();
            if (attempts.incrementAndGet() == 1) {
                throw new NoSuchMethodError("injected mapping mismatch");
            }
            Thread.currentThread().interrupt();
        }));

        supervisor.start();
        assertTrue(restarted.await(30L, TimeUnit.SECONDS), "supervisor did not restart the session");
        supervisor.join(30_000L);

        assertFalse(supervisor.isAlive());
        assertEquals(2, attempts.get());
    }

    @Test
    void quietOldRootIsReleasedWhenTheStoreIsReplaced() throws Exception {
        ConfigFileSystem previousFileSystem = ClientConfigStore.store().fileSystem();
        ConfigStore observedStore = new ConfigStore(
                NioConfigFileSystem.rootedAt(tempDir.resolve("old-root")));
        ConfigStore replacementStore = new ConfigStore(
                NioConfigFileSystem.rootedAt(tempDir.resolve("new-root")));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ClientConfigStore.setStore(observedStore);
        observedStore.initialize();

        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            Path configPath = observedStore.configPath();
            Path configDirectory = observedStore.configDirectory();
            Path profileDirectory = observedStore.profileDirectory();
            Path profileMetadataPath = Config.getProfileMetadataPath(observedStore.fileSystem());
            Path internalDirectory = profileMetadataPath.getParent();
            ConfigWatcher.restoreWatchedDirectories(
                    watchService, configDirectory, profileDirectory, internalDirectory);
            CountDownLatch processing = new CountDownLatch(1);
            Future<?> watching = executor.submit(() -> {
                try {
                    processing.countDown();
                    ConfigWatcher.processEvents(watchService, observedStore,
                            configPath, profileMetadataPath, configDirectory, profileDirectory, internalDirectory,
                            new ConfigWatchDebouncer(250L));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(interrupted);
                } catch (IOException failure) {
                    throw new RuntimeException(failure);
                }
            });

            assertTrue(processing.await(1L, TimeUnit.SECONDS));
            ClientConfigStore.setStore(replacementStore);

            watching.get(2L, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            ClientConfigStore.setStore(new ConfigStore(previousFileSystem));
        }
    }

}
