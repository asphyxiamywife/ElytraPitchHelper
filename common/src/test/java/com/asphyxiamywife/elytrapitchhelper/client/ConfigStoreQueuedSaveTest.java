package com.asphyxiamywife.elytrapitchhelper.client;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.testing.FaultingConfigFileSystem;
import com.asphyxiamywife.elytrapitchhelper.testing.IsolatedConfigRoot;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfigStoreQueuedSaveTest {
    @IsolatedConfigRoot
    Path configRoot;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void queuedSaveRebasesItsOwnWritesButStillDetectsExternalEdits(boolean externalEdit) throws Exception {
        ClientConfigStore.initialize();
        Config first = ClientConfigStore.get();
        first.enabled = !first.enabled;
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean firstMove = new AtomicBoolean(true);
        FaultingConfigFileSystem fileSystem = FaultingConfigFileSystem.current();
        fileSystem.beforeAtomicMove(() -> {
            if (firstMove.compareAndSet(true, false)) {
                started.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to release first save");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
            }
        });
        AtomicBoolean externalWriteApplied = new AtomicBoolean();
        fileSystem.observeTemporaryPaths(temporary -> {
            if (externalEdit && temporary.getParent().getFileName().toString().startsWith("save-batch-")
                    && externalWriteApplied.compareAndSet(false, true)) {
                Path mainConfig = Config.getConfigPath(fileSystem);
                try {
                    JsonObject disk = JsonParser.parseString(Files.readString(mainConfig)).getAsJsonObject();
                    disk.addProperty("usedSettingsSearch", true);
                    Files.writeString(mainConfig, disk.toString());
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                }
            }
        });
        CompletableFuture<ClientConfigStore.AsyncSaveResult> firstSave = ClientConfigStore.saveAsync(first);
        CompletableFuture<ClientConfigStore.AsyncSaveResult> secondSave;
        try {
            assertTrue(started.await(10, TimeUnit.SECONDS));
            Config second = ClientConfigStore.get();
            second.profileSortMode = Config.PROFILE_SORT_NAME;
            secondSave = ClientConfigStore.saveAsync(second);
            Config third = ClientConfigStore.get();
            third.usedSettingsSearch = true;
            ClientConfigStore.update(third);
        } finally {
            release.countDown();
        }
        assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                firstSave.get(10, TimeUnit.SECONDS).result().outcome());
        assertEquals(externalEdit, externalWriteApplied.get());
        assertEquals(externalEdit ? ClientConfigStore.SaveOutcome.CONFLICTED : ClientConfigStore.SaveOutcome.COMMITTED,
                secondSave.get(10, TimeUnit.SECONDS).result().outcome());
        assertTrue(ClientConfigStore.hasUnsavedChanges());
        assertTrue(ClientConfigStore.get().usedSettingsSearch);
        if (!externalEdit) {
            assertFalse(Config.reloadFromDisk(fileSystem).usedSettingsSearch,
                    "Saving B must not prematurely persist pending edits C");
            assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                    ClientConfigStore.saveAsync(ClientConfigStore.get()).get(10, TimeUnit.SECONDS).result().outcome());
            assertFalse(ClientConfigStore.hasUnsavedChanges());
            assertTrue(Config.reloadFromDisk(fileSystem).usedSettingsSearch);
        }
    }
}
