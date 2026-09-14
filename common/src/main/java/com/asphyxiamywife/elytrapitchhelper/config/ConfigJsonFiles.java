package com.asphyxiamywife.elytrapitchhelper.config;

import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

final class ConfigJsonFiles {
    private ConfigJsonFiles() {
    }


    static <T> T read(
            ConfigFileSystem fileSystem, Path path, Class<T> type, Recovery<T> recovery) {
        try {
            T value = parse(fileSystem.read(path), type);
            if (value == null) {
                throw new JsonParseException("JSON root is null");
            }
            return value;
        } catch (IOException | JsonParseException | IllegalStateException failure) {
            return recovery.recover(failure);
        }
    }

    static <T> T parse(byte[] contents, Class<T> type) {
        if (contents.length > ConfigInputLimits.MAX_FILE_BYTES) {
            throw new JsonParseException("Config JSON exceeds the file size limit");
        }
        String json = new String(contents, StandardCharsets.UTF_8);
        validateStructure(json);
        return ConfigFiles.GSON.fromJson(json, type);
    }

    private static void validateStructure(String json) {
        int depth = 0;
        int tokens = 0;
        int[] arrayElements = new int[ConfigInputLimits.MAX_NESTING];
        boolean[] arrays = new boolean[ConfigInputLimits.MAX_NESTING];
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setStrictness(Strictness.LENIENT);
            JsonToken token;
            while ((token = reader.peek()) != JsonToken.END_DOCUMENT) {
                if (++tokens > ConfigInputLimits.MAX_TOKENS) {
                    throw new JsonParseException("Config JSON exceeds the token limit");
                }
                boolean value = token != JsonToken.NAME && token != JsonToken.END_ARRAY
                        && token != JsonToken.END_OBJECT;
                if (value && depth > 0 && arrays[depth - 1]
                        && ++arrayElements[depth - 1] > ConfigInputLimits.MAX_ARRAY_ELEMENTS) {
                    throw new JsonParseException("Config JSON array exceeds the element limit");
                }
                switch (token) {
                    case BEGIN_ARRAY, BEGIN_OBJECT -> {
                        if (depth == ConfigInputLimits.MAX_NESTING) {
                            throw new JsonParseException("Config JSON exceeds the nesting limit");
                        }
                        arrays[depth] = token == JsonToken.BEGIN_ARRAY;
                        arrayElements[depth++] = 0;
                        if (token == JsonToken.BEGIN_ARRAY) {
                            reader.beginArray();
                        } else {
                            reader.beginObject();
                        }
                    }
                    case END_ARRAY -> {
                        reader.endArray();
                        depth--;
                    }
                    case END_OBJECT -> {
                        reader.endObject();
                        depth--;
                    }
                    case NAME -> reader.nextName();
                    case STRING, NUMBER -> reader.nextString();
                    case BOOLEAN -> reader.nextBoolean();
                    case NULL -> reader.nextNull();
                    default -> throw new JsonParseException("Unexpected JSON token " + token);
                }
            }
        } catch (IOException failure) {
            throw new JsonParseException("Malformed config JSON", failure);
        }
    }

    @FunctionalInterface
    interface Recovery<T> {
        T recover(Exception failure);
    }
}
