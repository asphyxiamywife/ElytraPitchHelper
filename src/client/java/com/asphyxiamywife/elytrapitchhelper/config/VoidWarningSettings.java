package com.asphyxiamywife.elytrapitchhelper.config;

import static com.asphyxiamywife.elytrapitchhelper.config.ConfigRepair.repairColor;
import static com.asphyxiamywife.elytrapitchhelper.config.ConfigRepair.repairCustomPrideColors;
import static com.asphyxiamywife.elytrapitchhelper.config.ConfigRepair.repairFloat;
import static com.asphyxiamywife.elytrapitchhelper.config.ConfigRepair.repairInt;
import static com.asphyxiamywife.elytrapitchhelper.config.ConfigRepair.repairPrideFlag;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class VoidWarningSettings {
    public static final int MODE_PREDICTED_TIME = 0;
    public static final int MODE_SIMPLE_HEIGHT = 1;
    public static final int MIN_VOID_Y = -256;
    public static final int MAX_VOID_Y = 320;

    public boolean enabled = true;
    public int mode = MODE_PREDICTED_TIME;
    public float lookaheadSeconds = 5.0f;
    public int simpleWarningBlocks = 48;
    public int warningColorRgb = 0xFF4400;
    public boolean warningPrideEnabled = false;
    public String warningPrideFlag = PrideFlag.defaultId();
    public int[] customPrideColors = PrideFlag.defaultCustomColors();
    public boolean toleranceOverride = true;
    public float customToleranceDegrees = 6.0f;
    public boolean maxOffsetOverride = true;
    public int customMaxOffsetPixels = 42;
    public Map<String, Integer> dimensionYOverrides = new HashMap<>();

    public static int nextMode(int mode) {
        return mode >= MODE_SIMPLE_HEIGHT ? MODE_PREDICTED_TIME : mode + 1;
    }

    public static int previousMode(int mode) {
        return mode <= MODE_PREDICTED_TIME ? MODE_SIMPLE_HEIGHT : mode - 1;
    }

    public VoidWarningSettings copy() {
        VoidWarningSettings s = new VoidWarningSettings();
        s.copyFrom(this);
        return s;
    }

    public void copyFrom(VoidWarningSettings other) {
        if (other == null) return;
        enabled = other.enabled;
        mode = other.mode;
        lookaheadSeconds = other.lookaheadSeconds;
        simpleWarningBlocks = other.simpleWarningBlocks;
        warningColorRgb = other.warningColorRgb;
        warningPrideEnabled = other.warningPrideEnabled;
        warningPrideFlag = other.warningPrideFlag;
        customPrideColors = other.customPrideColors == null
                ? PrideFlag.defaultCustomColors()
                : Arrays.copyOf(other.customPrideColors, other.customPrideColors.length);
        toleranceOverride = other.toleranceOverride;
        customToleranceDegrees = other.customToleranceDegrees;
        maxOffsetOverride = other.maxOffsetOverride;
        customMaxOffsetPixels = other.customMaxOffsetPixels;
        dimensionYOverrides = other.dimensionYOverrides == null
                ? new HashMap<>() : new HashMap<>(other.dimensionYOverrides);
    }

    void sanitize(RepairLog repairs, VoidWarningSettings defaults) {
        if (defaults == null) defaults = new VoidWarningSettings();
        mode = repairInt(repairs, "voidWarning.mode", mode, defaults.mode,
                MODE_PREDICTED_TIME, MODE_SIMPLE_HEIGHT);
        lookaheadSeconds = repairFloat(repairs, "voidWarning.lookaheadSeconds",
                lookaheadSeconds, defaults.lookaheadSeconds, 1.0f, 15.0f);
        simpleWarningBlocks = repairInt(repairs, "voidWarning.simpleWarningBlocks",
                simpleWarningBlocks, defaults.simpleWarningBlocks, 1, 512);
        warningColorRgb = repairColor(repairs, "voidWarning.warningColorRgb",
                warningColorRgb, defaults.warningColorRgb);
        warningPrideFlag = repairPrideFlag(repairs, "voidWarning.warningPrideFlag",
                warningPrideFlag, defaults.warningPrideFlag);
        customPrideColors = repairCustomPrideColors(repairs, "voidWarning.customPrideColors",
                customPrideColors, defaults.customPrideColors);
        customToleranceDegrees = repairFloat(repairs, "voidWarning.customToleranceDegrees",
                customToleranceDegrees, defaults.customToleranceDegrees, 1.0f, 45.0f);
        customMaxOffsetPixels = repairInt(repairs, "voidWarning.customMaxOffsetPixels",
                customMaxOffsetPixels, defaults.customMaxOffsetPixels, 0, 200);
        if (dimensionYOverrides == null) dimensionYOverrides = new HashMap<>();
        dimensionYOverrides.replaceAll((dimension, value) -> repairInt(repairs,
                "voidWarning.dimensionYOverrides." + dimension, value, 0, MIN_VOID_Y, MAX_VOID_Y));
    }
}
