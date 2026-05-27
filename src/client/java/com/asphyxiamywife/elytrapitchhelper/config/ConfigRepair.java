package com.asphyxiamywife.elytrapitchhelper.config;

final class ConfigRepair {
    private ConfigRepair() {
    }

    static int repairInt(Config.RepairLog repairs, String field, int value, int fallback, int min, int max) {
        int repaired = value < min || value > max ? Math.max(min, Math.min(max, fallback)) : value;
        if (repaired != value) {
            repair(repairs, field, value, repaired);
        }
        return repaired;
    }

    static float repairFloat(Config.RepairLog repairs, String field, float value, float fallback, float min, float max) {
        float repaired = value;
        if (!Float.isFinite(repaired) || repaired < min || repaired > max) {
            repaired = clamp(fallback, min, max);
        }
        if (Float.compare(repaired, value) != 0) {
            repair(repairs, field, value, repaired);
        }
        return repaired;
    }

    static int repairColor(Config.RepairLog repairs, String field, int value, int fallback) {
        int repaired = value < 0 || value > 0x00FFFFFF ? fallback & 0x00FFFFFF : value;
        if (repaired != value) {
            repair(repairs, field, value, repaired);
        }
        return repaired;
    }

    static String repairPrideFlag(Config.RepairLog repairs, String field, String value, String fallback) {
        String repaired = PrideFlag.isValidId(value) ? PrideFlag.sanitizeId(value) : PrideFlag.sanitizeId(fallback);
        if (value == null || !repaired.equals(value)) {
            repair(repairs, field, value, repaired);
        }
        return repaired;
    }

    static void repair(Config.RepairLog repairs, String field, Object oldValue, Object newValue) {
        if (repairs != null) {
            repairs.add(field + ": " + oldValue + " -> " + newValue);
        }
    }

    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
