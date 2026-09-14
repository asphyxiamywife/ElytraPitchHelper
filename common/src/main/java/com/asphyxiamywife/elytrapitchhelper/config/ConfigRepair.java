package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;

public final class ConfigRepair {
    private ConfigRepair() {
    }

    public static int repairInt(RepairLog repairs, String field, int value, int fallback, int min, int max) {
        int repaired = value < min || value > max ? MathUtil.clamp(fallback, min, max) : value;
        if (repaired != value) {
            repair(repairs, field, value, repaired);
        }
        return repaired;
    }

    public static float repairFloat(RepairLog repairs, String field, float value, float fallback, float min,
            float max) {
        float repaired = value;
        if (!Float.isFinite(repaired) || repaired < min || repaired > max) {
            repaired = MathUtil.clamp(fallback, min, max);
        }
        if (Float.compare(repaired, value) != 0) {
            repair(repairs, field, value, repaired);
        }
        return repaired;
    }

    public static int repairColor(RepairLog repairs, String field, int value, int fallback) {
        int repaired = value < 0 || value > 0x00FFFFFF ? fallback & 0x00FFFFFF : value;
        if (repaired != value) {
            repair(repairs, field, value, repaired);
        }
        return repaired;
    }

    public static String repairPrideFlag(RepairLog repairs, String field, String value, String fallback) {
        String repaired = PrideFlag.isValidId(value) ? PrideFlag.sanitizeId(value) : PrideFlag.sanitizeId(fallback);
        if (value == null || !repaired.equals(value)) {
            repair(repairs, field, value, repaired);
        }
        return repaired;
    }

    public static int[] repairCustomPrideColors(RepairLog repairs, String field, int[] value, int[] fallback) {
        int[] source = value == null || value.length < PrideFlag.MIN_CUSTOM_COLORS ? fallback : value;
        int[] repaired = PrideFlag.sanitizeCustomColors(source);
        if (value != null && !java.util.Arrays.equals(value, repaired)) {
            repair(repairs, field, value == null ? null : java.util.Arrays.toString(value),
                    java.util.Arrays.toString(repaired));
        }
        return repaired;
    }

    public static void repair(RepairLog repairs, String field, Object oldValue, Object newValue) {
        if (repairs != null) {
            repairs.add(field + ": " + oldValue + " -> " + newValue);
        }
    }
}
