package com.asphyxiamywife.elytrapitchhelper.client;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.ConfigConflictException;
import com.asphyxiamywife.elytrapitchhelper.config.ConfigSaveException;
import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;

final class ConfigSaveWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigSaveWorker.class);
    static final long DEFAULT_RETRY_DELAY_MILLIS = 750L;
    static final int DEFAULT_MAX_AUTOMATIC_RETRIES = 2;

    private final Object monitor = new Object();
    private final ScheduledThreadPoolExecutor worker;
    private final Persister persister;
    private final Consumer<Completion> completionApplier;
    private final BiFunction<Request, Completion, Request> rebaseQueued;
    private final long retryDelayMillis;
    private final int maxAutomaticRetries;

    private volatile Thread workerThread;
    private Entry active;
    private Entry queued;
    private boolean accepting = true;
    private long newestSubmitted = Long.MIN_VALUE;

    ConfigSaveWorker(Persister persister, Consumer<Completion> completionApplier,
            BiFunction<Request, Completion, Request> rebaseQueued) {
        this(persister, completionApplier, rebaseQueued,
                DEFAULT_RETRY_DELAY_MILLIS, DEFAULT_MAX_AUTOMATIC_RETRIES);
    }

    ConfigSaveWorker(Persister persister, Consumer<Completion> completionApplier,
            BiFunction<Request, Completion, Request> rebaseQueued, long retryDelayMillis, int maxAutomaticRetries) {
        this.persister = Objects.requireNonNull(persister, "persister");
        this.completionApplier = Objects.requireNonNull(completionApplier, "completionApplier");
        this.rebaseQueued = Objects.requireNonNull(rebaseQueued, "rebaseQueued");
        this.retryDelayMillis = Math.max(0L, retryDelayMillis);
        this.maxAutomaticRetries = Math.max(0, maxAutomaticRetries);
        worker = new ScheduledThreadPoolExecutor(1, daemonThreadFactory());
        worker.setRemoveOnCancelPolicy(true);
        worker.setExecuteExistingDelayedTasksAfterShutdownPolicy(true);
        worker.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    }

    CompletableFuture<Completion> submit(Request request) {
        Objects.requireNonNull(request, "request");
        CompletableFuture<Completion> future = new CompletableFuture<>();
        Entry dispatch = null;
        Entry superseded = null;
        boolean rejected;
        boolean obsolete;
        synchronized (monitor) {
            rejected = !accepting;
            obsolete = request.requestId() < newestSubmitted;
            newestSubmitted = Math.max(newestSubmitted, request.requestId());
            if (rejected || obsolete) {
            } else if (active == null) {
                active = new Entry(request, future);
                dispatch = active;
            } else if (queued == null) {
                queued = new Entry(request, future);
            } else {
                superseded = queued;
                queued = new Entry(request, future);
            }
        }
        if (obsolete && !rejected) {
            future.complete(supersededCompletion(request));
        } else if (rejected) {
            applyCompletion(new Entry(request, future),
                    cancelledCompletion(request, "Config save worker is stopping"));
        }
        if (superseded != null) {
            superseded.waiter.complete(supersededCompletion(superseded.request));
        }
        if (dispatch != null) {
            execute(dispatch, 0L);
        }
        return future;
    }

    boolean isWorkerThread() {
        return Thread.currentThread() == workerThread;
    }

    boolean shutdown(long timeoutMillis) {
        if (isWorkerThread()) {
            return false;
        }
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0L, timeoutMillis));
        synchronized (monitor) {
            accepting = false;
        }

        synchronized (monitor) {
            while (active != null) {
                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0L) {
                    break;
                }
                long waitMillis = MathUtil.clamp(TimeUnit.NANOSECONDS.toMillis(remainingNanos), 1L, 50L);
                try {
                    monitor.wait(waitMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            if (active != null || queued != null) {
                return false;
            }
            worker.shutdown();
        }

        long remainingNanos = Math.max(0L, deadlineNanos - System.nanoTime());
        try {
            return worker.awaitTermination(remainingNanos, TimeUnit.NANOSECONDS) || isIdle();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    void shutdownAndAwaitTermination(long timeoutMillis) {
        if (isWorkerThread()) {
            throw new IllegalStateException("Cannot close a store from its own save worker callback");
        }
        shutdown(0L);
        worker.shutdown();
        try {
            if (!worker.awaitTermination(Math.max(0L, timeoutMillis), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Config save worker is still writing; its directory cannot be released");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for config save worker termination", interrupted);
        }
    }

    boolean isIdle() {
        synchronized (monitor) {
            return active == null;
        }
    }

    private void execute(Entry entry, long delayMillis) {
        try {
            worker.schedule(() -> persist(entry), delayMillis, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException rejected) {
            applyCompletion(entry, cancelledCompletion(entry.request, "Config save worker rejected the request"));
        }
    }

    private void persist(Entry entry) {
        Request request;
        synchronized (monitor) {
            if (active != entry) {
                return;
            }
            request = entry.request;
        }

        Attempt attempt;
        try {
            attempt = Objects.requireNonNull(persister.persist(request), "persistence attempt");
            Objects.requireNonNull(attempt.outcome(), "persistence outcome");
        } catch (RuntimeException failure) {
            ConfigSaveException wrapped = failure instanceof ConfigSaveException saveFailure
                    ? saveFailure
                    : new ConfigSaveException("Unexpected asynchronous config persistence failure", failure);
            attempt = new Attempt(ClientConfigStore.SaveOutcome.FAILED, null, wrapped, null, false);
        }

        boolean terminal = !attempt.retryable() || entry.retryCount >= maxAutomaticRetries;
        applyCompletion(entry, new Completion(request, attempt, terminal));
    }

    private void applyCompletion(Entry entry, Completion completion) {
        try {
            completionApplier.accept(completion);
        } catch (RuntimeException failure) {
            finishTerminal(entry, completion);
            entry.waiter.completeExceptionally(failure);
            return;
        }

        if (!completion.terminal()) {
            synchronized (monitor) {
                if (active != entry) {
                    return;
                }
                entry.retryCount++;
            }
            execute(entry, retryDelayMillis);
            return;
        }

        finishTerminal(entry, completion);
        entry.waiter.complete(completion);
    }

    private void finishTerminal(Entry completed, Completion completion) {
        Entry dispatch;
        synchronized (monitor) {
            if (active != completed) {
                return;
            }
            active = queued;
            queued = null;
            dispatch = active;
            monitor.notifyAll();
        }
        if (dispatch != null) {
            try {
                dispatch.request = Objects.requireNonNull(
                        rebaseQueued.apply(dispatch.request, completion), "rebased request");
            } catch (RuntimeException failure) {
                ConfigSaveException wrapped = new ConfigSaveException("Could not rebase queued config save", failure);
                applyCompletion(dispatch, new Completion(dispatch.request,
                        new Attempt(ClientConfigStore.SaveOutcome.FAILED, null, wrapped, null, false), true));
                return;
            }
            execute(dispatch, 0L);
        }
    }

    private static Completion cancelledCompletion(Request request, String message) {
        ConfigSaveException failure = new ConfigSaveException(message);
        Attempt attempt = new Attempt(ClientConfigStore.SaveOutcome.CANCELLED, null, failure, null, false);
        return new Completion(request, attempt, true);
    }

    private static Completion supersededCompletion(Request request) {
        Attempt attempt = new Attempt(
                ClientConfigStore.SaveOutcome.SUPERSEDED, null, null, null, false);
        return new Completion(request, attempt, true);
    }

    private ThreadFactory daemonThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "elytra-pitch-helper-config-save");
            workerThread = thread;
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((failedThread, failure) ->
                    LOGGER.error("Uncaught config save worker failure", failure));
            return thread;
        };
    }

    record Request(long requestId, Config config, List<String> deletedProfileFiles) {
        Request {
            config = config.copy();
            deletedProfileFiles = List.copyOf(deletedProfileFiles);
        }

        @Override
        public Config config() {
            return config.copy();
        }
    }

    record Attempt(ClientConfigStore.SaveOutcome outcome, ConfigConflictException conflict,
            ConfigSaveException failure, Config savedConfig, boolean retryable) {
        Attempt {
            savedConfig = savedConfig == null ? null : savedConfig.copy();
        }

        @Override
        public Config savedConfig() {
            return savedConfig == null ? null : savedConfig.copy();
        }
    }

    record Completion(Request request, Attempt attempt, boolean terminal) {
    }

    @FunctionalInterface
    interface Persister {
        Attempt persist(Request request);
    }

    private static final class Entry {
        private volatile Request request;
        private final CompletableFuture<Completion> waiter;
        private int retryCount;

        private Entry(Request request, CompletableFuture<Completion> waiter) {
            this.request = request;
            this.waiter = waiter;
        }
    }
}
