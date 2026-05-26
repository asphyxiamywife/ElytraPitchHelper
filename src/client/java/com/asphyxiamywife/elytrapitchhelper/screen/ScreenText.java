package com.asphyxiamywife.elytrapitchhelper.screen;

import net.minecraft.network.chat.Component;

import java.time.Duration;
import java.util.Locale;

final class ScreenText {
    private ScreenText() {
    }

    static Component colorComponent(int color) {
        return Component.literal("#" + hexColor(color));
    }

    static String hexColor(int color) {
        return String.format(Locale.ROOT, "%06X", color & 0x00FFFFFF);
    }

    static String formatDecimal(double value) {
        return String.format(Locale.ROOT, value == Math.rint(value) ? "%.0f" : "%.1f", value);
    }

    static String relativeTime(long timestampMillis) {
        if (timestampMillis <= 0L) {
            return "unknown";
        }

        long seconds = Math.max(0L, Duration.ofMillis(System.currentTimeMillis() - timestampMillis).toSeconds());
        if (seconds < 45L) {
            return "just now";
        }
        long minutes = seconds / 60L;
        if (minutes < 60L) {
            return minutes + "m ago";
        }
        long hours = minutes / 60L;
        if (hours < 24L) {
            return hours + "h ago";
        }
        long days = hours / 24L;
        if (days < 30L) {
            return days + "d ago";
        }
        long months = days / 30L;
        if (months < 12L) {
            return months + "mo ago";
        }
        return (months / 12L) + "y ago";
    }
}
