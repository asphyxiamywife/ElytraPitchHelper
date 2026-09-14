package com.asphyxiamywife.elytrapitchhelper.client;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import com.asphyxiamywife.elytrapitchhelper.config.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.asphyxiamywife.elytrapitchhelper.client.WatchEventFixtures.event;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClientLogicFuzzTest {
    @FuzzTest(maxDuration = "30s")
    void watcherClassificationAndDebouncingMatchTheirModels(FuzzedDataProvider data) {
        fuzzDebouncer(data);
        fuzzWatcherClassification(data);
    }

    @FuzzTest(maxDuration = "30s")
    void saveWorkerCoalescesQueuedRequestsToTheLatest(FuzzedDataProvider data) throws Exception {
        int requestCount = data.consumeInt(1, 24);

        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        List<Long> persisted = Collections.synchronizedList(new ArrayList<>());
        ConfigSaveWorker worker = new ConfigSaveWorker(
                request -> {
                    persisted.add(request.requestId());
                    if (request.requestId() == 1L) {
                        firstStarted.countDown();
                        await(releaseFirst);
                    }
                    return new ConfigSaveWorker.Attempt(
                            ClientConfigStore.SaveOutcome.COMMITTED, null, null, request.config(), false);
                },
                completion -> {
                },
                (request, ancestor) -> request,
                0L, 0);
        try {
            List<CompletableFuture<ConfigSaveWorker.Completion>> futures = new ArrayList<>();
            futures.add(worker.submit(request(1L)));
            assertTrue(firstStarted.await(5L, TimeUnit.SECONDS));

            for (int index = 1; index < requestCount; index++) {
                futures.add(worker.submit(request(index + 1L)));
            }

            releaseFirst.countDown();
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .get(5L, TimeUnit.SECONDS);
            List<Long> expected = new ArrayList<>();
            expected.add(1L);
            if (requestCount > 1) {
                expected.add((long) requestCount);
            }
            assertEquals(expected, persisted);
        } finally {
            releaseFirst.countDown();
            worker.shutdown(5_000L);
        }
    }

    private static void fuzzDebouncer(FuzzedDataProvider data) {
        long debounceMillis = data.consumeLong(0L, 10_000L);
        ConfigWatchDebouncer debouncer = new ConfigWatchDebouncer(debounceMillis);
        Long reloadAt = null;
        int operations = data.consumeInt(1, 64);
        for (int i = 0; i < operations; i++) {
            long nowMillis = data.consumeLong(0L, 1_000_000L);
            switch (data.consumeInt(0, 2)) {
                case 0 -> {
                    debouncer.recordChange(nowMillis);
                    reloadAt = nowMillis + debounceMillis;
                }
                case 1 -> assertEquals(reloadAt == null ? -1L : Math.max(0L, reloadAt - nowMillis),
                        debouncer.waitMillis(nowMillis));
                default -> {
                    boolean expected = reloadAt != null && nowMillis >= reloadAt;
                    assertEquals(expected, debouncer.shouldReload(nowMillis));
                    if (expected) {
                        reloadAt = null;
                    }
                }
            }
        }
    }

    private static void fuzzWatcherClassification(FuzzedDataProvider data) {
        Path configDirectory = Path.of("config", "elytra-pitch-helper");
        Path profileDirectory = configDirectory.resolve("profiles");
        Path internalDirectory = configDirectory.resolve(".internal");
        Path otherDirectory = configDirectory.resolve("other");
        Path configPath = configDirectory.resolve("elytra-pitch-helper.json");
        Path profileMetadataPath = internalDirectory.resolve("profile-metadata.json");
        String fileName = data.consumeString(64).replace('\0', '_').replace('/', '_').replace('\\', '_');
        Path changedPath = Path.of(fileName);
        Path watchedDirectory = switch (data.consumeInt(0, 3)) {
            case 0 -> configDirectory;
            case 1 -> profileDirectory;
            case 2 -> internalDirectory;
            default -> otherDirectory;
        };
        boolean overflow = data.consumeBoolean();
        WatchEvent<Path> event = event(changedPath, overflow);
        boolean expected = overflow
                || watchedDirectory.equals(configDirectory)
                        && changedPath.getFileName().equals(configPath.getFileName())
                || watchedDirectory.equals(profileDirectory)
                        && changedPath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")
                || watchedDirectory.equals(internalDirectory)
                        && changedPath.getFileName().equals(profileMetadataPath.getFileName());

        assertEquals(expected, ConfigWatcher.changedConfigFile(event, watchedDirectory, configPath,
                profileMetadataPath, configDirectory, profileDirectory, internalDirectory));
    }

    private static ConfigSaveWorker.Request request(long id) {
        return new ConfigSaveWorker.Request(id, new Config(), List.of());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5L, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for fuzz latch");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }
}
