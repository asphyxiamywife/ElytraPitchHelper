package com.asphyxiamywife.elytrapitchhelper.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfigWatcherTest {
    @Test
    void detectsMainConfigAndProfileJsonChangesOnly() {
        Path configDirectory = Path.of("config", "elytra-pitch-helper");
        Path profileDirectory = configDirectory.resolve("profiles");
        Path configPath = configDirectory.resolve("elytra-pitch-helper.json");

        assertTrue(ConfigWatcher.changedConfigFile(event("elytra-pitch-helper.json"), configDirectory, configPath,
                configDirectory, profileDirectory));
        assertTrue(ConfigWatcher.changedConfigFile(event("speed.json"), profileDirectory, configPath,
                configDirectory, profileDirectory));
        assertFalse(ConfigWatcher.changedConfigFile(event("profile-metadata.json"),
                configDirectory.resolve(".internal"), configPath, configDirectory, profileDirectory));
        assertFalse(ConfigWatcher.changedConfigFile(event("speed.txt"), profileDirectory, configPath,
                configDirectory, profileDirectory));
        assertFalse(ConfigWatcher.changedConfigFile(event("other.json"), configDirectory, configPath,
                configDirectory, profileDirectory));
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

    private static WatchEvent<Path> event(String fileName) {
        return new WatchEvent<>() {
            @Override
            public Kind<Path> kind() {
                return StandardWatchEventKinds.ENTRY_MODIFY;
            }

            @Override
            public int count() {
                return 1;
            }

            @Override
            public Path context() {
                return Path.of(fileName);
            }
        };
    }
}
