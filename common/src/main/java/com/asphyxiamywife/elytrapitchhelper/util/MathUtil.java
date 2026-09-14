package com.asphyxiamywife.elytrapitchhelper.util;

public final class MathUtil {
    private MathUtil() {
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float clamp01(float value) {
        return clamp(value, 0.0f, 1.0f);
    }

    public static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    public static float smoothstep(float value) {
        value = clamp01(value);
        return value * value * (3.0f - 2.0f * value);
    }

    public static int argb(float alpha, int rgb) {
        int a = (int) (clamp01(alpha) * 255.0f) & 0xFF;
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    public static int blendRgb(int from, int to, float amount) {
        float t = clamp01(amount);
        int r = blendChannel(red(from), red(to), t);
        int g = blendChannel(green(from), green(to), t);
        int b = blendChannel(blue(from), blue(to), t);
        return packRgb(r, g, b);
    }

    public static int red(int rgb) {
        return (rgb >> 16) & 0xFF;
    }

    public static int green(int rgb) {
        return (rgb >> 8) & 0xFF;
    }

    public static int blue(int rgb) {
        return rgb & 0xFF;
    }

    public static int packRgb(int red, int green, int blue) {
        return ((red & 0xFF) << 16) | ((green & 0xFF) << 8) | (blue & 0xFF);
    }

    private static int blendChannel(int from, int to, float amount) {
        return (int) (from + (to - from) * amount);
    }
}
