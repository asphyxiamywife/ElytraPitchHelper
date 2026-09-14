package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;

import java.util.EnumMap;
import java.util.Map;

final class SectionAnimation {
    static final long MIN_DURATION_MILLIS = 150L;
    static final long MAX_DURATION_MILLIS = 260L;
    private static final long BASE_MILLIS = 70L;
    private static final long PER_ROW_MILLIS = 14L;
    private static final float FADE_START = 0.35f;

    private final Map<ConfigCategory, Float> progress = new EnumMap<>(ConfigCategory.class);
    private final Map<ConfigCategory, Boolean> targets = new EnumMap<>(ConfigCategory.class);
    private final Map<ConfigCategory, Long> durations = new EnumMap<>(ConfigCategory.class);
    private long lastMillis;
    private boolean started;

    void settle(ConfigCategory section, boolean expanded) {
        progress.put(section, expanded ? 1.0f : 0.0f);
        targets.put(section, expanded);
    }

    static long durationFor(int childRows) {
        long scaled = BASE_MILLIS + PER_ROW_MILLIS * Math.max(0, childRows);
        return Math.max(MIN_DURATION_MILLIS, Math.min(MAX_DURATION_MILLIS, scaled));
    }

    void setExpanded(ConfigCategory section, boolean expanded, int childRows) {
        targets.put(section, expanded);
        durations.put(section, durationFor(childRows));
        progress.putIfAbsent(section, expanded ? 0.0f : 1.0f);
    }

    boolean animating(ConfigCategory section) {
        return tracked(section) && progress(section) != (targets.getOrDefault(section, false) ? 1.0f : 0.0f);
    }

    private boolean tracked(ConfigCategory section) {
        return progress.containsKey(section);
    }

    void syncTo(ConfigCategory section, boolean expanded) {
        if (!animating(section)) {
            settle(section, expanded);
        }
    }

    float progress(ConfigCategory section) {
        return progress.getOrDefault(section, 0.0f);
    }

    boolean showsChildren(ConfigCategory section) {
        return progress(section) > 0.0f;
    }

    boolean animating() {
        for (ConfigCategory section : ConfigCategory.values()) {
            if (progress(section) != (targets.getOrDefault(section, false) ? 1.0f : 0.0f)) {
                return true;
            }
        }
        return false;
    }

    boolean advance(long nowMillis) {
        long elapsed = started ? Math.max(0L, nowMillis - lastMillis) : 0L;
        lastMillis = nowMillis;
        started = true;
        if (elapsed == 0L) {
            return false;
        }
        boolean moved = false;
        for (ConfigCategory section : ConfigCategory.values()) {
            float current = progress(section);
            float target = targets.getOrDefault(section, false) ? 1.0f : 0.0f;
            if (current == target) {
                continue;
            }
            float step = (float) elapsed / durations.getOrDefault(section, MAX_DURATION_MILLIS);
            float next = current < target
                    ? Math.min(target, current + step)
                    : Math.max(target, current - step);
            progress.put(section, next);
            moved = true;
        }
        return moved;
    }

    static float heightFactor(float progress) {
        return smoothstep(MathUtil.clamp01(progress));
    }

    static float rowAlpha(float progress) {
        float eased = smoothstep(MathUtil.clamp01(progress));
        if (eased <= FADE_START) {
            return 0.0f;
        }
        return (eased - FADE_START) / (1.0f - FADE_START);
    }

    private static float smoothstep(float value) {
        return value * value * (3.0f - 2.0f * value);
    }
}
