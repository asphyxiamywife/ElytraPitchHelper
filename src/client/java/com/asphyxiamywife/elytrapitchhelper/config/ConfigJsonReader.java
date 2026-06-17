package com.asphyxiamywife.elytrapitchhelper.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

final class ConfigJsonReader {
    private ConfigJsonReader() {
    }

    static String readString(JsonObject json, String field, String fallback, RepairLog repairs) {
        JsonElement element = json.get(field);
        if (element == null || element.isJsonNull()) {
            ConfigRepair.repair(repairs, field, "missing", fallback);
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            ConfigRepair.repair(repairs, field, element, fallback);
            return fallback;
        }
        String value = element.getAsString();
        if (value.isBlank()) {
            ConfigRepair.repair(repairs, field, value, fallback);
            return fallback;
        }
        return value;
    }

    static boolean readBoolean(JsonObject json, String field, boolean fallback, RepairLog repairs) {
        JsonElement element = json.get(field);
        if (element == null || element.isJsonNull()) {
            ConfigRepair.repair(repairs, field, "missing", fallback);
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            ConfigRepair.repair(repairs, field, element, fallback);
            return fallback;
        }
        return element.getAsBoolean();
    }

    static boolean readOptionalBoolean(JsonObject json, String field, boolean fallback, RepairLog repairs) {
        return json.has(field) ? readBoolean(json, field, fallback, repairs) : fallback;
    }

    static int readInt(JsonObject json, String field, int fallback, RepairLog repairs) {
        JsonElement element = json.get(field);
        if (element == null || element.isJsonNull()) {
            ConfigRepair.repair(repairs, field, "missing", fallback);
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            ConfigRepair.repair(repairs, field, element, fallback);
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (NumberFormatException e) {
            ConfigRepair.repair(repairs, field, element, fallback);
            return fallback;
        }
    }

    static int readOptionalInt(JsonObject json, String field, int fallback, RepairLog repairs) {
        return json.has(field) ? readInt(json, field, fallback, repairs) : fallback;
    }

    static float readFloat(JsonObject json, String field, float fallback, RepairLog repairs) {
        JsonElement element = json.get(field);
        if (element == null || element.isJsonNull()) {
            ConfigRepair.repair(repairs, field, "missing", fallback);
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            ConfigRepair.repair(repairs, field, element, fallback);
            return fallback;
        }
        try {
            return element.getAsFloat();
        } catch (NumberFormatException e) {
            ConfigRepair.repair(repairs, field, element, fallback);
            return fallback;
        }
    }

    static float readOptionalFloat(JsonObject json, String field, float fallback, RepairLog repairs) {
        return json.has(field) ? readFloat(json, field, fallback, repairs) : fallback;
    }

    static int readLegacyInt(JsonObject json, String field, int fallback, int min, int max,
            RepairLog repairs) {
        int value = readOptionalInt(json, field, fallback, repairs);
        int repaired = Math.max(min, Math.min(max, value));
        if (repaired != value) {
            ConfigRepair.repair(repairs, field, value, repaired);
        }
        return repaired;
    }

    static float readLegacyFloat(JsonObject json, String field, float fallback, float min, float max,
            RepairLog repairs) {
        float value = readOptionalFloat(json, field, fallback, repairs);
        float repaired = Float.isFinite(value) ? ConfigRepair.clamp(value, min, max) : fallback;
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
}
