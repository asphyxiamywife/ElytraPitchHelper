package com.asphyxiamywife.elytrapitchhelper.config;

import java.util.Arrays;
import java.util.Objects;

public record LineSettings(
        int lengthPixels,
        int widthPixels,
        int colorRgb,
        boolean prideEnabled,
        String prideFlag,
        int[] customPrideColors,
        int cueFlashIntensity) {

    public static final int MIN_CUE_FLASH_INTENSITY = 0;
    public static final int MAX_CUE_FLASH_INTENSITY = 100;

    public LineSettings() {
        this(ProfileDefaults.template().line());
    }

    private LineSettings(LineSettings defaults) {
        this(defaults.lengthPixels, defaults.widthPixels, defaults.colorRgb, defaults.prideEnabled,
                defaults.prideFlag, defaults.customPrideColors, defaults.cueFlashIntensity);
    }

    public LineSettings {
        prideFlag = prideFlag == null ? PrideFlag.defaultId() : prideFlag;
        customPrideColors = copyColors(customPrideColors);
    }

    @Override
    public int[] customPrideColors() {
        return copyColors(customPrideColors);
    }

    public float cueFlashScale() {
        return Math.clamp(cueFlashIntensity, MIN_CUE_FLASH_INTENSITY, MAX_CUE_FLASH_INTENSITY) / 100.0f;
    }

    public LineSettings withLengthPixels(int value) {
        return new LineSettings(value, widthPixels, colorRgb, prideEnabled, prideFlag, customPrideColors,
                cueFlashIntensity);
    }

    public LineSettings withWidthPixels(int value) {
        return new LineSettings(lengthPixels, value, colorRgb, prideEnabled, prideFlag, customPrideColors,
                cueFlashIntensity);
    }

    public LineSettings withColorRgb(int value) {
        return new LineSettings(lengthPixels, widthPixels, value, prideEnabled, prideFlag, customPrideColors,
                cueFlashIntensity);
    }

    public LineSettings withPride(boolean enabled, String flag, int[] colors) {
        return new LineSettings(lengthPixels, widthPixels, colorRgb, enabled, flag, colors, cueFlashIntensity);
    }

    public LineSettings withPrideEnabled(boolean value) {
        return new LineSettings(lengthPixels, widthPixels, colorRgb, value, prideFlag, customPrideColors,
                cueFlashIntensity);
    }

    public LineSettings withPrideFlag(String value) {
        return new LineSettings(lengthPixels, widthPixels, colorRgb, prideEnabled, value, customPrideColors,
                cueFlashIntensity);
    }

    public LineSettings withCustomPrideColors(int[] value) {
        return new LineSettings(lengthPixels, widthPixels, colorRgb, prideEnabled, prideFlag, value,
                cueFlashIntensity);
    }

    public LineSettings withCueFlashIntensity(int value) {
        return new LineSettings(lengthPixels, widthPixels, colorRgb, prideEnabled, prideFlag,
                customPrideColors, value);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LineSettings that
                && lengthPixels == that.lengthPixels
                && widthPixels == that.widthPixels
                && colorRgb == that.colorRgb
                && prideEnabled == that.prideEnabled
                && cueFlashIntensity == that.cueFlashIntensity
                && Objects.equals(prideFlag, that.prideFlag)
                && Arrays.equals(customPrideColors, that.customPrideColors);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(lengthPixels, widthPixels, colorRgb, prideEnabled, prideFlag,
                cueFlashIntensity);
        return 31 * result + Arrays.hashCode(customPrideColors);
    }

    private static int[] copyColors(int[] colors) {
        int[] source = colors == null ? PrideFlag.defaultCustomColors() : colors;
        return Arrays.copyOf(source, source.length);
    }
}
