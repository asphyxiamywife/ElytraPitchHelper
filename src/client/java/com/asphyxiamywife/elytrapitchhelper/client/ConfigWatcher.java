package com.asphyxiamywife.elytrapitchhelper.client;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.ModConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

public final class ConfigWatcher {
    private static final long RELOAD_DEBOUNCE_MILLIS = 250L;
    private static final Logger LOGGER = LoggerFactory.getLogger(ModConstants.MOD_ID);

    private ConfigWatcher() {
    }

    public static void start() {
        Thread thread = new Thread(ConfigWatcher::watchConfigFiles, "ElytraPitchHelper Config Watcher");
        thread.setDaemon(true);
        thread.start();
    }

    private static void watchConfigFiles() {
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            Path configPath = Config.getConfigPath();
            Path configDirectory = configPath.getParent();
            Path profileDirectory = Config.getProfileDirectory();
            if (configDirectory == null) {
                return;
            }

            Files.createDirectories(configDirectory);
            Files.createDirectories(profileDirectory);
            registerDirectories(watchService, configDirectory, profileDirectory);
            processEvents(watchService, configPath, configDirectory, profileDirectory,
                    new ConfigWatchDebouncer(RELOAD_DEBOUNCE_MILLIS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            LOGGER.warn("Stopped watching Elytra Pitch Helper config files", e);
        }
    }

    private static void registerDirectories(WatchService watchService, Path configDirectory, Path profileDirectory)
            throws IOException {
        configDirectory.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
        profileDirectory.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
    }

    private static void processEvents(WatchService watchService, Path configPath, Path configDirectory,
            Path profileDirectory, ConfigWatchDebouncer debouncer) throws InterruptedException {
        while (true) {
            WatchKey key = watchService.take();
            Path watchedDirectory = (Path) key.watchable();
            boolean changed = false;
            for (WatchEvent<?> event : key.pollEvents()) {
                if (changedConfigFile(event, watchedDirectory, configPath, configDirectory, profileDirectory)) {
                    changed = true;
                }
            }
            if (changed && debouncer.shouldReload(System.currentTimeMillis())) {
                ClientConfigStore.reloadFromDiskIfIdle();
            }
            if (!key.reset()) {
                break;
            }
        }
    }

    static boolean changedConfigFile(WatchEvent<?> event, Path watchedDirectory, Path configPath,
            Path configDirectory, Path profileDirectory) {
        if (!(event.context() instanceof Path changedPath)) {
            return false;
        }
        if (watchedDirectory.equals(configDirectory)) {
            return changedPath.getFileName().equals(configPath.getFileName());
        }
        return watchedDirectory.equals(profileDirectory)
                && changedPath.getFileName().toString().endsWith(".json");
    }
}
