package com.asphyxiamywife.elytrapitchhelper.screen;

import static com.asphyxiamywife.elytrapitchhelper.util.MathUtil.clamp;
import static com.asphyxiamywife.elytrapitchhelper.util.MathUtil.clamp01;

final class ColorMath {
    static final double TWO_PI = Math.PI * 2.0;

    private ColorMath() {
    }

    static double hueFromVector(double dx, double dy) {
        if (dx == 0.0 && dy == 0.0) {
            return 0.0;
        }
        double hue = 1.0 - Math.atan2(dy, dx) / TWO_PI;
        return hue - Math.floor(hue);
    }

    static double[] squareToDisk(double x, double y) {
        x = clamp(x, -1.0, 1.0);
        y = clamp(y, -1.0, 1.0);
        return new double[] { x * Math.sqrt(1.0 - y * y * 0.5),
                y * Math.sqrt(1.0 - x * x * 0.5) };
    }

    static double[] diskToSquare(double x, double y) {
        double distance = Math.sqrt(x * x + y * y);
        if (distance > 1.0) {
            x /= distance;
            y /= distance;
        }

        double rootTwo = Math.sqrt(2.0);
        double squareX = 0.5 * (safeSqrt(2.0 + x * x - y * y + 2.0 * rootTwo * x)
                - safeSqrt(2.0 + x * x - y * y - 2.0 * rootTwo * x));
        double squareY = 0.5 * (safeSqrt(2.0 - x * x + y * y + 2.0 * rootTwo * y)
                - safeSqrt(2.0 - x * x + y * y - 2.0 * rootTwo * y));
        return new double[] { clamp(squareX, -1.0, 1.0), clamp(squareY, -1.0, 1.0) };
    }

    static double[] rgbToHsv(int red, int green, int blue) {
        double r = red / 255.0;
        double g = green / 255.0;
        double b = blue / 255.0;
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double delta = max - min;
        double hue = 0.0;

        if (delta > 0.0) {
            if (max == r) {
                hue = ((g - b) / delta) % 6.0;
            } else if (max == g) {
                hue = (b - r) / delta + 2.0;
            } else {
                hue = (r - g) / delta + 4.0;
            }
            hue /= 6.0;
            if (hue < 0.0) {
                hue += 1.0;
            }
        }

        double saturation = max <= 0.0 ? 0.0 : delta / max;
        return new double[] { hue, saturation, max };
    }

    static int hsvToRgb(double hue, double saturation, double value) {
        hue = hue - Math.floor(hue);
        saturation = clamp01(saturation);
        value = clamp01(value);

        double sector = hue * 6.0;
        int index = (int) Math.floor(sector);
        double fraction = sector - index;
        double p = value * (1.0 - saturation);
        double q = value * (1.0 - fraction * saturation);
        double t = value * (1.0 - (1.0 - fraction) * saturation);
        double r;
        double g;
        double b;

        switch (index % 6) {
            case 0 -> {
                r = value;
                g = t;
                b = p;
            }
            case 1 -> {
                r = q;
                g = value;
                b = p;
            }
            case 2 -> {
                r = p;
                g = value;
                b = t;
            }
            case 3 -> {
                r = p;
                g = q;
                b = value;
            }
            case 4 -> {
                r = t;
                g = p;
                b = value;
            }
            default -> {
                r = value;
                g = p;
                b = q;
            }
        }

        return (toRgbChannel(r) << 16) | (toRgbChannel(g) << 8) | toRgbChannel(b);
    }

    static int parseHexColor(String value) {
        String digits = stripHash(value);
        if (digits.length() != 6) {
            return -1;
        }
        int color = 0;
        for (int i = 0; i < 6; i++) {
            int digit = Character.digit(digits.charAt(i), 16);
            if (digit < 0) {
                return -1;
            }
            color = (color << 4) | digit;
        }
        return color;
    }

    static String stripHash(String value) {
        return value.startsWith("#") ? value.substring(1) : value;
    }

    private static int toRgbChannel(double value) {
        return clamp((int) Math.round(value * 255.0), 0, 255);
    }

    private static double safeSqrt(double value) {
        return Math.sqrt(Math.max(0.0, value));
    }
}
