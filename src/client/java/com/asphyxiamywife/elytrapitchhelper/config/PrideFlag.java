package com.asphyxiamywife.elytrapitchhelper.config;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum PrideFlag {
    RAINBOW("rainbow", "option.elytrapitchhelper.pride_flag.rainbow",
            0xE40303, 0xFF8C00, 0xFFED00, 0x008026, 0x24408E, 0x732982),
    PROGRESS("progress", "option.elytrapitchhelper.pride_flag.progress",
            0x000000, 0x784F17, 0xE40303, 0xFF8C00, 0xFFED00, 0x008026, 0x24408E, 0x732982, 0xFFFFFF,
            0xFFAFC8, 0x74D7EE),
    LESBIAN("lesbian", "option.elytrapitchhelper.pride_flag.lesbian",
            0xD52D00, 0xEF7627, 0xFF9A56, 0xFFFFFF, 0xD162A4, 0xB55690, 0xA30262),
    GAY_MEN("gay-men", "option.elytrapitchhelper.pride_flag.gay_men",
            0x078D70, 0x26CEAA, 0x98E8C1, 0xFFFFFF, 0x7BADE2, 0x5049CC, 0x3D1A78),
    BISEXUAL("bisexual", "option.elytrapitchhelper.pride_flag.bisexual",
            0xD60270, 0xD60270, 0x9B4F96, 0x0038A8, 0x0038A8),
    TRANS("trans", "option.elytrapitchhelper.pride_flag.trans",
            0x5BCEFA, 0xF5A9B8, 0xFFFFFF, 0xF5A9B8, 0x5BCEFA),
    NONBINARY("nonbinary", "option.elytrapitchhelper.pride_flag.nonbinary",
            0xFFF430, 0xFFFFFF, 0x9C59D1, 0x000000),
    PANSEXUAL("pansexual", "option.elytrapitchhelper.pride_flag.pansexual",
            0xFF218C, 0xFFD800, 0x21B1FF),
    POLYSEXUAL("polysexual", "option.elytrapitchhelper.pride_flag.polysexual",
            0xF51BB8, 0x08D868, 0x1A95F4),
    POLYAMORY("polyamory", "option.elytrapitchhelper.pride_flag.polyamory",
            0x0142E4, 0xFF0D01, 0x202020),
    ASEXUAL("asexual", "option.elytrapitchhelper.pride_flag.asexual",
            0x000000, 0xA3A3A3, 0xFFFFFF, 0x800080),
    GENDERFLUID("genderfluid", "option.elytrapitchhelper.pride_flag.genderfluid",
            0xFF76A4, 0xFFFFFF, 0xC011D7, 0x000000, 0x2F3CBE),
    GENDERQUEER("genderqueer", "option.elytrapitchhelper.pride_flag.genderqueer",
            0xB481DA, 0xFFFFFF, 0x498022),
    DEMIGIRL("demigirl", "option.elytrapitchhelper.pride_flag.demigirl",
            0x7F7F7F, 0xC4C4C4, 0xFFAEC9, 0xFFFFFF, 0xFFAEC9, 0xC4C4C4, 0x7F7F7F),
    DEMIBOY("demiboy", "option.elytrapitchhelper.pride_flag.demiboy",
            0x7F7F7F, 0xC4C4C4, 0x9AD9EA, 0xFFFFFF, 0x9AD9EA, 0xC4C4C4, 0x7F7F7F),
    AGENDER("agender", "option.elytrapitchhelper.pride_flag.agender",
            0x000000, 0xBCC4C7, 0xFFFFFF, 0xB7F684, 0xFFFFFF, 0xBCC4C7, 0x000000),
    ABROSEXUAL("abrosexual", "option.elytrapitchhelper.pride_flag.abrosexual",
            0x77C992, 0xB3E5C7, 0xFFFFFF, 0xE494B4, 0xD6456A),
    AROMANTIC("aromantic", "option.elytrapitchhelper.pride_flag.aromatic",
            0x41A546, 0xA9D476, 0xFFFFFF, 0xA9A9A9, 0x000000),
    GENDERFLUX("genderflux", "option.elytrapitchhelper.pride_flag.genderflux",
            0xF47694, 0xF2A2B9, 0xCECECE, 0x7CE0F7, 0x3ECDF9, 0xFFF48D),
    CUSTOM("custom", "option.elytrapitchhelper.pride_flag.custom");

    public static final int MIN_CUSTOM_COLORS = 2;
    public static final int MAX_CUSTOM_COLORS = 16;
    private static final Map<String, PrideFlag> BY_ID = createIdLookup();

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
        return BY_ID.getOrDefault(normalizeId(id), RAINBOW);
    }

    public static boolean isValidId(String id) {
        return BY_ID.containsKey(normalizeId(id));
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

    private static Map<String, PrideFlag> createIdLookup() {
        Map<String, PrideFlag> flags = new HashMap<>();
        for (PrideFlag flag : values()) {
            flags.put(flag.id, flag);
        }
        return Map.copyOf(flags);
    }
}
