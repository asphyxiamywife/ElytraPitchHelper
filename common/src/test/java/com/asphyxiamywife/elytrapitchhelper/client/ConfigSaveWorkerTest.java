package com.asphyxiamywife.elytrapitchhelper.client;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfigSaveWorkerTest {
    @Test
    void aFailedCompletionApplierRetiresItsEntryInsteadOfWedgingTheQueue() throws Exception {
        AtomicBoolean rejecting = new AtomicBoolean(true);
        ConfigSaveWorker worker = new ConfigSaveWorker(
                request -> new ConfigSaveWorker.Attempt(
                        ClientConfigStore.SaveOutcome.COMMITTED, null, null, request.config(), false),
                completion -> {
                    if (rejecting.get()) {
                        throw new IllegalStateException("completion application failed");
                    }
                },
                (request, ancestor) -> request,
                0L, 0);

        try {
            ExecutionException failure = org.junit.jupiter.api.Assertions.assertThrows(
                    ExecutionException.class,
                    () -> worker.submit(request(1L)).get(5L, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof IllegalStateException);

            rejecting.set(false);
            ConfigSaveWorker.Completion committed =
                    worker.submit(request(2L)).get(5L, TimeUnit.SECONDS);
            assertEquals(ClientConfigStore.SaveOutcome.COMMITTED, committed.attempt().outcome());
        } finally {
            worker.shutdown(5_000L);
        }
    }

    @Test
    void terminalFutureCompletesOnlyAfterTheWorkerRetiresItsEntry() throws Exception {
        CountDownLatch persistenceStarted = new CountDownLatch(1);
        CountDownLatch allowPersistence = new CountDownLatch(1);
        ConfigSaveWorker worker = new ConfigSaveWorker(
                request -> {
                    persistenceStarted.countDown();
                    try {
                        assertTrue(allowPersistence.await(5L, TimeUnit.SECONDS));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(interrupted);
                    }
                    return new ConfigSaveWorker.Attempt(
                            ClientConfigStore.SaveOutcome.COMMITTED, null, null, request.config(), false);
                },
                completion -> {
                },
                (request, ancestor) -> request,
                0L, 0);

        try {
            var completion = worker.submit(request(1L));
            assertTrue(persistenceStarted.await(5L, TimeUnit.SECONDS));
            var idleAtCompletion = completion.thenApply(committed -> worker.isIdle());
            allowPersistence.countDown();

            assertTrue(idleAtCompletion.get(5L, TimeUnit.SECONDS),
                    "a completed save must be immediately safe to reload after");
        } finally {
            allowPersistence.countDown();
            worker.shutdown(5_000L);
        }
    }

    @Test
    void replacedQueuedRequestCompletesAsSupersededWithItsOwnIdentity() throws Exception {
        CountDownLatch firstPersistenceStarted = new CountDownLatch(1);
        CountDownLatch allowFirstPersistence = new CountDownLatch(1);
        List<Long> persisted = new CopyOnWriteArrayList<>();
        List<Long> applied = new CopyOnWriteArrayList<>();
        ConfigSaveWorker worker = new ConfigSaveWorker(
                request -> {
                    persisted.add(request.requestId());
                    if (request.requestId() == 1L) {
                        firstPersistenceStarted.countDown();
                        try {
                            assertTrue(allowFirstPersistence.await(5L, TimeUnit.SECONDS));
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(interrupted);
                        }
                    }
                    return new ConfigSaveWorker.Attempt(
                            ClientConfigStore.SaveOutcome.COMMITTED, null, null, request.config(), false);
                },
                completion -> applied.add(completion.request().requestId()),
                (request, ancestor) -> request,
                0L, 0);

        try {
            var first = worker.submit(request(1L));
            assertTrue(firstPersistenceStarted.await(5L, TimeUnit.SECONDS));
            var replaced = worker.submit(request(2L));
            var newest = worker.submit(request(3L));

            ConfigSaveWorker.Completion superseded = replaced.get(1L, TimeUnit.SECONDS);
            assertEquals(2L, superseded.request().requestId());
            assertEquals(ClientConfigStore.SaveOutcome.SUPERSEDED, superseded.attempt().outcome());
            assertTrue(superseded.terminal());
            assertFalse(newest.isDone());

            allowFirstPersistence.countDown();
            assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                    first.get(5L, TimeUnit.SECONDS).attempt().outcome());
            assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                    newest.get(5L, TimeUnit.SECONDS).attempt().outcome());
            assertEquals(List.of(1L, 3L), persisted);
            assertEquals(List.of(1L, 3L), applied);
        } finally {
            allowFirstPersistence.countDown();
            worker.shutdown(5_000L);
        }
    }

    @Test
    void zeroTimeoutShutdownDoesNotInterruptAnInFlightPersistenceAttempt() throws Exception {
        CountDownLatch persistenceStarted = new CountDownLatch(1);
        CountDownLatch allowPersistence = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        ConfigSaveWorker worker = new ConfigSaveWorker(
                request -> {
                    persistenceStarted.countDown();
                    try {
                        assertTrue(allowPersistence.await(5L, TimeUnit.SECONDS));
                    } catch (InterruptedException failure) {
                        interrupted.set(true);
                        Thread.currentThread().interrupt();
                        throw new AssertionError(failure);
                    }
                    return new ConfigSaveWorker.Attempt(
                            ClientConfigStore.SaveOutcome.COMMITTED, null, null, request.config(), false);
                },
                completion -> {
                },
                (request, ancestor) -> request,
                0L, 0);

        try {
            var completion = worker.submit(request(1L));
            assertTrue(persistenceStarted.await(5L, TimeUnit.SECONDS));

            assertFalse(worker.shutdown(0L));
            assertFalse(interrupted.get());
            assertFalse(completion.isDone());

            allowPersistence.countDown();
            assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                    completion.get(5L, TimeUnit.SECONDS).attempt().outcome());
            assertFalse(interrupted.get());
        } finally {
            allowPersistence.countDown();
            worker.shutdownAndAwaitTermination(5_000L);
        }
    }

    @Test
    void aTimedOutShutdownStillRunsTheSaveQueuedBehindTheActiveOne() throws Exception {
        CountDownLatch persistenceStarted = new CountDownLatch(1);
        CountDownLatch allowPersistence = new CountDownLatch(1);
        List<Long> persisted = new CopyOnWriteArrayList<>();
        ConfigSaveWorker worker = new ConfigSaveWorker(
                request -> {
                    persisted.add(request.requestId());
                    if (request.requestId() == 1L) {
                        persistenceStarted.countDown();
                        try {
                            assertTrue(allowPersistence.await(5L, TimeUnit.SECONDS));
                        } catch (InterruptedException failure) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(failure);
                        }
                    }
                    return new ConfigSaveWorker.Attempt(
                            ClientConfigStore.SaveOutcome.COMMITTED, null, null, request.config(), false);
                },
                completion -> {
                },
                (request, ancestor) -> request,
                0L, 0);

        try {
            var active = worker.submit(request(1L));
            assertTrue(persistenceStarted.await(5L, TimeUnit.SECONDS));
            var queued = worker.submit(request(2L));

            assertFalse(worker.shutdown(0L));

            allowPersistence.countDown();
            assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                    active.get(5L, TimeUnit.SECONDS).attempt().outcome());
            assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                    queued.get(5L, TimeUnit.SECONDS).attempt().outcome());
            assertEquals(List.of(1L, 2L), persisted);

            assertTrue(worker.shutdown(5_000L));
        } finally {
            allowPersistence.countDown();
            worker.shutdownAndAwaitTermination(5_000L);
        }
    }

    @Test
    void closeRefusesToReleaseAnActiveWriterOnTimeoutOrInterruption() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ConfigSaveWorker worker = new ConfigSaveWorker(request -> {
            started.countDown();
            try {
                assertTrue(release.await(5L, TimeUnit.SECONDS));
            } catch (InterruptedException failure) {
                throw new AssertionError("Closing must not interrupt persistence", failure);
            }
            return new ConfigSaveWorker.Attempt(ClientConfigStore.SaveOutcome.COMMITTED,
                    null, null, request.config(), false);
        }, completion -> {}, (request, ancestor) -> request, 0L, 0);
        try {
            var saved = worker.submit(request(1L));
            assertTrue(started.await(5L, TimeUnit.SECONDS));
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                    () -> worker.shutdownAndAwaitTermination(10L));
            assertFalse(saved.isDone());
            Thread.currentThread().interrupt();
            try {
                org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                        () -> worker.shutdownAndAwaitTermination(5_000L));
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
            release.countDown();
            worker.shutdownAndAwaitTermination(5_000L);
            assertTrue(saved.isDone(), "Successful close must leave no writer behind");
            assertEquals(ClientConfigStore.SaveOutcome.COMMITTED, saved.get().attempt().outcome());
        } finally {
            release.countDown();
            worker.shutdownAndAwaitTermination(5_000L);
        }
    }

    @Test
    void rebaseFailureSettlesBothWaitersAndAllowsLaterRequests() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ConfigSaveWorker worker = new ConfigSaveWorker(request -> {
            started.countDown();
            try {
                assertTrue(release.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException failure) {
                throw new AssertionError(failure);
            }
            return new ConfigSaveWorker.Attempt(ClientConfigStore.SaveOutcome.COMMITTED,
                    null, null, request.config(), false);
        }, completion -> {}, (request, ancestor) -> {
            throw new IllegalStateException("Injected rebase failure");
        }, 0L, 0);
        try {
            var active = worker.submit(request(1));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            var queued = worker.submit(request(2));
            release.countDown();
            assertEquals(ClientConfigStore.SaveOutcome.COMMITTED, active.get(5, TimeUnit.SECONDS).attempt().outcome());
            assertEquals(ClientConfigStore.SaveOutcome.FAILED, queued.get(5, TimeUnit.SECONDS).attempt().outcome());
            assertTrue(worker.isIdle());
            assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                    worker.submit(request(3)).get(5, TimeUnit.SECONDS).attempt().outcome());
        } finally {
            release.countDown();
            worker.shutdownAndAwaitTermination(5_000);
        }
    }

    @Test
    void delayedOlderSubmissionCannotOverwriteANewerGeneration() throws Exception {
        List<Long> persisted = new CopyOnWriteArrayList<>();
        ConfigSaveWorker worker = new ConfigSaveWorker(request -> {
            persisted.add(request.requestId());
            return new ConfigSaveWorker.Attempt(ClientConfigStore.SaveOutcome.COMMITTED,
                    null, null, request.config(), false);
        }, completion -> {}, (request, ancestor) -> request, 0L, 0);
        try {
            worker.submit(request(2)).get(5, TimeUnit.SECONDS);
            assertEquals(ClientConfigStore.SaveOutcome.SUPERSEDED,
                    worker.submit(request(1)).get(5, TimeUnit.SECONDS).attempt().outcome());
            assertEquals(List.of(2L), persisted);
        } finally {
            worker.shutdownAndAwaitTermination(5_000);
        }
    }

    @Test
    void closingExecutorSettlesActiveAndQueuedRequests() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ConfigSaveWorker worker = new ConfigSaveWorker(request -> {
            started.countDown();
            try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
            catch (InterruptedException failure) { throw new AssertionError(failure); }
            return new ConfigSaveWorker.Attempt(ClientConfigStore.SaveOutcome.COMMITTED,
                    null, null, request.config(), false);
        }, completion -> {}, (request, ancestor) -> request, 0L, 0);
        try {
            var active = worker.submit(request(1));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            var queued = worker.submit(request(2));
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                    () -> worker.shutdownAndAwaitTermination(0));
            release.countDown();
            assertEquals(ClientConfigStore.SaveOutcome.COMMITTED, active.get(5, TimeUnit.SECONDS).attempt().outcome());
            assertEquals(ClientConfigStore.SaveOutcome.CANCELLED, queued.get(5, TimeUnit.SECONDS).attempt().outcome());
            worker.shutdownAndAwaitTermination(5_000);
            assertTrue(worker.isIdle());
        } finally { release.countDown(); worker.shutdownAndAwaitTermination(5_000); }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void malformedAttemptFailsAndDrainsTheQueuedSave(boolean missingOutcome) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<ConfigSaveWorker.Completion> applied = new CopyOnWriteArrayList<>();
        ConfigSaveWorker worker = new ConfigSaveWorker(request -> {
            if (request.requestId() == 1) {
                started.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException failure) { throw new AssertionError(failure); }
                return missingOutcome ? new ConfigSaveWorker.Attempt(null, null, null, null, false) : null;
            }
            return new ConfigSaveWorker.Attempt(ClientConfigStore.SaveOutcome.COMMITTED,
                    null, null, request.config(), false);
        }, applied::add, (request, ancestor) -> request, 0L, 0);
        try {
            var active = worker.submit(request(1));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            var queued = worker.submit(request(2));
            release.countDown();
            var failed = active.get(5, TimeUnit.SECONDS);
            assertEquals(ClientConfigStore.SaveOutcome.FAILED, failed.attempt().outcome());
            assertTrue(failed.terminal());
            assertTrue(failed.attempt().failure().getCause() instanceof NullPointerException);
            assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                    queued.get(5, TimeUnit.SECONDS).attempt().outcome());
            assertEquals(List.of(1L, 2L), applied.stream().map(value -> value.request().requestId()).toList());
            assertTrue(worker.isIdle());
        } finally {
            release.countDown();
            worker.shutdownAndAwaitTermination(5_000);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void completionApplierFailureDoesNotStrandAnAlreadyQueuedRequest(boolean retryable) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<Long> persisted = new CopyOnWriteArrayList<>();
        ConfigSaveWorker worker = new ConfigSaveWorker(request -> {
            persisted.add(request.requestId());
            if (request.requestId() == 1) {
                started.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException failure) { throw new AssertionError(failure); }
                return new ConfigSaveWorker.Attempt(ClientConfigStore.SaveOutcome.FAILED,
                        null, null, null, retryable);
            }
            return new ConfigSaveWorker.Attempt(ClientConfigStore.SaveOutcome.COMMITTED,
                    null, null, request.config(), false);
        }, completion -> {
            if (completion.request().requestId() == 1) throw new IllegalStateException("Injected callback failure");
        }, (request, ancestor) -> request, 0L, 2);
        try {
            var active = worker.submit(request(1));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            var queued = worker.submit(request(2));
            release.countDown();
            var failure = org.junit.jupiter.api.Assertions.assertThrows(ExecutionException.class,
                    () -> active.get(5, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof IllegalStateException);
            assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                    queued.get(5, TimeUnit.SECONDS).attempt().outcome());
            assertEquals(List.of(1L, 2L), persisted);
            assertTrue(worker.isIdle());
        } finally {
            release.countDown();
            worker.shutdownAndAwaitTermination(5_000);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void callbackCannotWaitForItsOwnWorkerToTerminate(boolean close) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ConfigSaveWorker worker = new ConfigSaveWorker(request -> {
            started.countDown();
            try {
                assertTrue(release.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
            return new ConfigSaveWorker.Attempt(ClientConfigStore.SaveOutcome.COMMITTED,
                    null, null, request.config(), false);
        }, completion -> {}, (request, ancestor) -> request, 0L, 0);
        try {
            var saved = worker.submit(request(1));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            var callback = saved.thenRun(() -> {
                if (close) {
                    var failure = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                            () -> worker.shutdownAndAwaitTermination(5_000));
                    assertTrue(failure.getMessage().contains("own save worker"));
                } else {
                    assertFalse(worker.shutdown(5_000));
                }
            });
            release.countDown();
            callback.get(1, TimeUnit.SECONDS);
            assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                    worker.submit(request(2)).get(5, TimeUnit.SECONDS).attempt().outcome());
            worker.shutdownAndAwaitTermination(5_000);
        } finally {
            release.countDown();
            worker.shutdownAndAwaitTermination(6_000);
        }
    }

    private static ConfigSaveWorker.Request request(long id) {
        return new ConfigSaveWorker.Request(id, new Config(), List.of());
    }
}
