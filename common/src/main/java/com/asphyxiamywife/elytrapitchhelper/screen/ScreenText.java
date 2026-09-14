package com.asphyxiamywife.elytrapitchhelper.screen;

import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.Font;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.ToIntFunction;

public final class ScreenText {
    private static final String TRUNCATION_MARKER = "...";

    private ScreenText() {
    }

    static Component colorComponent(int color) {
        return Component.literal("#" + hexColor(color));
    }

    static String hexColor(int color) {
        return String.format(Locale.ROOT, "%06X", color & 0x00FFFFFF);
    }

    static String formatDecimal(double value, double step) {
        int decimals = Math.max(0, BigDecimal.valueOf(step)
                .setScale(6, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .scale());
        String formatted = String.format(Locale.ROOT, "%." + decimals + "f", value);
        if (formatted.indexOf('.') >= 0) {
            formatted = formatted.replaceFirst("0+$", "").replaceFirst("\\.$", "");
        }
        return formatted;
    }

    static String formatInteger(double value, String suffix) {
        int rounded = (int) Math.round(value);
        return suffix == null || suffix.isBlank() ? Integer.toString(rounded) : rounded + " " + suffix;
    }

    static String formatPercent(double value) {
        return formatInteger(value, "") + "%";
    }

    public static String truncate(Font font, String text, int maxWidth) {
        return truncate(text, maxWidth, font::width, font::plainSubstrByWidth);
    }

    static String truncate(String text, int maxWidth, ToIntFunction<String> width,
            BiFunction<String, Integer, String> substringByWidth) {
        if (width.applyAsInt(text) <= maxWidth) {
            return text;
        }
        int markerWidth = width.applyAsInt(TRUNCATION_MARKER);
        if (maxWidth <= markerWidth) {
            return "";
        }
        return substringByWidth.apply(text, maxWidth - markerWidth) + TRUNCATION_MARKER;
    }

    static Component relativeTime(long timestampMillis) {
        if (timestampMillis <= 0L) {
            return Component.translatable("screen.elytrapitchhelper.relative_time.unknown");
        }

        long seconds = Math.max(0L, Duration.ofMillis(System.currentTimeMillis() - timestampMillis).toSeconds());
        if (seconds < 45L) {
            return Component.translatable("screen.elytrapitchhelper.relative_time.just_now");
        }
        long minutes = seconds / 60L;
        if (minutes < 60L) {
            return Component.translatable("screen.elytrapitchhelper.relative_time.minutes", minutes);
        }
        long hours = minutes / 60L;
        if (hours < 24L) {
            return Component.translatable("screen.elytrapitchhelper.relative_time.hours", hours);
        }
        long days = hours / 24L;
        if (days < 30L) {
            return Component.translatable("screen.elytrapitchhelper.relative_time.days", days);
        }
        long months = days / 30L;
        if (months < 12L) {
            return Component.translatable("screen.elytrapitchhelper.relative_time.months", months);
        }
        return Component.translatable("screen.elytrapitchhelper.relative_time.years", months / 12L);
    }

    static Component profileBasedOn(String value) {
        if ("Custom".equals(value)) {
            return Component.translatable("screen.elytrapitchhelper.profile.based_on.custom");
        }
        if ("Bundled default".equals(value)) {
            return Component.translatable("screen.elytrapitchhelper.profile.based_on.bundled_default");
        }
        return Component.literal(value == null ? "" : value);
    }
}
