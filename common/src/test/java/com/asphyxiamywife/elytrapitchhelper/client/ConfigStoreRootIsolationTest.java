package com.asphyxiamywife.elytrapitchhelper.client;

import com.asphyxiamywife.elytrapitchhelper.config.NioConfigFileSystem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfigStoreRootIsolationTest {
    @Test
    void storesResolvePathsFromTheirOwnRootRatherThanAGlobalOne() throws IOException {
        Path firstRoot = Files.createTempDirectory("elytra-root-a-");
        Path secondRoot = Files.createTempDirectory("elytra-root-b-");
        try (ConfigStore first = new ConfigStore(NioConfigFileSystem.rootedAt(firstRoot));
                ConfigStore second = new ConfigStore(NioConfigFileSystem.rootedAt(secondRoot))) {

            assertTrue(first.configPath().startsWith(firstRoot.toAbsolutePath().normalize()));
            assertTrue(second.configPath().startsWith(secondRoot.toAbsolutePath().normalize()));
            assertNotEquals(first.configPath(), second.configPath());
            assertNotEquals(first.profileDirectory(), second.profileDirectory());

            ConfigStore installed = ClientConfigStore.store();
            assertNotEquals(installed.configRoot(), first.configRoot());
            assertNotEquals(installed.configRoot(), second.configRoot());
        } finally {
            deleteRecursively(firstRoot);
            deleteRecursively(secondRoot);
        }
    }

    @Test
    void aStoreWritesOnlyBeneathItsOwnRoot() throws IOException {
        Path root = Files.createTempDirectory("elytra-root-c-");
        try (ConfigStore store = new ConfigStore(NioConfigFileSystem.rootedAt(root))) {
            store.initialize();

            assertTrue(Files.exists(store.configPath()), "startup should have written the main config");
            assertEquals(root.toAbsolutePath().normalize(), store.configRoot());
        } finally {
            deleteRecursively(root);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
