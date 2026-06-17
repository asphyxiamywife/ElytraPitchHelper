package com.asphyxiamywife.elytrapitchhelper.screen;

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
        double length = Math.sqrt(x * x + y * y);
        if (length <= 0.0) {
            return new double[] { 0.0, 0.0 };
        }

        double radius = Math.max(Math.abs(x), Math.abs(y));
        return new double[] { x / length * radius, y / length * radius };
    }

    static double[] diskToSquare(double x, double y) {
        double distance = Math.sqrt(x * x + y * y);
        if (distance <= 0.0) {
            return new double[] { 0.0, 0.0 };
        }

        double unitX = x / distance;
        double unitY = y / distance;
        double maxComponent = Math.max(Math.abs(unitX), Math.abs(unitY));
        return new double[] { clamp(unitX * distance / maxComponent, -1.0, 1.0),
                clamp(unitY * distance / maxComponent, -1.0, 1.0) };
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

    static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static int parseHexColor(String value) {
        if (value.length() != 6) {
            return -1;
        }
        try {
            return Integer.parseInt(value, 16) & 0x00FFFFFF;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static int toRgbChannel(double value) {
        return clampInt((int) Math.round(value * 255.0), 0, 255);
    }
}
