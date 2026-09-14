package com.asphyxiamywife.elytrapitchhelper.client;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.testing.FaultingConfigFileSystem;
import com.asphyxiamywife.elytrapitchhelper.testing.IsolatedConfigRoot;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

final class ConfigSaveDeliveryTest {
    @IsolatedConfigRoot Path root;

    @Test
    void completionTimeSuccessBecomesObsoleteBeforeDelivery() throws Exception {
        ClientConfigStore.initialize();
        Config first = ClientConfigStore.get();
        first.enabled = !first.enabled;
        var oldResult = ClientConfigStore.saveAsync(first).get(5, TimeUnit.SECONDS);
        assertTrue(oldResult.latestEditsCommitted());
        assertTrue(oldResult.isCurrentAtDelivery());
        Config next = ClientConfigStore.get();
        next.enabled = !next.enabled;
        ClientConfigStore.update(next);
        assertFalse(oldResult.isCurrentAtDelivery());
        var current = ClientConfigStore.flushPending().get(5, TimeUnit.SECONDS);
        assertTrue(current.isCurrentAtDelivery());
        assertFalse(oldResult.isCurrentAtDelivery());
    }

    @Test
    void failureIsObsoleteAfterDiscardEvenWithoutANewGeneration() throws Exception {
        ClientConfigStore.initialize();
        Config config = ClientConfigStore.get();
        config.enabled = !config.enabled;
        FaultingConfigFileSystem.current().failWritesWhen(path -> true);
        var failure = ClientConfigStore.saveAsync(config).get(10, TimeUnit.SECONDS);
        assertEquals(ClientConfigStore.SaveOutcome.FAILED, failure.result().outcome());
        assertTrue(failure.isCurrentAtDelivery());
        assertEquals(ClientConfigStore.SaveOutcome.CANCELLED,
                ClientConfigStore.store().discardPendingSave(failure.pendingGeneration()).outcome());
        assertFalse(failure.isCurrentAtDelivery());
    }

    @Test
    void successfulRetryMakesDelayedFailureObsolete() throws Exception {
        ClientConfigStore.initialize();
        Config config = ClientConfigStore.get();
        config.enabled = !config.enabled;
        FaultingConfigFileSystem.current().failWritesWhen(path -> true);
        var failure = ClientConfigStore.saveAsync(config).get(10, TimeUnit.SECONDS);
        assertEquals(ClientConfigStore.SaveOutcome.FAILED, failure.result().outcome());
        FaultingConfigFileSystem.current().failWritesWhen(null);
        var success = ClientConfigStore.flushPending().get(5, TimeUnit.SECONDS);
        assertEquals(ClientConfigStore.SaveOutcome.COMMITTED, success.result().outcome());
        assertTrue(success.isCurrentAtDelivery());
        assertFalse(failure.isCurrentAtDelivery());
    }

    @Test
    void replacementStoreAndCancellationCannotProduceNotifications() throws Exception {
        ClientConfigStore.initialize();
        ConfigStore oldStore = ClientConfigStore.store();
        Config config = oldStore.get();
        config.enabled = !config.enabled;
        var committed = oldStore.saveAsync(config).get(5, TimeUnit.SECONDS);
        assertTrue(committed.isCurrentAtDelivery());
        ClientConfigStore.setStore(new ConfigStore(oldStore.fileSystem()));
        ClientConfigStore.initialize();
        assertFalse(committed.isCurrentAtDelivery());
        var cancelled = oldStore.saveAsync(config).get(5, TimeUnit.SECONDS);
        assertEquals(ClientConfigStore.SaveOutcome.CANCELLED, cancelled.result().outcome());
        assertFalse(cancelled.isCurrentAtDelivery());
        var currentStore = ClientConfigStore.store();
        currentStore.close();
        var currentCancellation = currentStore.saveAsync(currentStore.get()).get(5, TimeUnit.SECONDS);
        assertEquals(ClientConfigStore.SaveOutcome.CANCELLED, currentCancellation.result().outcome());
        assertFalse(currentCancellation.isCurrentAtDelivery());
    }
}
