package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.function.Predicate;

public final class ConfigJsonReader {
    private ConfigJsonReader() {
    }

    public static String readString(JsonObject json, String field, String fallback, RepairLog repairs) {
        JsonElement element = require(json, field, JsonPrimitive::isString, fallback, repairs);
        if (element == null) {
            return fallback;
        }
        String value = element.getAsString();
        if (value.isBlank()) {
            ConfigRepair.repair(repairs, field, value, fallback);
            return fallback;
        }
        return value;
    }

    public static boolean readBoolean(JsonObject json, String field, boolean fallback, RepairLog repairs) {
        JsonElement element = require(json, field, JsonPrimitive::isBoolean, fallback, repairs);
        return element == null ? fallback : element.getAsBoolean();
    }

    static boolean readOptionalBoolean(JsonObject json, String field, boolean fallback, RepairLog repairs) {
        return json.has(field) ? readBoolean(json, field, fallback, repairs) : fallback;
    }

    public static int readInt(JsonObject json, String field, int fallback, RepairLog repairs) {
        JsonElement element = require(json, field, JsonPrimitive::isNumber, fallback, repairs);
        if (element == null) {
            return fallback;
        }
        Integer value = parseExactInt(element);
        if (value == null) {
            ConfigRepair.repair(repairs, field, element, fallback);
            return fallback;
        }
        return value;
    }

    static int readOptionalInt(JsonObject json, String field, int fallback, RepairLog repairs) {
        return json.has(field) ? readInt(json, field, fallback, repairs) : fallback;
    }

    public static Integer parseExactInt(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        try {
            return element.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    public static float readFloat(JsonObject json, String field, float fallback, RepairLog repairs) {
        JsonElement element = require(json, field, JsonPrimitive::isNumber, fallback, repairs);
        if (element == null) {
            return fallback;
        }
        float value = element.getAsFloat();
        if (!Float.isFinite(value)) {
            ConfigRepair.repair(repairs, field, element, fallback);
            return fallback;
        }
        return value;
    }

    static float readOptionalFloat(JsonObject json, String field, float fallback, RepairLog repairs) {
        return json.has(field) ? readFloat(json, field, fallback, repairs) : fallback;
    }

    static int readLegacyInt(JsonObject json, String field, int fallback, int min, int max,
            RepairLog repairs) {
        int value = readOptionalInt(json, field, fallback, repairs);
        int repaired = MathUtil.clamp(value, min, max);
        if (repaired != value) {
            ConfigRepair.repair(repairs, field, value, repaired);
        }
        return repaired;
    }

    static float readLegacyFloat(JsonObject json, String field, float fallback, float min, float max,
            RepairLog repairs) {
        float value = readOptionalFloat(json, field, fallback, repairs);
        float repaired = Float.isFinite(value) ? MathUtil.clamp(value, min, max) : fallback;
        if (Float.compare(repaired, value) != 0) {
            ConfigRepair.repair(repairs, field, value, repaired);
        }
        return repaired;
    }

    static int readLegacyColor(JsonObject json, String field, int fallback, RepairLog repairs) {
        int value = readOptionalInt(json, field, fallback, repairs);
        int repaired = value & 0x00FFFFFF;
        if (repaired != value) {
            ConfigRepair.repair(repairs, field, value, repaired);
        }
        return repaired;
    }

    private static JsonElement require(JsonObject json, String field, Predicate<JsonPrimitive> type,
            Object fallback, RepairLog repairs) {
        JsonElement element = json.get(field);
        if (element == null || element.isJsonNull()) {
            ConfigRepair.repair(repairs, field, "missing", fallback);
            return null;
        }
        if (!element.isJsonPrimitive() || !type.test(element.getAsJsonPrimitive())) {
            ConfigRepair.repair(repairs, field, element, fallback);
            return null;
        }
        return element;
    }
}
