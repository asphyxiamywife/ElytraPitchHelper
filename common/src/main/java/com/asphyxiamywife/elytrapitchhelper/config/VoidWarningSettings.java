package com.asphyxiamywife.elytrapitchhelper.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public record VoidWarningSettings(
        boolean enabled,
        int mode,
        float lookaheadSeconds,
        int simpleWarningBlocks,
        int warningColorRgb,
        boolean warningPrideEnabled,
        String warningPrideFlag,
        int[] customPrideColors,
        boolean toleranceOverride,
        float customToleranceDegrees,
        boolean maxOffsetOverride,
        int customMaxOffsetPixels,
        Map<String, Integer> dimensionYOverrides) {

    public static final int MODE_PREDICTED_TIME = 0;
    public static final int MODE_SIMPLE_HEIGHT = 1;
    public static final int MIN_VOID_Y = -256;
    public static final int MAX_VOID_Y = 320;
    public static final float MIN_CUSTOM_TOLERANCE_DEGREES = 1.0f;
    public static final float MAX_CUSTOM_TOLERANCE_DEGREES = 180.0f;
    private static final Pattern DIMENSION_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    public VoidWarningSettings() {
        this(ProfileDefaults.template().voidWarning());
    }

    private VoidWarningSettings(VoidWarningSettings defaults) {
        this(defaults.enabled, defaults.mode, defaults.lookaheadSeconds, defaults.simpleWarningBlocks,
                defaults.warningColorRgb, defaults.warningPrideEnabled, defaults.warningPrideFlag,
                defaults.customPrideColors, defaults.toleranceOverride, defaults.customToleranceDegrees,
                defaults.maxOffsetOverride, defaults.customMaxOffsetPixels, defaults.dimensionYOverrides);
    }

    public VoidWarningSettings {
        warningPrideFlag = warningPrideFlag == null ? PrideFlag.defaultId() : warningPrideFlag;
        customPrideColors = copyColors(customPrideColors);
        dimensionYOverrides = dimensionYOverrides == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(dimensionYOverrides));
    }

    @Override
    public int[] customPrideColors() {
        return copyColors(customPrideColors);
    }

    public static int nextMode(int mode) {
        return mode >= MODE_SIMPLE_HEIGHT ? MODE_PREDICTED_TIME : mode + 1;
    }

    public static int previousMode(int mode) {
        return mode <= MODE_PREDICTED_TIME ? MODE_SIMPLE_HEIGHT : mode - 1;
    }

    public VoidWarningSettings withEnabled(boolean value) {
        return edit(builder -> builder.enabled = value);
    }

    public VoidWarningSettings withMode(int value) {
        return edit(builder -> builder.mode = value);
    }

    public VoidWarningSettings withLookaheadSeconds(float value) {
        return edit(builder -> builder.lookaheadSeconds = value);
    }

    public VoidWarningSettings withSimpleWarningBlocks(int value) {
        return edit(builder -> builder.simpleWarningBlocks = value);
    }

    public VoidWarningSettings withWarningColorRgb(int value) {
        return edit(builder -> builder.warningColorRgb = value);
    }

    public VoidWarningSettings withWarningPrideEnabled(boolean value) {
        return edit(builder -> builder.warningPrideEnabled = value);
    }

    public VoidWarningSettings withWarningPrideFlag(String value) {
        return edit(builder -> builder.warningPrideFlag = value);
    }

    public VoidWarningSettings withCustomPrideColors(int[] value) {
        return edit(builder -> builder.customPrideColors = value);
    }

    public VoidWarningSettings withToleranceOverride(boolean value) {
        return edit(builder -> builder.toleranceOverride = value);
    }

    public VoidWarningSettings withCustomToleranceDegrees(float value) {
        return edit(builder -> builder.customToleranceDegrees = value);
    }

    public VoidWarningSettings withMaxOffsetOverride(boolean value) {
        return edit(builder -> builder.maxOffsetOverride = value);
    }

    public VoidWarningSettings withCustomMaxOffsetPixels(int value) {
        return edit(builder -> builder.customMaxOffsetPixels = value);
    }

    public VoidWarningSettings withDimensionYOverrides(Map<String, Integer> value) {
        return edit(builder -> builder.dimensionYOverrides = value);
    }

    public VoidWarningSettings withWarningPride(boolean enabledValue, String flag, int[] colors) {
        return edit(builder -> {
            builder.warningPrideEnabled = enabledValue;
            builder.warningPrideFlag = flag;
            builder.customPrideColors = colors;
        });
    }

    public static boolean isValidDimensionId(String dimension) {
        return dimension != null && DIMENSION_ID.matcher(dimension).matches();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof VoidWarningSettings that
                && enabled == that.enabled
                && mode == that.mode
                && Float.compare(lookaheadSeconds, that.lookaheadSeconds) == 0
                && simpleWarningBlocks == that.simpleWarningBlocks
                && warningColorRgb == that.warningColorRgb
                && warningPrideEnabled == that.warningPrideEnabled
                && Objects.equals(warningPrideFlag, that.warningPrideFlag)
                && Arrays.equals(customPrideColors, that.customPrideColors)
                && toleranceOverride == that.toleranceOverride
                && Float.compare(customToleranceDegrees, that.customToleranceDegrees) == 0
                && maxOffsetOverride == that.maxOffsetOverride
                && customMaxOffsetPixels == that.customMaxOffsetPixels
                && Objects.equals(dimensionYOverrides, that.dimensionYOverrides);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(enabled, mode, lookaheadSeconds, simpleWarningBlocks, warningColorRgb,
                warningPrideEnabled, warningPrideFlag, toleranceOverride, customToleranceDegrees,
                maxOffsetOverride, customMaxOffsetPixels, dimensionYOverrides);
        return 31 * result + Arrays.hashCode(customPrideColors);
    }

    private VoidWarningSettings edit(Consumer<Builder> mutation) {
        Builder builder = new Builder(this);
        mutation.accept(builder);
        return builder.build();
    }

    private static final class Builder {
        private boolean enabled;
        private int mode;
        private float lookaheadSeconds;
        private int simpleWarningBlocks;
        private int warningColorRgb;
        private boolean warningPrideEnabled;
        private String warningPrideFlag;
        private int[] customPrideColors;
        private boolean toleranceOverride;
        private float customToleranceDegrees;
        private boolean maxOffsetOverride;
        private int customMaxOffsetPixels;
        private Map<String, Integer> dimensionYOverrides;

        private Builder(VoidWarningSettings from) {
            enabled = from.enabled;
            mode = from.mode;
            lookaheadSeconds = from.lookaheadSeconds;
            simpleWarningBlocks = from.simpleWarningBlocks;
            warningColorRgb = from.warningColorRgb;
            warningPrideEnabled = from.warningPrideEnabled;
            warningPrideFlag = from.warningPrideFlag;
            customPrideColors = from.customPrideColors;
            toleranceOverride = from.toleranceOverride;
            customToleranceDegrees = from.customToleranceDegrees;
            maxOffsetOverride = from.maxOffsetOverride;
            customMaxOffsetPixels = from.customMaxOffsetPixels;
            dimensionYOverrides = from.dimensionYOverrides;
        }

        private VoidWarningSettings build() {
            return new VoidWarningSettings(
                    enabled, mode, lookaheadSeconds,
                    simpleWarningBlocks, warningColorRgb, warningPrideEnabled,
                    warningPrideFlag, customPrideColors, toleranceOverride,
                    customToleranceDegrees, maxOffsetOverride, customMaxOffsetPixels,
                    dimensionYOverrides);
        }
    }

    private static int[] copyColors(int[] colors) {
        int[] source = colors == null ? PrideFlag.defaultCustomColors() : colors;
        return Arrays.copyOf(source, source.length);
    }
}
