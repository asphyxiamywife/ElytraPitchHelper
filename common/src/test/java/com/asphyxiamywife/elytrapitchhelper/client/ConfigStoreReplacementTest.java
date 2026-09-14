package com.asphyxiamywife.elytrapitchhelper.client;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.testing.FaultingConfigFileSystem;
import com.asphyxiamywife.elytrapitchhelper.testing.IsolatedConfigRoot;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

final class ConfigStoreReplacementTest {
    @IsolatedConfigRoot
    Path root;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void drainingReplacementDoesNotBlockInlineCallbackReentry(boolean nestedReplacement) throws Exception {
        ClientConfigStore.initialize();
        ConfigStore original = ClientConfigStore.store();
        ConfigStore replacement = new ConfigStore(new FaultingConfigFileSystem(root));
        CountDownLatch releasePersist = new CountDownLatch(1);
        CountDownLatch callbackEntered = new CountDownLatch(1);
        CountDownLatch releaseCallback = new CountDownLatch(1);
        var replacementDone = new java.util.concurrent.CompletableFuture<Void>();
        Thread replacing = new Thread(() -> {
            try {
                ClientConfigStore.setStore(replacement);
                replacementDone.complete(null);
            } catch (Throwable failure) {
                replacementDone.completeExceptionally(failure);
            }
        }, "test-config-replacement");
        FaultingConfigFileSystem fileSystem = FaultingConfigFileSystem.current();
        fileSystem.beforeAtomicMove(() -> await(releasePersist));
        try {
            Config edited = original.get();
            edited.enabled = !edited.enabled;
            var saved = ClientConfigStore.saveAsync(edited);
            var callback = saved.thenRun(() -> {
                callbackEntered.countDown();
                await(releaseCallback);
                assertSame(original, ClientConfigStore.store());
                if (nestedReplacement) {
                    assertThrows(IllegalStateException.class, () -> ClientConfigStore.setStore(replacement));
                } else {
                    Config current = ClientConfigStore.get();
                    assertEquals(edited.enabled, current.enabled);
                    assertEquals(ClientConfigStore.SavePhase.IDLE, ClientConfigStore.saveStatus().phase());
                    current.enabled = !current.enabled;
                    var rejected = ClientConfigStore.saveAsync(current).join();
                    assertSame(original, rejected.store());
                    assertEquals(ClientConfigStore.SaveOutcome.CANCELLED, rejected.result().outcome());
                    assertEquals(ClientConfigStore.SaveOutcome.CANCELLED,
                            ClientConfigStore.discardPendingSave(rejected.pendingGeneration()).outcome());
                }
            });
            releasePersist.countDown();
            assertTrue(callbackEntered.await(5, TimeUnit.SECONDS));
            replacing.start();
            awaitReplacementDrain(replacing);
            releaseCallback.countDown();
            callback.get(1, TimeUnit.SECONDS);
            replacementDone.get(5, TimeUnit.SECONDS);
            assertSame(replacement, ClientConfigStore.store());
            replacement.initialize();
            assertEquals(edited.enabled, replacement.get().enabled);
            assertFalse(replacement.hasUnsavedChanges());
            assertEquals(edited.enabled, Config.reloadFromDisk(replacement.fileSystem()).enabled);
        } finally {
            releasePersist.countDown();
            releaseCallback.countDown();
            replacing.join(5_000);
            assertFalse(replacing.isAlive());
            fileSystem.beforeAtomicMove(null);
            original.close();
            replacement.close();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private static void awaitReplacementDrain(Thread replacing) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline && replacing.isAlive()) {
            for (StackTraceElement frame : replacing.getStackTrace()) {
                if (frame.getClassName().equals(java.util.concurrent.ThreadPoolExecutor.class.getName())
                        && frame.getMethodName().equals("awaitTermination")) {
                    return;
                }
            }
            Thread.sleep(1);
        }
        fail("Replacement did not reach the worker termination barrier");
    }

