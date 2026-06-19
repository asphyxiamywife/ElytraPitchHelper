package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.client.ClientConfigStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClientConfigStoreTest {
    @TempDir
    Path configRoot;

    @BeforeEach
    void setConfigRoot() {
        Config.setConfigRootOverrideForTests(configRoot);
        ClientConfigStore.reloadFromDisk();
    }

    @AfterEach
    void clearConfigRoot() {
        Config.setConfigRootOverrideForTests(null);
    }

    @Test
    void setPropagatesSaveFailureWithoutPublishingConfig() throws IOException {
        Config current = new Config();
        current.enabled = false;
        ClientConfigStore.set(current);
        long revision = ClientConfigStore.revision();

        Path failingRoot = configRoot.resolve("failing");
        Files.createDirectories(failingRoot);
        Files.writeString(failingRoot.resolve("elytra-pitch-helper"), "not a directory",
                StandardCharsets.UTF_8);
        Config.setConfigRootOverrideForTests(failingRoot);

        Config next = ClientConfigStore.get().copy();
        next.enabled = true;

        assertThrows(ConfigSaveException.class, () -> ClientConfigStore.set(next));
        assertFalse(ClientConfigStore.get().enabled);
        assertEquals(revision, ClientConfigStore.revision());
    }

    @Test
    void watcherReloadOfJustSavedStateDoesNotCreateExternalRevision() {
        Config current = ClientConfigStore.get().copy();
        current.enabled = !current.enabled;
        ClientConfigStore.set(current);
        long savedRevision = ClientConfigStore.revision();

        assertTrue(ClientConfigStore.reloadFromDiskIfIdle());
        assertEquals(savedRevision, ClientConfigStore.revision());
    }
}
