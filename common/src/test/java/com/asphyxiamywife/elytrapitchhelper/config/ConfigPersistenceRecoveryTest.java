package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.testing.IsolatedConfigRoot;
import com.asphyxiamywife.elytrapitchhelper.testing.FaultingConfigFileSystem;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.asphyxiamywife.elytrapitchhelper.config.ConfigTestFixtures.fs;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ConfigPersistenceRecoveryTest {
    @IsolatedConfigRoot
    Path configRoot;

    @Test
    void successfulRetryMustSurviveAnEarlierRollbackMarkerFailure() throws Exception {
        Config config = Config.load(fs());
        Path main = Config.getConfigPath(fs());
        config.enabled = !config.enabled;
        config.replaceProfile(0, config.profile(0).withName("Saved after retry"));
        FaultingConfigFileSystem faulting = FaultingConfigFileSystem.current();
        faulting.failWritesWhen(path -> path.equals(main)
                || path.getFileName().toString().equals("finalized"));
        try {
            assertThrows(ConfigSaveException.class, () -> config.save(fs()));
        } finally {
            faulting.failWritesWhen(null);
        }
        config.save(fs());
        assertEquals("Saved after retry", Config.reloadFromDisk(fs()).profileName(0));
        assertEquals("Saved after retry", Config.load(fs()).profileName(0));
    }

    @Test
    void editAfterProfilePublicationMustNotBeAcceptedAsTheSavedBaseline() throws Exception {
        Config config = Config.load(fs());
        Path profile = config.getProfilePath(fs(), 0);
        config.replaceProfile(0, config.profile(0).withName("In-game name"));
        FaultingConfigFileSystem faulting = FaultingConfigFileSystem.current();
        AtomicBoolean edited = new AtomicBoolean();
        faulting.observeMutations(path -> {
            if (!path.equals(profile.getParent()) || edited.get()) {
                return;
            }
            try {
                if (!Files.readString(profile).contains("In-game name")) {
                    return;
                }
                edited.set(true);
                var json = ConfigFiles.GSON.fromJson(Files.readString(profile),
                        com.google.gson.JsonObject.class);
                json.addProperty("name", "External edit");
                Files.writeString(profile, ConfigFiles.GSON.toJson(json));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        try {
            config.save(fs());
        } finally {
            faulting.observeMutations(null);
        }
        assertTrue(edited.get());
        assertEquals("External edit", Config.reloadFromDisk(fs()).profileName(0));
        assertThrows(ConfigConflictException.class, () -> config.save(fs()));
        assertEquals("External edit", Config.reloadFromDisk(fs()).profileName(0));
    }

    @Test
    void savingMustStayBlockedUntilRollbackCanBeFinalized() throws Exception {
        Config config = Config.load(fs());
        Path main = Config.getConfigPath(fs());
        Path profile = config.getProfilePath(fs(), 0);
        byte[] originalMain = Files.readAllBytes(main);
        byte[] originalProfile = Files.readAllBytes(profile);
        config.enabled = !config.enabled;
        config.replaceProfile(0, config.profile(0).withName("Pending retry"));
        FaultingConfigFileSystem faulting = FaultingConfigFileSystem.current();
        faulting.failWritesWhen(path -> path.equals(main)
                || path.getFileName().toString().equals("finalized"));
        try {
            assertThrows(ConfigSaveException.class, () -> config.save(fs()));
            faulting.failWritesWhen(path -> path.getFileName().toString().equals("finalized"));
            assertThrows(ConfigSaveException.class, () -> config.save(fs()));
            assertArrayEquals(originalMain, Files.readAllBytes(main));
            assertArrayEquals(originalProfile, Files.readAllBytes(profile));
        } finally {
            faulting.failWritesWhen(null);
        }
        config.save(fs());
        assertEquals("Pending retry", Config.load(fs()).profileName(0));
    }

    @Test
    void editAfterMainPublicationMustNotBeAcceptedAsTheSavedBaseline() throws Exception {
        Config config = Config.load(fs());
        Path main = Config.getConfigPath(fs());
        config.enabled = false;
        FaultingConfigFileSystem faulting = FaultingConfigFileSystem.current();
        AtomicBoolean edited = new AtomicBoolean();
        faulting.observeMutations(path -> {
            if (!path.equals(main.getParent()) || edited.get()) {
                return;
            }
            try {
                var json = ConfigFiles.GSON.fromJson(Files.readString(main),
                        com.google.gson.JsonObject.class);
                if (json.get("enabled").getAsBoolean()) {
                    return;
                }
                edited.set(true);
                json.addProperty("enabled", true);
                Files.writeString(main, ConfigFiles.GSON.toJson(json));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        try {
            config.save(fs());
        } finally {
            faulting.observeMutations(null);
        }
        assertTrue(edited.get());
        assertFalse(config.enabled);
        assertTrue(Config.reloadFromDisk(fs()).enabled);
        assertThrows(ConfigConflictException.class, () -> config.save(fs()));
        assertTrue(Config.reloadFromDisk(fs()).enabled);
    }

}
