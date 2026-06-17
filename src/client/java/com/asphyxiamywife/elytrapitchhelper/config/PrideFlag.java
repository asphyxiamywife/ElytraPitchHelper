package com.asphyxiamywife.elytrapitchhelper.config;

import java.util.Arrays;
import java.util.Locale;

public enum PrideFlag {
    RAINBOW("rainbow", "option.elytrapitchhelper.pride_flag.rainbow",
            0xE40303, 0xFF8C00, 0xFFED00, 0x008026, 0x24408E, 0x732982),
    PROGRESS("progress", "option.elytrapitchhelper.pride_flag.progress",
            0x000000, 0x784F17, 0xE40303, 0xFF8C00, 0xFFED00, 0x008026, 0x24408E, 0x732982, 0xFFFFFF,
            0xFFAFC8, 0x74D7EE),
    TRANS("trans", "option.elytrapitchhelper.pride_flag.trans",
            0x5BCEFA, 0xF5A9B8, 0xFFFFFF, 0xF5A9B8, 0x5BCEFA),
    BISEXUAL("bisexual", "option.elytrapitchhelper.pride_flag.bisexual",
            0xD60270, 0xD60270, 0x9B4F96, 0x0038A8, 0x0038A8),
    PANSEXUAL("pansexual", "option.elytrapitchhelper.pride_flag.pansexual",
            0xFF218C, 0xFFD800, 0x21B1FF),
    ASEXUAL("asexual", "option.elytrapitchhelper.pride_flag.asexual",
            0x000000, 0xA3A3A3, 0xFFFFFF, 0x800080),
    NONBINARY("nonbinary", "option.elytrapitchhelper.pride_flag.nonbinary",
            0xFFF430, 0xFFFFFF, 0x9C59D1, 0x000000),
    LESBIAN("lesbian", "option.elytrapitchhelper.pride_flag.lesbian",
            0xD52D00, 0xEF7627, 0xFF9A56, 0xFFFFFF, 0xD162A4, 0xB55690, 0xA30262),
    GAY_MEN("gay-men", "option.elytrapitchhelper.pride_flag.gay_men",
            0x078D70, 0x26CEAA, 0x98E8C1, 0xFFFFFF, 0x7BADE2, 0x5049CC, 0x3D1A78),
    GENDERFLUID("genderfluid", "option.elytrapitchhelper.pride_flag.genderfluid",
            0xFF76A4, 0xFFFFFF, 0xC011D7, 0x000000, 0x2F3CBE),
    AGENDER("agender", "option.elytrapitchhelper.pride_flag.agender",
            0x000000, 0xBCC4C7, 0xFFFFFF, 0xB7F684, 0xFFFFFF, 0xBCC4C7, 0x000000),
    CUSTOM("custom", "option.elytrapitchhelper.pride_flag.custom");

    public static final int MIN_CUSTOM_COLORS = 2;
    public static final int MAX_CUSTOM_COLORS = 16;

    private final String id;
    private final String translationKey;
    private final int[] colors;

    PrideFlag(String id, String translationKey, int... colors) {
        this.id = id;
        this.translationKey = translationKey;
        this.colors = colors;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return translationKey;
    }

    public int[] colors() {
        return colors;
    }

    public static String defaultId() {
        return RAINBOW.id;
    }

    public static PrideFlag byId(String id) {
        String normalized = normalizeId(id);
        for (PrideFlag flag : values()) {
            if (flag.id.equals(normalized)) {
                return flag;
            }
        }
        return RAINBOW;
    }

    public static boolean isValidId(String id) {
        String normalized = normalizeId(id);
        for (PrideFlag flag : values()) {
            if (flag.id.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    public static String sanitizeId(String id) {
        return byId(id).id;
    }

    public static boolean isCustomId(String id) {
        return CUSTOM.id.equals(sanitizeId(id));
    }

    public static int[] defaultCustomColors() {
        return Arrays.copyOf(RAINBOW.colors, RAINBOW.colors.length);
    }

    public static int[] sanitizeCustomColors(int[] colors) {
        if (colors == null || colors.length < MIN_CUSTOM_COLORS) {
            return defaultCustomColors();
        }

        int length = Math.min(MAX_CUSTOM_COLORS, colors.length);
        int[] sanitized = new int[length];
        for (int i = 0; i < length; i++) {
            sanitized[i] = colors[i] & 0x00FFFFFF;
        }
        return sanitized;
    }

    public static int[] colorsFor(String id, int[] customColors) {
        PrideFlag flag = byId(id);
        if (flag == CUSTOM) {
            return sanitizeCustomColors(customColors);
        }
        return flag.colors();
    }

    public static PrideFlag next(String id) {
        PrideFlag current = byId(id);
        PrideFlag[] flags = values();
        return flags[(current.ordinal() + 1) % flags.length];
    }

    public static int colorAt(int[] colors, double position) {
        if (colors == null || colors.length == 0) {
            return 0xFFFFFF;
        }
        int index = (int) Math.floor(Math.max(0.0, Math.min(0.999999, position)) * colors.length);
        return colors[Math.max(0, Math.min(colors.length - 1, index))] & 0x00FFFFFF;
    }

    private static String normalizeId(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