    @Test
    void replacementInsideAFacadeReadRefusesLockUpgrade() throws Exception {
        ClientConfigStore.initialize();
        ConfigStore original = ClientConfigStore.store();
        try (ConfigStore replacement = new ConfigStore(new FaultingConfigFileSystem(root))) {
            FaultingConfigFileSystem fileSystem = FaultingConfigFileSystem.current();
            AtomicBoolean observed = new AtomicBoolean();
            fileSystem.observeReads(path -> {
                if (observed.compareAndSet(false, true)) {
                    ClientConfigStore.setStore(original);
                    assertThrows(IllegalStateException.class, () -> ClientConfigStore.setStore(replacement));
                }
            });
            try {
                assertTrue(ClientConfigStore.reloadFromDiskIfIdle());
                assertTrue(observed.get());
                assertSame(original, ClientConfigStore.store());
            } finally {
                fileSystem.observeReads(null);
            }
        }
    }

    @Test
    void interruptedReplacementRetainsOwnershipAndRetrySettlesActiveAndQueuedSaves() throws Exception {
        ClientConfigStore.initialize();
        ConfigStore original = ClientConfigStore.store();
        ConfigStore replacement = new ConfigStore(new FaultingConfigFileSystem(root));
        FaultingConfigFileSystem fileSystem = FaultingConfigFileSystem.current();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean firstMove = new AtomicBoolean(true);
        fileSystem.beforeAtomicMove(() -> {
            if (firstMove.compareAndSet(true, false)) {
                started.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to release the active save");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
            }
        });
        try {
            Config first = original.get();
            first.enabled = !first.enabled;
            var active = ClientConfigStore.saveAsync(first);
            assertTrue(started.await(5, TimeUnit.SECONDS));
            Config second = original.get();
            second.profileSortMode = Config.PROFILE_SORT_NAME;
            var queued = ClientConfigStore.saveAsync(second);
            AtomicInteger activeCompletions = new AtomicInteger();
            AtomicInteger queuedCompletions = new AtomicInteger();
            var activeDelivered = active.thenRun(activeCompletions::incrementAndGet);
            var queuedDelivered = queued.thenRun(queuedCompletions::incrementAndGet);

            Thread.currentThread().interrupt();
            try {
                assertThrows(IllegalStateException.class, () -> ClientConfigStore.setStore(replacement));
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
            assertSame(original, ClientConfigStore.store());
            assertFalse(active.isDone());
            assertFalse(queued.isDone());

            release.countDown();
            ClientConfigStore.setStore(replacement);
            assertTrue(active.isDone());
            assertTrue(queued.isDone());
            assertEquals(ClientConfigStore.SaveOutcome.COMMITTED, active.join().result().outcome());
            assertEquals(ClientConfigStore.SaveOutcome.CANCELLED, queued.join().result().outcome());
            activeDelivered.get(5, TimeUnit.SECONDS);
            queuedDelivered.get(5, TimeUnit.SECONDS);
            assertEquals(1, activeCompletions.get());
            assertEquals(1, queuedCompletions.get());
            assertSame(original, active.join().store());
            assertSame(original, queued.join().store());
            assertTrue(active.join().pendingGeneration() < queued.join().pendingGeneration());
            assertEquals(ClientConfigStore.SaveOutcome.CANCELLED,
                    original.discardPendingSave(queued.join().pendingGeneration()).outcome());
            assertFalse(original.hasUnsavedChanges());

            replacement.initialize();
            assertSame(replacement, ClientConfigStore.store());
            assertEquals(first.enabled, replacement.get().enabled);
            assertEquals(first.profileSortMode, replacement.get().profileSortMode);
            String committedBytes = Files.readString(replacement.configPath());
            long replacementGeneration = replacement.saveObservation().latestGeneration();
            var late = original.saveAsync(second).get(5, TimeUnit.SECONDS);
            assertEquals(ClientConfigStore.SaveOutcome.CANCELLED, late.result().outcome());
            assertSame(original, late.store());
            assertEquals(ClientConfigStore.SaveOutcome.CANCELLED,
                    original.discardPendingSave(late.pendingGeneration()).outcome());
            assertEquals(replacementGeneration, replacement.saveObservation().latestGeneration());
            assertFalse(replacement.hasUnsavedChanges());
            assertEquals(committedBytes, Files.readString(replacement.configPath()));
            original.close();
        } finally {
            release.countDown();
            fileSystem.beforeAtomicMove(null);
            original.close();
            replacement.close();
        }
    }
}
