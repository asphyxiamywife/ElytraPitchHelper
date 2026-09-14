package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingSpec;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingsRegistry;
import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

public record CommandPaletteUsageSettings(Map<String, ActionUsage> actions) {
    private static final int MAX_TRACKED_ACTIONS = 64;
    private static final int MAX_ACTION_ID_LENGTH = 160;
    private static final int MAX_USE_COUNT = 1_000;
    private static final double MAX_DECAYED_COUNT = 20.0;
    private static final double EDIT_INCREMENT = 0.65;
    private static final long DECAY_HALF_LIFE_MILLIS = 7L * 24L * 60L * 60L * 1_000L;

    public CommandPaletteUsageSettings() {
        this(Map.of());
    }

    public CommandPaletteUsageSettings {
        actions = actions == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(actions));
    }

    public CommandPaletteUsageSettings recordUse(String actionId, long nowMillis) {
        return record(actionId, nowMillis, 1.0);
    }

    public CommandPaletteUsageSettings recordEdit(String actionId, long nowMillis) {
        return record(actionId, nowMillis, EDIT_INCREMENT);
    }

    private CommandPaletteUsageSettings record(String actionId, long nowMillis, double increment) {
        if (!validActionId(actionId)) {
            return this;
        }
        LinkedHashMap<String, ActionUsage> updated = new LinkedHashMap<>(actions);
        ActionUsage usage = updated.getOrDefault(actionId, new ActionUsage());
        double currentDecayedCount = decayedCount(usage, nowMillis);
        updated.put(actionId, new ActionUsage(
                Math.min(MAX_USE_COUNT, Math.max(0, usage.count()) + 1),
                Math.min(MAX_DECAYED_COUNT, currentDecayedCount + Math.max(0.0, increment)),
                Math.max(0L, nowMillis)));
        return new CommandPaletteUsageSettings(updated).sanitized(null);
    }

    public int rankingBoost(String actionId, long nowMillis) {
        if (!validActionId(actionId)) {
            return 0;
        }
        ActionUsage usage = actions.get(actionId);
        if (usage == null) {
            return 0;
        }
        double decayedCount = decayedCount(usage, nowMillis);
        if (decayedCount <= 0.05) {
            return 0;
        }
        int frequencyBoost = Math.min(140, (int) Math.round(decayedCount * 35.0));
        int recencyBoost = recencyBoost(Math.max(0L, nowMillis - Math.max(0L, usage.lastUsedAtMillis())));
        return frequencyBoost + recencyBoost;
    }

    CommandPaletteUsageSettings sanitized(RepairLog repairs) {
        LinkedHashMap<String, ActionUsage> merged = new LinkedHashMap<>();
        actions.forEach((id, usage) -> {
            if (validActionId(id)) {
                merged.merge(canonicalActionId(id), sanitizeUsage(usage),
                        CommandPaletteUsageSettings::mergeUsage);
            }
        });
        LinkedHashMap<String, ActionUsage> sanitized = new LinkedHashMap<>();
        merged.entrySet().stream()
                .filter(entry -> entry.getValue().count() > 0 || entry.getValue().decayedCount() > 0.0)
                .sorted(Comparator
                        .<Map.Entry<String, ActionUsage>>comparingLong(
                                entry -> entry.getValue().lastUsedAtMillis())
                        .reversed()
                        .thenComparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER))
                .limit(MAX_TRACKED_ACTIONS)
                .forEach(entry -> sanitized.put(entry.getKey(), entry.getValue()));
        if (!sanitized.equals(actions)) {
            ConfigRepair.repair(repairs, "commandPaletteUsage.actions", actions.keySet(), sanitized.keySet());
        }
        return sanitized.equals(actions) ? this : new CommandPaletteUsageSettings(sanitized);
    }

    private static String canonicalActionId(String actionId) {
        String legacyMarker = ":palette/settings/toggle_";
        int marker = actionId.indexOf(legacyMarker);
        if (marker < 0) {
            return actionId;
        }
        String settingId = actionId.substring(marker + legacyMarker.length());
        SettingSpec<?> spec = SettingsRegistry.byId(settingId);
        boolean toggleable = spec != null && (spec.control() instanceof SettingSpec.Toggle
                || spec.control() instanceof SettingSpec.Cycle cycle && cycle.paletteToggle());
        return toggleable
                ? actionId.substring(0, marker) + ":palette/settings/" + settingId
                : actionId;
    }

    private static ActionUsage mergeUsage(ActionUsage first, ActionUsage second) {
        return new ActionUsage(
                Math.min(MAX_USE_COUNT, first.count() + second.count()),
                Math.min(MAX_DECAYED_COUNT, first.decayedCount() + second.decayedCount()),
                Math.max(first.lastUsedAtMillis(), second.lastUsedAtMillis()));
    }

    private static ActionUsage sanitizeUsage(ActionUsage usage) {
        ActionUsage source = usage == null ? new ActionUsage() : usage;
        int count = MathUtil.clamp(source.count(), 0, MAX_USE_COUNT);
        double decayedCount = source.decayedCount();
        if (!Double.isFinite(decayedCount) || decayedCount <= 0.0) {
            decayedCount = count;
        }
        return new ActionUsage(count, MathUtil.clamp(decayedCount, 0.0, MAX_DECAYED_COUNT),
                Math.max(0L, source.lastUsedAtMillis()));
    }

    private static double decayedCount(ActionUsage usage, long nowMillis) {
        ActionUsage sanitized = sanitizeUsage(usage);
        long ageMillis = Math.max(0L, Math.max(0L, nowMillis) - sanitized.lastUsedAtMillis());
        if (ageMillis == 0L) {
            return sanitized.decayedCount();
        }
        double halfLives = (double) ageMillis / DECAY_HALF_LIFE_MILLIS;
        return sanitized.decayedCount() * Math.pow(0.5, halfLives);
    }

    private static int recencyBoost(long ageMillis) {
        long minute = 60_000L;
        if (ageMillis <= 10L * minute) return 50;
        if (ageMillis <= 60L * minute) return 35;
        if (ageMillis <= 24L * 60L * minute) return 20;
        if (ageMillis <= 7L * 24L * 60L * minute) return 10;
        return 0;
    }

    private static boolean validActionId(String actionId) {
        if (actionId == null || actionId.isBlank() || actionId.length() > MAX_ACTION_ID_LENGTH) {
            return false;
        }
        for (int i = 0; i < actionId.length(); i++) {
            if (Character.isISOControl(actionId.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public record ActionUsage(int count, double decayedCount, long lastUsedAtMillis) {
        public ActionUsage() {
            this(0, 0.0, 0L);
        }
    }
}
