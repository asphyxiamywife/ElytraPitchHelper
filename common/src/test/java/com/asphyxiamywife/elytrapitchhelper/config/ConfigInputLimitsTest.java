package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingSpec;
import com.asphyxiamywife.elytrapitchhelper.testing.IsolatedConfigRoot;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.asphyxiamywife.elytrapitchhelper.config.ConfigTestFixtures.fs;
import static com.asphyxiamywife.elytrapitchhelper.config.ConfigFileAssertions.backupContains;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfigInputLimitsTest {
    @IsolatedConfigRoot
    Path configRoot;

    @Test
    void nestingIsRejectedBeforeDeserializationAndHonorsTheBoundary() {
        int limit = ConfigInputLimits.MAX_NESTING;
        assertNotNull(ConfigJsonFiles.parse(bytes(nested(limit)), JsonArray.class));
        assertThrows(JsonParseException.class,
                () -> ConfigJsonFiles.parse(bytes(nested(limit + 1)), JsonArray.class));
        assertThrows(JsonParseException.class,
                () -> ConfigJsonFiles.parse(bytes(nested(20_000)), Config.class));
    }

    @Test
    void delimitersInStringsAndCommentsDoNotCountAsNesting() {
        String brackets = "[".repeat(1000) + "}".repeat(1000);
        JsonObject parsed = ConfigJsonFiles.parse(bytes("/* " + brackets + " */ {\"text\":\""
                + brackets + "\\\"\\\\\"}"), JsonObject.class);
        assertEquals(brackets + "\"\\", parsed.get("text").getAsString());
    }

    @Test
    void arrayAndTokenLimitsApplyToIgnoredFieldsToo() {
        String array = "[" + "0,".repeat(ConfigInputLimits.MAX_ARRAY_ELEMENTS - 1) + "0]";
        assertEquals(ConfigInputLimits.MAX_ARRAY_ELEMENTS,
                ConfigJsonFiles.parse(bytes(array), JsonArray.class).size());
        assertThrows(JsonParseException.class, () -> ConfigJsonFiles.parse(
                bytes("{\"ignored\":[" + "0,".repeat(ConfigInputLimits.MAX_ARRAY_ELEMENTS) + "0]}"), Config.class));
        StringBuilder wide = new StringBuilder("{");
        for (int i = 0; i < ConfigInputLimits.MAX_TOKENS / 2; i++) {
            wide.append('"').append(i).append("\":0,");
        }
        wide.append("\"last\":0}");
        assertThrows(JsonParseException.class,
                () -> ConfigJsonFiles.parse(bytes(wide.toString()), Config.class));
    }

    @Test
    void integerArrayCodecRejectsOversizedPrebuiltTreesBeforeAllocating() {
        JsonArray array = new JsonArray();
        for (int i = 0; i <= ConfigInputLimits.MAX_ARRAY_ELEMENTS; i++) array.add(i);
        JsonObject root = new JsonObject();
        root.add("colors", array);
        int[] fallback = {0x123456, 0x654321};
        assertSame(fallback, SettingSpec.INT_ARRAY_JSON.read(root, "colors", fallback, null));
    }

    @Test
    void fileReadAcceptsTheByteBoundaryAndRejectsTheNextByte() throws IOException {
        Path path = configRoot.resolve("large.json");
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
            file.setLength(ConfigInputLimits.MAX_FILE_BYTES);
            assertEquals(ConfigInputLimits.MAX_FILE_BYTES, fs().read(path).length);
            file.setLength(ConfigInputLimits.MAX_FILE_BYTES + 1L);
            assertThrows(IOException.class, () -> fs().read(path));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"main", "metadata", "profile"})
    void oversizedFilesSurviveStartupAndReload(String source) throws IOException {
        Config baseline = Config.load(fs());
        Path path = switch (source) {
            case "main" -> Config.getConfigPath(fs());
            case "metadata" -> Config.getProfileMetadataPath(fs());
            default -> baseline.getProfilePath(fs(), 0);
        };
        long size = ConfigInputLimits.MAX_FILE_BYTES + 1L;
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
            file.setLength(size);
        }
        assertTrue(Config.tryReloadFromDisk(fs()).isEmpty());
        Config loaded = Config.load(fs());
        if (!source.equals("profile")) assertTrue(loaded.isReadOnly());
        assertEquals(size, Files.size(path));
    }

    @Test
    void deeplyNestedMainConfigIsBackedUpBeforeStartupUsesDefaults() throws IOException {
        Config.load(fs());
        Path path = Config.getConfigPath(fs());
        byte[] deep = bytes("{\"ignored\":" + nested(20_000) + "}");
        Files.write(path, deep);
        assertNotNull(Config.reloadFromDisk(fs()));
        assertArrayEquals(deep, Files.readAllBytes(path));
        assertFalse(Config.load(fs()).isReadOnly());
        assertTrue(backupContains(ConfigBackups.directory(fs(), "main"), deep));
    }

    @Test
    void deeplyNestedBackupIsSkippedAndDamagedJournalIsPreserved() throws IOException {
        Config baseline = Config.load(fs());
        Path profilePath = baseline.getProfilePath(fs(), 0);
        ProfileBackups.save(fs(), profilePath, baseline.profile(0));
        Path backupDirectory = ProfileBackups.profileBackupDirectoryForTests(fs(), baseline.profile(0).fileName());
        byte[] deep = bytes("{\"ignored\":" + nested(20_000) + "}");
        Files.write(backupDirectory.resolve(ProfileBackups.backupFileName("20990101-000000-000", 1)), deep);
        assertEquals(baseline.profileName(0), ProfileBackups.latestValid(fs(), profilePath, null).name());

        Path journalDirectory = Files.createTempDirectory(
                Config.getConfigDirectory(fs()).resolve(".internal"), "save-batch-");
        Files.write(journalDirectory.resolve("journal.json"), deep);
        ConfigSaveBatch.recoverIncomplete(fs());
        assertArrayEquals(deep, Files.readAllBytes(journalDirectory.resolve("journal.json")));
    }

    private static String nested(int depth) {
        return "[".repeat(depth) + "0" + "]".repeat(depth);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
