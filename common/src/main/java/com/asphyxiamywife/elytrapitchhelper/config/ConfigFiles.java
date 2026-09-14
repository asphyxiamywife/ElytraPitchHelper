package com.asphyxiamywife.elytrapitchhelper.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

final class ConfigFiles {
    static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Profile.class, new ProfileAdapter())
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private ConfigFiles() {
    }

    private static final class ProfileAdapter implements JsonSerializer<Profile> {
        @Override
        public JsonElement serialize(Profile profile, java.lang.reflect.Type type,
                JsonSerializationContext context) {
            return ProfileJson.write(profile);
        }
    }


    static boolean isJsonFile(ConfigFileSystem fileSystem, Path path) {
        return fileSystem.isRegularFile(path, false)
                && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(ProfileFileNames.JSON_SUFFIX);
    }


    static void writeJsonAtomic(ConfigFileSystem fileSystem, Path path, Object value) throws IOException {
        writeBytesAtomic(fileSystem, path, jsonBytes(value));
    }


    static DiskSnapshot writeJsonAtomicWithSnapshot(ConfigFileSystem fileSystem, Path path, Object value)
            throws IOException {
        byte[] contents = jsonBytes(value);
        writeBytesAtomic(fileSystem, path, contents);
        return new DiskSnapshot(true, fileSystem.modifiedAtMillis(path), sha256Hex(contents));
    }

    static void writeBytesAtomic(ConfigFileSystem fileSystem, Path path, byte[] value) throws IOException {
        fileSystem.writeAtomic(path, value);
    }

    static byte[] jsonBytes(Object value) {
        return GSON.toJson(value).getBytes(StandardCharsets.UTF_8);
    }


    static String fileFingerprint(ConfigFileSystem fileSystem, Path path) throws IOException {
        return sha256Hex(fileSystem.read(path));
    }

    static String sha256Hex(byte[] contents) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contents));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    static boolean jsonValuesEqual(JsonElement first, JsonElement second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null || first.isJsonNull() || second.isJsonNull()) {
            return (first == null || first.isJsonNull()) && (second == null || second.isJsonNull());
        }
        if (first.isJsonObject() && second.isJsonObject()) {
            if (!first.getAsJsonObject().keySet().equals(second.getAsJsonObject().keySet())) {
                return false;
            }
            for (String key : first.getAsJsonObject().keySet()) {
                if (!jsonValuesEqual(first.getAsJsonObject().get(key), second.getAsJsonObject().get(key))) {
                    return false;
                }
            }
            return true;
        }
        if (first.isJsonArray() && second.isJsonArray()) {
            if (first.getAsJsonArray().size() != second.getAsJsonArray().size()) {
                return false;
            }
            for (int i = 0; i < first.getAsJsonArray().size(); i++) {
                if (!jsonValuesEqual(first.getAsJsonArray().get(i), second.getAsJsonArray().get(i))) {
                    return false;
                }
            }
            return true;
        }
        if (!first.isJsonPrimitive() || !second.isJsonPrimitive()) {
            return false;
        }
        if (first.getAsJsonPrimitive().isNumber() && second.getAsJsonPrimitive().isNumber()) {
            try {
                return new BigDecimal(first.getAsString()).compareTo(new BigDecimal(second.getAsString())) == 0;
            } catch (NumberFormatException ignored) {
                return first.getAsDouble() == second.getAsDouble();
            }
        }
        return first.equals(second);
    }
}
