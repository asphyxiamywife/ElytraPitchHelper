package com.asphyxiamywife.elytrapitchhelper.util;

public final class MathUtil {
    private MathUtil() {
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float smoothstep(float value) {
        return value * value * (3.0f - 2.0f * value);
    }

    public static int argb(float alpha, int rgb) {
        int a = (int) (clamp(alpha, 0.0f, 1.0f) * 255.0f) & 0xFF;
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    public static int blendRgb(int from, int to, float amount) {
        float t = clamp(amount, 0.0f, 1.0f);
        int fromR = (from >> 16) & 0xFF;
        int fromG = (from >> 8) & 0xFF;
        int fromB = from & 0xFF;
        int toR = (to >> 16) & 0xFF;
        int toG = (to >> 8) & 0xFF;
        int toB = to & 0xFF;
        int r = (int) (fromR + (toR - fromR) * t);
        int g = (int) (fromG + (toG - fromG) * t);
        int b = (int) (fromB + (toB - fromB) * t);
        return (r << 16) | (g << 8) | b;
    }
}
